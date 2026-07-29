<script setup lang="ts">
import { formatMoney } from '@/utils/format'

const props = defineProps<{
  subtotal: number
  discount: number
  total: number
  nds: number
  loading: boolean
}>()

const emit = defineEmits<{
  save: []
  finalize: []
  preset: []
}>()
</script>

<template>
  <div class="summary card">
    <div class="totals">
      <p><span>Сумма позиций</span><strong>{{ formatMoney(props.subtotal) }}</strong></p>
      <p><span>Скидка</span><strong>- {{ formatMoney(props.discount) }}</strong></p>
      <p><span>Итого</span><strong>{{ formatMoney(props.total) }}</strong></p>
      <p><span>НДС к возмещению</span><strong>{{ formatMoney(props.nds) }}</strong></p>
    </div>
    <div class="actions actions-row">
      <button class="btn secondary" type="button" :disabled="props.loading" @click="emit('save')">Сохранить черновик</button>
      <button class="btn" type="button" :disabled="props.loading" @click="emit('finalize')">Сформировать PDF</button>
      <button class="btn secondary" type="button" :disabled="props.loading" @click="emit('preset')">КП из шаблона</button>
    </div>
  </div>
</template>

<style scoped>
.summary { position: sticky; bottom: 0.5rem; padding: 0.75rem; margin-top: 0.8rem; }
.totals { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0.5rem; }
.totals p { margin: 0; display: grid; gap: 0.2rem; }
.totals span { color: var(--muted); font-size: 0.8rem; }
.totals strong { font-size: 0.95rem; }
.actions { margin-top: 0.7rem; justify-content: flex-start; }
@media (max-width: 960px) {
  .summary { position: static; }
  .totals { grid-template-columns: repeat(2, minmax(0,1fr)); }
}
@media (max-width: 560px) {
  .totals { grid-template-columns: 1fr; }
  .actions { display: grid; grid-template-columns: 1fr; justify-content: stretch; }
  .actions .btn { width: 100%; }
}
</style>
