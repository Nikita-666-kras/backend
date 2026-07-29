<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div class="placeholder card">
    <p class="eyebrow">Скоро</p>
    <h1>{{ title }}</h1>
    <p class="muted">{{ description || 'Модуль в разработке' }}</p>
    <p class="hint">Раздел появится в менеджерском хабе в следующих обновлениях.</p>
  </div>
</template>

<style scoped>
.placeholder {
  padding: 2rem;
  max-width: 560px;
}

.placeholder h1 {
  margin: 0.25rem 0 0.5rem;
  font-size: 1.75rem;
}

.hint {
  margin-top: 1rem;
  font-size: 0.9rem;
  color: var(--muted);
}
</style>
