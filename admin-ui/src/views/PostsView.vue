<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  archivePost,
  bulkPosts,
  deletePost,
  fetchPosts,
  publicPostPath,
  publishPost,
  type PageResponse
} from '@/api/posts'
import { mediaPublicUrl } from '@/api/media'
import { useAuthStore } from '@/stores/auth'
import { statusLabel } from '@/utils/labels'

const auth = useAuthStore()
const route = useRoute()
const q = ref('')
const status = ref(typeof route.query.status === 'string' ? route.query.status : '')
const page = ref(0)
const data = ref<PageResponse | null>(null)
const error = ref('')
const message = ref('')
const loading = ref(false)
const selected = ref<Set<string>>(new Set())

const allSelected = computed(() => {
  const ids = data.value?.content.map((p) => p.id) ?? []
  return ids.length > 0 && ids.every((id) => selected.value.has(id))
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await fetchPosts({
      q: q.value || undefined,
      status: status.value || undefined,
      page: page.value,
      size: 10
    })
    selected.value = new Set()
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка загрузки постов'
  } finally {
    loading.value = false
  }
}

function toggleAll() {
  if (!data.value) return
  if (allSelected.value) {
    selected.value = new Set()
    return
  }
  selected.value = new Set(data.value.content.map((p) => p.id))
}

function toggleOne(id: string) {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}

async function publish(id: string) {
  await publishPost(id)
  await load()
}

async function archive(id: string) {
  await archivePost(id)
  await load()
}

async function remove(id: string) {
  if (!confirm('Удалить пост?')) return
  await deletePost(id)
  await load()
}

async function runBulk(action: 'PUBLISH' | 'ARCHIVE' | 'DELETE') {
  const ids = [...selected.value]
  if (!ids.length) return
  if (action === 'DELETE' && !confirm(`Удалить выбранные посты (${ids.length})?`)) return
  message.value = ''
  error.value = ''
  try {
    const result = await bulkPosts(ids, action)
    message.value = `Готово: успешно ${result.success}, ошибок ${result.failed}`
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка массового действия'
  }
}

function copyUrl(slug: string) {
  const url = `${window.location.origin.replace(':8088', ':8080')}${publicPostPath(slug)}`
  navigator.clipboard?.writeText(url)
  message.value = 'Публичный URL скопирован'
}

onMounted(load)
watch([status], () => {
  page.value = 0
  load()
})
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <p class="eyebrow">Контент</p>
        <h1>Посты</h1>
      </div>
      <RouterLink class="btn" to="/posts/new">Новый пост</RouterLink>
    </header>

    <div class="toolbar card">
      <input v-model="q" placeholder="Поиск по заголовку" @keyup.enter="load" />
      <select v-model="status">
        <option value="">Все статусы</option>
        <option value="DRAFT">Черновик</option>
        <option value="PUBLISHED">Опубликован</option>
        <option value="ARCHIVED">В архиве</option>
      </select>
      <button class="btn secondary" @click="load">Фильтр</button>
      <div class="bulk" v-if="selected.size">
        <span>{{ selected.size }} выбрано</span>
        <button class="btn secondary" @click="runBulk('PUBLISH')">Опубликовать</button>
        <button class="btn secondary" @click="runBulk('ARCHIVE')">В архив</button>
        <button v-if="auth.isAdmin" class="btn danger" @click="runBulk('DELETE')">Удалить</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="ok">{{ message }}</p>
    <p v-if="loading">Загрузка…</p>

    <div v-if="data" class="list card">
      <div class="head">
        <label class="check">
          <input type="checkbox" :checked="allSelected" @change="toggleAll" />
          Выбрать все
        </label>
      </div>
      <article v-for="post in data.content" :key="post.id" class="row">
        <label class="check">
          <input type="checkbox" :checked="selected.has(post.id)" @change="toggleOne(post.id)" />
        </label>
        <div class="cover" v-if="post.coverUrl || post.coverMediaId">
          <img :src="mediaPublicUrl(post.coverUrl || post.coverMediaId)" :alt="post.title" />
        </div>
        <div class="cover placeholder" v-else>Нет обложки</div>
        <div class="body">
          <RouterLink :to="`/posts/${post.id}`" class="title">{{ post.title }}</RouterLink>
          <p>{{ post.shortDescription }}</p>
          <div class="meta">
            <span class="badge" :class="post.status">{{ statusLabel(post.status) }}</span>
            <code class="slug" :title="post.slug">{{ post.slug }}</code>
            <button class="btn ghost" type="button" @click="copyUrl(post.slug)">URL</button>
          </div>
        </div>
        <div class="actions">
          <button class="btn secondary" @click="publish(post.id)">Опубликовать</button>
          <button class="btn secondary" @click="archive(post.id)">В архив</button>
          <button v-if="auth.isAdmin" class="btn danger" @click="remove(post.id)">Удалить</button>
        </div>
      </article>
      <div v-if="!data.content.length" class="empty">Постов пока нет</div>
    </div>

    <div v-if="data && data.totalPages > 1" class="pager">
      <button class="btn secondary" :disabled="page === 0" @click="page--; load()">Назад</button>
      <span>{{ page + 1 }} / {{ data.totalPages }}</span>
      <button class="btn secondary" :disabled="page + 1 >= data.totalPages" @click="page++; load()">Далее</button>
    </div>
  </section>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
}

.toolbar input {
  flex: 1;
  min-width: 180px;
}

.bulk {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
  margin-left: auto;
  color: var(--muted);
  font-size: 0.9rem;
}

.list {
  overflow: hidden;
}

.head {
  padding: 0.75rem 1.15rem;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 0.9rem;
}

.row {
  display: grid;
  grid-template-columns: auto 88px 1fr auto;
  gap: 1rem;
  padding: 1rem 1.15rem;
  border-bottom: 1px solid var(--line);
  align-items: start;
}

.row:last-child {
  border-bottom: 0;
}

.check {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin-top: 0.35rem;
}

.cover {
  width: 88px;
  height: 64px;
  border-radius: 10px;
  overflow: hidden;
  background: #e7eeeb;
  display: grid;
  place-items: center;
  font-size: 0.7rem;
  color: var(--muted);
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.title {
  font-family: var(--font-serif);
  font-size: 1.25rem;
}

.body p,
.meta {
  color: var(--muted);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
  align-items: center;
  margin-top: 0.55rem;
  font-size: 0.85rem;
}

.slug {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: #eef4f1;
  padding: 0.15rem 0.45rem;
  border-radius: 6px;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.pager {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  margin-top: 1rem;
}

@media (max-width: 900px) {
  .row {
    grid-template-columns: auto 1fr;
  }

  .cover,
  .actions {
    grid-column: 1 / -1;
  }

  .actions {
    flex-direction: row;
    flex-wrap: wrap;
  }
}
</style>
