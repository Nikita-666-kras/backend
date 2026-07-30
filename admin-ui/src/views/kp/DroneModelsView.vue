<script setup lang="ts">
import { onMounted, ref } from 'vue'
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
    const { id, ...payload } = item
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
        <p class="muted">Код, цена по умолчанию и порядок в списке менеджера</p>
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
      <article v-for="item in items" :key="item.id" class="card model-card">
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
          <button class="btn danger" type="button" @click="remove(item)">Удалить</button>
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
  margin-bottom: 0.65rem;
}

.form-card {
  margin-bottom: 1rem;
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
}
</style>
