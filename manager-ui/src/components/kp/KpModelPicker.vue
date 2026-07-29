<script setup lang="ts">
import type { DroneModel } from '@/types/kp'
import { formatMoney } from '@/utils/format'

const props = defineProps<{
  models: DroneModel[]
  modelId: string
}>()

const emit = defineEmits<{
  pick: [id: string]
}>()
</script>

<template>
  <div class="models">
    <button
      v-for="m in props.models"
      :key="m.id"
      type="button"
      class="model-card"
      :class="{ active: m.id === props.modelId }"
      @click="emit('pick', m.id)"
    >
      <strong>{{ m.name }}</strong>
      <small>{{ m.code }}</small>
      <span>{{ formatMoney(Number(m.defaultPrice)) }}</span>
    </button>
  </div>
</template>

<style scoped>
.models { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 0.5rem; }
.model-card { text-align: left; border: 1px solid var(--line); border-radius: 10px; background: rgba(255,255,255,0.04); padding: 0.65rem; color: var(--ink); display: grid; gap: 0.2rem; }
.model-card strong { font-size: 0.9rem; }
.model-card small { color: var(--muted); }
.model-card span { font-size: 0.82rem; color: #d9ffc0; }
.model-card.active { border-color: rgba(141,198,63,0.7); background: rgba(141,198,63,0.12); }
</style>
