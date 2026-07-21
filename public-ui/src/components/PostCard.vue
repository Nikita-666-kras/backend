<script setup lang="ts">
import { mediaUrl, type Post, formatDate, formatReadingTime } from '@/api/posts'

defineProps<{
  post: Post
  featured?: boolean
}>()
</script>

<template>
  <article class="post-card" :class="{ 'post-card--featured': featured }">
    <RouterLink class="post-card__link" :to="{ name: 'post', params: { slug: post.slug } }">
      <div v-if="post.coverUrl" class="post-card__media">
        <img
          :src="mediaUrl(post.coverUrl)"
          :alt="post.title"
          loading="lazy"
          decoding="async"
        />
      </div>
      <div class="post-card__body">
        <div class="post-card__meta">
          <span v-if="post.categories?.length" class="post-card__tag">
            {{ post.categories[0] }}
          </span>
          <time :datetime="post.createdAt">{{ formatDate(post.createdAt) }}</time>
          <span>{{ formatReadingTime(post.readingTime) }}</span>
        </div>
        <h2 class="post-card__title">{{ post.title }}</h2>
        <p class="post-card__desc">{{ post.shortDescription }}</p>
        <span class="post-card__cta">Читать статью</span>
      </div>
    </RouterLink>
  </article>
</template>
