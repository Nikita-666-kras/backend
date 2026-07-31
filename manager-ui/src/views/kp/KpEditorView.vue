<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import KpCatalogSearch from '@/components/kp/KpCatalogSearch.vue'
import { useUnsavedGuard } from '@/composables/useUnsavedGuard'
import { useKpEditorStore } from '@/stores/kpEditor'
import type { ProposalLine } from '@/types/kp'

const route = useRoute()
const router = useRouter()
const editor = useKpEditorStore()
const catalogRef = ref<InstanceType<typeof KpCatalogSearch> | null>(null)
const catalogOpen = ref(false)

useUnsavedGuard(editor.dirty)

function money(v: number) {
  return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 }).format(Number(v || 0))
}

async function openCatalog() {
  catalogOpen.value = true
  await catalogRef.value?.lookup()
}

function closeCatalog() {
  catalogOpen.value = false
}

async function initializeEditor(id?: string) {
  catalogOpen.value = false
  editor.reset()
  await editor.loadModels()

  if (id) {
    await editor.loadDraft(id)
  } else {
    const clone = sessionStorage.getItem('manager_kp_clone')
    if (clone) {
      try {
        const snapshot = JSON.parse(clone) as {
          recipient: string
          droneModelId: string
          kitQty: number
          unitKitPrice: number
          extraLines?: ProposalLine[]
        }
        editor.recipient = snapshot.recipient
        editor.modelId = snapshot.droneModelId
        editor.kitQty = snapshot.kitQty || 1
        editor.unitKitPrice = Number(snapshot.unitKitPrice)
        if (snapshot.extraLines?.length) editor.extraLines = snapshot.extraLines
        editor.markDirty()
        await editor.loadZipForModel(editor.modelId)
        await editor.refreshPreview()
      } finally {
        sessionStorage.removeItem('manager_kp_clone')
      }
    } else if (editor.models.length) {
      await editor.applyModel(editor.models[0].id)
    }
  }
}

watch(
  () => route.params.id,
  async (nextId) => {
    await initializeEditor(typeof nextId === 'string' ? nextId : undefined)
  },
  { immediate: true }
)

watch(
  () => [editor.kitQty, editor.unitKitPrice] as const,
  async () => {
    if (!editor.modelId) return
    editor.markDirty()
    await editor.refreshPreview()
  }
)

async function onGenerate() {
  const finalized = await editor.finalizeAndDownload()
  if (finalized?.id) router.replace(`/kp/${finalized.id}`)
}

async function onModelChange() {
  await editor.applyModel(editor.modelId)
}

function onAddLines(lines: ProposalLine[]) {
  editor.addLines(lines)
}
</script>

<template>
  <div class="calc">
    <header class="head">
      <h1>{{ route.name === 'kp-edit' ? 'КП' : 'Новый КП' }}</h1>
      <button class="link" type="button" @click="router.push('/kp')">Мои КП</button>
    </header>

    <form class="panel" @submit.prevent="onGenerate">
      <label class="field">
        <span>Модель</span>
        <select v-model="editor.modelId" required @change="onModelChange">
          <option v-for="m in editor.models" :key="m.id" :value="m.id">{{ m.name }}</option>
        </select>
      </label>

      <label class="field">
        <span>Для кого</span>
        <input v-model="editor.recipient" required placeholder="Уважаемый клиент" @input="editor.markDirty" />
      </label>

      <div class="row2">
        <label class="field">
          <span>Количество</span>
          <input v-model.number="editor.kitQty" type="number" min="1" step="1" required />
        </label>
        <label class="field">
          <span>Цена комплекта, ₽</span>
          <input v-model.number="editor.unitKitPrice" type="number" min="0" step="1" required />
        </label>
      </div>

      <p v-if="editor.calcError" class="error">{{ editor.calcError }}</p>

      <div v-if="editor.zipAvailable && editor.zipPackage" class="block">
        <p class="block-title">Комплектация</p>
        <div class="zip-card" :class="{ on: editor.zipIncluded }">
          <button
            class="zip-tip"
            type="button"
            title="Состав ЗИП-пакета"
            aria-label="Состав ЗИП-пакета"
            @click="editor.toggleZipTip()"
          >
            ?
          </button>
          <label class="zip-main">
            <input
              type="checkbox"
              :checked="editor.zipIncluded"
              @change="editor.setZipIncluded(($event.target as HTMLInputElement).checked)"
            />
            <span class="zip-text">
              <strong>{{ editor.zipPackage.name }}</strong>
              <em>{{ money(Number(editor.zipPackage.price)) }} ₽</em>
            </span>
          </label>
          <div v-if="editor.zipTipOpen" class="zip-popover">
            <p class="zip-popover-title">Состав</p>
            <ul>
              <li v-for="item in editor.zipPackage.items" :key="item.id">
                <span>{{ item.name }}</span>
                <em>×{{ item.qty }}</em>
              </li>
            </ul>
          </div>
        </div>
      </div>

      <div class="block">
        <div class="block-head">
          <p class="block-title">Дополнительно</p>
          <button
            v-if="!catalogOpen"
            class="btn secondary add-parts"
            type="button"
            @click="openCatalog"
          >
            Добавить запчасти
          </button>
          <button v-else class="link" type="button" @click="closeCatalog">Скрыть каталог</button>
        </div>

        <KpCatalogSearch v-show="catalogOpen" ref="catalogRef" @add-lines="onAddLines" />

        <ul v-if="editor.catalogExtras.length" class="extras">
          <li v-for="(line, idx) in editor.catalogExtras" :key="`${line.refId}-${idx}`">
            <div class="extra-main">
              <strong>{{ line.name }}</strong>
              <span class="muted">{{ line.lineType === 'KIT' ? 'комплект' : 'запчасть' }}</span>
            </div>
            <input
              v-model.number="line.qty"
              class="qty"
              type="number"
              min="1"
              step="1"
              @input="editor.markDirty"
            />
            <span class="price">{{ money(line.unitPrice * line.qty) }} ₽</span>
            <button class="link danger" type="button" @click="editor.removeExtra(idx)">Убрать</button>
          </li>
        </ul>
        <p v-else-if="!catalogOpen" class="muted tiny">Запчасти и комплекты из каталога — по кнопке выше. ЗИП отдельно.</p>
      </div>

      <div v-if="!editor.calcError" class="sum">
        <span>Итого</span>
        <strong>{{ money(editor.grandTotal) }} ₽</strong>
      </div>
      <p v-if="editor.extrasTotal > 0 && !editor.calcError" class="muted hint">
        Базовый комплект {{ money(Number(editor.preview?.grandTotal || 0)) }} ₽
        + доп. {{ money(editor.extrasTotal) }} ₽
      </p>

      <button class="btn generate" type="submit" :disabled="editor.loading || !!editor.calcError">
        {{ editor.loading ? 'Формируем…' : 'Сгенерировать КП' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.calc {
  max-width: 640px;
  margin: 0 auto;
}

.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.25rem;
}

.head h1 {
  margin: 0;
  font-size: 1.45rem;
  font-weight: 700;
}

.link {
  border: 0;
  background: transparent;
  color: var(--muted);
  padding: 0;
  font-size: 0.9rem;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.link:hover { color: var(--ink); }
.link.danger { color: #ffb4b4; }

.panel {
  display: grid;
  gap: 0.9rem;
  padding: 1.15rem 1.2rem 1.25rem;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--glass);
}

.row2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

.field { display: grid; gap: 0.35rem; }
.field span { font-size: 0.82rem; color: var(--muted); }
.field input,
.field select {
  width: 100%;
  min-height: 2.6rem;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: var(--input-bg);
  padding: 0.55rem 0.75rem;
}

.block {
  display: grid;
  gap: 0.65rem;
  padding-top: 0.35rem;
  border-top: 1px solid var(--line);
}

.block-title {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.add-parts {
  min-height: 2.2rem;
  padding: 0.35rem 0.75rem;
  font-size: 0.85rem;
  white-space: nowrap;
}

.tiny {
  margin: 0;
  font-size: 0.8rem;
}

.extras {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.4rem;
}

.extras li {
  display: grid;
  grid-template-columns: 1fr 70px auto auto;
  gap: 0.45rem;
  align-items: center;
  padding: 0.45rem 0.55rem;
  border: 1px solid var(--line);
  border-radius: 10px;
}

.extra-main { display: grid; gap: 0.1rem; min-width: 0; }
.extra-main strong {
  font-size: 0.88rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.extra-main span { font-size: 0.75rem; }

.qty {
  min-height: 2.2rem;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: var(--input-bg);
  padding: 0.3rem 0.45rem;
}

.price { font-size: 0.85rem; white-space: nowrap; }

.sum {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding-top: 0.35rem;
  border-top: 1px solid var(--line);
}

.sum span { color: var(--muted); }
.sum strong { font-size: 1.25rem; }

.hint { margin: -0.35rem 0 0; font-size: 0.8rem; }
.error { margin: 0; color: #ffb4b4; font-size: 0.88rem; }
.generate { width: 100%; min-height: 2.75rem; }

.zip-card {
  position: relative;
  padding: 0.75rem 0.85rem;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--input-bg);
}

.zip-card.on {
  border-color: color-mix(in srgb, var(--accent, #6ea8fe) 55%, var(--line));
}

.zip-tip {
  position: absolute;
  top: 0.35rem;
  right: 0.35rem;
  width: 1.55rem;
  height: 1.55rem;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--glass);
  color: var(--muted);
  font-size: 0.78rem;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
}

.zip-tip:hover {
  color: var(--ink);
}

.zip-main {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  padding-right: 1.75rem;
  cursor: pointer;
}

.zip-main input {
  margin-top: 0.2rem;
}

.zip-text {
  display: grid;
  gap: 0.15rem;
  min-width: 0;
}

.zip-text strong {
  font-size: 0.95rem;
}

.zip-text em {
  font-style: normal;
  color: var(--muted);
  font-size: 0.85rem;
}

.zip-popover {
  margin-top: 0.65rem;
  padding: 0.55rem 0.65rem;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: var(--glass);
}

.zip-popover-title {
  margin: 0 0 0.35rem;
  font-size: 0.78rem;
  color: var(--muted);
}

.zip-popover ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.25rem;
}

.zip-popover li {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  font-size: 0.85rem;
}

.zip-popover em {
  font-style: normal;
  color: var(--muted);
  white-space: nowrap;
}

@media (max-width: 640px) {
  .calc { max-width: none; }
  .row2, .extras li { grid-template-columns: 1fr; }
}
</style>
