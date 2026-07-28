<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  createDrone,
  deleteDrone,
  fetchDrones,
  publishDrone,
  updateDrone,
  type Drone,
  type PageResponse
} from '@/api/parts'
import { fetchMedia, mediaPublicUrl, type MediaAsset } from '@/api/media'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { statusLabel } from '@/utils/labels'

const auth = useAuthStore()
const toast = useToastStore()
const data = ref<PageResponse<Drone> | null>(null)
const library = ref<MediaAsset[]>([])
const name = ref('')
const description = ref('')
const imageMediaId = ref('')
const editingId = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [drones, media] = await Promise.all([
      fetchDrones({ size: 100 }),
      fetchMedia({ section: 'DRONES', size: 100 })
    ])
    data.value = drones
    library.value = media.content
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось загрузить дроны')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = ''
  name.value = ''
  description.value = ''
  imageMediaId.value = ''
}

function startEdit(item: Drone) {
  editingId.value = item.id
  name.value = item.name
  description.value = item.description || ''
  imageMediaId.value = item.imageMediaId || ''
}

async function save() {
  if (!name.value.trim()) return
  try {
    const payload = {
      name: name.value.trim(),
      description: description.value.trim() || undefined,
      imageMediaId: imageMediaId.value || null,
      status: 'DRAFT' as const
    }
    if (editingId.value) {
      await updateDrone(editingId.value, payload)
      toast.ok('Дрон сохранён')
    } else {
      await createDrone(payload)
      toast.ok('Дрон добавлен')
    }
    resetForm()
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Ошибка сохранения')
  }
}

async function publish(item: Drone) {
  try {
    await publishDrone(item.id)
    toast.ok(`Опубликован: ${item.name}`)
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Ошибка публикации')
  }
}

async function remove(item: Drone) {
  if (!confirm(`Удалить «${item.name}»?`)) return
  try {
    await deleteDrone(item.id)
    toast.ok('Удалено')
    if (editingId.value === item.id) resetForm()
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Удаление доступно только администратору')
  }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <p class="eyebrow">Справочник</p>
        <h1>Дроны</h1>
        <p class="muted">Модели с описанием и обложкой</p>
      </div>
    </header>

    <div class="card editor">
      <h3>{{ editingId ? 'Редактирование' : 'Новый дрон' }}</h3>
      <div class="grid-2">
        <div class="field">
          <label>Название</label>
          <input v-model="name" @keyup.enter="save" />
        </div>
        <div class="field">
          <label>Обложка (медиа DRONES)</label>
          <select v-model="imageMediaId">
            <option value="">Без фото</option>
            <option v-for="m in library" :key="m.id" :value="m.id">{{ m.originalName }}</option>
          </select>
        </div>
      </div>
      <div class="field">
        <label>Описание</label>
        <textarea v-model="description" rows="3" />
      </div>
      <div class="actions">
        <button class="btn" type="button" @click="save">{{ editingId ? 'Сохранить' : 'Добавить' }}</button>
        <button v-if="editingId" class="btn ghost" type="button" @click="resetForm">Отмена</button>
      </div>
    </div>

    <p v-if="loading" class="muted">Загрузка…</p>

    <div v-if="data" class="list">
      <article v-for="item in data.content" :key="item.id" class="card row">
        <div class="thumb">
          <img v-if="item.imageMediaId" :src="mediaPublicUrl(item.imageMediaId)" :alt="item.name" />
          <span v-else class="muted">Нет фото</span>
        </div>
        <div>
          <strong>{{ item.name }}</strong>
          <p class="muted">{{ item.slug }}</p>
          <p v-if="item.description" class="desc">{{ item.description }}</p>
        </div>
        <span class="badge" :class="item.status">{{ statusLabel(item.status) }}</span>
        <div class="actions">
          <button class="btn secondary" type="button" @click="startEdit(item)">Изменить</button>
          <button v-if="item.status !== 'PUBLISHED'" class="btn secondary" type="button" @click="publish(item)">
            Опубл.
          </button>
          <button v-if="auth.isAdmin" class="btn danger" type="button" @click="remove(item)">Удалить</button>
        </div>
      </article>
      <p v-if="!data.content.length" class="muted">Пока нет дронов</p>
    </div>
  </section>
</template>

<style scoped>
.editor {
  padding: 1rem;
  display: grid;
  gap: 0.75rem;
  margin-bottom: 1rem;
}
.editor h3 {
  margin: 0;
  font-size: 0.95rem;
}
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}
.list {
  display: grid;
  gap: 0.65rem;
}
.row {
  display: grid;
  grid-template-columns: 72px 1fr auto auto;
  gap: 0.75rem;
  align-items: center;
  padding: 0.85rem 1rem;
}
.thumb {
  width: 72px;
  height: 54px;
  border-radius: 8px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.06);
  display: grid;
  place-items: center;
  font-size: 0.7rem;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.actions {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}
.muted {
  margin: 0.15rem 0 0;
}
.desc {
  margin: 0.25rem 0 0;
  font-size: 0.85rem;
  color: var(--muted);
}
@media (max-width: 860px) {
  .grid-2,
  .row {
    grid-template-columns: 1fr;
  }
}
</style>
