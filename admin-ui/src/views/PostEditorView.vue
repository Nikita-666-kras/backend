<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import {
  createPost,
  fetchPost,
  publicPostPath,
  publishPost,
  updatePost,
  type PostPayload
} from '@/api/posts'
import { fetchMedia, mediaPublicUrl, uploadMedia, type MediaAsset } from '@/api/media'
import { statusLabel } from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))
const isNew = computed(() => route.name === 'post-new')

const title = ref('')
const shortDescription = ref('')
const content = ref('# Новый материал\n\nНапишите текст здесь.')
const tags = ref('новости')
const categories = ref('блог')
const status = ref('DRAFT')
const slug = ref('')
const coverMediaId = ref<string | null>(null)
const mediaObjectNames = ref<string[]>([])
const library = ref<MediaAsset[]>([])
const saving = ref(false)
const uploading = ref(false)
const error = ref('')
const message = ref('')
const contentEl = ref<HTMLTextAreaElement | null>(null)

const previewHtml = computed(() =>
  DOMPurify.sanitize(marked.parse(content.value || '') as string, {
    USE_PROFILES: { html: true },
    ADD_TAGS: ['video', 'source'],
    ADD_ATTR: ['controls', 'src', 'alt']
  })
)

const publicUrl = computed(() => {
  if (!slug.value) return 'Появится после сохранения'
  const apiOrigin = window.location.origin.includes(':8088')
    ? window.location.origin.replace(':8088', ':8080')
    : window.location.origin
  return `${apiOrigin}${publicPostPath(slug.value)}`
})

const attached = computed(() =>
  library.value.filter((m) => mediaObjectNames.value.includes(m.id))
)

function payload(): PostPayload {
  return {
    title: title.value,
    shortDescription: shortDescription.value,
    content: content.value,
    coverMediaId: coverMediaId.value,
    tags: tags.value.split(',').map((s) => s.trim()).filter(Boolean),
    categories: categories.value.split(',').map((s) => s.trim()).filter(Boolean),
    mediaObjectNames: mediaObjectNames.value
  }
}

async function loadLibrary() {
  const page = await fetchMedia({ size: 48 })
  library.value = page.content
}

async function load() {
  if (isNew.value) {
    await loadLibrary()
    return
  }
  const post = await fetchPost(id.value)
  title.value = post.title
  shortDescription.value = post.shortDescription
  content.value = post.content
  tags.value = post.tags.join(', ')
  categories.value = post.categories.join(', ')
  status.value = post.status
  slug.value = post.slug
  coverMediaId.value = post.coverMediaId || null
  mediaObjectNames.value = [...(post.mediaObjectNames || [])]
  await loadLibrary()
}

function insertAtCursor(snippet: string) {
  const el = contentEl.value
  if (!el) {
    content.value += `\n${snippet}\n`
    return
  }
  const start = el.selectionStart
  const end = el.selectionEnd
  const before = content.value.slice(0, start)
  const after = content.value.slice(end)
  content.value = `${before}${snippet}${after}`
  requestAnimationFrame(() => {
    el.focus()
    const pos = start + snippet.length
    el.setSelectionRange(pos, pos)
  })
}

function insertMedia(item: MediaAsset) {
  if (!mediaObjectNames.value.includes(item.id)) {
    mediaObjectNames.value = [...mediaObjectNames.value, item.id]
  }
  if (item.kind === 'VIDEO') {
    insertAtCursor(`\n<video controls src="${item.url}"></video>\n`)
  } else {
    insertAtCursor(`\n![${item.originalName}](${item.url})\n`)
  }
}

function setCover(item: MediaAsset) {
  if (item.kind !== 'IMAGE') {
    error.value = 'Обложкой может быть только изображение'
    return
  }
  coverMediaId.value = item.id
  if (!mediaObjectNames.value.includes(item.id)) {
    mediaObjectNames.value = [...mediaObjectNames.value, item.id]
  }
}

function clearCover() {
  coverMediaId.value = null
}

function detach(mediaId: string) {
  mediaObjectNames.value = mediaObjectNames.value.filter((n) => n !== mediaId)
  if (coverMediaId.value === mediaId) coverMediaId.value = null
}

async function uploadAndAttach(files: FileList | null) {
  if (!files?.length) return
  uploading.value = true
  error.value = ''
  try {
    for (const file of Array.from(files)) {
      const asset = await uploadMedia(file)
      library.value = [asset, ...library.value]
      insertMedia(asset)
      if (!coverMediaId.value && asset.kind === 'IMAGE') {
        coverMediaId.value = asset.id
      }
    }
    message.value = 'Файл загружен и вставлен в текст'
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка загрузки'
  } finally {
    uploading.value = false
  }
}

async function saveDraft() {
  saving.value = true
  error.value = ''
  message.value = ''
  try {
    if (isNew.value) {
      const created = await createPost(payload())
      message.value = 'Черновик создан'
      await router.replace(`/posts/${created.id}`)
      slug.value = created.slug
      status.value = created.status
    } else {
      const updated = await updatePost(id.value, payload())
      message.value = 'Сохранено'
      slug.value = updated.slug
      status.value = updated.status
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка сохранения'
  } finally {
    saving.value = false
  }
}

async function saveAndPublish() {
  saving.value = true
  error.value = ''
  message.value = ''
  try {
    let currentId = id.value
    if (isNew.value) {
      const created = await createPost(payload())
      currentId = created.id
      slug.value = created.slug
      await router.replace(`/posts/${created.id}`)
    } else {
      await updatePost(id.value, payload())
    }
    const published = await publishPost(currentId)
    status.value = published.status
    slug.value = published.slug
    message.value = 'Опубликовано'
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка публикации'
  } finally {
    saving.value = false
  }
}

function copyPublicUrl() {
  if (!slug.value) return
  navigator.clipboard?.writeText(publicUrl.value)
  message.value = 'Публичный URL скопирован'
}

onMounted(async () => {
  try {
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Не удалось загрузить пост'
  }
})
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <p class="eyebrow">Редактор</p>
        <h1>{{ isNew ? 'Новый пост' : 'Редактирование' }}</h1>
      </div>
      <div class="actions">
        <span class="badge" :class="status">{{ statusLabel(status) }}</span>
        <button class="btn secondary" :disabled="saving" @click="saveDraft">Сохранить черновик</button>
        <button class="btn" :disabled="saving" @click="saveAndPublish">Опубликовать</button>
      </div>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="ok">{{ message }}</p>

    <div class="layout">
      <div class="editor card">
        <div class="field">
          <label>Заголовок</label>
          <input v-model="title" required />
        </div>
        <div class="field">
          <label>Краткое описание</label>
          <input v-model="shortDescription" required />
        </div>
        <div class="meta-grid">
          <div class="field">
            <label>Теги (через запятую)</label>
            <input v-model="tags" />
          </div>
          <div class="field">
            <label>Категории (через запятую)</label>
            <input v-model="categories" />
          </div>
        </div>
        <div class="split">
          <div class="field">
            <label>Текст (Markdown)</label>
            <textarea ref="contentEl" v-model="content" rows="22" />
          </div>
          <div class="field">
            <label>Превью</label>
            <div class="preview" v-html="previewHtml" />
          </div>
        </div>
      </div>

      <aside class="side">
        <div class="card panel">
          <h3>Публичный URL / slug</h3>
          <p class="slug">{{ slug || '—' }}</p>
          <p class="url">{{ publicUrl }}</p>
          <button class="btn secondary" :disabled="!slug" @click="copyPublicUrl">Копировать URL</button>
        </div>

        <div class="card panel">
          <h3>Обложка</h3>
          <div v-if="coverMediaId" class="cover">
            <img :src="mediaPublicUrl(coverMediaId)" alt="Обложка" />
            <button class="btn ghost" @click="clearCover">Убрать</button>
          </div>
          <p v-else class="muted">Выберите изображение из медиатеки ниже</p>
        </div>

        <div class="card panel">
          <div class="panel-head">
            <h3>Медиа к посту</h3>
            <label class="btn secondary mini">
              {{ uploading ? '…' : 'Загрузить' }}
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif,video/mp4,video/webm"
                multiple
                hidden
                :disabled="uploading"
                @change="uploadAndAttach(($event.target as HTMLInputElement).files)"
              />
            </label>
          </div>
          <div class="media-list">
            <div v-for="item in library" :key="item.id" class="media-row">
              <div class="thumb">
                <img v-if="item.kind === 'IMAGE'" :src="mediaPublicUrl(item.url)" alt="" />
                <video v-else :src="mediaPublicUrl(item.url)" muted />
              </div>
              <div class="info">
                <strong>{{ item.originalName }}</strong>
                <div class="row-actions">
                  <button class="btn ghost" @click="insertMedia(item)">В текст</button>
                  <button v-if="item.kind === 'IMAGE'" class="btn ghost" @click="setCover(item)">Обложка</button>
                  <button
                    v-if="mediaObjectNames.includes(item.id)"
                    class="btn ghost"
                    @click="detach(item.id)"
                  >
                    Открепить
                  </button>
                </div>
              </div>
            </div>
            <p v-if="!library.length" class="muted">Пока пусто — загрузите файл</p>
          </div>
          <p v-if="attached.length" class="muted tiny">Прикреплено к посту: {{ attached.length }}</p>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.actions {
  display: flex;
  gap: 0.6rem;
  align-items: center;
  flex-wrap: wrap;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 1rem;
  align-items: start;
}

.editor {
  padding: 1.15rem;
  display: grid;
  gap: 1rem;
}

.meta-grid,
.split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.preview {
  min-height: 420px;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 1rem;
  background: white;
  overflow: auto;
}

.preview :deep(img),
.preview :deep(video) {
  max-width: 100%;
  border-radius: 8px;
}

.preview :deep(h1),
.preview :deep(h2),
.preview :deep(h3) {
  font-family: var(--font-serif);
}

.side {
  display: grid;
  gap: 1rem;
}

.panel {
  padding: 1rem;
  display: grid;
  gap: 0.75rem;
}

.panel h3 {
  margin: 0;
  font-size: 0.95rem;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
}

.mini {
  padding: 0.4rem 0.7rem;
  font-size: 0.85rem;
}

.slug {
  font-family: ui-monospace, monospace;
  font-size: 0.85rem;
  word-break: break-all;
  margin: 0;
}

.url {
  margin: 0;
  color: var(--muted);
  font-size: 0.85rem;
  word-break: break-all;
}

.cover img {
  width: 100%;
  border-radius: 10px;
  display: block;
  margin-bottom: 0.5rem;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

.media-list {
  display: grid;
  gap: 0.65rem;
  max-height: 420px;
  overflow: auto;
}

.media-row {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 0.55rem;
}

.thumb {
  width: 56px;
  height: 42px;
  border-radius: 8px;
  overflow: hidden;
  background: #e7eeeb;
}

.thumb img,
.thumb video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.info strong {
  display: block;
  font-size: 0.8rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.15rem;
}

.row-actions .btn {
  padding: 0.2rem 0.35rem;
  font-size: 0.75rem;
}

.tiny {
  font-size: 0.8rem;
  margin: 0;
}

@media (max-width: 1100px) {
  .layout,
  .meta-grid,
  .split,
  .page-header {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
