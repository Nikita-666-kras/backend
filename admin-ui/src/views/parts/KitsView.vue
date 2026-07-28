<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import {
  createKit,
  deleteKit,
  fetchKit,
  fetchKits,
  fetchParts,
  fetchDrones,
  formatPrice,
  publishKit,
  updateKit,
  type Drone,
  type Kit,
  type Part,
  type PageResponse
} from '@/api/parts'
import { fetchMedia, mediaPublicUrl, type MediaAsset } from '@/api/media'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useUnsavedGuard } from '@/composables/useUnsavedGuard'
import { statusLabel } from '@/utils/labels'

const auth = useAuthStore()
const toast = useToastStore()
const route = useRoute()
const router = useRouter()
const editingId = computed(() => (typeof route.params.id === 'string' ? route.params.id === 'new' ? '' : route.params.id : ''))
const isEditor = computed(() => route.name === 'kit-new' || route.name === 'kit-edit')

const list = ref<PageResponse<Kit> | null>(null)
const drones = ref<Drone[]>([])
const parts = ref<Part[]>([])
const mediaLib = ref<MediaAsset[]>([])
const name = ref('')
const sku = ref('')
const description = ref('')
const price = ref(0)
const priceMode = ref<'MANUAL' | 'SUM'>('MANUAL')
const droneId = ref('')
const coverMediaId = ref('')
const items = ref<Array<{ partId: string; qty: number }>>([])
const partQuery = ref('')
const partPick = ref('')
const saving = ref(false)
const dirty = ref(false)
const baseline = ref('')

useUnsavedGuard(dirty)

const sumPreview = computed(() =>
  items.value.reduce((acc, item) => {
    const part = parts.value.find((p) => p.id === item.partId) || parts.value.find((p) => p.id === item.partId)
    return acc + (part ? Number(part.price) * item.qty : 0)
  }, 0)
)

const filteredParts = computed(() => {
  const q = partQuery.value.trim().toLowerCase()
  if (!q) return parts.value.slice(0, 80)
  return parts.value
    .filter((p) => p.sku.toLowerCase().includes(q) || p.name.toLowerCase().includes(q))
    .slice(0, 80)
})

function snapshot() {
  return JSON.stringify({
    name: name.value,
    sku: sku.value,
    description: description.value,
    price: price.value,
    priceMode: priceMode.value,
    droneId: droneId.value,
    coverMediaId: coverMediaId.value,
    items: items.value
  })
}

function markClean() {
  baseline.value = snapshot()
  dirty.value = false
}

function touchDirty() {
  dirty.value = baseline.value !== '' && snapshot() !== baseline.value
}

async function loadList() {
  list.value = await fetchKits({ size: 100, q: undefined })
}

async function searchParts(q?: string) {
  const page = await fetchParts({ q: q || undefined, size: 200 })
  parts.value = page.content
}

async function loadRefs() {
  const [dronePage, media] = await Promise.all([
    fetchDrones({ size: 100 }),
    fetchMedia({ section: 'PARTS', size: 80 })
  ])
  drones.value = dronePage.content
  mediaLib.value = media.content
  await searchParts()
}

async function loadEditor() {
  await loadRefs()
  if (route.name === 'kit-new') {
    name.value = ''
    sku.value = ''
    description.value = ''
    price.value = 0
    priceMode.value = 'MANUAL'
    droneId.value = ''
    coverMediaId.value = ''
    items.value = []
    markClean()
    return
  }
  const kit = await fetchKit(editingId.value)
  name.value = kit.name
  sku.value = kit.sku
  description.value = kit.description || ''
  price.value = Number(kit.price)
  priceMode.value = kit.priceMode
  droneId.value = kit.droneId || ''
  coverMediaId.value = kit.coverMediaId || ''
  items.value = kit.items.map((i) => ({ partId: i.partId, qty: i.qty }))
  // ensure kit parts are in local list
  const missing = kit.items.filter((i) => !parts.value.some((p) => p.id === i.partId))
  if (missing.length) {
    await searchParts()
  }
  markClean()
}

function addItem() {
  if (!partPick.value) return
  if (items.value.some((i) => i.partId === partPick.value)) return
  items.value = [...items.value, { partId: partPick.value, qty: 1 }]
  partPick.value = ''
  touchDirty()
}

function removeItem(partId: string) {
  items.value = items.value.filter((i) => i.partId !== partId)
  touchDirty()
}

function payload() {
  return {
    name: name.value.trim(),
    sku: sku.value.trim(),
    description: description.value.trim() || undefined,
    price: priceMode.value === 'SUM' ? sumPreview.value : Number(price.value),
    currency: 'RUB',
    priceMode: priceMode.value,
    droneId: droneId.value || null,
    coverMediaId: coverMediaId.value || null,
    mediaIds: coverMediaId.value ? [coverMediaId.value] : [],
    items: items.value,
    status: 'DRAFT' as const
  }
}

async function save() {
  saving.value = true
  try {
    if (route.name === 'kit-new') {
      const created = await createKit(payload())
      toast.ok('Комплект создан')
      dirty.value = false
      await router.replace(`/kits/${created.id}`)
    } else {
      await updateKit(editingId.value, payload())
      toast.ok('Сохранено')
      markClean()
    }
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Ошибка сохранения')
  } finally {
    saving.value = false
  }
}

async function doPublish(id: string) {
  try {
    await publishKit(id)
    toast.ok('Опубликовано')
    if (isEditor.value) await loadEditor()
    else await loadList()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Ошибка публикации')
  }
}

async function remove(id: string, label: string) {
  if (!confirm(`Удалить комплект «${label}»?`)) return
  try {
    await deleteKit(id)
    toast.ok('Удалено')
    if (isEditor.value) {
      dirty.value = false
      await router.push('/kits')
    } else await loadList()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Удаление доступно только администратору')
  }
}

let partSearchTimer: ReturnType<typeof setTimeout> | null = null
watch(partQuery, (q) => {
  if (partSearchTimer) clearTimeout(partSearchTimer)
  partSearchTimer = setTimeout(() => {
    searchParts(q.trim() || undefined).catch(() => undefined)
  }, 280)
})

watch([name, sku, description, price, priceMode, droneId, coverMediaId, items], touchDirty, { deep: true })

onMounted(async () => {
  try {
    if (isEditor.value) await loadEditor()
    else await loadList()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Ошибка загрузки')
  }
})

watch(
  () => route.fullPath,
  async () => {
    try {
      if (isEditor.value) await loadEditor()
      else await loadList()
    } catch (e: any) {
      toast.error(e?.response?.data?.message || 'Ошибка загрузки')
    }
  }
)
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <p class="eyebrow">Витрина</p>
        <h1>{{ isEditor ? (route.name === 'kit-new' ? 'Новый комплект' : 'Комплект') : 'Комплекты' }}</h1>
      </div>
      <div class="actions">
        <RouterLink v-if="!isEditor" class="btn" to="/kits/new">+ Комплект</RouterLink>
        <template v-else>
          <button class="btn secondary" :disabled="saving" type="button" @click="save">Сохранить</button>
          <button v-if="editingId" class="btn" type="button" @click="doPublish(editingId)">Опубликовать</button>
          <button
            v-if="editingId && auth.isAdmin"
            class="btn danger"
            type="button"
            @click="remove(editingId, name || sku)"
          >
            Удалить
          </button>
        </template>
      </div>
    </header>

    <div v-if="!isEditor && list" class="list">
      <article v-for="item in list.content" :key="item.id" class="card row">
        <div class="thumb">
          <img v-if="item.coverMediaId" :src="mediaPublicUrl(item.coverMediaId)" :alt="item.name" />
        </div>
        <div>
          <RouterLink :to="`/kits/${item.id}`"><strong>{{ item.name }}</strong></RouterLink>
          <p class="muted">{{ item.sku }} · {{ item.items.length }} поз. · {{ formatPrice(item.price, item.currency) }}</p>
        </div>
        <span class="badge" :class="item.status">{{ statusLabel(item.status) }}</span>
        <div class="actions">
          <button v-if="item.status !== 'PUBLISHED'" class="btn secondary" type="button" @click="doPublish(item.id)">
            Опубл.
          </button>
          <button v-if="auth.isAdmin" class="btn danger" type="button" @click="remove(item.id, item.name)">Удал.</button>
        </div>
      </article>
      <p v-if="!list.content.length" class="muted">Пока нет комплектов</p>
    </div>

    <div v-else class="card editor">
      <div class="grid-2">
        <div class="field">
          <label>Название</label>
          <input v-model="name" />
        </div>
        <div class="field">
          <label>Артикул комплекта</label>
          <input v-model="sku" />
        </div>
      </div>
      <div class="grid-2">
        <div class="field">
          <label>Режим цены</label>
          <select v-model="priceMode">
            <option value="MANUAL">Вручную</option>
            <option value="SUM">Сумма запчастей</option>
          </select>
        </div>
        <div class="field">
          <label>Цена (₽)</label>
          <input v-if="priceMode === 'MANUAL'" v-model.number="price" type="number" min="0" step="0.01" />
          <input v-else :value="sumPreview" disabled />
        </div>
      </div>
      <div class="grid-2">
        <div class="field">
          <label>Дрон</label>
          <select v-model="droneId">
            <option value="">Без привязки</option>
            <option v-for="d in drones" :key="d.id" :value="d.id">{{ d.name }}</option>
          </select>
        </div>
        <div class="field">
          <label>Обложка</label>
          <select v-model="coverMediaId">
            <option value="">Без фото</option>
            <option v-for="m in mediaLib" :key="m.id" :value="m.id">{{ m.originalName }}</option>
          </select>
        </div>
      </div>
      <div class="field">
        <label>Описание</label>
        <textarea v-model="description" rows="4" />
      </div>

      <div class="items">
        <h3>Состав</h3>
        <div class="add-row">
          <input v-model="partQuery" placeholder="Поиск артикула / названия…" />
          <select v-model="partPick">
            <option value="">Выберите запчасть</option>
            <option v-for="p in filteredParts" :key="p.id" :value="p.id">{{ p.sku }} — {{ p.name }}</option>
          </select>
          <button class="btn secondary" type="button" @click="addItem">Добавить</button>
        </div>
        <div v-for="item in items" :key="item.partId" class="item-row">
          <span>{{ parts.find((p) => p.id === item.partId)?.sku }} — {{ parts.find((p) => p.id === item.partId)?.name || item.partId }}</span>
          <input v-model.number="item.qty" type="number" min="1" />
          <button class="btn ghost" type="button" @click="removeItem(item.partId)">Убрать</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.actions { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.list { display: grid; gap: 0.65rem; }
.row { display: grid; grid-template-columns: 64px 1fr auto auto; gap: 0.75rem; align-items: center; padding: 0.9rem 1rem; }
.thumb { width: 64px; height: 48px; border-radius: 8px; overflow: hidden; background: rgba(255,255,255,0.05); }
.thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.editor { padding: 1.1rem; display: grid; gap: 0.9rem; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0.9rem; }
.items { display: grid; gap: 0.55rem; }
.items h3 { margin: 0; font-size: 0.95rem; }
.add-row { display: grid; grid-template-columns: 1fr 1.4fr auto; gap: 0.5rem; }
.item-row { display: grid; grid-template-columns: 1fr 80px auto; gap: 0.5rem; align-items: center; }
.muted { margin: 0.2rem 0 0; color: var(--muted); }
@media (max-width: 860px) {
  .grid-2, .row, .item-row, .add-row { grid-template-columns: 1fr; }
}
</style>
