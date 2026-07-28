<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchDashboard, type DashboardStats } from '@/api/posts'
import { useToastStore } from '@/stores/toast'

const stats = ref<DashboardStats | null>(null)
const toast = useToastStore()

onMounted(async () => {
  try {
    stats.value = await fetchDashboard()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось загрузить обзор')
  }
})
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <p class="eyebrow">Сводка</p>
        <h1>Обзор</h1>
        <p class="muted lead">Контент, каталог и медиатека — цифры и быстрые действия</p>
      </div>
    </header>

    <div class="hubs">
      <article class="card hub">
        <p class="hub-kicker">Контент</p>
        <h2>Посты</h2>
        <p>Черновики, публикация и редактура статей</p>
        <div v-if="stats" class="hub-stats">
          <span>{{ stats.posts?.drafts ?? stats.drafts }} черн.</span>
          <span>{{ stats.posts?.published ?? stats.published }} опубл.</span>
          <span>{{ stats.posts?.total ?? stats.total }} всего</span>
        </div>
        <div class="hub-actions">
          <RouterLink class="btn" to="/posts">К постам</RouterLink>
          <RouterLink class="btn secondary" to="/posts/new">Новый</RouterLink>
          <RouterLink class="btn secondary" to="/media?section=ARTICLES">Медиа</RouterLink>
        </div>
      </article>

      <article class="card hub accent">
        <p class="hub-kicker">Каталог</p>
        <h2>Запчасти</h2>
        <p>Номенклатура, комплекты, дроны и импорт</p>
        <div v-if="stats" class="hub-stats">
          <span>{{ stats.parts?.drafts ?? 0 }} черн.</span>
          <span>{{ stats.parts?.published ?? 0 }} опубл.</span>
          <span>{{ stats.parts?.total ?? 0 }} запч.</span>
          <span>{{ stats.kits?.total ?? 0 }} компл.</span>
        </div>
        <div class="hub-actions">
          <RouterLink class="btn" to="/parts">К каталогу</RouterLink>
          <RouterLink class="btn secondary" to="/parts/new">Добавить</RouterLink>
          <RouterLink class="btn secondary" to="/categories">Категории</RouterLink>
          <RouterLink class="btn secondary" to="/media?section=PARTS">Медиа</RouterLink>
        </div>
      </article>

      <article class="card hub soft">
        <p class="hub-kicker">Файлы</p>
        <h2>Медиатека</h2>
        <p>Загрузка, квадрат, watermark и WebP</p>
        <div v-if="stats" class="hub-stats">
          <span>{{ stats.media?.total ?? 0 }} файлов</span>
          <span>{{ stats.media?.incomplete ?? 0 }} без обработки</span>
        </div>
        <div class="hub-actions">
          <RouterLink class="btn" to="/media">Открыть</RouterLink>
          <RouterLink class="btn secondary" to="/media?section=PARTS">Запчасти</RouterLink>
          <RouterLink class="btn secondary" to="/media?section=ARTICLES">Статьи</RouterLink>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.lead {
  margin: 0.4rem 0 0;
}

.hubs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
}

.hub {
  padding: 1.35rem 1.3rem;
  display: grid;
  gap: 0.55rem;
  align-content: start;
  min-height: 240px;
  background:
    linear-gradient(160deg, rgba(141, 198, 63, 0.08), transparent 50%),
    var(--glass);
}

.hub h2 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 1.85rem;
}

.hub p {
  margin: 0;
  color: var(--muted);
}

.hub-kicker {
  text-transform: uppercase;
  letter-spacing: 0.1em;
  font-size: 0.72rem;
  font-weight: 650;
  color: var(--accent) !important;
}

.hub-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
  font-size: 0.85rem;
  color: var(--muted);
}

.hub-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-top: auto;
  padding-top: 0.6rem;
}

.hub.accent {
  background:
    radial-gradient(circle at 20% 0%, rgba(141, 198, 63, 0.22), transparent 45%),
    linear-gradient(155deg, rgba(2, 18, 40, 0.95), rgba(4, 29, 72, 0.92));
  color: #fff;
  border: 1px solid rgba(141, 198, 63, 0.28);
}

.hub.accent p,
.hub.accent .hub-stats {
  color: #e4ecf6;
}

.hub.accent .hub-kicker {
  color: var(--accent) !important;
}

.hub.accent .btn.secondary {
  color: #fff;
  border-color: rgba(141, 198, 63, 0.35);
}

.hub.soft {
  background:
    linear-gradient(160deg, rgba(141, 198, 63, 0.08), transparent 45%),
    var(--glass);
}

@media (max-width: 1100px) {
  .hubs {
    grid-template-columns: 1fr;
  }
}
</style>
