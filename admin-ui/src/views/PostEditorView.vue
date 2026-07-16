<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import {
  createPost,
  fetchPost,
  publishPost,
  updatePost,
  type PostPayload
} from '@/api/posts'
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
const saving = ref(false)
const error = ref('')
const message = ref('')

const previewHtml = computed(() =>
  DOMPurify.sanitize(marked.parse(content.value || '') as string, {
    USE_PROFILES: { html: true }
  })
)

function payload(): PostPayload {
  return {
    title: title.value,
    shortDescription: shortDescription.value,
    content: content.value,
    tags: tags.value.split(',').map((s) => s.trim()).filter(Boolean),
    categories: categories.value.split(',').map((s) => s.trim()).filter(Boolean),
    mediaObjectNames: []
  }
}

async function load() {
  if (isNew.value) return
  const post = await fetchPost(id.value)
  title.value = post.title
  shortDescription.value = post.shortDescription
  content.value = post.content
  tags.value = post.tags.join(', ')
  categories.value = post.categories.join(', ')
  status.value = post.status
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
    } else {
      await updatePost(id.value, payload())
      message.value = 'Сохранено'
      await load()
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
      await router.replace(`/posts/${created.id}`)
    } else {
      await updatePost(id.value, payload())
    }
    await publishPost(currentId)
    message.value = 'Опубликовано'
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка публикации'
  } finally {
    saving.value = false
  }
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
          <textarea v-model="content" rows="22" />
        </div>
        <div class="field">
          <label>Превью</label>
          <div class="preview" v-html="previewHtml" />
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: end;
  margin-bottom: 1rem;
}

.eyebrow {
  margin: 0;
  color: var(--accent);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-size: 0.75rem;
  font-weight: 600;
}

h1 {
  margin: 0.25rem 0 0;
  font-family: var(--font-serif);
  font-size: 2.4rem;
}

.actions {
  display: flex;
  gap: 0.6rem;
  align-items: center;
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
  border-radius: 10px;
  padding: 1rem;
  background: white;
  overflow: auto;
}

.preview :deep(h1),
.preview :deep(h2),
.preview :deep(h3) {
  font-family: var(--font-serif);
}

.error {
  color: var(--danger);
}

.ok {
  color: var(--accent);
}

@media (max-width: 960px) {
  .meta-grid,
  .split,
  .page-header {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
