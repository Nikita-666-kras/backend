import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  calculateKp,
  createProposal,
  downloadPdf,
  fetchZipPackage,
  finalizeProposal as finalizeApi,
  getProposal,
  kitPreset,
  listModels,
  updateProposal
} from '@/api/kp'
import { useToastStore } from '@/stores/toast'
import type { CalcPreview, DroneModel, Proposal, ProposalLine, ZipPackage } from '@/types/kp'

const DEFAULT_RECIPIENT = 'Уважаемый клиент'
const ZIP_SKU_PREFIX = 'ZIP-'

function isZipLine(line: ProposalLine) {
  return line.lineType === 'KIT' && (line.sku || '').startsWith(ZIP_SKU_PREFIX)
}

export const useKpEditorStore = defineStore('kpEditor', () => {
  const toast = useToastStore()

  const draftId = ref<string | null>(null)
  const recipient = ref(DEFAULT_RECIPIENT)
  const modelId = ref('')
  const kitQty = ref(1)
  const unitKitPrice = ref(0)
  const extraLines = ref<ProposalLine[]>([])
  const zipPackage = ref<ZipPackage | null>(null)
  const zipTipOpen = ref(false)
  const preview = ref<CalcPreview | null>(null)
  const models = ref<DroneModel[]>([])
  const dirty = ref(false)
  const loading = ref(false)
  const status = ref<'DRAFT' | 'FINAL' | null>(null)
  const calcError = ref('')

  const catalogExtras = computed(() => extraLines.value.filter((l) => !isZipLine(l)))
  const zipIncluded = computed(() => extraLines.value.some(isZipLine))
  const zipAvailable = computed(() => (zipPackage.value?.items?.length || 0) > 0)

  const extrasTotal = computed(() =>
    extraLines.value.reduce((sum, l) => {
      const mult = l.lineType === 'KIT' ? (100 - (l.discountPct || 0)) / 100 : 1
      return sum + Number(l.unitPrice || 0) * Number(l.qty || 0) * mult
    }, 0)
  )

  const grandTotal = computed(() => Number(preview.value?.grandTotal || 0) + extrasTotal.value)
  const startPrice = computed(() => Number(preview.value?.startPrice || 0))

  function markDirty() {
    dirty.value = true
  }

  function reset() {
    draftId.value = null
    recipient.value = DEFAULT_RECIPIENT
    modelId.value = models.value[0]?.id || ''
    kitQty.value = 1
    unitKitPrice.value = Number(models.value[0]?.defaultPrice || 0)
    extraLines.value = []
    zipPackage.value = null
    zipTipOpen.value = false
    preview.value = null
    status.value = null
    calcError.value = ''
    dirty.value = false
  }

  function snapshotFromProposal(p: Proposal) {
    draftId.value = p.id
    recipient.value = p.recipient
    modelId.value = p.droneModelId
    kitQty.value = p.kitQty || 1
    unitKitPrice.value = Number(p.unitKitPrice ?? p.grandTotal)
    status.value = p.status
    // строки калькулятора без refId; каталог / ЗИП — с refId
    extraLines.value = (p.lines || [])
      .filter((l) => l.refId)
      .map((l) => ({
        lineType: l.lineType === 'DRONE' ? 'PART' : l.lineType,
        refId: l.refId || '',
        sku: l.sku || '',
        name: l.name,
        qty: l.qty,
        unitPrice: Number(l.unitPrice),
        discountPct: l.discountPct || 0,
        kitItems: l.kitItems?.map((i) => ({
          partId: i.partId || '',
          partSku: i.partSku || '',
          partName: i.partName,
          qty: i.qty,
          partPrice: Number(i.partPrice || 0)
        }))
      }))
    dirty.value = false
  }

  function validateInputs(): string | null {
    if (!recipient.value.trim()) return 'Укажите, для кого КП'
    if (!modelId.value) return 'Выберите модель'
    if (!Number.isInteger(kitQty.value) || kitQty.value < 1) {
      return 'Количество комплектов должно быть целым числом >= 1'
    }
    if (!Number.isFinite(unitKitPrice.value) || unitKitPrice.value < 0) {
      return 'Цена одного комплекта должна быть числом >= 0'
    }
    return null
  }

  async function loadZipForModel(id: string) {
    zipTipOpen.value = false
    if (!id) {
      zipPackage.value = null
      return
    }
    try {
      const pkg = await fetchZipPackage(id)
      zipPackage.value = (pkg.items || []).length ? pkg : null
    } catch {
      zipPackage.value = null
    }
  }

  function stripZipLines() {
    extraLines.value = extraLines.value.filter((l) => !isZipLine(l))
  }

  function buildZipLine(pkg: ZipPackage, model: DroneModel | undefined): ProposalLine {
    return {
      lineType: 'KIT',
      refId: pkg.droneModelId,
      sku: `${ZIP_SKU_PREFIX}${model?.code || 'PKG'}`,
      name: pkg.name || 'ЗИП-пакет',
      qty: 1,
      unitPrice: Number(pkg.price || 0),
      discountPct: 0,
      kitItems: (pkg.items || []).map((i) => ({
        partId: i.id || '',
        partSku: i.sku || '',
        partName: i.name,
        qty: i.qty || 1,
        partPrice: Number(i.unitPrice || 0)
      }))
    }
  }

  function setZipIncluded(include: boolean) {
    stripZipLines()
    if (include && zipPackage.value) {
      const model = models.value.find((m) => m.id === modelId.value)
      extraLines.value.push(buildZipLine(zipPackage.value, model))
    }
    markDirty()
  }

  function toggleZipTip() {
    zipTipOpen.value = !zipTipOpen.value
  }

  async function loadModels() {
    models.value = await listModels()
    if (!modelId.value && models.value.length) {
      modelId.value = models.value[0].id
      unitKitPrice.value = Number(models.value[0].defaultPrice)
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
      await loadZipForModel(modelId.value)
      await refreshPreview()
    } finally {
      loading.value = false
    }
  }

  async function applyModel(id: string) {
    const keepZip = zipIncluded.value
    modelId.value = id
    stripZipLines()
    await loadZipForModel(id)
    try {
      const preset = await kitPreset(id)
      unitKitPrice.value = Number(preset.startPrice)
      markDirty()
      await refreshPreview()
    } catch (e: unknown) {
      const m = models.value.find((x) => x.id === id)
      unitKitPrice.value = Number(m?.defaultPrice || 0)
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg || 'Для этой модели нет прайса калькулятора')
      await refreshPreview()
    }
    if (keepZip && zipAvailable.value) {
      setZipIncluded(true)
    }
  }

  async function refreshPreview() {
    calcError.value = ''
    if (!modelId.value) {
      preview.value = null
      return
    }
    if (!Number.isInteger(kitQty.value) || kitQty.value < 1) {
      preview.value = null
      calcError.value = 'Количество комплектов должно быть целым числом >= 1'
      return
    }
    if (!Number.isFinite(unitKitPrice.value) || unitKitPrice.value < 0) {
      preview.value = null
      calcError.value = 'Цена одного комплекта должна быть числом >= 0'
      return
    }
    try {
      preview.value = await calculateKp({
        droneModelId: modelId.value,
        kitQty: kitQty.value,
        unitKitPrice: Number(unitKitPrice.value || 0)
      })
    } catch (e: unknown) {
      preview.value = null
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      calcError.value = msg || 'Не удалось посчитать КП'
    }
  }

  function addLines(lines: ProposalLine[]) {
    for (const line of lines) {
      extraLines.value.push({ ...line, qty: line.qty || 1, discountPct: line.discountPct || 0 })
    }
    markDirty()
  }

  function removeExtra(index: number) {
    const target = catalogExtras.value[index]
    if (!target) return
    const realIdx = extraLines.value.indexOf(target)
    if (realIdx >= 0) extraLines.value.splice(realIdx, 1)
    markDirty()
  }

  function buildPayload() {
    return {
      recipient: recipient.value.trim(),
      droneModelId: modelId.value,
      kitQty: kitQty.value,
      unitKitPrice: Number(unitKitPrice.value || 0),
      extraLines: extraLines.value.map((line) => ({
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
    }
  }

  async function saveDraft() {
    const err = validateInputs()
    if (err) {
      toast.error(err)
      return null
    }
    if (calcError.value) {
      toast.error(calcError.value)
      return null
    }

    loading.value = true
    try {
      const payload = buildPayload()
      const proposal = draftId.value ? await updateProposal(draftId.value, payload) : await createProposal(payload)
      snapshotFromProposal(proposal)
      await loadZipForModel(modelId.value)
      await refreshPreview()
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
      toast.ok(`КП №${finalized.number} готово`)
      return finalized
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg || 'Не удалось сформировать PDF')
      return null
    } finally {
      loading.value = false
    }
  }

  return {
    draftId,
    recipient,
    modelId,
    kitQty,
    unitKitPrice,
    extraLines,
    catalogExtras,
    zipPackage,
    zipAvailable,
    zipIncluded,
    zipTipOpen,
    preview,
    models,
    dirty,
    loading,
    status,
    calcError,
    extrasTotal,
    grandTotal,
    startPrice,
    markDirty,
    reset,
    snapshotFromProposal,
    loadModels,
    loadDraft,
    applyModel,
    refreshPreview,
    addLines,
    removeExtra,
    setZipIncluded,
    toggleZipTip,
    loadZipForModel,
    saveDraft,
    finalizeAndDownload
  }
})
