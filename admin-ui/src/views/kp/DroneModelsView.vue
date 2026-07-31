<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  createDroneModel,
  deleteDroneModel,
  fetchDroneModels,
  fetchZipPackage,
  saveZipPackage,
  updateDroneModel,
  type KpDroneModel,
  type KpZipItem,
  type KpZipPackage
} from '@/api/kp'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const items = ref<KpDroneModel[]>([])
const loading = ref(false)
const form = ref({ code: '', name: '', defaultPrice: 0, sortOrder: 0, active: true })

const zipOpenId = ref<string | null>(null)
const zipLoading = ref(false)
const zipSaving = ref(false)
const zipForm = ref<{ name: string; price: number | null; useCustomPrice: boolean; items: KpZipItem[] }>({
  name: 'ЗИП-пакет',
  price: null,
  useCustomPrice: false,
  items: []
})

async function load() {
  loading.value = true
  try {
    items.value = await fetchDroneModels()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось загрузить модели')
  } finally {
    loading.value = false
  }
}

async function submit() {
  try {
    await createDroneModel(form.value)
    toast.ok('Модель добавлена')
    form.value = { code: '', name: '', defaultPrice: 0, sortOrder: 0, active: true }
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось добавить модель')
  }
}

async function save(item: KpDroneModel) {
  try {
    const { id, hasZipPackage: _h, ...payload } = item
    await updateDroneModel(id, payload)
    toast.ok('Сохранено')
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось сохранить')
  }
}

async function remove(item: KpDroneModel) {
  if (!confirm(`Удалить модель «${item.name}» (${item.code})?`)) return
  try {
    await deleteDroneModel(item.id)
    if (zipOpenId.value === item.id) zipOpenId.value = null
    toast.ok('Удалено')
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось удалить модель')
  }
}

function emptyItem(): KpZipItem {
  return { name: '', sku: '', qty: 1, unitPrice: 0 }
}

function applyZip(pkg: KpZipPackage) {
  zipForm.value = {
    name: pkg.name || 'ЗИП-пакет',
    price: pkg.price,
    useCustomPrice: pkg.price != null,
    items: (pkg.items || []).map((i) => ({
      name: i.name,
      sku: i.sku || '',
      qty: i.qty || 1,
      unitPrice: Number(i.unitPrice || 0),
      sortOrder: i.sortOrder
    }))
  }
  if (!zipForm.value.items.length) zipForm.value.items = [emptyItem()]
}

async function openZip(item: KpDroneModel) {
  if (zipOpenId.value === item.id) {
    zipOpenId.value = null
    return
  }
  zipOpenId.value = item.id
  zipLoading.value = true
  try {
    applyZip(await fetchZipPackage(item.id))
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось загрузить ЗИП-пакет')
    zipOpenId.value = null
  } finally {
    zipLoading.value = false
  }
}

function addZipRow() {
  zipForm.value.items.push(emptyItem())
}

function removeZipRow(idx: number) {
  zipForm.value.items.splice(idx, 1)
  if (!zipForm.value.items.length) zipForm.value.items.push(emptyItem())
}

async function saveZip(modelId: string) {
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
    const saved = await saveZipPackage(modelId, {
      name: zipForm.value.name.trim() || 'ЗИП-пакет',
      price: zipForm.value.useCustomPrice ? Number(zipForm.value.price || 0) : null,
      items: itemsPayload
    })
    applyZip(saved)
    toast.ok('ЗИП-пакет сохранён')
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось сохранить ЗИП-пакет')
  } finally {
    zipSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="models-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">Коммерческие предложения</p>
        <h1>КП · Модели дронов</h1>
        <p class="muted">Код, цена по умолчанию, порядок и ЗИП-пакет для каждой модели</p>
      </div>
    </header>

    <form class="card form-card" @submit.prevent="submit">
      <label class="field">
        <span>Код</span>
        <input v-model="form.code" required placeholder="HD580" />
      </label>
      <label class="field">
        <span>Название</span>
        <input v-model="form.name" required placeholder="HD580" />
      </label>
      <label class="field">
        <span>Цена по умолчанию</span>
        <input v-model.number="form.defaultPrice" type="number" min="0" step="0.01" required />
      </label>
      <label class="field field--order">
        <span>Порядок</span>
        <input v-model.number="form.sortOrder" type="number" />
      </label>
      <label class="check">
        <input v-model="form.active" type="checkbox" />
        <span>Активна</span>
      </label>
      <button class="btn form-submit" type="submit">Добавить</button>
    </form>

    <p v-if="loading" class="muted">Загрузка…</p>

    <div class="list">
      <article v-for="item in items" :key="item.id" class="card model-wrap">
        <div class="model-card">
          <label class="field">
            <span>Код</span>
            <input v-model="item.code" />
          </label>
          <label class="field field--name">
            <span>Название</span>
            <input v-model="item.name" />
          </label>
          <label class="field">
            <span>Цена по умолчанию</span>
            <input v-model.number="item.defaultPrice" type="number" min="0" step="0.01" />
          </label>
          <label class="field field--order">
            <span>Порядок</span>
            <input v-model.number="item.sortOrder" type="number" />
          </label>
          <label class="check">
            <input v-model="item.active" type="checkbox" />
            <span>Активна</span>
          </label>
          <div class="actions">
            <button class="btn secondary" type="button" @click="save(item)">Сохранить</button>
            <button class="btn secondary" type="button" @click="openZip(item)">
              {{ zipOpenId === item.id ? 'Скрыть ЗИП' : item.hasZipPackage ? 'ЗИП-пакет ✓' : 'ЗИП-пакет' }}
            </button>
            <button class="btn danger" type="button" @click="remove(item)">Удалить</button>
          </div>
        </div>

        <div v-if="zipOpenId === item.id" class="zip-panel">
          <p v-if="zipLoading" class="muted">Загрузка ЗИП…</p>
          <template v-else>
            <div class="zip-head">
              <label class="field">
                <span>Название пакета</span>
                <input v-model="zipForm.name" placeholder="ЗИП-пакет" />
              </label>
              <label class="check">
                <input v-model="zipForm.useCustomPrice" type="checkbox" />
                <span>Своя цена пакета</span>
              </label>
              <label v-if="zipForm.useCustomPrice" class="field">
                <span>Цена пакета</span>
                <input v-model.number="zipForm.price" type="number" min="0" step="0.01" />
              </label>
              <p v-else class="muted zip-hint">Цена = сумма позиций</p>
            </div>

            <div class="zip-items">
              <div v-for="(row, idx) in zipForm.items" :key="idx" class="zip-row">
                <input v-model="row.name" placeholder="Наименование" required />
                <input v-model="row.sku" placeholder="Артикул" />
                <input v-model.number="row.qty" type="number" min="1" step="1" title="Кол-во" />
                <input v-model.number="row.unitPrice" type="number" min="0" step="0.01" title="Цена" />
                <button class="btn danger" type="button" @click="removeZipRow(idx)">×</button>
              </div>
            </div>

            <div class="zip-actions">
              <button class="btn secondary" type="button" @click="addZipRow">+ Позиция</button>
              <button class="btn" type="button" :disabled="zipSaving" @click="saveZip(item.id)">
                {{ zipSaving ? 'Сохранение…' : 'Сохранить ЗИП' }}
              </button>
            </div>
          </template>
        </div>
      </article>
      <p v-if="!loading && !items.length" class="muted">Пока нет моделей</p>
    </div>
  </section>
</template>

<style scoped>
.models-page {
  min-width: 0;
}

.form-card,
.model-card {
  display: grid;
  grid-template-columns: 110px minmax(160px, 1.4fr) minmax(140px, 1fr) 88px auto auto;
  gap: 0.65rem 0.75rem;
  align-items: end;
  padding: 0.9rem 1rem;
}

.form-card {
  margin-bottom: 1rem;
}

.model-wrap {
  padding: 0;
  margin-bottom: 0.65rem;
  overflow: hidden;
}

.model-wrap .model-card {
  margin: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.list {
  display: grid;
  gap: 0.65rem;
  min-width: 0;
}

.field {
  display: grid;
  gap: 0.35rem;
  min-width: 0;
  font-size: 0.78rem;
  color: var(--muted);
}

.field span {
  line-height: 1.1;
}

.field input {
  width: 100%;
  min-width: 0;
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

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  justify-content: flex-end;
}

.actions .btn,
.form-submit {
  min-height: 2.6rem;
  white-space: nowrap;
}

.zip-panel {
  border-top: 1px solid var(--line);
  padding: 0.85rem 1rem 1rem;
  background: color-mix(in srgb, var(--glass) 80%, transparent);
  display: grid;
  gap: 0.75rem;
}

.zip-head {
  display: grid;
  grid-template-columns: minmax(180px, 1.2fr) auto minmax(140px, 0.8fr);
  gap: 0.65rem 0.75rem;
  align-items: end;
}

.zip-hint {
  margin: 0;
  align-self: center;
  font-size: 0.85rem;
}

.zip-items {
  display: grid;
  gap: 0.4rem;
}

.zip-row {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(90px, 0.7fr) 72px 110px 40px;
  gap: 0.4rem;
  align-items: center;
}

.zip-row input {
  min-width: 0;
  min-height: 2.3rem;
}

.zip-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  justify-content: flex-end;
}

@media (max-width: 1100px) {
  .form-card,
  .model-card {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: stretch;
  }

  .field--name {
    grid-column: 1 / -1;
  }

  .check,
  .actions,
  .form-submit {
    align-self: end;
  }

  .actions {
    justify-content: stretch;
  }

  .actions .btn {
    flex: 1;
  }

  .zip-head {
    grid-template-columns: 1fr;
  }

  .zip-row {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .form-card,
  .model-card {
    grid-template-columns: 1fr;
  }

  .field--name,
  .field--order {
    grid-column: auto;
  }

  .check {
    min-height: auto;
  }

  .actions {
    width: 100%;
  }

  .actions .btn,
  .form-submit {
    width: 100%;
  }

  .zip-row {
    grid-template-columns: 1fr;
  }
}
</style>
