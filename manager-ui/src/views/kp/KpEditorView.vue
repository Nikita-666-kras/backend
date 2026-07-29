<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import KpCatalogSearch from '@/components/kp/KpCatalogSearch.vue'
import KpLinesTable from '@/components/kp/KpLinesTable.vue'
import KpModelPicker from '@/components/kp/KpModelPicker.vue'
import KpStepHeader from '@/components/kp/KpStepHeader.vue'
import KpSummaryBar from '@/components/kp/KpSummaryBar.vue'
import { useUnsavedGuard } from '@/composables/useUnsavedGuard'
import { useKpEditorStore } from '@/stores/kpEditor'
import { useToastStore } from '@/stores/toast'
import type { ProposalLine } from '@/types/kp'

const route = useRoute()
const router = useRouter()
const editor = useKpEditorStore()
const toast = useToastStore()
const catalogRef = ref<InstanceType<typeof KpCatalogSearch> | null>(null)

useUnsavedGuard(editor.dirty)

onMounted(async () => {
  await editor.loadModels()
  const id = route.params.id as string | undefined

  if (id) {
    await editor.loadDraft(id)
  } else {
    const clone = sessionStorage.getItem('manager_kp_clone')
    if (clone) {
      const snapshot = JSON.parse(clone) as {
        recipient: string
        droneModelId: string
        dronePrice: number
        lines: ProposalLine[]
      }
      editor.recipient = snapshot.recipient
      editor.modelId = snapshot.droneModelId
      editor.dronePrice = Number(snapshot.dronePrice)
      editor.lines = snapshot.lines
      editor.markDirty()
      sessionStorage.removeItem('manager_kp_clone')
      toast.ok('Копия КП загружена в редактор')
    } else if (editor.models.length && !editor.modelId) {
      await editor.applyModelPreset(editor.models[0].id)
    }
  }

  await catalogRef.value?.lookup()
})

function addLines(lines: ProposalLine[]) {
  for (const line of lines) editor.addLine(line)
}

async function onSave() {
  const saved = await editor.saveDraft()
  if (saved?.id && route.name !== 'kp-edit') {
    router.replace(`/kp/${saved.id}`)
  }
}

async function onFinalize() {
  const finalized = await editor.finalizeAndDownload()
  if (finalized?.id) {
    router.replace(`/kp/${finalized.id}`)
  }
}

async function onPresetPdf() {
  await editor.quickPresetPdf()
}

function onReset() {
  if (editor.dirty && !window.confirm('Сбросить все изменения?')) return
  editor.reset()
  if (editor.models.length) {
    editor.applyModelPreset(editor.models[0].id)
  }
}
</script>

<template>
  <div>
    <header class="page-header">
      <div>
        <p class="eyebrow">КП</p>
        <h1>{{ route.name === 'kp-edit' ? 'Редактирование КП' : 'Новый КП' }}</h1>
        <p class="subtitle">Редактируйте состав: удаляйте лишние позиции, меняйте количество и стоимость.</p>
      </div>
      <div class="row-actions actions-row">
        <button class="btn secondary" type="button" @click="onReset">Сброс</button>
        <button class="btn secondary" type="button" @click="router.push('/kp')">К списку</button>
      </div>
    </header>

    <KpStepHeader :step="3" />

    <div class="editor-grid">
      <section class="card side-panel section-pad">
        <div class="field">
          <label>Получатель</label>
          <input v-model="editor.recipient" placeholder="ООО АгроТех" @input="editor.markDirty" />
        </div>

        <div class="field">
          <label>Базовая цена дрона</label>
          <input v-model.number="editor.dronePrice" type="number" min="0" step="0.01" @input="editor.markDirty" />
        </div>

        <KpModelPicker :models="editor.models" :model-id="editor.modelId" @pick="editor.applyModelPreset" />
        <KpCatalogSearch ref="catalogRef" @add-lines="addLines" />
      </section>

      <section class="card main-panel section-pad">
        <KpLinesTable :lines="editor.lines" @remove="editor.removeLine" @dirty="editor.markDirty" />
      </section>
    </div>

    <KpSummaryBar
      :subtotal="editor.linesSubtotal"
      :discount="editor.discountTotal"
      :total="editor.grandTotal"
      :nds="editor.ndsTotal"
      :loading="editor.loading"
      @save="onSave"
      @finalize="onFinalize"
      @preset="onPresetPdf"
    />
  </div>
</template>

<style scoped>
.editor-grid { display: grid; grid-template-columns: 1fr; gap: 0.9rem; }
.side-panel, .main-panel { display: grid; gap: 0.8rem; align-content: start; }
.side-panel { position: static; max-height: none; overflow: visible; }
@media (max-width: 768px) {
  .row-actions { width: 100%; display: grid; grid-template-columns: 1fr; }
  .row-actions .btn { width: 100%; }
}
</style>
