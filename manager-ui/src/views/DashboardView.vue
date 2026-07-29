<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { finalizeProposal, downloadPdf, listMyProposals } from '@/api/kp'
import type { Proposal } from '@/types/kp'
import { formatDate, formatMoney } from '@/utils/format'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const proposals = ref<Proposal[]>([])
const loading = ref(true)

const stats = computed(() => {
  const all = proposals.value
  return {
    total: all.length,
    drafts: all.filter((p) => p.status === 'DRAFT').length,
    final: all.filter((p) => p.status === 'FINAL').length
  }
})

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

async function downloadPdfAction(p: Proposal) {
  try {
    let id = p.id
    if (p.status !== 'FINAL') {
      const finalized = await finalizeProposal(id)
      id = finalized.id
      proposals.value = await listMyProposals()
    }
    await downloadPdf(id, `KP_${p.number}.pdf`)
    toast.ok('PDF скачан')
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    toast.error(msg || 'Не удалось скачать PDF')
  }
}
</script>

<template>
  <div>
    <header class="page-header">
      <div>
        <p class="eyebrow">Главная</p>
        <h1>Обзор</h1>
      </div>
      <RouterLink class="btn" to="/kp/new">Новый КП</RouterLink>
    </header>

    <div v-if="loading" class="muted">Загрузка…</div>
    <template v-else>
      <div class="stat-grid">
        <div class="stat-card">
          <strong>{{ stats.total }}</strong>
          <span>Всего КП</span>
        </div>
        <div class="stat-card">
          <strong>{{ stats.drafts }}</strong>
          <span>Черновики</span>
        </div>
        <div class="stat-card">
          <strong>{{ stats.final }}</strong>
          <span>Финальные</span>
        </div>
      </div>

      <section class="card surface-light section-pad recent">
        <div class="section-head">
          <h2>Последние КП</h2>
          <RouterLink class="muted link" to="/kp">Все КП →</RouterLink>
        </div>

        <p v-if="!recent.length" class="empty">Пока нет коммерческих предложений</p>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>№</th>
              <th>Получатель</th>
              <th>Модель</th>
              <th>Сумма</th>
              <th>Статус</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in recent" :key="p.id">
              <td>{{ p.number }}</td>
              <td>{{ p.recipient }}</td>
              <td>{{ p.droneModelName }}</td>
              <td>{{ formatMoney(Number(p.grandTotal)) }}</td>
              <td><span class="badge" :class="p.status">{{ p.status === 'FINAL' ? 'Готово' : 'Черновик' }}</span></td>
              <td class="actions">
                <div class="actions-row">
                  <RouterLink v-if="p.status === 'DRAFT'" class="btn secondary compact" :to="`/kp/${p.id}`">Открыть</RouterLink>
                  <button class="btn secondary compact" type="button" @click="downloadPdfAction(p)">PDF</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>

<style scoped>
.recent { margin-top: 1rem; }

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.section-head h2 {
  margin: 0;
  font-size: 1.1rem;
}

.link {
  font-size: 0.85rem;
}

.actions { text-align: right; white-space: nowrap; }
</style>
