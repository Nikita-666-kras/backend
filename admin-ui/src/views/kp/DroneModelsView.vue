<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  createDroneModel,
  deleteDroneModel,
  fetchDroneModels,
  updateDroneModel,
  type KpDroneModel,
  type KpPriceComponent
} from '@/api/kp'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const items = ref<KpDroneModel[]>([])
const loading = ref(false)
const expandedId = ref<string | null>(null)

const form = ref({
  code: '',
  name: '',
  defaultPrice: 0,
  dronePrice: 0,
  vatMode: 'mixed',
  components: [] as KpPriceComponent[],
  sortOrder: 0,
  active: true
})

function emptyPart(): KpPriceComponent {
  return { name: '', unitPrice: 0, qtyPerKit: 1 }
}

function ensureComponents(item: KpDroneModel) {
  if (!item.components) item.components = []
  return item.components
}

function toggleParts(id: string) {
  expandedId.value = expandedId.value === id ? null : id
}

async function load() {
  loading.value = true
  try {
    items.value = (await fetchDroneModels()).map((m) => ({
      ...m,
      components: (m.components || []).map((c) => ({
        name: c.name,
        unitPrice: Number(c.unitPrice || 0),
        qtyPerKit: Number(c.qtyPerKit || 1)
      })),
      dronePrice: Number(m.dronePrice ?? m.defaultPrice ?? 0),
      vatMode: m.vatMode || 'mixed'
    }))
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось загрузить модели')
  } finally {
    loading.value = false
  }
}

function payloadFrom(model: Omit<KpDroneModel, 'id' | 'hasZipPackage'> | typeof form.value) {
  const components = (model.components || [])
    .filter((c) => c.name && String(c.name).trim())
    .map((c) => ({
      name: String(c.name).trim(),
      unitPrice: Math.max(0, Number(c.unitPrice) || 0),
      qtyPerKit: Math.max(1, Number(c.qtyPerKit) || 1)
    }))
  return {
    code: model.code,
    name: model.name,
    defaultPrice: Number(model.defaultPrice) || 0,
    dronePrice: Number(model.dronePrice) || Number(model.defaultPrice) || 0,
    vatMode: model.vatMode || 'mixed',
    components,
    sortOrder: Number(model.sortOrder) || 0,
    active: !!model.active
  }
}

async function submit() {
  try {
    if (!form.value.dronePrice) {
      form.value.dronePrice = form.value.defaultPrice
    }
    await createDroneModel(payloadFrom(form.value))
    toast.ok('Модель добавлена — прайс КП обновлён')
    form.value = {
      code: '',
      name: '',
      defaultPrice: 0,
      dronePrice: 0,
      vatMode: 'mixed',
      components: [],
      sortOrder: 0,
      active: true
    }
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось добавить модель')
  }
}

async function save(item: KpDroneModel) {
  try {
    const { id, hasZipPackage: _h, ...rest } = item
    await updateDroneModel(id, payloadFrom(rest))
    toast.ok('Сохранено — цены в менеджерском хабе обновлены')
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось сохранить')
  }
}

async function remove(item: KpDroneModel) {
  if (!confirm(`Удалить модель «${item.name}» (${item.code})?`)) return
  try {
    await deleteDroneModel(item.id)
    toast.ok('Удалено')
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось удалить')
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
        <p class="muted">
          Цены комплекта, дрона и комплектующих — единый прайс для менеджерского хаба. ЗИП настраивается отдельно.
        </p>
      </div>
      <RouterLink class="btn secondary" to="/kp/zip-packages">ЗИП-пакеты</RouterLink>
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
        <span>Цена комплекта</span>
        <input v-model.number="form.defaultPrice" type="number" min="0" step="0.01" required />
      </label>
      <label class="field">
        <span>Цена дрона</span>
        <input v-model.number="form.dronePrice" type="number" min="0" step="0.01" required />
      </label>
      <label class="field field--vat">
        <span>НДС дрона</span>
        <select v-model="form.vatMode">
          <option value="mixed">0% (mixed)</option>
          <option value="all_vat">22% (all_vat)</option>
        </select>
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

    <div class="table-wrap card" v-if="!loading">
      <table class="models-table">
        <thead>
          <tr>
            <th>Код</th>
            <th>Название</th>
            <th>Цена комплекта</th>
            <th>Цена дрона</th>
            <th>НДС</th>
            <th>Порядок</th>
            <th>Активна</th>
            <th>ЗИП</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <template v-for="item in items" :key="item.id">
            <tr>
              <td><input v-model="item.code" class="cell-input" /></td>
              <td><input v-model="item.name" class="cell-input" /></td>
              <td>
                <input v-model.number="item.defaultPrice" class="cell-input" type="number" min="0" step="0.01" />
              </td>
              <td>
                <input v-model.number="item.dronePrice" class="cell-input" type="number" min="0" step="0.01" />
              </td>
              <td>
                <select v-model="item.vatMode" class="cell-input">
                  <option value="mixed">0%</option>
                  <option value="all_vat">22%</option>
                </select>
              </td>
              <td>
                <input v-model.number="item.sortOrder" class="cell-input cell-input--sm" type="number" />
              </td>
              <td class="center">
                <input v-model="item.active" type="checkbox" />
              </td>
              <td>
                <RouterLink
                  class="zip-link"
                  :class="{ filled: item.hasZipPackage }"
                  :to="{ name: 'kp-zip-packages', query: { modelId: item.id } }"
                >
                  {{ item.hasZipPackage ? 'Заполнен →' : 'Добавить ЗИП →' }}
                </RouterLink>
              </td>
              <td class="row-actions">
                <button class="btn secondary tiny" type="button" @click="toggleParts(item.id)">
                  {{ expandedId === item.id ? 'Скрыть состав' : `Состав (${item.components?.length || 0})` }}
                </button>
                <button class="btn secondary tiny" type="button" @click="save(item)">Сохранить</button>
                <button class="btn danger tiny" type="button" @click="remove(item)">Удалить</button>
              </td>
            </tr>
            <tr v-if="expandedId === item.id" class="parts-row">
              <td colspan="9">
                <div class="parts-panel">
                  <div class="parts-head">
                    <strong>Комплектующие комплекта</strong>
                    <span class="muted">АКБ, зарядки и т.п. — входят в расчёт КП в хабе</span>
                    <button class="btn secondary tiny" type="button" @click="ensureComponents(item).push(emptyPart())">
                      + позиция
                    </button>
                  </div>
                  <div v-if="!item.components?.length" class="muted parts-empty">Нет позиций (только дрон/комплект)</div>
                  <div v-for="(part, idx) in item.components" :key="idx" class="parts-grid">
                    <label class="field">
                      <span>Название</span>
                      <input v-model="part.name" placeholder="АКБ…" />
                    </label>
                    <label class="field">
                      <span>Цена, ₽</span>
                      <input v-model.number="part.unitPrice" type="number" min="0" step="0.01" />
                    </label>
                    <label class="field field--qty">
                      <span>Кол-во / комплект</span>
                      <input v-model.number="part.qtyPerKit" type="number" min="1" step="1" />
                    </label>
                    <button class="btn danger tiny parts-remove" type="button" @click="item.components!.splice(idx, 1)">
                      Удалить
                    </button>
                  </div>
                </div>
              </td>
            </tr>
          </template>
          <tr v-if="!items.length">
            <td colspan="9" class="muted">Пока нет моделей</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.models-page { min-width: 0; }

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.form-card {
  display: grid;
  grid-template-columns: 100px minmax(140px, 1.2fr) minmax(120px, 1fr) minmax(120px, 1fr) 110px 72px auto auto;
  gap: 0.65rem 0.75rem;
  align-items: end;
  padding: 0.9rem 1rem;
  margin-bottom: 1rem;
}

.field {
  display: grid;
  gap: 0.35rem;
  min-width: 0;
  font-size: 0.78rem;
  color: var(--muted);
}

.field input,
.field select { width: 100%; min-width: 0; }

.check {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  min-height: 2.6rem;
  white-space: nowrap;
  color: var(--ink);
  font-size: 0.9rem;
}

.form-submit { min-height: 2.6rem; white-space: nowrap; }

.table-wrap {
  padding: 0;
  overflow: auto;
}

.models-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 980px;
}

.models-table th,
.models-table td {
  padding: 0.65rem 0.7rem;
  border-bottom: 1px solid var(--line);
  text-align: left;
  vertical-align: middle;
}

.models-table th {
  font-size: 0.75rem;
  color: var(--muted);
  font-weight: 500;
  white-space: nowrap;
}

.cell-input {
  width: 100%;
  min-width: 0;
  min-height: 2.2rem;
}

.cell-input--sm { max-width: 72px; }

.center { text-align: center; }

.zip-link {
  display: inline-block;
  font-size: 0.88rem;
  color: var(--muted);
  text-decoration: underline;
  text-underline-offset: 3px;
  white-space: nowrap;
}

.zip-link.filled { color: #8dc63f; }
.zip-link:hover { color: var(--ink); }

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  justify-content: flex-end;
}

.tiny {
  min-height: 2rem;
  padding: 0.25rem 0.55rem;
  font-size: 0.82rem;
  white-space: nowrap;
}

.parts-row td {
  background: color-mix(in srgb, var(--line) 35%, transparent);
  padding: 0.75rem 0.9rem 1rem;
}

.parts-panel { display: grid; gap: 0.65rem; }

.parts-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem 1rem;
}

.parts-empty { font-size: 0.88rem; }

.parts-grid {
  display: grid;
  grid-template-columns: minmax(180px, 2fr) minmax(110px, 1fr) 110px auto;
  gap: 0.5rem 0.65rem;
  align-items: end;
}

.parts-remove { min-height: 2.4rem; }

@media (max-width: 1200px) {
  .form-card,
  .parts-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .form-card,
  .parts-grid { grid-template-columns: 1fr; }
}
</style>
