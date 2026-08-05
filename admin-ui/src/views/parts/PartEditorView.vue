<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createPart,
  fetchPart,
  fetchPartCategories,
  fetchDrones,
  publishPart,
  updatePart,
  type Drone,
  type PartCategory
} from '@/api/parts'
import { fetchMedia, isProcessableImage, mediaPublicUrl, processMedia, uploadMedia, type MediaAsset } from '@/api/media'

const route = useRoute()
const router = useRouter()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))
const isNew = computed(() => route.name === 'part-new')

const name = ref('')
const sku = ref('')
const description = ref('')
const price = ref<number | null>(null)
const droneId = ref('')
const categoryId = ref('')
const coverMediaId = ref<string | null>(null)
const mediaIds = ref<string[]>([])
const status = ref('DRAFT')
const drones = ref<Drone[]>([])
const categories = ref<PartCategory[]>([])
const library = ref<MediaAsset[]>([])
const saving = ref(false)
const uploading = ref(false)
const processing = ref(false)
const error = ref('')
const message = ref('')

function payload() {
  return {
    name: name.value.trim(),
    sku: sku.value.trim(),
    description: description.value.trim() || undefined,
    price: price.value == null || Number.isNaN(price.value) ? null : price.value,
    currency: 'RUB',
    droneId: droneId.value || null,
    categoryId: categoryId.value || null,
    coverMediaId: coverMediaId.value,
    mediaIds: mediaIds.value,
    status: status.value as 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  }
}

async function loadRefs() {
  const [dronePage, cats, mediaPage] = await Promise.all([
    fetchDrones({ size: 100 }),
    fetchPartCategories(),
    fetchMedia({ section: 'PARTS', size: 48 })
  ])
  drones.value = dronePage.content
  categories.value = cats
  library.value = mediaPage.content
}

async function load() {
  await loadRefs()
  if (isNew.value) return
  const part = await fetchPart(id.value)
  name.value = part.name
  sku.value = part.sku
  description.value = part.description || ''
  price.value = part.price == null ? null : Number(part.price)
  droneId.value = part.droneId || ''
  categoryId.value = part.categoryId || ''
  coverMediaId.value = part.coverMediaId || null
  mediaIds.value = [...(part.mediaIds || [])]
  status.value = part.status
}

function attachMedia(item: MediaAsset) {
  if (!mediaIds.value.includes(item.id)) {
    mediaIds.value = [...mediaIds.value, item.id]
  }
  if (!coverMediaId.value && item.kind === 'IMAGE') {
    coverMediaId.value = item.id
  }
}

async function uploadFiles(files: FileList | null) {
  if (!files?.length) return
  uploading.value = true
  error.value = ''
  try {
    for (const file of Array.from(files)) {
      const asset = await uploadMedia(file, 'PARTS')
      library.value = [asset, ...library.value]
      attachMedia(asset)
    }
    message.value = 'Фото загружено'
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка загрузки'
  } finally {
    uploading.value = false
  }
}

function coverAsset() {
  if (!coverMediaId.value) return null
  return library.value.find((item) => item.id === coverMediaId.value) || null
}

function coverSrc() {
  const item = coverAsset()
  if (!item) return mediaPublicUrl(coverMediaId.value)
  return mediaPublicUrl(item.url, item.updatedAt)
}

async function processCover(opts: { square?: boolean; watermark?: boolean; convertToWebp?: boolean }) {
  const item = coverAsset()
  if (!item || !isProcessableImage(item)) {
    error.value = 'Обложка должна быть JPEG, PNG, WebP или GIF'
    return
  }
  processing.value = true
  error.value = ''
  message.value = ''
  try {
    const updated = await processMedia(item.id, {
      square: !!opts.square,
      watermark: !!opts.watermark,
      convertToWebp: !!opts.convertToWebp,
      backgroundColor: '#ffffff',
      opacity: opts.watermark ? 0.15 : undefined,
      bgThreshold: opts.watermark ? 40 : undefined
    })
    library.value = library.value.map((entry) => (entry.id === updated.id ? updated : entry))
    message.value = 'Обложка обработана'
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка обработки'
  } finally {
    processing.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  message.value = ''
  try {
    if (isNew.value) {
      const created = await createPart(payload())
      message.value = 'Сохранено'
      await router.replace(`/parts/${created.id}`)
      status.value = created.status
    } else {
      const updated = await updatePart(id.value, payload())
      message.value = 'Сохранено'
      status.value = updated.status
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка сохранения'
  } finally {
    saving.value = false
  }
}

async function saveAndPublish() {
  await save()
  if (!id.value && isNew.value) return
  const currentId = id.value
  if (!currentId) return
  try {
    const published = await publishPart(currentId)
    status.value = published.status
    message.value = 'Опубликовано'
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка публикации'
  }
}

onMounted(async () => {
  try {
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Не удалось загрузить'
  }
})
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <p class="eyebrow">Запчасть</p>
        <h1>{{ isNew ? 'Новая запчасть' : 'Редактирование' }}</h1>
      </div>
      <div class="actions">
        <button class="btn secondary" :disabled="saving" @click="save">Сохранить</button>
        <button class="btn" :disabled="saving" @click="saveAndPublish">Опубликовать</button>
      </div>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="ok">{{ message }}</p>

    <div class="layout">
      <div class="card editor">
        <div class="grid-2">
          <div class="field">
            <label>Название</label>
            <input v-model="name" required />
          </div>
          <div class="field">
            <label>Артикул</label>
            <input v-model="sku" required />
          </div>
        </div>
        <div class="grid-2">
          <div class="field">
            <label>Цена (₽)</label>
            <input v-model.number="price" type="number" min="0" step="0.01" placeholder="Без цены" />
          </div>
          <div class="field">
            <label>Дрон</label>
            <select v-model="droneId">
              <option value="">Без привязки</option>
              <option v-for="d in drones" :key="d.id" :value="d.id">{{ d.name }}</option>
            </select>
          </div>
        </div>
        <div class="field">
          <label>Категория</label>
          <select v-model="categoryId">
            <option value="">Без категории</option>
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </div>
        <div class="field">
          <label>Описание</label>
          <textarea v-model="description" rows="8" />
        </div>
      </div>

      <aside class="side">
        <div class="card panel">
          <div class="panel-head">
            <h3>Фото</h3>
            <label class="btn secondary mini">
              {{ uploading ? '…' : 'Загрузить' }}
              <input type="file" accept="image/*" multiple hidden :disabled="uploading" @change="uploadFiles(($event.target as HTMLInputElement).files)" />
            </label>
          </div>
          <div v-if="coverMediaId" class="cover">
            <img :src="coverSrc()" alt="Обложка" />
            <div v-if="coverAsset() && isProcessableImage(coverAsset()!)" class="cover-actions">
              <button class="btn secondary mini" :disabled="processing" @click="processCover({ square: true })">1:1</button>
              <button class="btn secondary mini" :disabled="processing" @click="processCover({ watermark: true })">WM</button>
              <button class="btn secondary mini" :disabled="processing" @click="processCover({ convertToWebp: true })">WebP</button>
              <button
                class="btn mini"
                :disabled="processing"
                @click="processCover({ square: true, watermark: true, convertToWebp: true })"
              >
                Всё
              </button>
            </div>
          </div>
          <div class="media-list">
            <button
              v-for="item in library"
              :key="item.id"
              type="button"
              class="media-item"
              :class="{ active: mediaIds.includes(item.id) }"
              @click="attachMedia(item); coverMediaId = item.id"
            >
              <img :src="mediaPublicUrl(item.url, item.updatedAt)" :alt="item.originalName" />
            </button>
          </div>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.actions { display: flex; gap: 0.5rem; }
.layout { display: grid; grid-template-columns: 1fr 300px; gap: 1rem; align-items: start; }
.editor { padding: 1.1rem; display: grid; gap: 0.9rem; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0.9rem; }
.panel { padding: 1rem; display: grid; gap: 0.75rem; }
.panel-head { display: flex; justify-content: space-between; align-items: center; }
.panel h3 { margin: 0; font-size: 0.95rem; }
.mini { padding: 0.35rem 0.65rem; font-size: 0.85rem; }
.cover img { width: 100%; border-radius: 10px; aspect-ratio: 1; object-fit: cover; }
.cover-actions { display: flex; gap: 0.35rem; margin-top: 0.45rem; flex-wrap: wrap; }
.media-list { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.4rem; max-height: 360px; overflow: auto; }
.media-item { border: 2px solid transparent; border-radius: 8px; padding: 0; overflow: hidden; cursor: pointer; background: rgba(255, 255, 255, 0.04); }
.media-item.active { border-color: var(--accent); }
.media-item img { width: 100%; aspect-ratio: 1; object-fit: cover; display: block; }
@media (max-width: 960px) {
  .layout, .grid-2 { grid-template-columns: 1fr; }
}
</style>
