<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink } from 'vue-router'
import {
  deletePart,
  fetchPartCategories,
  fetchParts,
  fetchDrones,
  formatPrice,
  publishPart,
  updatePart,
  bulkParts,
  type Drone,
  type Part,
  type PartCategory,
  type PageResponse
} from '@/api/parts'
import { mediaPublicUrl, uploadMedia } from '@/api/media'
import { useAuthStore } from '@/stores/auth'
import { usePartsCatalogStore } from '@/stores/partsCatalog'
import { useToastStore } from '@/stores/toast'
import { statusLabel } from '@/utils/labels'

const auth = useAuthStore()
const toast = useToastStore()
const store = usePartsCatalogStore()
const { q, status, droneId, categoryId, catalogFilter, page, pageSize, columns } = storeToRefs(store)

const data = ref<PageResponse<Part> | null>(null)
const drones = ref<Drone[]>([])
const categories = ref<PartCategory[]>([])
const loading = ref(false)
const error = ref('')
const message = ref('')
const selected = ref<Set<string>>(new Set())
const folderInput = ref<HTMLInputElement | null>(null)
const photoBusy = ref(false)
const photoProgress = ref('')

const totalLabel = computed(() => {
  if (!data.value) return ''
  return `${data.value.totalElements} в каталоге`
})

const selectedCount = computed(() => selected.value.size)
const batchBusy = ref(false)
const selectBusy = ref(false)

const pageItems = computed(() => data.value?.content || [])

const allPageSelected = computed(() => {
  const items = pageItems.value
  return items.length > 0 && items.every((p) => selected.value.has(p.id))
})

const somePageSelected = computed(() => {
  const items = pageItems.value
  const n = items.filter((p) => selected.value.has(p.id)).length
  return n > 0 && n < items.length
})

const canPrev = computed(() => page.value > 0)
const canNext = computed(() => !!data.value && page.value + 1 < data.value.totalPages)

function normalizeSku(value: string) {
  return value.trim().toUpperCase().replace(/\s+/g, '')
}

function compactSku(value: string) {
  return normalizeSku(value).replace(/[^A-Z0-9]/g, '')
}

function stemFromFilename(name: string) {
  const base = name.replace(/^.*[\\/]/, '')
  return base.replace(/\.[^.]+$/, '')
}

/** Longest SKU that appears as a contiguous token in the filename stem. */
function matchSkuFromFilename(filename: string, skus: string[]): string | null {
  const stem = normalizeSku(stemFromFilename(filename))
  const stemCompact = compactSku(stemFromFilename(filename))
  if (!stem) return null

  const sorted = [...skus].sort((a, b) => b.length - a.length)
  for (const sku of sorted) {
    const n = normalizeSku(sku)
    const nc = compactSku(sku)
    if (!n || n.length < 2) continue
    if (stem === n) return sku
    // token boundaries: start/end or non-alnum around SKU
    const escaped = n.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const re = new RegExp(`(?:^|[^A-Z0-9])${escaped}(?:$|[^A-Z0-9])`)
    if (re.test(stem)) return sku
    // relaxed match for different separators: BC.AG.SS000391.01 vs BC-AG-SS000391-01
    if (nc.length >= 4 && stemCompact.includes(nc)) return sku
  }
  return null
}

function partPayload(part: Part, coverMediaId: string, mediaIds: string[]) {
  return {
    name: part.name,
    sku: part.sku,
    description: part.description || undefined,
    price: part.price,
    currency: part.currency,
    droneId: part.droneId ?? null,
    categoryId: part.categoryId ?? null,
    coverMediaId,
    mediaIds,
    status: part.status,
    sortOrder: part.sortOrder,
    externalSource: part.externalSource,
    externalId: part.externalId ?? null
  }
}

async function loadRefs() {
  const [dronePage, cats] = await Promise.all([fetchDrones({ size: 100 }), fetchPartCategories()])
  drones.value = dronePage.content
  categories.value = cats
}

async function load() {
  loading.value = true
  error.value = ''
  store.persist()
  try {
    data.value = await fetchParts({
      q: q.value || undefined,
      status: status.value || undefined,
      droneId: droneId.value || undefined,
      categoryId: categoryId.value || undefined,
      catalogFilter: catalogFilter.value || undefined,
      page: page.value,
      size: pageSize.value
    })
    // drop selection for items no longer on page? Keep global selection across pages.
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Не удалось загрузить каталог'
  } finally {
    loading.value = false
  }
}

function toggleSelect(id: string) {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}

function selectPage() {
  const next = new Set(selected.value)
  for (const p of pageItems.value) next.add(p.id)
  selected.value = next
}

function deselectPage() {
  const next = new Set(selected.value)
  for (const p of pageItems.value) next.delete(p.id)
  selected.value = next
}

function toggleSelectAllPage() {
  if (allPageSelected.value) deselectPage()
  else selectPage()
}

function clearSelection() {
  selected.value = new Set()
}

async function selectAllFiltered() {
  selectBusy.value = true
  error.value = ''
  try {
    const parts = await fetchAllPartsForMatching()
    selected.value = new Set(parts.map((p) => p.id))
    message.value = `Выбрано все по фильтру: ${parts.length}`
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Не удалось выбрать все'
  } finally {
    selectBusy.value = false
  }
}

function goPage(target: number) {
  if (!data.value) return
  const next = Math.max(0, Math.min(data.value.totalPages - 1, target))
  if (next === page.value) return
  page.value = next
  load()
}

async function doPublish(item: Part) {
  try {
    await publishPart(item.id)
    toast.ok(`Опубликовано: ${item.sku}`)
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Ошибка публикации')
  }
}

async function doDelete(item: Part) {
  if (!confirm(`Удалить «${item.name}»?`)) return
  try {
    await deletePart(item.id)
    selected.value.delete(item.id)
    selected.value = new Set(selected.value)
    toast.ok('Удалено')
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Удаление доступно только администратору')
  }
}

async function runBulk(action: 'PUBLISH' | 'ARCHIVE' | 'DELETE') {
  const ids = [...selected.value]
  if (!ids.length || batchBusy.value) return
  if (action === 'DELETE' && !confirm(`Удалить выбранные запчасти (${ids.length})?`)) return
  batchBusy.value = true
  try {
    const result = await bulkParts(ids, action)
    const label = action === 'PUBLISH' ? 'Опубликовано' : action === 'ARCHIVE' ? 'В архив' : 'Удалено'
    toast.ok(`${label}: ${result.success}` + (result.failed ? `, ошибок: ${result.failed}` : ''))
    if (action === 'DELETE') clearSelection()
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Массовое действие не выполнено')
  } finally {
    batchBusy.value = false
  }
}

async function fetchAllPartsForMatching(): Promise<Part[]> {
  const all: Part[] = []
  let p = 0
  let totalPages = 1
  while (p < totalPages) {
    const pageData = await fetchParts({
      q: q.value || undefined,
      status: status.value || undefined,
      droneId: droneId.value || undefined,
      categoryId: categoryId.value || undefined,
      catalogFilter: catalogFilter.value || undefined,
      page: p,
      size: 500
    })
    all.push(...pageData.content)
    totalPages = Math.max(1, pageData.totalPages)
    p++
    if (p > 40) break
  }
  return all
}

async function onFolderSelected(ev: Event) {
  const input = ev.target as HTMLInputElement
  const files = [...(input.files || [])].filter((f) => f.type.startsWith('image/') || /\.(jpe?g|png|webp|gif|bmp|tiff?)$/i.test(f.name))
  input.value = ''
  if (!files.length) {
    message.value = 'В папке нет изображений'
    return
  }

  photoBusy.value = true
  error.value = ''
  message.value = ''
  photoProgress.value = 'Загрузка каталога…'

  try {
    const parts = await fetchAllPartsForMatching()
    const bySku = new Map<string, Part>()
    for (const part of parts) {
      bySku.set(normalizeSku(part.sku), part)
    }
    const skus = parts.map((p) => p.sku)

    let matched = 0
    let skipped = 0
    const unmatched: string[] = []

    for (let i = 0; i < files.length; i++) {
      const file = files[i]
      photoProgress.value = `${i + 1} / ${files.length}: ${file.name}`
      const sku = matchSkuFromFilename(file.name, skus)
      if (!sku) {
        unmatched.push(file.name)
        skipped++
        continue
      }
      const part = bySku.get(normalizeSku(sku))
      if (!part) {
        unmatched.push(file.name)
        skipped++
        continue
      }
      try {
        const media = await uploadMedia(file, 'PARTS')
        const mediaIds = [...new Set([...(part.mediaIds || []), media.id])]
        const updated = await updatePart(part.id, partPayload(part, media.id, mediaIds))
        bySku.set(normalizeSku(updated.sku), updated)
        matched++
      } catch (e: any) {
        unmatched.push(`${file.name} (${e?.response?.data?.message || 'ошибка'})`)
        skipped++
      }
    }

    const hint = unmatched.length
      ? ` Без совпадения: ${unmatched.slice(0, 5).join(', ')}${unmatched.length > 5 ? '…' : ''}`
      : ''
    message.value = `Фото: ${matched} привязано, ${skipped} пропущено.${hint}`
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Не удалось загрузить папку с фото'
  } finally {
    photoBusy.value = false
    photoProgress.value = ''
  }
}

function setColumns(value: 1 | 2) {
  columns.value = value
  store.persist()
}

onMounted(async () => {
  try {
    await loadRefs()
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Ошибка инициализации'
  }
})

watch([status, droneId, categoryId, catalogFilter, pageSize], () => {
  page.value = 0
  clearSelection()
  load()
})
</script>

<template>
  <section class="catalog">
    <header class="page-header">
      <div>
        <p class="eyebrow">Каталог</p>
        <h1>Запчасти</h1>
        <p class="muted">{{ totalLabel || 'Цена, дрон, статус и фото' }}</p>
      </div>
      <div class="actions">
        <button class="btn secondary" type="button" :disabled="photoBusy" @click="folderInput?.click()">
          Папка с фото
        </button>
        <input
          ref="folderInput"
          type="file"
          class="sr-only"
          multiple
          webkitdirectory
          directory
          accept="image/*"
          @change="onFolderSelected"
        />
        <RouterLink class="btn secondary" to="/parts-import">Импорт</RouterLink>
        <RouterLink class="btn" to="/parts/new">+ Добавить</RouterLink>
      </div>
    </header>

    <div class="toolbar card">
      <input v-model="q" placeholder="Артикул или название…" @keyup.enter="page = 0; clearSelection(); load()" />
      <select v-model="status">
        <option value="">Все статусы</option>
        <option value="DRAFT">Черновик</option>
        <option value="PUBLISHED">На сайте</option>
        <option value="ARCHIVED">Архив</option>
      </select>
      <select v-model="droneId">
        <option value="">Все дроны</option>
        <option v-for="d in drones" :key="d.id" :value="d.id">{{ d.name }}</option>
      </select>
      <select v-model="categoryId">
        <option value="">Все категории</option>
        <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <select v-model="catalogFilter">
        <option value="">Все позиции</option>
        <option value="INCOMPLETE">Неполные (любой пробел)</option>
        <option value="NO_PRICE">Без цены</option>
        <option value="NO_NAME">Без названия</option>
        <option value="NO_PHOTO">Без фото</option>
        <option value="NO_DRONE">Без дрона</option>
        <option value="NO_CATEGORY">Без категории</option>
      </select>
      <select v-model.number="pageSize">
        <option :value="10">10 / стр.</option>
        <option :value="50">50 / стр.</option>
        <option :value="100">100 / стр.</option>
        <option :value="500">500 / стр.</option>
      </select>
      <div class="layout-toggle" role="group" aria-label="Колонки">
        <button type="button" class="tog" :class="{ active: columns === 1 }" @click="setColumns(1)">1</button>
        <button type="button" class="tog" :class="{ active: columns === 2 }" @click="setColumns(2)">2</button>
      </div>
      <button class="btn secondary" @click="page = 0; clearSelection(); load()">Найти</button>
    </div>

    <div class="batch card">
      <label class="check-all">
        <input
          type="checkbox"
          :checked="allPageSelected"
          :indeterminate.prop="somePageSelected"
          :disabled="!pageItems.length"
          @change="toggleSelectAllPage"
        />
        <span>Страница</span>
      </label>
      <button class="btn secondary mini" type="button" :disabled="!pageItems.length" @click="selectPage">
        Выбрать страницу
      </button>
      <button
        class="btn secondary mini"
        type="button"
        :disabled="selectBusy || !data?.totalElements"
        @click="selectAllFiltered"
      >
        {{ selectBusy ? 'Выбор…' : 'Выбрать все' }}
      </button>
      <button class="btn ghost mini" type="button" :disabled="!selectedCount" @click="clearSelection">Снять</button>
      <span class="batch-count" :class="{ active: selectedCount }">Выбрано: {{ selectedCount }}</span>
      <div class="batch-actions">
        <button class="btn secondary mini" type="button" :disabled="!selectedCount || batchBusy" @click="runBulk('PUBLISH')">
          Опубликовать
        </button>
        <button class="btn secondary mini" type="button" :disabled="!selectedCount || batchBusy" @click="runBulk('ARCHIVE')">
          В архив
        </button>
        <button
          v-if="auth.isAdmin"
          class="btn danger mini"
          type="button"
          :disabled="!selectedCount || batchBusy"
          @click="runBulk('DELETE')"
        >
          Удалить
        </button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="ok">{{ message }}</p>
    <p v-if="loading || photoBusy" class="muted">{{ photoProgress || 'Загрузка…' }}</p>

    <div v-if="data" class="parts-grid" :class="columns === 2 ? 'cols-2' : 'cols-1'">
      <article
        v-for="item in data.content"
        :key="item.id"
        class="part-card card"
        :class="{ selected: selected.has(item.id) }"
      >
        <label class="pick" title="Выбрать" @click.stop>
          <input type="checkbox" :checked="selected.has(item.id)" @change="toggleSelect(item.id)" />
        </label>
        <RouterLink class="cover" :to="`/parts/${item.id}`">
          <img v-if="item.coverMediaId" :src="mediaPublicUrl(item.coverMediaId)" :alt="item.name" loading="lazy" />
          <div v-else class="cover-empty">Нет фото</div>
        </RouterLink>
        <div class="body">
          <div class="top">
            <code>{{ item.sku }}</code>
            <span class="badge" :class="item.status">{{ statusLabel(item.status) }}</span>
          </div>
          <RouterLink class="title" :to="`/parts/${item.id}`">{{ item.name }}</RouterLink>
          <div class="meta">
            <span>{{ item.droneName || '—' }}</span>
            <strong>{{ formatPrice(item.price, item.currency) }}</strong>
          </div>
          <div class="row-actions">
            <RouterLink class="btn secondary mini" :to="`/parts/${item.id}`">Открыть</RouterLink>
            <button v-if="item.status !== 'PUBLISHED'" class="btn secondary mini" type="button" @click="doPublish(item)">
              Опубл.
            </button>
            <button v-if="auth.isAdmin" class="btn danger mini" type="button" @click="doDelete(item)">Удал.</button>
          </div>
        </div>
      </article>
      <div v-if="!data.content.length" class="empty card">Пока пусто — добавьте первую запчасть</div>
    </div>

    <div v-if="data" class="pager card">
      <button class="btn secondary mini" type="button" :disabled="!canPrev || loading" @click="goPage(0)">«</button>
      <button class="btn secondary mini" type="button" :disabled="!canPrev || loading" @click="goPage(page - 1)">Назад</button>
      <span class="pager-info">
        Стр. {{ data.totalPages ? page + 1 : 0 }} / {{ data.totalPages }} · {{ data.totalElements }} шт.
      </span>
      <button class="btn secondary mini" type="button" :disabled="!canNext || loading" @click="goPage(page + 1)">Далее</button>
      <button class="btn secondary mini" type="button" :disabled="!canNext || loading" @click="goPage(data.totalPages - 1)">
        »
      </button>
    </div>
  </section>
</template>

<style scoped>
.actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-bottom: 0.65rem;
  align-items: center;
}

.toolbar input[type='text'],
.toolbar input:not([type]) {
  flex: 1;
  min-width: 160px;
}

.check-all {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.82rem;
  color: var(--muted);
  white-space: nowrap;
  cursor: pointer;
}

.check-all input {
  width: 1rem;
  height: 1rem;
  cursor: pointer;
}

.layout-toggle {
  display: inline-flex;
  border: 1px solid var(--line);
  border-radius: 8px;
  overflow: hidden;
}

.tog {
  border: 0;
  background: transparent;
  padding: 0.45rem 0.65rem;
  color: var(--muted);
  font-size: 0.85rem;
}

.tog.active {
  background: var(--accent);
  color: var(--accent-ink);
}

.batch {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  align-items: center;
  padding: 0.55rem 0.75rem;
  margin-bottom: 0.65rem;
  font-size: 0.9rem;
  position: sticky;
  top: 0.5rem;
  z-index: 5;
  background: rgba(22, 58, 102, 0.94);
}

.batch-count {
  color: var(--muted);
  margin-left: 0.15rem;
}

.batch-count.active {
  color: var(--accent);
  font-weight: 700;
}

.batch-actions {
  display: flex;
  gap: 0.4rem;
  margin-left: auto;
}

.parts-grid {
  display: grid;
  gap: 0.5rem;
}

.parts-grid.cols-1 {
  grid-template-columns: 1fr;
}

.parts-grid.cols-2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.part-card {
  position: relative;
  display: grid;
  grid-template-columns: 72px 1fr;
  overflow: hidden;
  min-height: 0;
}

.part-card.selected {
  border-color: rgba(141, 198, 63, 0.45);
  background: rgba(141, 198, 63, 0.05);
}

.cols-2 .part-card {
  grid-template-columns: 64px 1fr;
}

.pick {
  position: absolute;
  z-index: 2;
  left: 0.3rem;
  top: 0.3rem;
  display: grid;
  place-items: center;
  width: 1.55rem;
  height: 1.55rem;
  border-radius: 6px;
  background: rgba(18, 48, 85, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.22);
  cursor: pointer;
}

.pick input {
  margin: 0;
  width: 1rem;
  height: 1rem;
  cursor: pointer;
}

.cover {
  position: relative;
  display: block;
  background: rgba(255, 255, 255, 0.04);
  min-height: 72px;
  height: 100%;
}

.cols-2 .cover {
  min-height: 64px;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-empty {
  height: 100%;
  min-height: 72px;
  display: grid;
  place-items: center;
  color: var(--muted);
  font-size: 0.7rem;
  text-align: center;
  padding: 0.25rem;
}

.cols-2 .cover-empty {
  min-height: 64px;
}

.body {
  padding: 0.45rem 0.65rem 0.5rem;
  display: grid;
  gap: 0.2rem;
  align-content: center;
  min-width: 0;
}

.top {
  display: flex;
  gap: 0.4rem;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.body code {
  font-size: 0.72rem;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.title {
  font-weight: 650;
  font-size: 0.9rem;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.title:hover {
  color: var(--accent);
}

.meta {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  color: var(--muted);
  font-size: 0.78rem;
  min-width: 0;
}

.meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta strong {
  color: var(--ink);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.row-actions {
  display: flex;
  gap: 0.3rem;
  flex-wrap: wrap;
  margin-top: 0.15rem;
}

.mini {
  padding: 0.28rem 0.45rem;
  font-size: 0.75rem;
}

.pager {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  align-items: center;
  margin-top: 0.85rem;
  padding: 0.55rem 0.75rem;
}

.pager-info {
  flex: 1;
  text-align: center;
  color: var(--muted);
  font-size: 0.88rem;
  min-width: 10rem;
}

@media (max-width: 860px) {
  .parts-grid.cols-2 {
    grid-template-columns: 1fr;
  }
}
</style>
