<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createDroneModel, fetchDroneModels, updateDroneModel, type KpDroneModel } from '@/api/kp'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const items = ref<KpDroneModel[]>([])
const form = ref({ code: '', name: '', defaultPrice: 0, sortOrder: 0, active: true })

async function load() {
  items.value = await fetchDroneModels()
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

onMounted(load)
</script>

<template>
  <section>
    <header class="page-header"><h1>КП · Модели дронов</h1></header>
    <form class="card row row--form" @submit.prevent="submit">
      <label class="field-mini">
        <span>Код</span>
        <input v-model="form.code" required />
      </label>
      <label class="field-mini">
        <span>Название</span>
        <input v-model="form.name" required />
      </label>
      <label class="field-mini">
        <span>Цена по умолчанию</span>
        <input v-model.number="form.defaultPrice" type="number" min="0" step="0.01" required />
      </label>
      <label class="field-mini">
        <span>Порядок</span>
        <input v-model.number="form.sortOrder" type="number" />
      </label>
      <label class="check-mini"><input v-model="form.active" type="checkbox" /> Активна</label>
      <button class="btn">Добавить</button>
    </form>
    <div class="card list">
      <div v-for="item in items" :key="item.id" class="row">
        <input v-model="item.code" />
        <input v-model="item.name" />
        <input v-model.number="item.defaultPrice" type="number" min="0" step="0.01" />
        <input v-model.number="item.sortOrder" type="number" />
        <label><input v-model="item.active" type="checkbox" /> Активна</label>
        <button class="btn secondary" @click="save(item)">Сохранить</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.row { display: grid; grid-template-columns: 120px 1fr 160px 90px 100px 120px; gap: 8px; align-items: center; margin-bottom: 8px; }
.list { margin-top: 12px; }
.row--form {
  grid-template-columns: 120px minmax(260px, 1fr) 170px 90px auto 120px;
  align-items: stretch;
  gap: 10px;
  padding: 12px;
  margin-bottom: 0;
}
.field-mini { display: grid; gap: 6px; font-size: 0.78rem; color: var(--muted); min-width: 0; }
.field-mini span { display: block; line-height: 1.1; }
.field-mini input { width: 100%; }
.check-mini { display: inline-flex; align-items: center; gap: 6px; min-height: 42px; white-space: nowrap; margin-top: 20px; }
.row--form > .btn { align-self: end; min-height: 42px; margin-top: 20px; }

@media (max-width: 980px) {
  .row--form {
    grid-template-columns: repeat(2, minmax(220px, 1fr));
  }
  .check-mini,
  .row--form > .btn {
    margin-top: 0;
  }
}
</style>
