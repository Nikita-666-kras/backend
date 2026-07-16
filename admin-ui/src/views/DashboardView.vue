<script setup lang="ts">
import { onMounted, ref } from 'vue'
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
        <h1>Обзор</h1>
      </div>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="stats" class="grid">
      <article class="card stat">
        <span>Черновики</span>
        <strong>{{ stats.drafts }}</strong>
      </article>
      <article class="card stat">
        <span>Опубликовано</span>
        <strong>{{ stats.published }}</strong>
      </article>
      <article class="card stat">
        <span>В архиве</span>
        <strong>{{ stats.archived }}</strong>
      </article>
      <article class="card stat">
        <span>Всего</span>
        <strong>{{ stats.total }}</strong>
      </article>
    </div>
  </section>
</template>

<style scoped>
.page-header {
  margin-bottom: 1.25rem;
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

.grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
}

.stat {
  padding: 1.25rem;
  display: grid;
  gap: 0.5rem;
}

.stat span {
  color: var(--muted);
}

.stat strong {
  font-family: var(--font-serif);
  font-size: 2.4rem;
}

.error {
  color: var(--danger);
}

@media (max-width: 960px) {
  .grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
