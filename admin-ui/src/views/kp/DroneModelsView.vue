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
    <form class="card row" @submit.prevent="submit">
      <input v-model="form.code" placeholder="Код" required />
      <input v-model="form.name" placeholder="Название" required />
      <input v-model.number="form.defaultPrice" type="number" min="0" step="0.01" required />
      <input v-model.number="form.sortOrder" type="number" />
      <label><input v-model="form.active" type="checkbox" /> Активна</label>
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
</style>
