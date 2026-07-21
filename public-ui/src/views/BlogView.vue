<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PostCard from '@/components/PostCard.vue'
import { fetchPosts, type Post } from '@/api/posts'

const siteName = import.meta.env.VITE_SITE_NAME || 'Блог'

const posts = ref<Post[]>([])
const page = ref(0)
const totalPages = ref(0)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')

const pageSize = 9

async function loadPage(nextPage: number, append = false) {
  const data = await fetchPosts(nextPage, pageSize)
  posts.value = append ? [...posts.value, ...data.content] : data.content
  page.value = data.number
  totalPages.value = data.totalPages
}

async function init() {
  loading.value = true
  error.value = ''
  try {
    await loadPage(0, false)
  } catch {
    error.value = 'Не удалось загрузить публикации. Проверьте, что API доступен.'
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || page.value + 1 >= totalPages.value) return
  loadingMore.value = true
  try {
    await loadPage(page.value + 1, true)
  } catch {
    error.value = 'Не удалось загрузить следующую страницу.'
  } finally {
    loadingMore.value = false
  }
}

onMounted(() => {
  document.title = `${siteName} — экспертные материалы`
  void init()
})
</script>

<template>
  <div class="blog-page">
    <header class="blog-cover">
      <div class="blog-cover__inner">
        <p class="blog-cover__eyebrow">{{ siteName }}</p>
        <h1>Экспертные материалы</h1>
        <p class="blog-cover__lead">
          Статьи, гайды и обзоры из админки blog-platform. Публикуются через API и
          сразу появляются в этой ленте.
        </p>
      </div>
    </header>

    <main class="blog-main">
      <div v-if="loading" class="blog-state">Загрузка…</div>
      <div v-else-if="error && !posts.length" class="blog-state blog-state--error">{{ error }}</div>
      <div v-else-if="!posts.length" class="blog-state">Пока нет опубликованных статей.</div>

      <template v-else>
        <div class="blog-grid">
          <PostCard
            v-for="(post, index) in posts"
            :key="post.id"
            :post="post"
            :featured="index === 0"
          />
        </div>

        <p v-if="error" class="blog-inline-error">{{ error }}</p>

        <div v-if="page + 1 < totalPages" class="blog-actions">
          <button class="blog-btn" type="button" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? 'Загрузка…' : 'Загрузить ещё' }}
          </button>
        </div>
      </template>
    </main>
  </div>
</template>
