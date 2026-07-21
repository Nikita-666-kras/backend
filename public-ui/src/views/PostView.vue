<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  fetchPostBySlug,
  formatDate,
  formatReadingTime,
  mediaUrl,
  type Post
} from '@/api/posts'

const props = defineProps<{ slug: string }>()
const route = useRoute()

const post = ref<Post | null>(null)
const loading = ref(true)
const error = ref('')

const siteName = import.meta.env.VITE_SITE_NAME || 'Блог'

const breadcrumbs = computed(() => {
  if (!post.value) return siteName
  return `${siteName} · ${post.value.title}`
})

async function loadPost(slug: string) {
  loading.value = true
  error.value = ''
  post.value = null
  try {
    post.value = await fetchPostBySlug(slug)
    document.title = `${post.value.title} | ${siteName}`
  } catch {
    error.value = 'Статья не найдена или ещё не опубликована.'
    document.title = `Статья не найдена | ${siteName}`
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadPost(props.slug)
})

watch(
  () => route.params.slug,
  (slug) => {
    if (typeof slug === 'string' && slug !== post.value?.slug) {
      void loadPost(slug)
    }
  }
)
</script>

<template>
  <div class="post-page">
    <header class="post-top">
      <div class="post-top__inner">
        <RouterLink class="post-top__back" to="/">К блогу</RouterLink>
        <p class="post-top__crumbs">{{ breadcrumbs }}</p>
      </div>
    </header>

    <main class="post-main">
      <div v-if="loading" class="blog-state">Загрузка…</div>
      <div v-else-if="error" class="blog-state blog-state--error">{{ error }}</div>

      <article v-else-if="post" class="post-article">
        <div class="post-article__meta">
          <span v-if="post.categories?.length" class="post-article__tag">
            {{ post.categories[0] }}
          </span>
          <time :datetime="post.createdAt">{{ formatDate(post.createdAt) }}</time>
          <span>{{ formatReadingTime(post.readingTime) }}</span>
        </div>

        <h1 class="post-article__title">{{ post.title }}</h1>
        <p v-if="post.shortDescription" class="post-article__lead">{{ post.shortDescription }}</p>

        <figure v-if="post.coverUrl" class="post-article__cover">
          <img :src="mediaUrl(post.coverUrl)" :alt="post.title" decoding="async" />
        </figure>

        <div class="post-article__content" v-html="post.htmlContent" />

        <div v-if="post.tags?.length" class="post-article__tags">
          <span v-for="tag in post.tags" :key="tag" class="post-article__tag-pill">#{{ tag }}</span>
        </div>

        <div class="post-article__footer">
          <RouterLink class="blog-btn blog-btn--ghost" to="/">← Все статьи</RouterLink>
        </div>
      </article>
    </main>
  </div>
</template>
