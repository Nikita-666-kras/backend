<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  createDroneModel,
  deleteDroneModel,
  fetchDroneModels,
  updateDroneModel,
  type KpDroneModel
} from '@/api/kp'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const items = ref<KpDroneModel[]>([])
const loading = ref(false)
const form = ref({ code: '', name: '', defaultPrice: 0, sortOrder: 0, active: true })

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
    toast.ok('Удалено')
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось удалить модель')
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
        <p class="muted">Код, цена по умолчанию и порядок в списке менеджера. ЗИП настраивается отдельно.</p>
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

    <div class="table-wrap card" v-if="!loading">
      <table class="models-table">
        <thead>
          <tr>
            <th>Код</th>
            <th>Название</th>
            <th>Цена</th>
            <th>Порядок</th>
            <th>Активна</th>
            <th>ЗИП</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.id">
            <td><input v-model="item.code" class="cell-input" /></td>
            <td><input v-model="item.name" class="cell-input" /></td>
            <td>
              <input v-model.number="item.defaultPrice" class="cell-input" type="number" min="0" step="0.01" />
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
              <button class="btn secondary tiny" type="button" @click="save(item)">Сохранить</button>
              <button class="btn danger tiny" type="button" @click="remove(item)">Удалить</button>
            </td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="7" class="muted">Пока нет моделей</td>
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
  grid-template-columns: 110px minmax(160px, 1.4fr) minmax(140px, 1fr) 88px auto auto;
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

.field input { width: 100%; min-width: 0; }

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
  min-width: 780px;
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

@media (max-width: 1100px) {
  .form-card {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .form-card { grid-template-columns: 1fr; }
}
</style>
