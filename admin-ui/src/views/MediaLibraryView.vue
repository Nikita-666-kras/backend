<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { deleteMedia, fetchMedia, mediaPublicUrl, uploadMedia, type MediaAsset, type MediaPage } from '@/api/media'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const kind = ref('')
const q = ref('')
const page = ref(0)
const data = ref<MediaPage | null>(null)
const loading = ref(false)
const uploading = ref(false)
const progress = ref(0)
const error = ref('')
const message = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const previewItem = ref<MediaAsset | null>(null)

function formatSize(bytes: number) {
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} МБ`
  return `${Math.round(bytes / 1024)} КБ`
}

function openPreview(item: MediaAsset) {
  previewItem.value = item
}

function closePreview() {
  previewItem.value = null
}

function onPreviewKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') closePreview()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await fetchMedia({
      kind: kind.value || undefined,
      q: q.value || undefined,
      page: page.value,
      size: 24
    })
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Не удалось загрузить медиатеку'
  } finally {
    loading.value = false
  }
}

async function onFiles(files: FileList | null) {
  if (!files?.length) return
  uploading.value = true
  error.value = ''
  message.value = ''
  try {
    for (const file of Array.from(files)) {
      progress.value = 0
      await uploadMedia(file, (pct) => {
        progress.value = pct
      })
    }
    message.value = `Загружено файлов: ${files.length}`
    page.value = 0
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка загрузки'
  } finally {
    uploading.value = false
    progress.value = 0
    if (fileInput.value) fileInput.value.value = ''
  }
}

async function remove(item: MediaAsset) {
  if (!confirm(`Удалить «${item.originalName}»?`)) return
  try {
    await deleteMedia(item.id)
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Удаление доступно только администратору'
  }
}

function copy(item: MediaAsset) {
  navigator.clipboard?.writeText(item.url)
  message.value = 'Путь скопирован: ' + item.url
}

function insertMarkdown(item: MediaAsset) {
  const md =
    item.kind === 'VIDEO'
      ? `<video controls src="${mediaPublicUrl(item.url)}"></video>`
      : `![${item.originalName}](${mediaPublicUrl(item.url)})`
  navigator.clipboard?.writeText(md)
  message.value = 'Markdown скопирован — вставьте в редактор поста'
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  onFiles(e.dataTransfer?.files ?? null)
}

onMounted(() => {
  load()
  window.addEventListener('keydown', onPreviewKeydown)
})
onUnmounted(() => window.removeEventListener('keydown', onPreviewKeydown))
watch([kind], () => {
  page.value = 0
  load()
})
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <p class="eyebrow">Файлы</p>
        <h1>Медиатека</h1>
        <p class="muted">Изображения и видео для постов</p>
      </div>
      <label class="btn upload">
        {{ uploading ? `Загрузка ${progress}%` : 'Загрузить файлы' }}
        <input
          ref="fileInput"
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif,video/mp4,video/webm"
          multiple
          hidden
          :disabled="uploading"
          @change="onFiles(($event.target as HTMLInputElement).files)"
        />
      </label>
    </header>

    <div class="drop card" @dragover.prevent @drop="onDrop">
      Перетащите сюда фото или видео (jpg, png, webp, gif, mp4, webm)
    </div>

    <div class="toolbar card">
      <input v-model="q" placeholder="Поиск по имени файла" @keyup.enter="page = 0; load()" />
      <select v-model="kind">
        <option value="">Все типы</option>
        <option value="IMAGE">Изображения</option>
        <option value="VIDEO">Видео</option>
      </select>
      <button class="btn secondary" @click="page = 0; load()">Фильтр</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="ok">{{ message }}</p>
    <p v-if="loading">Загрузка…</p>

    <div v-if="data" class="grid">
      <article v-for="item in data.content" :key="item.id" class="card item">
        <button type="button" class="preview" @click="openPreview(item)">
          <img v-if="item.kind === 'IMAGE'" :src="mediaPublicUrl(item.url)" :alt="item.originalName" loading="lazy" />
          <video v-else :src="mediaPublicUrl(item.url)" muted preload="metadata" />
          <span class="kind">{{ item.kind === 'IMAGE' ? 'Фото' : 'Видео' }}</span>
          <span class="preview-hint">Просмотр</span>
        </button>
        <div class="meta">
          <strong :title="item.originalName">{{ item.originalName }}</strong>
          <span>{{ formatSize(item.sizeBytes) }}</span>
        </div>
        <div class="actions">
          <button class="btn secondary" @click="openPreview(item)">Открыть</button>
          <button class="btn secondary" @click="insertMarkdown(item)">MD</button>
          <button class="btn secondary" @click="copy(item)">URL</button>
          <button v-if="auth.isAdmin" class="btn danger" @click="remove(item)">Удалить</button>
        </div>
      </article>
      <div v-if="!data.content.length" class="empty card">Медиатека пуста — загрузите первый файл</div>
    </div>

    <div v-if="data && data.totalPages > 1" class="pager">
      <button class="btn secondary" :disabled="page === 0" @click="page--; load()">Назад</button>
      <span>{{ page + 1 }} / {{ data.totalPages }}</span>
      <button class="btn secondary" :disabled="page + 1 >= data.totalPages" @click="page++; load()">Далее</button>
    </div>

    <Teleport to="body">
      <div v-if="previewItem" class="preview-modal" @click.self="closePreview">
        <div class="preview-dialog card" role="dialog" aria-modal="true" :aria-label="previewItem.originalName">
          <header class="preview-header">
            <div>
              <strong>{{ previewItem.originalName }}</strong>
              <span class="muted">
                {{ previewItem.kind === 'IMAGE' ? 'Фото' : 'Видео' }} · {{ formatSize(previewItem.sizeBytes) }}
              </span>
            </div>
            <button type="button" class="btn secondary preview-close" @click="closePreview">Закрыть</button>
          </header>

          <div class="preview-body">
            <img
              v-if="previewItem.kind === 'IMAGE'"
              :src="mediaPublicUrl(previewItem.url)"
              :alt="previewItem.originalName"
            />
            <video
              v-else
              :src="mediaPublicUrl(previewItem.url)"
              controls
              autoplay
              playsinline
              preload="auto"
            />
          </div>

          <footer class="preview-footer">
            <button class="btn secondary" @click="copy(previewItem)">URL</button>
            <button class="btn secondary" @click="insertMarkdown(previewItem)">Markdown</button>
          </footer>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.upload {
  display: inline-flex;
  align-items: center;
}

.drop {
  padding: 1.4rem;
  margin-bottom: 1rem;
  text-align: center;
  color: var(--muted);
  border-style: dashed;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
}

.toolbar input {
  flex: 1;
  min-width: 180px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 1rem;
}

.item {
  overflow: hidden;
  display: grid;
}

.preview {
  position: relative;
  aspect-ratio: 4 / 3;
  background: #e7eeeb;
  border: 0;
  padding: 0;
  cursor: zoom-in;
  width: 100%;
  display: block;
  overflow: hidden;
}

.preview:hover .preview-hint {
  opacity: 1;
}

.preview-hint {
  position: absolute;
  inset: auto 0 0;
  padding: 0.55rem;
  background: linear-gradient(transparent, rgba(15, 31, 27, 0.72));
  color: white;
  font-size: 0.8rem;
  opacity: 0;
  transition: opacity 0.15s ease;
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
  left: 0.6rem;
  top: 0.6rem;
  background: rgba(15, 31, 27, 0.75);
  color: white;
  font-size: 0.7rem;
  padding: 0.2rem 0.5rem;
  border-radius: 999px;
}

.meta {
  padding: 0.85rem 0.9rem 0.35rem;
  display: grid;
  gap: 0.25rem;
}

.meta strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta span {
  color: var(--muted);
  font-size: 0.85rem;
}

.actions {
  display: flex;
  gap: 0.4rem;
  padding: 0.5rem 0.75rem 0.85rem;
}

.actions .btn {
  flex: 1;
  padding: 0.45rem 0.4rem;
  font-size: 0.8rem;
}

.preview-modal {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(12, 22, 19, 0.72);
  display: grid;
  place-items: center;
  padding: 1.5rem;
}

.preview-dialog {
  width: min(960px, 100%);
  max-height: min(90vh, 900px);
  display: grid;
  grid-template-rows: auto 1fr auto;
  overflow: hidden;
}

.preview-header,
.preview-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.1rem;
}

.preview-header div {
  display: grid;
  gap: 0.2rem;
  min-width: 0;
}

.preview-header strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-close {
  flex-shrink: 0;
}

.preview-body {
  background: #0f1f1b;
  display: grid;
  place-items: center;
  min-height: 280px;
  max-height: calc(90vh - 150px);
  overflow: auto;
}

.preview-body img,
.preview-body video {
  max-width: 100%;
  max-height: calc(90vh - 150px);
  width: auto;
  height: auto;
  display: block;
}

.preview-footer {
  border-top: 1px solid var(--line, #d8e3df);
}

.pager {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  margin-top: 1rem;
}
</style>
