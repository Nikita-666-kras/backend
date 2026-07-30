<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchLogs, fetchLogsStats, type LogEntry } from '@/api/logs'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const loading = ref(false)
const page = ref(0)
const size = ref(50)
const total = ref(0)
const rows = ref<LogEntry[]>([])
const q = ref('')
const level = ref('')
const category = ref('')
const service = ref('')
const from = ref('')
const to = ref('')
const stats = ref<{ byLevel: Record<string, number>; byCategory: Record<string, number> } | null>(null)

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

function isoOrUndefined(value: string) {
  return value ? new Date(value).toISOString() : undefined
}

async function load() {
  loading.value = true
  try {
    const response = await fetchLogs({
      from: isoOrUndefined(from.value),
      to: isoOrUndefined(to.value),
      level: level.value || undefined,
      category: category.value || undefined,
      service: service.value || undefined,
      q: q.value || undefined,
      page: page.value,
      size: size.value
    })
    rows.value = response.items
    total.value = response.totalElements
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось загрузить логи')
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await fetchLogsStats(isoOrUndefined(from.value), isoOrUndefined(to.value))
  } catch {
    stats.value = null
  }
}

async function applyFilters() {
  page.value = 0
  await Promise.all([load(), loadStats()])
}

function resetFilters() {
  q.value = ''
  level.value = ''
  category.value = ''
  service.value = ''
  from.value = ''
  to.value = ''
  applyFilters()
}

function openDetails(entry: LogEntry) {
  const json = entry.detailsJson || '{}'
  alert(`Message: ${entry.message}\n\nDetails:\n${json}`)
}

onMounted(async () => {
  await Promise.all([load(), loadStats()])
})
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <p class="eyebrow">Система</p>
        <h1>Логи</h1>
        <p class="muted">Аудит-события и важные предупреждения</p>
      </div>
    </header>

    <div class="card filters">
      <input v-model="q" placeholder="Поиск по сообщению / requestId" @keyup.enter="applyFilters" />
      <select v-model="level">
        <option value="">Все уровни</option>
        <option>SECURITY</option>
        <option>ERROR</option>
        <option>WARN</option>
        <option>AUDIT</option>
        <option>INFO</option>
      </select>
      <input v-model="category" placeholder="Категория (AUTH, USERS...)" />
      <input v-model="service" placeholder="Сервис" />
      <input v-model="from" type="datetime-local" />
      <input v-model="to" type="datetime-local" />
      <div class="actions">
        <button class="btn" type="button" @click="applyFilters">Применить</button>
        <button class="btn secondary" type="button" @click="resetFilters">Сброс</button>
      </div>
    </div>

    <div class="card stats" v-if="stats">
      <p><strong>By level:</strong> {{ stats.byLevel }}</p>
      <p><strong>By category:</strong> {{ stats.byCategory }}</p>
    </div>

    <div class="card table-wrap">
      <table>
        <thead>
          <tr>
            <th>Время</th>
            <th>Сервис</th>
            <th>Уровень</th>
            <th>Категория</th>
            <th>Сообщение</th>
            <th>Повторы</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in rows" :key="item.id">
            <td>{{ new Date(item.lastSeenAt).toLocaleString('ru-RU') }}</td>
            <td>{{ item.service }}</td>
            <td>{{ item.level }}</td>
            <td>{{ item.category }}</td>
            <td>{{ item.message }}</td>
            <td>{{ item.count }}</td>
            <td><button class="btn secondary" type="button" @click="openDetails(item)">Детали</button></td>
          </tr>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="7">Логи не найдены</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pager">
      <button class="btn secondary" :disabled="page === 0" @click="page--; load()">Назад</button>
      <span>{{ page + 1 }} / {{ pageCount }}</span>
      <button class="btn secondary" :disabled="page + 1 >= pageCount" @click="page++; load()">Вперёд</button>
    </div>
  </section>
</template>

<style scoped>
.filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.7rem;
  margin-bottom: 1rem;
}

.actions {
  display: flex;
  gap: 0.6rem;
}

.stats {
  margin-bottom: 1rem;
}

.table-wrap {
  overflow: auto;
}

.pager {
  margin-top: 0.8rem;
  display: flex;
  align-items: center;
  gap: 0.8rem;
}
</style>
