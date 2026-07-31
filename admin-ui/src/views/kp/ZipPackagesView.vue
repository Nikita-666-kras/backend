<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import {
  fetchDroneModels,
  fetchZipPackage,
  saveZipPackage,
  type KpDroneModel,
  type KpZipItem,
  type KpZipPackage
} from '@/api/kp'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const route = useRoute()
const router = useRouter()

const models = ref<KpDroneModel[]>([])
const loading = ref(false)
const selectedId = ref('')
const zipLoading = ref(false)
const zipSaving = ref(false)
const zipForm = ref<{ name: string; price: number | null; useCustomPrice: boolean; items: KpZipItem[] }>({
  name: 'ЗИП-пакет',
  price: null,
  useCustomPrice: false,
  items: []
})

const selectedModel = computed(() => models.value.find((m) => m.id === selectedId.value) || null)

const itemsSum = computed(() =>
  zipForm.value.items.reduce(
    (acc, row) => acc + Math.max(0, Number(row.unitPrice) || 0) * Math.max(1, Number(row.qty) || 1),
    0
  )
)

const displayPrice = computed(() =>
  zipForm.value.useCustomPrice ? Number(zipForm.value.price || 0) : itemsSum.value
)

function money(v: number) {
  return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 }).format(Number(v || 0))
}

function emptyItem(): KpZipItem {
  return { name: '', sku: '', qty: 1, unitPrice: 0 }
}

function applyZip(pkg: KpZipPackage) {
  const items = (pkg.items || []).map((i) => ({
    name: i.name,
    sku: i.sku || '',
    qty: i.qty || 1,
    unitPrice: Number(i.unitPrice || 0),
    sortOrder: i.sortOrder
  }))
  const sum = items.reduce((acc, row) => acc + Number(row.unitPrice || 0) * Number(row.qty || 1), 0)
  const custom = pkg.price != null && Math.abs(Number(pkg.price) - sum) > 0.009
  zipForm.value = {
    name: pkg.name || 'ЗИП-пакет',
    price: pkg.price,
    useCustomPrice: custom,
    items: items.length ? items : [emptyItem()]
  }
}

async function loadModels() {
  loading.value = true
  try {
    models.value = await fetchDroneModels()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось загрузить модели')
  } finally {
    loading.value = false
  }
}

async function loadZip(modelId: string) {
  if (!modelId) return
  zipLoading.value = true
  try {
    applyZip(await fetchZipPackage(modelId))
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось загрузить ЗИП-пакет')
    zipForm.value = { name: 'ЗИП-пакет', price: null, useCustomPrice: false, items: [emptyItem()] }
  } finally {
    zipLoading.value = false
  }
}

async function selectModel(id: string) {
  selectedId.value = id
  if (route.query.modelId !== id) {
    await router.replace({ name: 'kp-zip-packages', query: id ? { modelId: id } : {} })
  }
  await loadZip(id)
}

function addZipRow() {
  zipForm.value.items.push(emptyItem())
}

function removeZipRow(idx: number) {
  zipForm.value.items.splice(idx, 1)
  if (!zipForm.value.items.length) zipForm.value.items.push(emptyItem())
}

async function saveZip() {
  if (!selectedId.value) return
  const itemsPayload = zipForm.value.items
    .filter((i) => i.name.trim())
    .map((i, idx) => ({
      name: i.name.trim(),
      sku: i.sku?.trim() || null,
      qty: Math.max(1, Number(i.qty) || 1),
      unitPrice: Math.max(0, Number(i.unitPrice) || 0),
      sortOrder: idx
    }))

  zipSaving.value = true
  try {
    const saved = await saveZipPackage(selectedId.value, {
      name: zipForm.value.name.trim() || 'ЗИП-пакет',
      price: zipForm.value.useCustomPrice ? Number(zipForm.value.price || 0) : null,
      items: itemsPayload
    })
    applyZip(saved)
    toast.ok('ЗИП-пакет сохранён')
    await loadModels()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось сохранить ЗИП-пакет')
  } finally {
    zipSaving.value = false
  }
}

async function clearZip() {
  if (!selectedId.value || !selectedModel.value) return
  if (!confirm(`Очистить ЗИП-пакет для «${selectedModel.value.name}»?`)) return
  zipSaving.value = true
  try {
    await saveZipPackage(selectedId.value, { name: 'ЗИП-пакет', price: null, items: [] })
    zipForm.value = { name: 'ЗИП-пакет', price: null, useCustomPrice: false, items: [emptyItem()] }
    toast.ok('ЗИП-пакет очищен')
    await loadModels()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось очистить ЗИП-пакет')
  } finally {
    zipSaving.value = false
  }
}

watch(
  () => route.query.modelId,
  async (raw) => {
    const id = typeof raw === 'string' ? raw : ''
    if (id && id !== selectedId.value) {
      selectedId.value = id
      await loadZip(id)
    }
  }
)

onMounted(async () => {
  await loadModels()
  const fromQuery = typeof route.query.modelId === 'string' ? route.query.modelId : ''
  if (fromQuery && models.value.some((m) => m.id === fromQuery)) {
    await selectModel(fromQuery)
  } else if (models.value.length) {
    await selectModel(models.value[0].id)
  }
})
</script>

<template>
  <section class="zip-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">Коммерческие предложения</p>
        <h1>КП · ЗИП-пакеты</h1>
        <p class="muted">Отдельное наполнение ЗИП для каждой модели дрона — менеджер видит состав по кнопке «?»</p>
      </div>
      <RouterLink class="btn secondary" to="/kp/drone-models">К моделям</RouterLink>
    </header>

    <p v-if="loading" class="muted">Загрузка…</p>

    <div v-else class="layout">
      <div class="card models-panel">
        <p class="panel-title">Модели дронов</p>
        <table class="models-table">
          <thead>
            <tr>
              <th>Модель</th>
              <th>Код</th>
              <th>ЗИП</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="m in models"
              :key="m.id"
              :class="{ active: m.id === selectedId }"
              @click="selectModel(m.id)"
            >
              <td>
                <strong>{{ m.name }}</strong>
              </td>
              <td class="muted">{{ m.code }}</td>
              <td>
                <span class="badge" :class="m.hasZipPackage ? 'ok' : 'empty'">
                  {{ m.hasZipPackage ? 'Заполнен' : 'Пусто' }}
                </span>
              </td>
              <td class="actions-cell">
                <button class="btn secondary tiny" type="button" @click.stop="selectModel(m.id)">
                  {{ m.hasZipPackage ? 'Изменить' : 'Добавить' }}
                </button>
              </td>
            </tr>
            <tr v-if="!models.length">
              <td colspan="4" class="muted">Сначала добавьте модели в «КП · Модели»</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="card editor-panel">
        <template v-if="selectedModel">
          <div class="editor-head">
            <div>
              <p class="panel-title">ЗИП для модели</p>
              <h2>{{ selectedModel.name }} <span class="muted">({{ selectedModel.code }})</span></h2>
            </div>
            <button class="btn danger secondary" type="button" :disabled="zipSaving" @click="clearZip">
              Очистить
            </button>
          </div>

          <p v-if="zipLoading" class="muted">Загрузка наполнения…</p>
          <template v-else>
            <div class="zip-meta">
              <label class="field">
                <span>Название пакета</span>
                <input v-model="zipForm.name" placeholder="ЗИП-пакет" />
              </label>
              <label class="check">
                <input v-model="zipForm.useCustomPrice" type="checkbox" />
                <span>Своя цена пакета</span>
              </label>
              <label v-if="zipForm.useCustomPrice" class="field">
                <span>Цена пакета, ₽</span>
                <input v-model.number="zipForm.price" type="number" min="0" step="0.01" />
              </label>
              <p v-else class="price-hint muted">Цена = сумма позиций · {{ money(itemsSum) }} ₽</p>
            </div>

            <p class="items-label">Позиции наполнения</p>
            <div class="zip-items">
              <div class="zip-row head">
                <span>Наименование</span>
                <span>Артикул</span>
                <span>Кол-во</span>
                <span>Цена, ₽</span>
                <span></span>
              </div>
              <div v-for="(row, idx) in zipForm.items" :key="idx" class="zip-row">
                <input v-model="row.name" placeholder="Например: Пропеллер" />
                <input v-model="row.sku" placeholder="SKU" />
                <input v-model.number="row.qty" type="number" min="1" step="1" />
                <input v-model.number="row.unitPrice" type="number" min="0" step="0.01" />
                <button class="btn danger tiny" type="button" @click="removeZipRow(idx)">×</button>
              </div>
            </div>

            <div class="editor-actions">
              <button class="btn secondary" type="button" @click="addZipRow">+ Позиция</button>
              <div class="save-side">
                <span class="muted">Итого пакета: {{ money(displayPrice) }} ₽</span>
                <button class="btn" type="button" :disabled="zipSaving" @click="saveZip">
                  {{ zipSaving ? 'Сохранение…' : 'Сохранить ЗИП' }}
                </button>
              </div>
            </div>
          </template>
        </template>
        <p v-else class="muted">Выберите модель слева, чтобы настроить ЗИП-пакет</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.zip-page { min-width: 0; }

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.layout {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(0, 1.4fr);
  gap: 0.85rem;
  align-items: start;
}

.models-panel,
.editor-panel {
  padding: 0.9rem 1rem 1rem;
}

.panel-title {
  margin: 0 0 0.55rem;
  font-size: 0.78rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--muted);
}

.editor-head {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  align-items: flex-start;
  margin-bottom: 0.85rem;
}

.editor-head h2 {
  margin: 0;
  font-size: 1.15rem;
}

.models-table {
  width: 100%;
  border-collapse: collapse;
}

.models-table th,
.models-table td {
  text-align: left;
  padding: 0.55rem 0.4rem;
  border-bottom: 1px solid var(--line);
  font-size: 0.9rem;
}

.models-table th {
  color: var(--muted);
  font-weight: 500;
  font-size: 0.78rem;
}

.models-table tbody tr {
  cursor: pointer;
}

.models-table tbody tr:hover,
.models-table tbody tr.active {
  background: color-mix(in srgb, var(--glass) 70%, transparent);
}

.badge {
  display: inline-block;
  padding: 0.15rem 0.45rem;
  border-radius: 999px;
  font-size: 0.75rem;
  border: 1px solid var(--line);
}

.badge.ok {
  color: #8dc63f;
  border-color: color-mix(in srgb, #8dc63f 45%, var(--line));
}

.badge.empty {
  color: var(--muted);
}

.actions-cell { text-align: right; }

.tiny {
  min-height: 2rem;
  padding: 0.25rem 0.55rem;
  font-size: 0.82rem;
}

.zip-meta {
  display: grid;
  grid-template-columns: minmax(180px, 1.2fr) auto minmax(140px, 0.8fr);
  gap: 0.65rem 0.75rem;
  align-items: end;
  margin-bottom: 1rem;
}

.field {
  display: grid;
  gap: 0.35rem;
  min-width: 0;
  font-size: 0.78rem;
  color: var(--muted);
}

.field input {
  width: 100%;
  min-width: 0;
  min-height: 2.4rem;
}

.check {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  min-height: 2.6rem;
  white-space: nowrap;
  color: var(--ink);
  font-size: 0.9rem;
}

.price-hint {
  margin: 0;
  align-self: center;
  font-size: 0.85rem;
}

.items-label {
  margin: 0 0 0.45rem;
  font-size: 0.85rem;
  font-weight: 600;
}

.zip-items {
  display: grid;
  gap: 0.4rem;
  margin-bottom: 0.85rem;
}

.zip-row {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(90px, 0.7fr) 72px 110px 40px;
  gap: 0.4rem;
  align-items: center;
}

.zip-row.head {
  font-size: 0.75rem;
  color: var(--muted);
  padding: 0 0.1rem;
}

.zip-row input {
  min-width: 0;
  min-height: 2.3rem;
}

.editor-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
  justify-content: space-between;
  align-items: center;
}

.save-side {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
  align-items: center;
}

@media (max-width: 980px) {
  .layout { grid-template-columns: 1fr; }
  .zip-meta { grid-template-columns: 1fr; }
  .zip-row,
  .zip-row.head { grid-template-columns: 1fr 1fr; }
  .zip-row.head span:nth-child(n + 3) { display: none; }
}
</style>
