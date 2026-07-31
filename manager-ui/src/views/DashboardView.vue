<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { listMyProposals } from '@/api/kp'
import type { Proposal } from '@/types/kp'
import { formatDate, formatMoney } from '@/utils/format'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const proposals = ref<Proposal[]>([])
const loading = ref(true)

const recent = computed(() => proposals.value.slice(0, 5))

onMounted(async () => {
  try {
    proposals.value = await listMyProposals()
  } catch {
    toast.error('Не удалось загрузить КП')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="dash">
    <header class="head">
      <h1>Обзор</h1>
      <RouterLink class="btn" to="/kp/new">Новый КП</RouterLink>
    </header>

    <p v-if="loading" class="muted">Загрузка…</p>
    <template v-else>
      <p class="count muted">Всего КП: {{ proposals.length }}</p>

      <ul v-if="recent.length" class="items">
        <li v-for="p in recent" :key="p.id" class="item">
          <div>
            <strong>№{{ p.number }} · {{ p.droneModelName }}</strong>
            <p class="muted">{{ formatMoney(Number(p.grandTotal)) }} · {{ formatDate(p.updatedAt) }}</p>
          </div>
          <RouterLink class="btn secondary compact" :to="p.status === 'DRAFT' ? `/kp/${p.id}` : '/kp'">
            {{ p.status === 'DRAFT' ? 'Открыть' : 'К списку' }}
          </RouterLink>
        </li>
      </ul>
      <p v-else class="muted">Пока нет КП — создайте первое.</p>
    </template>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.85rem;
}
.head h1 {
  margin: 0;
  font-size: 1.45rem;
}
.count {
  margin: 0 0 0.85rem;
}
.items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.55rem;
}
.item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  padding: 0.85rem 1rem;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--glass);
}
.item p {
  margin: 0.2rem 0 0;
}
</style>
