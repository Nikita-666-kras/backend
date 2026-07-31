<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { downloadPdf, finalizeProposal, getProposal, listMyProposals } from '@/api/kp'
import { useToastStore } from '@/stores/toast'
import type { Proposal } from '@/types/kp'
import { formatDate, formatMoney } from '@/utils/format'

const toast = useToastStore()
const loading = ref(false)
const filter = ref<'ALL' | 'DRAFT' | 'FINAL'>('ALL')
const proposals = ref<Proposal[]>([])

const rows = computed(() =>
  proposals.value.filter((p) => filter.value === 'ALL' || p.status === filter.value)
)

async function load() {
  loading.value = true
  try {
    proposals.value = await listMyProposals()
  } catch {
    toast.error('Не удалось загрузить список КП')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function onPdf(p: Proposal) {
  try {
    let id = p.id
    if (p.status !== 'FINAL') {
      const finalized = await finalizeProposal(id)
      id = finalized.id
      await load()
    }
    await downloadPdf(id, `KP_${p.number}.pdf`)
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    toast.error(msg || 'Не удалось скачать PDF')
  }
}

async function cloneAsNew(p: Proposal) {
  try {
    const full = await getProposal(p.id)
    sessionStorage.setItem(
      'manager_kp_clone',
      JSON.stringify({
        recipient: full.recipient,
        droneModelId: full.droneModelId,
        kitQty: full.kitQty || 1,
        unitKitPrice: Number(full.unitKitPrice ?? full.grandTotal),
        dronePrice: Number(full.dronePrice || 0),
        droneVatPct: full.droneVatPct === 22 ? 22 : 0,
        extraLines: (full.lines || [])
          .filter((l) => l.refId)
          .map((l) => ({
            lineType: l.lineType === 'DRONE' ? 'PART' : l.lineType,
            refId: l.refId || '',
            sku: l.sku || '',
            name: l.name,
            qty: l.qty,
            unitPrice: Number(l.unitPrice),
            discountPct: l.discountPct || 0,
            kitItems: l.kitItems
          }))
      })
    )
    toast.ok('Откройте «Новый КП» — данные подставятся')
  } catch {
    toast.error('Не удалось скопировать КП')
  }
}
</script>

<template>
  <div class="list-page">
    <header class="head">
      <h1>Мои КП</h1>
      <RouterLink class="btn" to="/kp/new">Новый КП</RouterLink>
    </header>

    <div class="filters">
      <select v-model="filter" aria-label="Фильтр">
        <option value="ALL">Все</option>
        <option value="DRAFT">Черновики</option>
        <option value="FINAL">Готовые</option>
      </select>
    </div>

    <p v-if="loading" class="muted">Загрузка…</p>
    <p v-else-if="!rows.length" class="muted">Пока нет КП</p>

    <ul v-else class="items">
      <li v-for="p in rows" :key="p.id" class="item">
        <div class="meta">
          <strong>№{{ p.number }} · {{ p.droneModelName }}</strong>
          <span class="muted">{{ p.recipient }}</span>
          <span class="muted">{{ formatMoney(Number(p.grandTotal)) }} · {{ formatDate(p.updatedAt) }}</span>
        </div>
        <div class="actions">
          <RouterLink v-if="p.status === 'DRAFT'" class="btn secondary compact" :to="`/kp/${p.id}`">Открыть</RouterLink>
          <button class="btn secondary compact" type="button" @click="onPdf(p)">PDF</button>
          <button class="btn secondary compact" type="button" @click="cloneAsNew(p)">Копия</button>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}
.head h1 {
  margin: 0;
  font-size: 1.45rem;
}
.filters {
  margin-bottom: 0.85rem;
}
.filters select {
  min-height: 2.4rem;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: var(--input-bg);
  padding: 0.4rem 0.7rem;
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
  gap: 0.75rem;
  justify-content: space-between;
  align-items: center;
  padding: 0.85rem 1rem;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--glass);
}
.meta {
  display: grid;
  gap: 0.15rem;
  min-width: 0;
}
.meta strong,
.meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  justify-content: flex-end;
}
@media (max-width: 720px) {
  .item {
    flex-direction: column;
    align-items: stretch;
  }
  .actions .btn {
    flex: 1;
  }
}
</style>
