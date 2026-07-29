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
    sessionStorage.setItem('manager_kp_clone', JSON.stringify({
      recipient: `${full.recipient} (копия)`,
      droneModelId: full.droneModelId,
      dronePrice: full.dronePrice,
      lines: full.lines.map((l) => ({
        lineType: l.lineType === 'DRONE' ? 'PART' : l.lineType,
        refId: l.refId || '',
        sku: l.sku || '',
        name: l.name,
        qty: l.qty,
        unitPrice: Number(l.unitPrice),
        discountPct: l.discountPct || 0
      }))
    }))
    toast.ok('Копия подготовлена. Откройте «Новый КП»')
  } catch {
    toast.error('Не удалось подготовить копию КП')
  }
}
</script>

<template>
  <div>
    <header class="page-header">
      <div>
        <p class="eyebrow">КП</p>
        <h1>Мои коммерческие предложения</h1>
        <p class="subtitle">Открывайте черновики, копируйте прошлые КП и выгружайте PDF в один клик.</p>
      </div>
      <RouterLink class="btn" to="/kp/new">Новый КП</RouterLink>
    </header>

    <div class="toolbar card">
      <label class="muted">Статус:</label>
      <select v-model="filter">
        <option value="ALL">Все</option>
        <option value="DRAFT">Черновики</option>
        <option value="FINAL">Финальные</option>
      </select>
      <button class="btn secondary" type="button" @click="load">Обновить</button>
    </div>

    <section class="card surface-light section-pad table-wrap">
      <p v-if="loading" class="muted">Загрузка…</p>
      <p v-else-if="!rows.length" class="empty">КП не найдены</p>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>№</th><th>Получатель</th><th>Модель</th><th>Сумма</th><th>Обновлено</th><th>Статус</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in rows" :key="p.id">
            <td>{{ p.number }}</td>
            <td>{{ p.recipient }}</td>
            <td>{{ p.droneModelName }}</td>
            <td>{{ formatMoney(Number(p.grandTotal)) }}</td>
            <td>{{ formatDate(p.updatedAt) }}</td>
            <td><span class="badge" :class="p.status">{{ p.status === 'DRAFT' ? 'Черновик' : 'Финал' }}</span></td>
            <td class="actions">
              <div class="actions-row">
                <RouterLink v-if="p.status === 'DRAFT'" class="btn secondary compact" :to="`/kp/${p.id}`">Открыть</RouterLink>
                <button class="btn secondary compact" type="button" @click="onPdf(p)">PDF</button>
                <button class="btn secondary compact" type="button" @click="cloneAsNew(p)">Копировать</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.table-wrap { padding-top: 0.9rem; padding-bottom: 0.9rem; }
.actions { text-align: right; white-space: nowrap; }
</style>
