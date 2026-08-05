<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import {
  deleteMedia,
  fetchMedia,
  fetchProcessingSettings,
  isProcessableImage,
  isWebp,
  mediaPublicUrl,
  moveMedia,
  processMedia,
  processMediaBatch,
  uploadMediaBatch,
  MEDIA_UPLOAD_BATCH_SIZE,
  type MediaAsset,
  type MediaPage,
  type MediaProcessOptions,
  type MediaSection
} from '@/api/media'
import { useAuthStore } from '@/stores/auth'
import { useMediaLibraryStore } from '@/stores/mediaLibrary'
import {
  ALL_MEDIA_SECTIONS,
  allowedMediaSectionOptions,
  canAccessMediaSection,
  defaultMediaSection,
  mediaSectionLabel,
  normalizeMediaSectionQuery
} from '@/utils/mediaSections'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
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
const deleting = ref(false)
const moving = ref(false)
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
let loadRequestVersion = 0

const sectionOptions = computed(() => allowedMediaSectionOptions(auth.user?.roles))
const showAllTab = computed(() => auth.isAdmin)
const busy = computed(() => uploading.value || processing.value || loading.value || deleting.value || moving.value)
const selectedCount = computed(() => selectedIds.value.length)
const processableOnPage = computed(() => data.value?.content.filter(isProcessableImage) ?? [])
const selectedProcessableCount = computed(() =>
  selectedIds.value.filter((id) => {
    const item = data.value?.content.find((x) => x.id === id)
    return item && isProcessableImage(item)
  }).length
)
const allPageSelected = computed(
  () => processableOnPage.value.length > 0 && processableOnPage.value.every((i) => selectedIds.value.includes(i.id))
)
const autoPipelineLabel = computed(() => {
  const parts: string[] = []
  if (autoSquare.value) parts.push('1:1')
  if (autoWatermark.value) parts.push('WM')
  if (autoWebp.value) parts.push('WebP')
  return parts.length ? parts.join(' · ') : 'выкл'
})

function formatSize(bytes: number) {
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} МБ`
  return `${Math.round(bytes / 1024)} КБ`
}

function imageSrc(item: MediaAsset) {
  return mediaPublicUrl(item.url, item.updatedAt)
}

function applyRouteSection(raw?: string | null) {
  const next = normalizeMediaSectionQuery(auth.user?.roles, raw)
  section.value = next
  uploadSection.value = next
}

function chooseSection(value: MediaSection | '') {
  if (value && !canAccessMediaSection(auth.user?.roles, value)) return
  section.value = value
  if (value) uploadSection.value = value
  router.replace({ query: value ? { section: value } : {} })
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
  const requestVersion = ++loadRequestVersion
  loading.value = true
  error.value = ''
  try {
    const pageData = await fetchMedia(listParams() as any)
    if (requestVersion !== loadRequestVersion) return

    if (processingFilter.value === 'not_webp') {
      data.value = {
        ...pageData,
        content: pageData.content.filter((item) => item.kind === 'IMAGE' && !isWebp(item))
      }
    } else {
      data.value = pageData
    }
    const visible = new Set((data.value?.content || []).map((item) => item.id))
    selectedIds.value = selectedIds.value.filter((id) => visible.has(id))
  } catch (e: any) {
    if (requestVersion !== loadRequestVersion) return
    error.value = e?.response?.data?.message || 'Не удалось загрузить медиатеку'
  } finally {
    if (requestVersion !== loadRequestVersion) return
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

async function bulkDeleteSelected() {
  const ids = [...selectedIds.value]
  if (!ids.length || !confirm(`Удалить выбранные медиа (${ids.length})?`)) return
  deleting.value = true
  error.value = ''
  message.value = ''
  try {
    const results = await Promise.allSettled(ids.map((id) => deleteMedia(id)))
    const failed = results.filter((r) => r.status === 'rejected').length
    selectedIds.value = []
    await load()
    message.value = failed ? `Удалено не всё: ошибок ${failed}` : `Удалено: ${ids.length}`
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка удаления'
  } finally {
    deleting.value = false
  }
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

function targetIds() {
  return selectedIds.value.filter((id) => {
    const item = data.value?.content.find((x) => x.id === id)
    return item && isProcessableImage(item)
  })
}

async function runOnSelected(options: MediaProcessOptions, label: string) {
  await runProcess(targetIds(), options, label)
}

async function runOnOne(item: MediaAsset, options: MediaProcessOptions, label: string) {
  await runProcess([item.id], options, label)
}

async function onMove(item: MediaAsset, nextSection: MediaSection) {
  if (item.section === nextSection) return
  moving.value = true
  error.value = ''
  try {
    const updated = await moveMedia(item.id, nextSection)
    mergeProcessed(updated)
    message.value = `Перемещено в «${mediaSectionLabel(nextSection)}»`
    if (section.value && section.value !== nextSection) {
      await load()
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Не удалось переместить'
  } finally {
    moving.value = false
  }
}

async function onFiles(files: FileList | null) {
  if (!files?.length) return
  uploading.value = true
  error.value = ''
  message.value = ''
  const uploaded: MediaAsset[] = []
  try {
    const targetSection = section.value || uploadSection.value || defaultMediaSection(auth.user?.roles)
    const list = Array.from(files)
    for (let i = 0; i < list.length; i += MEDIA_UPLOAD_BATCH_SIZE) {
      const chunk = list.slice(i, i + MEDIA_UPLOAD_BATCH_SIZE)
      const batchIndex = Math.floor(i / MEDIA_UPLOAD_BATCH_SIZE)
      const batchCount = Math.ceil(list.length / MEDIA_UPLOAD_BATCH_SIZE)
      const result = await uploadMediaBatch(chunk, targetSection, (pct) => {
        progress.value = Math.round(((batchIndex + pct / 100) / batchCount) * 100)
      })
      uploaded.push(...result.uploaded)
      if (result.failed > 0 && result.errors?.length) {
        error.value = result.errors.slice(0, 3).join('; ')
      }
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
  navigator.clipboard
    ?.writeText(mediaPublicUrl(item.url))
    .then(() => {
      message.value = 'URL скопирован'
    })
    .catch(() => {
      error.value = 'Не удалось скопировать URL'
    })
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  onFiles(e.dataTransfer?.files ?? null)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    if (previewItem.value) closePreview()
    else clearSelection()
  }
}

onMounted(() => {
  applyRouteSection(typeof route.query.section === 'string' ? route.query.section : null)
  if (!section.value && !showAllTab.value) {
    section.value = uploadSection.value
    router.replace({ query: { section: uploadSection.value } })
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
    applyRouteSection(typeof value === 'string' ? value : null)
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
        <p class="muted">Разделы по роли · авто {{ autoPipelineLabel }} · logo {{ logoOk ? 'OK' : '—' }}</p>
      </div>
      <div class="header-actions">
        <select v-model="uploadSection" :disabled="busy" title="Раздел загрузки">
          <option v-for="item in sectionOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
        <label class="btn icon-btn" :class="{ disabled: busy }">
          <AppIcon name="upload" />
          <span>{{ uploading ? `${progress}%` : 'Загрузить' }}</span>
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
      <AppIcon name="upload" :size="20" />
      <span>Перетащите файлы сюда</span>
    </div>

    <div class="panel card">
      <div class="panel-row">
        <label class="check"><input v-model="autoSquare" type="checkbox" /> 1:1</label>
        <label class="check"><input v-model="autoWatermark" type="checkbox" /> WM</label>
        <label class="check"><input v-model="autoWebp" type="checkbox" /> WebP</label>
        <button class="btn ghost icon-btn" type="button" @click="settingsOpen = !settingsOpen">
          <AppIcon name="settings" />
          <span>{{ settingsOpen ? 'Скрыть' : 'Настройки' }}</span>
        </button>
        <span class="sel">{{ selectedCount ? `Выбрано ${selectedCount}` : '' }}</span>
        <button class="btn ghost icon-btn" type="button" :disabled="busy || !processableOnPage.length" @click="toggleSelectAll">
          <AppIcon name="check" />
          <span>{{ allPageSelected ? 'Снять' : 'Страница' }}</span>
        </button>
        <button class="btn ghost" :disabled="busy || !selectedProcessableCount" @click="runOnSelected({ square: true }, '1:1')">1:1</button>
        <button class="btn ghost" :disabled="busy || !selectedProcessableCount" @click="runOnSelected({ watermark: true }, 'WM')">WM</button>
        <button class="btn ghost" :disabled="busy || !selectedProcessableCount" @click="runOnSelected({ convertToWebp: true }, 'WebP')">WebP</button>
        <button class="btn" :disabled="busy || !selectedProcessableCount" @click="runOnSelected({ square: true, watermark: true, convertToWebp: true }, 'Полный')">
          Полный
        </button>
        <button v-if="auth.isAdmin" class="btn danger icon-btn" type="button" :disabled="busy || !selectedCount" @click="bulkDeleteSelected">
          <AppIcon name="trash" />
        </button>
      </div>
      <div v-if="settingsOpen" class="settings-row">
        <label>Фон <input v-model="squareBackground" type="color" /><input v-model="squareBackground" class="hex" /></label>
        <label>Opacity <input v-model.number="watermarkOpacity" type="number" min="0.01" max="1" step="0.01" /></label>
        <label>Threshold <input v-model.number="bgThreshold" type="number" min="0" max="255" step="1" /></label>
      </div>
    </div>

    <div class="toolbar card">
      <div class="search-wrap">
        <AppIcon name="search" />
        <input v-model="q" placeholder="Поиск…" @keyup.enter="page = 0; load()" />
      </div>
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
      <button class="btn ghost icon-btn" @click="page = 0; load()">
        <AppIcon name="refresh" />
      </button>
    </div>

    <div class="section-tabs">
      <button v-if="showAllTab" class="tab" :class="{ active: section === '' }" type="button" @click="chooseSection('')">
        <AppIcon name="folder" :size="16" />
        <span>Все</span>
      </button>
      <button
        v-for="item in sectionOptions"
        :key="item.value"
        class="tab"
        :class="{ active: section === item.value }"
        type="button"
        @click="chooseSection(item.value)"
      >
        <AppIcon :name="item.icon" :size="16" />
        <span>{{ item.label }}</span>
      </button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="ok">{{ message }}</p>
    <p v-if="busy" class="status">{{ uploading ? `Загрузка ${progress}%` : processLabel || 'Загрузка…' }}</p>

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
          <span class="kind">
            <AppIcon :name="item.kind === 'IMAGE' ? 'image' : 'video'" :size="12" />
          </span>
          <span class="section-badge">{{ mediaSectionLabel(item.section) }}</span>
          <button
            class="select-chip"
            type="button"
            :class="{ selected: selectedIds.includes(item.id) }"
            @click.stop="toggleSelect(item.id)"
          >
            <AppIcon v-if="selectedIds.includes(item.id)" name="check" :size="14" />
          </button>
          <div v-if="item.square || item.watermark || isWebp(item)" class="badges">
            <span v-if="item.square">1:1</span>
            <span v-if="item.watermark">WM</span>
            <span v-if="isWebp(item)">WebP</span>
          </div>
        </div>
        <div class="meta">
          <strong :title="item.originalName">{{ item.originalName }}</strong>
          <span>{{ formatSize(item.sizeBytes) }}</span>
        </div>
        <div class="actions" @click.stop>
          <button class="icon-action" type="button" title="URL" @click="copy(item)"><AppIcon name="copy" :size="16" /></button>
          <button class="icon-action" type="button" title="Открыть" @click="openPreview(item)"><AppIcon name="maximize" :size="16" /></button>
          <select
            v-if="auth.isAdmin"
            class="move-select"
            :value="item.section"
            :disabled="moving"
            title="Переместить"
            @change="onMove(item, ($event.target as HTMLSelectElement).value as MediaSection)"
          >
            <option v-for="opt in ALL_MEDIA_SECTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
          <button v-if="auth.isAdmin" class="icon-action danger" type="button" title="Удалить" @click="remove(item)">
            <AppIcon name="trash" :size="16" />
          </button>
        </div>
      </article>
      <div v-if="!data.content.length" class="empty card">Нет файлов в этом разделе</div>
    </div>

    <div v-if="data && data.totalPages > 1" class="pager">
      <button class="btn ghost" :disabled="page === 0 || busy" @click="page--; load()">Назад</button>
      <span>{{ page + 1 }} / {{ data.totalPages }}</span>
      <button class="btn ghost" :disabled="page + 1 >= data.totalPages || busy" @click="page++; load()">Далее</button>
    </div>

    <Teleport to="body">
      <div v-if="previewItem" class="preview-modal" @click.self="closePreview">
        <div class="preview-dialog card" role="dialog" aria-modal="true">
          <header class="preview-header">
            <div>
              <strong>{{ previewItem.originalName }}</strong>
              <span class="muted">{{ mediaSectionLabel(previewItem.section) }} · {{ formatSize(previewItem.sizeBytes) }}</span>
            </div>
            <button type="button" class="btn ghost icon-btn" @click="closePreview"><AppIcon name="x" /></button>
          </header>
          <div class="preview-body">
            <img v-if="previewItem.kind === 'IMAGE'" :src="imageSrc(previewItem)" :alt="previewItem.originalName" />
            <video v-else :src="imageSrc(previewItem)" controls autoplay playsinline />
          </div>
          <footer class="preview-footer">
            <template v-if="isProcessableImage(previewItem)">
              <button class="btn ghost" :disabled="busy" @click="runOnOne(previewItem, { square: true }, '1:1')">1:1</button>
              <button class="btn ghost" :disabled="busy" @click="runOnOne(previewItem, { watermark: true }, 'WM')">WM</button>
              <button class="btn ghost" :disabled="busy" @click="runOnOne(previewItem, { convertToWebp: true }, 'WebP')">WebP</button>
            </template>
            <button class="btn ghost icon-btn" @click="copy(previewItem)"><AppIcon name="copy" /><span>URL</span></button>
            <select
              v-if="auth.isAdmin"
              class="move-select"
              :value="previewItem.section"
              :disabled="moving"
              @change="onMove(previewItem, ($event.target as HTMLSelectElement).value as MediaSection)"
            >
              <option v-for="opt in ALL_MEDIA_SECTIONS" :key="opt.value" :value="opt.value">→ {{ opt.label }}</option>
            </select>
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

.header-actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  flex-wrap: wrap;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.icon-btn.disabled {
  opacity: 0.6;
  pointer-events: none;
}

.drop {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.75rem;
  margin-bottom: 0.75rem;
  color: var(--muted);
  border-style: dashed;
}

.panel,
.toolbar {
  margin-bottom: 0.75rem;
  padding: 0.75rem;
}

.panel-row,
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

.sel {
  font-size: 0.85rem;
  color: var(--muted);
  min-width: 5rem;
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
  align-items: center;
}

.search-wrap {
  flex: 1;
  min-width: 160px;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0 0.65rem;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--input-bg);
}

.search-wrap input {
  flex: 1;
  border: 0;
  background: transparent;
  padding: 0.65rem 0;
}

.section-tabs {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
  margin-bottom: 0.85rem;
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.03);
  color: var(--muted);
  border-radius: 10px;
  padding: 0.35rem 0.65rem;
  font-size: 0.82rem;
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
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 0.75rem;
}

.item {
  overflow: hidden;
  cursor: pointer;
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

.kind {
  position: absolute;
  left: 0.4rem;
  top: 0.4rem;
  background: rgba(15, 31, 27, 0.75);
  color: #fff;
  border-radius: 6px;
  padding: 0.2rem;
  display: grid;
  place-items: center;
}

.section-badge {
  position: absolute;
  right: 0.4rem;
  top: 0.4rem;
  background: rgba(255, 255, 255, 0.95);
  color: #111;
  font-size: 0.65rem;
  padding: 0.15rem 0.35rem;
  border-radius: 999px;
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.select-chip {
  position: absolute;
  left: 0.4rem;
  bottom: 0.4rem;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  background: rgba(2, 18, 40, 0.78);
  color: #fff;
  display: grid;
  place-items: center;
  padding: 0;
  cursor: pointer;
}

.select-chip.selected {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-ink);
}

.badges {
  position: absolute;
  left: 0.4rem;
  bottom: 2.5rem;
  display: flex;
  gap: 0.2rem;
}

.badges span {
  font-size: 0.62rem;
  padding: 0.12rem 0.3rem;
  border-radius: 999px;
  background: rgba(2, 18, 40, 0.88);
  color: #fff;
}

.meta {
  padding: 0.55rem 0.65rem 0.2rem;
  display: grid;
  gap: 0.1rem;
}

.meta strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.82rem;
}

.meta span {
  color: var(--muted);
  font-size: 0.72rem;
}

.actions {
  display: flex;
  gap: 0.25rem;
  padding: 0.35rem 0.55rem 0.6rem;
  align-items: center;
}

.icon-action {
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.04);
  color: inherit;
  border-radius: 8px;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  cursor: pointer;
}

.icon-action.danger {
  color: #ffb4b4;
}

.move-select {
  flex: 1;
  min-width: 0;
  font-size: 0.72rem;
  padding: 0.25rem;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: var(--input-bg);
  color: inherit;
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
  border-top: 1px solid var(--line);
  justify-content: flex-start;
}

.pager {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  margin-top: 1rem;
}

.btn.ghost {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--line);
}
</style>
