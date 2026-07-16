<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  archivePost,
  deletePost,
  fetchPosts,
  publishPost,
  type PageResponse
} from '@/api/posts'
import { useAuthStore } from '@/stores/auth'
import { statusLabel } from '@/utils/labels'

const auth = useAuthStore()
const q = ref('')
const status = ref('')
const page = ref(0)
const data = ref<PageResponse | null>(null)
const error = ref('')
const loading = ref(false)

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
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка загрузки постов'
  } finally {
    loading.value = false
  }
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
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading">Загрузка…</p>

    <div v-if="data" class="list card">
      <article v-for="post in data.content" :key="post.id" class="row">
        <div>
          <RouterLink :to="`/posts/${post.id}`" class="title">{{ post.title }}</RouterLink>
          <p>{{ post.shortDescription }}</p>
          <div class="meta">
            <span class="badge" :class="post.status">{{ statusLabel(post.status) }}</span>
            <span>{{ post.slug }}</span>
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
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 1rem;
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

.toolbar {
  display: grid;
  grid-template-columns: 1fr 180px auto;
  gap: 0.75rem;
  padding: 0.9rem;
  margin-bottom: 1rem;
}

.toolbar input,
.toolbar select {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 0.7rem 0.85rem;
  background: white;
}

.list {
  overflow: hidden;
}

.row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 1rem;
  padding: 1rem 1.15rem;
  border-bottom: 1px solid var(--line);
}

.row:last-child {
  border-bottom: 0;
}

.title {
  font-family: var(--font-serif);
  font-size: 1.25rem;
}

.row p,
.meta,
.empty,
.error {
  color: var(--muted);
}

.meta {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  margin-top: 0.55rem;
  font-size: 0.85rem;
}

.actions {
  display: flex;
  gap: 0.5rem;
  align-items: start;
}

.pager {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  margin-top: 1rem;
}

.empty {
  padding: 1.5rem;
}

@media (max-width: 900px) {
  .row,
  .toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
