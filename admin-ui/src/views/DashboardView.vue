<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchDashboard, type DashboardStats } from '@/api/posts'

const stats = ref<DashboardStats | null>(null)
const error = ref('')

onMounted(async () => {
  try {
    stats.value = await fetchDashboard()
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Не удалось загрузить обзор'
  }
})
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <p class="eyebrow">Сводка</p>
        <h1>Обзор редакции</h1>
        <p class="muted lead">Статусы материалов и быстрый старт</p>
      </div>
      <div class="actions">
        <RouterLink class="btn secondary" to="/media">Медиатека</RouterLink>
        <RouterLink class="btn" to="/posts/new">Новый пост</RouterLink>
      </div>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="stats" class="grid">
      <article class="card stat">
        <span>Черновики</span>
        <strong>{{ stats.drafts }}</strong>
        <RouterLink to="/posts?status=DRAFT">Открыть</RouterLink>
      </article>
      <article class="card stat">
        <span>Опубликовано</span>
        <strong>{{ stats.published }}</strong>
        <RouterLink to="/posts?status=PUBLISHED">Открыть</RouterLink>
      </article>
      <article class="card stat">
        <span>В архиве</span>
        <strong>{{ stats.archived }}</strong>
        <RouterLink to="/posts?status=ARCHIVED">Открыть</RouterLink>
      </article>
      <article class="card stat accent">
        <span>Всего</span>
        <strong>{{ stats.total }}</strong>
        <RouterLink to="/posts">Все посты</RouterLink>
      </article>
    </div>

    <div class="tips card">
      <h2>Рабочий процесс</h2>
      <ol>
        <li>Загрузите обложку и медиа в <RouterLink to="/media">медиатеку</RouterLink></li>
        <li>Создайте пост и вставьте картинки/видео в Markdown</li>
        <li>Опубликуйте — материал сразу появится в публичном API</li>
      </ol>
    </div>
  </section>
</template>

<style scoped>
.lead {
  margin: 0.4rem 0 0;
}

.actions {
  display: flex;
  gap: 0.6rem;
}

.grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
}

.stat {
  padding: 1.25rem;
  display: grid;
  gap: 0.45rem;
}

.stat span {
  color: var(--muted);
}

.stat strong {
  font-family: var(--font-serif);
  font-size: 2.5rem;
}

.stat a {
  color: var(--accent);
  font-size: 0.9rem;
}

.stat.accent {
  background: linear-gradient(145deg, #0f1f1b, #13473d);
  color: white;
  border: none;
}

.stat.accent span,
.stat.accent a {
  color: rgba(255, 255, 255, 0.75);
}

.tips {
  margin-top: 1.25rem;
  padding: 1.25rem 1.4rem;
}

.tips h2 {
  margin: 0 0 0.75rem;
  font-family: var(--font-serif);
  font-size: 1.35rem;
}

.tips ol {
  margin: 0;
  padding-left: 1.2rem;
  color: var(--muted);
  display: grid;
  gap: 0.45rem;
}

.tips a {
  color: var(--accent);
  text-decoration: underline;
}

@media (max-width: 960px) {
  .grid {
    grid-template-columns: 1fr 1fr;
  }

  .page-header {
    align-items: start;
    flex-direction: column;
  }
}
</style>
