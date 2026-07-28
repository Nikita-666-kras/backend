<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  createPartCategory,
  deletePartCategory,
  fetchPartCategories,
  updatePartCategory,
  type PartCategory
} from '@/api/parts'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const auth = useAuthStore()
const toast = useToastStore()
const items = ref<PartCategory[]>([])
const name = ref('')
const parentId = ref('')
const sortOrder = ref(0)
const editingId = ref('')
const editName = ref('')
const editParentId = ref('')
const editSort = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    items.value = await fetchPartCategories()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось загрузить категории')
  } finally {
    loading.value = false
  }
}

async function add() {
  if (!name.value.trim()) return
  try {
    await createPartCategory({
      name: name.value.trim(),
      parentId: parentId.value || null,
      sortOrder: sortOrder.value
    })
    name.value = ''
    parentId.value = ''
    sortOrder.value = 0
    toast.ok('Категория создана')
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Ошибка создания')
  }
}

function startEdit(item: PartCategory) {
  editingId.value = item.id
  editName.value = item.name
  editParentId.value = item.parentId || ''
  editSort.value = item.sortOrder
}

function cancelEdit() {
  editingId.value = ''
}

async function saveEdit() {
  if (!editingId.value || !editName.value.trim()) return
  try {
    await updatePartCategory(editingId.value, {
      name: editName.value.trim(),
      parentId: editParentId.value || null,
      sortOrder: editSort.value
    })
    toast.ok('Сохранено')
    editingId.value = ''
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Ошибка сохранения')
  }
}

async function remove(item: PartCategory) {
  if (!confirm(`Удалить категорию «${item.name}»?`)) return
  try {
    await deletePartCategory(item.id)
    toast.ok('Удалено')
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Удаление доступно только администратору')
  }
}

function parentName(id?: string | null) {
  if (!id) return '—'
  return items.value.find((c) => c.id === id)?.name || id
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <p class="eyebrow">Справочник</p>
        <h1>Категории</h1>
        <p class="muted">Группы запчастей для фильтра в каталоге</p>
      </div>
    </header>

    <div class="card toolbar">
      <input v-model="name" placeholder="Название" @keyup.enter="add" />
      <select v-model="parentId">
        <option value="">Без родителя</option>
        <option v-for="c in items" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <input v-model.number="sortOrder" type="number" title="Порядок" style="width: 6rem" />
      <button class="btn" type="button" @click="add">Добавить</button>
    </div>

    <p v-if="loading" class="muted">Загрузка…</p>

    <div class="list">
      <article v-for="item in items" :key="item.id" class="card row">
        <template v-if="editingId === item.id">
          <input v-model="editName" />
          <select v-model="editParentId">
            <option value="">Без родителя</option>
            <option v-for="c in items.filter((x) => x.id !== item.id)" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <input v-model.number="editSort" type="number" style="width: 5rem" />
          <div class="actions">
            <button class="btn" type="button" @click="saveEdit">Сохранить</button>
            <button class="btn ghost" type="button" @click="cancelEdit">Отмена</button>
          </div>
        </template>
        <template v-else>
          <div>
            <strong>{{ item.name }}</strong>
            <p class="muted">{{ item.slug }} · родитель: {{ parentName(item.parentId) }} · #{{ item.sortOrder }}</p>
          </div>
          <div class="actions">
            <button class="btn secondary" type="button" @click="startEdit(item)">Изменить</button>
            <button v-if="auth.isAdmin" class="btn danger" type="button" @click="remove(item)">Удалить</button>
          </div>
        </template>
      </article>
      <p v-if="!items.length && !loading" class="muted empty">Категорий пока нет</p>
    </div>
  </section>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
  align-items: center;
}
.toolbar input:first-child {
  flex: 1;
  min-width: 140px;
}
.list {
  display: grid;
  gap: 0.55rem;
}
.row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.65rem;
  align-items: center;
  padding: 0.85rem 1rem;
}
.actions {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}
.muted {
  margin: 0.2rem 0 0;
}
@media (max-width: 720px) {
  .row {
    grid-template-columns: 1fr;
  }
}
</style>
