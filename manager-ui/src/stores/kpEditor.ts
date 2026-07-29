import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  createFromPreset,
  createProposal,
  downloadPdf,
  finalizeProposal as finalizeApi,
  getProposal,
  kitPreset,
  listModels,
  updateProposal
} from '@/api/kp'
import { useToastStore } from '@/stores/toast'
import type { DroneModel, Proposal, ProposalLine } from '@/types/kp'

export const useKpEditorStore = defineStore('kpEditor', () => {
  const toast = useToastStore()

  const draftId = ref<string | null>(null)
  const recipient = ref('')
  const modelId = ref('')
  const dronePrice = ref(0)
  const lines = ref<ProposalLine[]>([])
  const models = ref<DroneModel[]>([])
  const dirty = ref(false)
  const loading = ref(false)
  const status = ref<'DRAFT' | 'FINAL' | null>(null)

  const linesSubtotal = computed(() =>
    lines.value.reduce((sum, l) => {
      const mult = l.lineType === 'KIT' ? (100 - l.discountPct) / 100 : 1
      return sum + l.unitPrice * l.qty * mult
    }, 0)
  )
  const discountTotal = computed(() =>
    lines.value.reduce((sum, l) => {
      if (l.lineType !== 'KIT' || !l.discountPct) return sum
      return sum + l.unitPrice * l.qty * (l.discountPct / 100)
    }, 0)
  )
  const grandTotal = computed(() => dronePrice.value + linesSubtotal.value)
  const ndsTotal = computed(() => (grandTotal.value * 22) / 122)

  function markDirty() {
    dirty.value = true
  }

  function reset() {
    draftId.value = null
    recipient.value = ''
    modelId.value = models.value[0]?.id || ''
    dronePrice.value = Number(models.value[0]?.defaultPrice || 0)
    lines.value = []
    status.value = null
    dirty.value = false
  }

  function snapshotFromProposal(p: Proposal) {
    draftId.value = p.id
    recipient.value = p.recipient
    modelId.value = p.droneModelId
    dronePrice.value = Number(p.dronePrice)
    status.value = p.status
    lines.value = p.lines.map((l) => ({
      lineType: l.lineType === 'DRONE' ? 'PART' : l.lineType,
      refId: l.refId || '',
      sku: l.sku || '',
      name: l.name,
      qty: l.qty,
      unitPrice: Number(l.unitPrice),
      discountPct: l.discountPct || 0,
      kitItems: l.kitItems?.length
        ? l.kitItems.map((i) => ({
            partId: i.partId || '',
            partSku: i.partSku || '',
            partName: i.partName,
            qty: i.qty,
            partPrice: Number(i.partPrice || 0)
          }))
        : undefined
    }))
    dirty.value = false
  }

  async function loadModels() {
    models.value = await listModels()
    if (!modelId.value && models.value.length) {
      modelId.value = models.value[0].id
      dronePrice.value = Number(models.value[0].defaultPrice)
    }
  }

  async function loadDraft(id: string) {
    loading.value = true
    try {
      const p = await getProposal(id)
      if (p.status !== 'DRAFT') {
        toast.info('Финальное КП открыто в режиме просмотра')
      }
      snapshotFromProposal(p)
    } finally {
      loading.value = false
    }
  }

  async function applyModelPreset(id: string) {
    modelId.value = id
    try {
      const preset = await kitPreset(id)
      dronePrice.value = Number(preset.dronePrice)
      lines.value = preset.lines.map((l) => ({
        lineType: l.lineType === 'DRONE' ? 'PART' : l.lineType,
        refId: l.refId || '',
        sku: l.sku || '',
        name: l.name,
        qty: l.qty,
        unitPrice: Number(l.unitPrice),
        discountPct: l.discountPct || 0
      }))
      markDirty()
      toast.ok('Шаблон комплекта загружен')
    } catch {
      const m = models.value.find((x) => x.id === id)
      dronePrice.value = Number(m?.defaultPrice || 0)
      toast.info('Для этой модели нет шаблона — указана базовая цена')
    }
  }

  function addLine(line: ProposalLine) {
    lines.value.push({ ...line })
    markDirty()
  }

  function removeLine(index: number) {
    lines.value.splice(index, 1)
    markDirty()
  }

  function buildPayload() {
    const payloadLines = lines.value
      .filter((l) => l.name.trim())
      .map((line) => ({
        lineType: line.lineType,
        refId: line.refId || null,
        sku: line.sku || '',
        name: line.name,
        qty: line.qty,
        unitPrice: Number(line.unitPrice || 0),
        discountPct: line.discountPct || 0,
        kitItems:
          line.lineType === 'KIT' && line.kitItems?.length
            ? line.kitItems.map((item) => ({
                partId: item.partId || null,
                partSku: item.partSku || '',
                partName: item.partName,
                qty: item.qty,
                partPrice: Number(item.partPrice || 0)
              }))
            : []
      }))

    return {
      recipient: recipient.value.trim(),
      droneModelId: modelId.value,
      dronePrice: dronePrice.value,
      lines: payloadLines
    }
  }

  async function saveDraft() {
    if (!recipient.value.trim()) {
      toast.error('Укажите получателя')
      return null
    }
    if (!modelId.value) {
      toast.error('Выберите модель дрона')
      return null
    }

    loading.value = true
    try {
      const payload = buildPayload()
      const proposal = draftId.value ? await updateProposal(draftId.value, payload) : await createProposal(payload)
      snapshotFromProposal(proposal)
      toast.ok('Черновик сохранён')
      return proposal
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg || 'Не удалось сохранить черновик')
      return null
    } finally {
      loading.value = false
    }
  }

  async function finalizeAndDownload() {
    const saved = await saveDraft()
    if (!saved?.id) return null

    loading.value = true
    try {
      const finalized = await finalizeApi(saved.id)
      await downloadPdf(finalized.id, `KP_${finalized.number}.pdf`)
      snapshotFromProposal(finalized)
      toast.ok(`PDF КП №${finalized.number} готов`)
      return finalized
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg || 'Не удалось сформировать PDF')
      return null
    } finally {
      loading.value = false
    }
  }

  async function quickPresetPdf() {
    if (!recipient.value.trim()) {
      toast.error('Укажите получателя')
      return null
    }

    loading.value = true
    try {
      const proposal = await createFromPreset(recipient.value.trim(), modelId.value)
      const finalized = await finalizeApi(proposal.id)
      await downloadPdf(finalized.id, `KP_${finalized.number}.pdf`)
      toast.ok(`PDF КП №${finalized.number} сформирован`) 
      return finalized
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg || 'Не удалось сформировать КП из шаблона')
      return null
    } finally {
      loading.value = false
    }
  }

  return {
    draftId,
    recipient,
    modelId,
    dronePrice,
    lines,
    models,
    dirty,
    loading,
    status,
    linesSubtotal,
    discountTotal,
    grandTotal,
    ndsTotal,
    markDirty,
    reset,
    snapshotFromProposal,
    loadModels,
    loadDraft,
    applyModelPreset,
    addLine,
    removeLine,
    saveDraft,
    finalizeAndDownload,
    quickPresetPdf
  }
})
