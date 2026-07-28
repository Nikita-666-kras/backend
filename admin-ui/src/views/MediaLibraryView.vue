<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import {
  deleteMedia,
  fetchMedia,
  fetchProcessingSettings,
  isProcessableImage,
  isWebp,
  mediaPublicUrl,
  processMedia,
  processMediaBatch,
  uploadMedia,
  type MediaAsset,
  type MediaPage,
  type MediaProcessOptions,
  type MediaSection
} from '@/api/media'
import { useAuthStore } from '@/stores/auth'
import { useMediaLibraryStore } from '@/stores/mediaLibrary'

const auth = useAuthStore()
const route = useRoute()
const mediaStore = useMediaLibraryStore()
const {
  kind,
  section,
  q,
  page,
  pageSize,
  autoSquare,
  autoWatermark,
  autoWebp,
  squareBackground,
  watermarkOpacity,
  bgThreshold,
  uploadSection
} = storeToRefs(mediaStore)

const data = ref<MediaPage | null>(null)
const loading = ref(false)
const uploading = ref(false)
const processing = ref(false)
const progress = ref(0)
const processLabel = ref('')
const error = ref('')
const message = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const previewItem = ref<MediaAsset | null>(null)
const processingFilter = ref<'all' | 'no_square' | 'no_watermark' | 'incomplete' | 'not_webp'>('all')
const selectedIds = ref<string[]>([])
const settingsOpen = ref(false)
const logoOk = ref(false)

const sectionOptions: Array<{ value: MediaSection; label: string }> = [
  { value: 'PARTS', label: 'Запчасти' },
  { value: 'DRONES', label: 'Дроны' },
  { value: 'ARTICLES', label: 'Статьи' },
  { value: 'SERVICE', label: 'Сервис' },
  { value: 'TRAILERS', label: 'Прицепы' },
  { value: 'EDUCATION', label: 'Обучение' },
  { value: 'OTHER', label: 'Другое' }
]

const sectionLabelByValue = Object.fromEntries(sectionOptions.map((s) => [s.value, s.label])) as Record<
  MediaSection,
  string
>

const selectedCount = computed(() => selectedIds.value.length)
const processableOnPage = computed(() => data.value?.content.filter(isProcessableImage) ?? [])
const allPageSelected = computed(
  () => processableOnPage.value.length > 0 && processableOnPage.value.every((i) => selectedIds.value.includes(i.id))
)
const busy = computed(() => uploading.value || processing.value || loading.value)
const autoPipelineLabel = computed(() => {
  const parts: string[] = []
  if (autoSquare.value) parts.push('квадрат')
  if (autoWatermark.value) parts.push('знак')
  if (autoWebp.value) parts.push('WebP')
  return parts.length ? parts.join(' → ') : 'без авто'
})

function formatSize(bytes: number) {
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} МБ`
  return `${Math.round(bytes / 1024)} КБ`
}

function imageSrc(item: MediaAsset) {
  return mediaPublicUrl(item.url, item.updatedAt)
}

function sectionLabel(value: MediaSection) {
  return sectionLabelByValue[value]
}

function chooseSection(value: MediaSection | '') {
  section.value = value
  if (value) uploadSection.value = value
}

function openPreview(item: MediaAsset) {
  previewItem.value = item
}

function closePreview() {
  previewItem.value = null
}

function processOptions(partial: MediaProcessOptions): MediaProcessOptions {
  return {
    square: !!partial.square,
    watermark: !!partial.watermark,
    convertToWebp: !!partial.convertToWebp,
    backgroundColor: squareBackground.value || undefined,
    opacity: partial.watermark ? watermarkOpacity.value : undefined,
    bgThreshold: partial.watermark ? bgThreshold.value : undefined
  }
}

function listParams() {
  const params: Record<string, string | number | boolean | undefined> = {
    kind: kind.value || undefined,
    section: section.value || undefined,
    q: q.value || undefined,
    page: page.value,
    size: pageSize.value
  }
  if (processingFilter.value === 'no_square') {
    params.kind = 'IMAGE'
    params.square = false
  } else if (processingFilter.value === 'no_watermark') {
    params.kind = 'IMAGE'
    params.watermark = false
  } else if (processingFilter.value === 'incomplete') {
    params.kind = 'IMAGE'
    params.incomplete = true
  } else if (processingFilter.value === 'not_webp') {
    params.kind = 'IMAGE'
  }
  return params
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await fetchMedia(listParams() as any)
    if (processingFilter.value === 'not_webp' && data.value) {
      data.value = {
        ...data.value,
        content: data.value.content.filter((item) => item.kind === 'IMAGE' && !isWebp(item))
      }
    }
    const visible = new Set(data.value.content.map((item) => item.id))
    selectedIds.value = selectedIds.value.filter((id) => visible.has(id))
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Не удалось загрузить медиатеку'
  } finally {
    loading.value = false
  }
}

async function loadSettings() {
  try {
    const settings = await fetchProcessingSettings()
    logoOk.value = settings.logoAvailable
    if (!squareBackground.value) squareBackground.value = settings.squareBackground
  } catch {
    logoOk.value = false
  }
}

function toggleSelect(id: string, event?: Event) {
  event?.stopPropagation()
  if (selectedIds.value.includes(id)) {
    selectedIds.value = selectedIds.value.filter((x) => x !== id)
  } else {
    selectedIds.value = [...selectedIds.value, id]
  }
}

function selectAllOnPage() {
  selectedIds.value = processableOnPage.value.map((i) => i.id)
}

function clearSelection() {
  selectedIds.value = []
}

function toggleSelectAll() {
  if (allPageSelected.value) clearSelection()
  else selectAllOnPage()
}

function mergeProcessed(updated: MediaAsset) {
  if (data.value) {
    data.value = {
      ...data.value,
      content: data.value.content.map((item) => (item.id === updated.id ? updated : item))
    }
  }
  if (previewItem.value?.id === updated.id) previewItem.value = updated
}

async function runProcess(ids: string[], options: MediaProcessOptions, label: string) {
  if (!ids.length) {
    message.value = 'Выберите изображения'
    return
  }
  processing.value = true
  processLabel.value = label
  error.value = ''
  message.value = ''
  const payload = processOptions(options)
  try {
    if (ids.length === 1) {
      const updated = await processMedia(ids[0], payload)
      mergeProcessed(updated)
      message.value = `${label}: готово`
    } else {
      const result = await processMediaBatch(ids, payload)
      message.value = `${label}: ${result.processed} ок` + (result.failed ? `, ошибок ${result.failed}` : '')
      if (result.errors.length) error.value = result.errors.slice(0, 2).join('; ')
      await load()
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка обработки'
  } finally {
    processing.value = false
    processLabel.value = ''
  }
}

function targetIds(fallbackSelected = true) {
  if (fallbackSelected && selectedIds.value.length) {
    return selectedIds.value.filter((id) => {
      const item = data.value?.content.find((x) => x.id === id)
      return item && isProcessableImage(item)
    })
  }
  return []
}

async function runOnSelected(options: MediaProcessOptions, label: string) {
  await runProcess(targetIds(), options, label)
}

async function runOnOne(item: MediaAsset, options: MediaProcessOptions, label: string) {
  await runProcess([item.id], options, label)
}

async function onFiles(files: FileList | null) {
  if (!files?.length) return
  uploading.value = true
  error.value = ''
  message.value = ''
  const uploaded: MediaAsset[] = []
  try {
    const targetSection = section.value || uploadSection.value
    const list = Array.from(files)
    for (let i = 0; i < list.length; i++) {
      progress.value = Math.round(((i + 0.5) / list.length) * 100)
      const asset = await uploadMedia(list[i], targetSection, (pct) => {
        progress.value = Math.round(((i + pct / 100) / list.length) * 100)
      })
      uploaded.push(asset)
    }

    const needProcess = autoSquare.value || autoWatermark.value || autoWebp.value
    if (needProcess) {
      processing.value = true
      processLabel.value = `Авто: ${autoPipelineLabel.value}`
      const ids = uploaded.filter(isProcessableImage).map((a) => a.id)
      if (ids.length) {
        const result = await processMediaBatch(
          ids,
          processOptions({
            square: autoSquare.value,
            watermark: autoWatermark.value,
            convertToWebp: autoWebp.value
          })
        )
        message.value = `Загружено ${uploaded.length}, обработано ${result.processed}`
        if (result.failed) error.value = `Ошибок автообработки: ${result.failed}`
      } else {
        message.value = `Загружено: ${uploaded.length}`
      }
    } else {
      message.value = `Загружено: ${uploaded.length}`
    }
    page.value = 0
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка загрузки'
  } finally {
    uploading.value = false
    processing.value = false
    processLabel.value = ''
    progress.value = 0
    if (fileInput.value) fileInput.value.value = ''
  }
}

async function remove(item: MediaAsset) {
  if (!confirm(`Удалить «${item.originalName}»?`)) return
  try {
    await deleteMedia(item.id)
    selectedIds.value = selectedIds.value.filter((id) => id !== item.id)
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Удаление доступно только администратору'
  }
}

function copy(item: MediaAsset) {
  navigator.clipboard?.writeText(mediaPublicUrl(item.url))
  message.value = 'URL скопирован'
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  onFiles(e.dataTransfer?.files ?? null)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    if (previewItem.value) closePreview()
    else clearSelection()
    return
  }
  if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement || e.target instanceof HTMLSelectElement) {
    return
  }
  if (e.key === 'a' && (e.ctrlKey || e.metaKey)) {
    e.preventDefault()
    selectAllOnPage()
  }
  if (e.key === '1' && selectedCount.value) runOnSelected({ square: true }, 'Квадрат')
  if (e.key === '2' && selectedCount.value) runOnSelected({ watermark: true }, 'Watermark')
  if (e.key === '3' && selectedCount.value) runOnSelected({ convertToWebp: true }, 'WebP')
  if (e.key === '4' && selectedCount.value) {
    runOnSelected({ square: true, watermark: true, convertToWebp: true }, 'Полный пайплайн')
  }
}

onMounted(() => {
  const qSection = typeof route.query.section === 'string' ? route.query.section.toUpperCase() : ''
  const allowed: MediaSection[] = ['PARTS', 'DRONES', 'ARTICLES', 'SERVICE', 'TRAILERS', 'EDUCATION', 'OTHER']
  if (allowed.includes(qSection as MediaSection)) {
    section.value = qSection as MediaSection
    uploadSection.value = qSection as MediaSection
  }
  mediaStore.persistFilters()
  mediaStore.persistWorkflow()
  loadSettings()
  load()
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => window.removeEventListener('keydown', onKeydown))

watch(
  () => route.query.section,
  (value) => {
    if (typeof value !== 'string') return
    const next = value.toUpperCase()
    const allowed: MediaSection[] = ['PARTS', 'DRONES', 'ARTICLES', 'SERVICE', 'TRAILERS', 'EDUCATION', 'OTHER']
    if (!allowed.includes(next as MediaSection)) return
    section.value = next as MediaSection
    uploadSection.value = next as MediaSection
    page.value = 0
    mediaStore.persistFilters()
    load()
  }
)

watch([kind, section, processingFilter, pageSize], () => {
  page.value = 0
  mediaStore.persistFilters()
  load()
})
watch([q], () => mediaStore.persistFilters())
watch(
  [autoSquare, autoWatermark, autoWebp, squareBackground, watermarkOpacity, bgThreshold, uploadSection],
  () => mediaStore.persistWorkflow()
)
</script>

<template>
  <section class="media-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">Файлы</p>
        <h1>Медиатека</h1>
        <p class="muted">Быстрая обработка: квадрат → watermark → WebP · logo {{ logoOk ? 'OK' : 'авто' }}</p>
      </div>
      <div class="upload-group">
        <select v-model="uploadSection" :disabled="busy">
          <option v-for="item in sectionOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
        <label class="btn upload">
          {{ uploading ? `${progress}%` : 'Загрузить' }}
          <input
            ref="fileInput"
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif,image/tiff,video/mp4,video/webm"
            multiple
            hidden
            :disabled="busy"
            @change="onFiles(($event.target as HTMLInputElement).files)"
          />
        </label>
      </div>
    </header>

    <div class="drop card" @dragover.prevent @drop="onDrop">
      Drop файлы сюда · авто: <strong>{{ autoPipelineLabel }}</strong>
    </div>

    <div class="sticky-dock card">
      <div class="dock-row">
        <label class="check"><input v-model="autoSquare" type="checkbox" /> Квадрат</label>
        <label class="check"><input v-model="autoWatermark" type="checkbox" /> Знак</label>
        <label class="check"><input v-model="autoWebp" type="checkbox" /> WebP</label>
        <button class="btn secondary tiny" type="button" @click="settingsOpen = !settingsOpen">
          {{ settingsOpen ? 'Скрыть настройки' : 'Настройки' }}
        </button>
      </div>

      <div v-if="settingsOpen" class="settings-row">
        <label>Фон <input v-model="squareBackground" type="color" /><input v-model="squareBackground" class="hex" /></label>
        <label>Opacity <input v-model.number="watermarkOpacity" type="number" min="0.01" max="1" step="0.01" /></label>
        <label>Threshold <input v-model.number="bgThreshold" type="number" min="0" max="255" step="1" /></label>
      </div>

      <div class="dock-actions">
        <button class="btn secondary" type="button" :disabled="busy || !processableOnPage.length" @click="toggleSelectAll">
          {{ allPageSelected ? 'Снять всё' : 'Выбрать страницу' }}
        </button>
        <span class="sel">{{ selectedCount ? `Выбрано ${selectedCount}` : 'Ничего не выбрано' }}</span>
        <button
          class="btn secondary"
          :disabled="busy || !selectedCount"
          @click="runOnSelected({ square: true }, 'Квадрат')"
        >
          1:1
        </button>
        <button
          class="btn secondary"
          :disabled="busy || !selectedCount"
          @click="runOnSelected({ watermark: true }, 'Watermark')"
        >
          WM
        </button>
        <button
          class="btn secondary"
          :disabled="busy || !selectedCount"
          @click="runOnSelected({ convertToWebp: true }, 'WebP')"
        >
          WebP
        </button>
        <button
          class="btn"
          :disabled="busy || !selectedCount"
          @click="runOnSelected({ square: true, watermark: true, convertToWebp: true }, 'Полный пайплайн')"
        >
          1:1 + WM + WebP
        </button>
        <button class="btn secondary" type="button" :disabled="!selectedCount" @click="clearSelection">Сброс</button>
      </div>
      <p class="hint">Горячие клавиши: Ctrl+A · 1 квадрат · 2 знак · 3 WebP · 4 полный · Esc</p>
    </div>

    <div class="toolbar card">
      <input v-model="q" placeholder="Поиск…" @keyup.enter="page = 0; load()" />
      <select v-model="kind">
        <option value="">Все типы</option>
        <option value="IMAGE">Фото</option>
        <option value="VIDEO">Видео</option>
      </select>
      <select v-model="processingFilter">
        <option value="all">Все</option>
        <option value="incomplete">Нужна обработка</option>
        <option value="no_square">Без 1:1</option>
        <option value="no_watermark">Без WM</option>
        <option value="not_webp">Не WebP</option>
      </select>
      <select v-model.number="pageSize">
        <option :value="24">24</option>
        <option :value="48">48</option>
        <option :value="96">96</option>
      </select>
      <button class="btn secondary" @click="page = 0; load()">Обновить</button>
    </div>

    <div class="section-tabs">
      <button class="tab" :class="{ active: section === '' }" type="button" @click="chooseSection('')">Все</button>
      <button
        v-for="item in sectionOptions"
        :key="item.value"
        class="tab"
        :class="{ active: section === item.value }"
        type="button"
        @click="chooseSection(item.value)"
      >
        {{ item.label }}
      </button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="ok">{{ message }}</p>
    <p v-if="busy" class="status">{{ uploading ? `Загрузка ${progress}%` : processLabel || 'Обработка…' }}</p>

    <div v-if="data" class="grid">
      <article
        v-for="item in data.content"
        :key="item.id"
        class="card item"
        :class="{ selected: selectedIds.includes(item.id) }"
        @click="isProcessableImage(item) ? toggleSelect(item.id) : openPreview(item)"
      >
        <div class="preview">
          <img v-if="item.kind === 'IMAGE'" :src="imageSrc(item)" :alt="item.originalName" loading="lazy" />
          <video v-else :src="imageSrc(item)" muted preload="metadata" />
          <span class="kind">{{ item.kind === 'IMAGE' ? 'Фото' : 'Видео' }}</span>
          <span class="section-badge">{{ sectionLabel(item.section) }}</span>
          <span v-if="item.square" class="proc-badge square">1:1</span>
          <span v-if="item.watermark" class="proc-badge wm">WM</span>
          <span v-if="isWebp(item)" class="proc-badge webp">WebP</span>
          <div v-if="isProcessableImage(item)" class="quick" @click.stop>
            <button type="button" :disabled="busy" title="Квадрат" @click="runOnOne(item, { square: true }, 'Квадрат')">1:1</button>
            <button type="button" :disabled="busy" title="Watermark" @click="runOnOne(item, { watermark: true }, 'WM')">WM</button>
            <button type="button" :disabled="busy" title="WebP" @click="runOnOne(item, { convertToWebp: true }, 'WebP')">W</button>
            <button
              type="button"
              class="full"
              :disabled="busy"
              title="Полный пайплайн"
              @click="runOnOne(item, { square: true, watermark: true, convertToWebp: true }, 'Пайплайн')"
            >
              ★
            </button>
            <button type="button" title="Открыть" @click="openPreview(item)">⛶</button>
          </div>
        </div>
        <div class="meta">
          <strong :title="item.originalName">{{ item.originalName }}</strong>
          <span>{{ formatSize(item.sizeBytes) }} · {{ item.contentType.replace('image/', '') }}</span>
        </div>
        <div class="actions" @click.stop>
          <button class="btn secondary" @click="copy(item)">URL</button>
          <button v-if="auth.isAdmin" class="btn danger" @click="remove(item)">✕</button>
        </div>
      </article>
      <div v-if="!data.content.length" class="empty card">Пусто — загрузите файлы</div>
    </div>

    <div v-if="data && data.totalPages > 1" class="pager">
      <button class="btn secondary" :disabled="page === 0 || busy" @click="page--; load()">Назад</button>
      <span>{{ page + 1 }} / {{ data.totalPages }}</span>
      <button class="btn secondary" :disabled="page + 1 >= data.totalPages || busy" @click="page++; load()">Далее</button>
    </div>

    <Teleport to="body">
      <div v-if="previewItem" class="preview-modal" @click.self="closePreview">
        <div class="preview-dialog card" role="dialog" aria-modal="true">
          <header class="preview-header">
            <div>
              <strong>{{ previewItem.originalName }}</strong>
              <span class="muted">
                {{ formatSize(previewItem.sizeBytes) }} · {{ previewItem.contentType }}
                <template v-if="previewItem.square"> · 1:1</template>
                <template v-if="previewItem.watermark"> · WM</template>
              </span>
            </div>
            <button type="button" class="btn secondary" @click="closePreview">Закрыть</button>
          </header>
          <div class="preview-body">
            <img v-if="previewItem.kind === 'IMAGE'" :src="imageSrc(previewItem)" :alt="previewItem.originalName" />
            <video v-else :src="imageSrc(previewItem)" controls autoplay playsinline />
          </div>
          <footer v-if="isProcessableImage(previewItem)" class="preview-footer">
            <button class="btn secondary" :disabled="busy" @click="runOnOne(previewItem, { square: true }, 'Квадрат')">Квадрат</button>
            <button class="btn secondary" :disabled="busy" @click="runOnOne(previewItem, { watermark: true }, 'WM')">Знак</button>
            <button class="btn secondary" :disabled="busy" @click="runOnOne(previewItem, { convertToWebp: true }, 'WebP')">В WebP</button>
            <button
              class="btn"
              :disabled="busy"
              @click="runOnOne(previewItem, { square: true, watermark: true, convertToWebp: true }, 'Пайплайн')"
            >
              Полный пайплайн
            </button>
            <button class="btn secondary" @click="copy(previewItem)">URL</button>
          </footer>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.media-page {
  padding-bottom: 2rem;
}

.upload-group {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.upload {
  display: inline-flex;
}

.drop {
  padding: 0.85rem 1rem;
  margin-bottom: 0.75rem;
  text-align: center;
  color: var(--muted);
  border-style: dashed;
}

.sticky-dock {
  position: sticky;
  top: 0.5rem;
  z-index: 20;
  margin-bottom: 0.75rem;
  padding: 0.75rem 0.9rem;
  display: grid;
  gap: 0.55rem;
  background: rgba(2, 18, 40, 0.88);
  backdrop-filter: blur(10px);
}

.dock-row,
.dock-actions,
.settings-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  align-items: center;
}

.check {
  display: inline-flex;
  gap: 0.3rem;
  align-items: center;
  font-size: 0.85rem;
  color: var(--muted);
}

.tiny {
  padding: 0.3rem 0.55rem;
  font-size: 0.8rem;
}

.sel {
  font-size: 0.85rem;
  color: var(--muted);
  margin-right: 0.25rem;
}

.hint {
  margin: 0;
  font-size: 0.75rem;
  color: var(--muted);
}

.settings-row label {
  display: inline-flex;
  gap: 0.35rem;
  align-items: center;
  font-size: 0.82rem;
  color: var(--muted);
}

.settings-row .hex,
.settings-row input[type='number'] {
  width: 5.5rem;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 0.6rem;
}

.toolbar input {
  flex: 1;
  min-width: 160px;
}

.section-tabs {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
  margin-bottom: 0.85rem;
}

.tab {
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.03);
  color: var(--muted);
  border-radius: 10px;
  padding: 0.3rem 0.55rem;
  font-size: 0.8rem;
  cursor: pointer;
}

.tab.active {
  background: var(--accent);
  color: var(--accent-ink);
  border-color: var(--accent);
}

.status {
  color: var(--muted);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 0.75rem;
}

.item {
  overflow: hidden;
  cursor: pointer;
  transition: outline 0.12s ease;
}

.item.selected {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.preview {
  position: relative;
  aspect-ratio: 1;
  background: rgba(255, 255, 255, 0.04);
  overflow: hidden;
}

.preview img,
.preview video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.item:hover .quick {
  opacity: 1;
}

.quick {
  position: absolute;
  inset: auto 0.35rem 0.35rem;
  display: flex;
  gap: 0.25rem;
  opacity: 0;
  transition: opacity 0.12s ease;
}

.quick button {
  border: 0;
  background: rgba(2, 18, 40, 0.9);
  color: #fff;
  border-radius: 6px;
  padding: 0.25rem 0.4rem;
  font-size: 0.72rem;
  cursor: pointer;
}

.quick button.full {
  background: var(--accent);
  color: var(--accent-ink);
}

.quick button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.kind,
.section-badge,
.proc-badge {
  position: absolute;
  font-size: 0.65rem;
  padding: 0.15rem 0.35rem;
  border-radius: 999px;
}

.kind {
  left: 0.4rem;
  top: 0.4rem;
  background: rgba(15, 31, 27, 0.75);
  color: #fff;
}

.section-badge {
  right: 0.4rem;
  top: 0.4rem;
  background: rgba(255, 255, 255, 0.95);
  color: #111;
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.proc-badge {
  bottom: 2.2rem;
  color: #fff;
}

.proc-badge.square {
  left: 0.4rem;
  background: rgba(141, 198, 63, 0.92);
  color: #062554;
}

.proc-badge.wm {
  left: 2.5rem;
  background: rgba(2, 18, 40, 0.88);
}

.proc-badge.webp {
  left: 4.8rem;
  background: rgba(100, 140, 255, 0.9);
}

.meta {
  padding: 0.55rem 0.65rem 0.2rem;
  display: grid;
  gap: 0.15rem;
}

.meta strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.85rem;
}

.meta span {
  color: var(--muted);
  font-size: 0.75rem;
}

.actions {
  display: flex;
  gap: 0.35rem;
  padding: 0.35rem 0.55rem 0.6rem;
}

.actions .btn {
  flex: 1;
  padding: 0.35rem;
  font-size: 0.78rem;
}

.preview-modal {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(12, 22, 19, 0.72);
  display: grid;
  place-items: center;
  padding: 1.2rem;
}

.preview-dialog {
  width: min(960px, 100%);
  max-height: min(92vh, 900px);
  display: grid;
  grid-template-rows: auto 1fr auto;
  overflow: hidden;
}

.preview-header,
.preview-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
  justify-content: space-between;
  padding: 0.85rem 1rem;
}

.preview-header div {
  display: grid;
  gap: 0.15rem;
  min-width: 0;
}

.preview-body {
  background: #010c1f;
  display: grid;
  place-items: center;
  min-height: 260px;
  overflow: auto;
}

.preview-body img,
.preview-body video {
  max-width: 100%;
  max-height: calc(92vh - 160px);
}

.preview-footer {
  border-top: 1px solid var(--line, #d8e3df);
  justify-content: flex-start;
}

.pager {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  margin-top: 1rem;
}
</style>
