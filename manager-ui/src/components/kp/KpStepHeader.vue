<script setup lang="ts">
const props = defineProps<{
  step: 1 | 2 | 3
}>()

const steps = [
  { id: 1, title: 'Клиент и модель' },
  { id: 2, title: 'Состав' },
  { id: 3, title: 'Итог' }
]
</script>

<template>
  <div class="steps">
    <div v-for="s in steps" :key="s.id" class="step" :class="{ active: props.step >= s.id }">
      <span>{{ s.id }}</span>
      <p>{{ s.title }}</p>
    </div>
  </div>
</template>

<style scoped>
.steps { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0.5rem; margin-bottom: 1rem; }
.step { border: 1px solid var(--line); border-radius: 10px; padding: 0.5rem 0.65rem; display: flex; align-items: center; gap: 0.5rem; color: var(--muted); }
.step span { width: 1.4rem; height: 1.4rem; border-radius: 50%; display: grid; place-items: center; background: rgba(255,255,255,0.08); font-size: 0.75rem; font-weight: 700; }
.step p { margin: 0; font-size: 0.82rem; }
.step.active { border-color: rgba(141, 198, 63, 0.55); color: var(--ink); }
.step.active span { background: var(--accent); color: var(--accent-ink); }
</style>
