<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { getKitDetail, searchKits, searchParts } from '@/api/kp'
import type { CatalogItem, ProposalLine, KitCatalogDetail } from '@/types/kp'
import { useToastStore } from '@/stores/toast'
import {
  catalogSearchQueries,
  highlightMatch,
  rankCatalogItems
} from '@/utils/smartSearch'

const emit = defineEmits<{
  addLines: [lines: ProposalLine[]]
}>()

const toast = useToastStore()
const q = ref('')
const kitItems = ref<CatalogItem[]>([])
const partItems = ref<CatalogItem[]>([])
const loading = ref(false)
const searched = ref(false)

let debounceTimer: ReturnType<typeof setTimeout> | null = null
let requestSeq = 0

function money(v: number) {
  return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 }).format(Number(v || 0))
}

function toLine(item: CatalogItem, lineType: 'KIT' | 'PART'): ProposalLine {
  return {
    lineType,
    refId: item.id,
    sku: item.sku,
    name: item.name,
    qty: 1,
    unitPrice: Number(item.price),
    discountPct: 0
  }
}

async function addKit(item: CatalogItem) {
  try {
    const detail: KitCatalogDetail = await getKitDetail(item.id)
    const items = detail.items || []
    const sum = items.reduce((acc, i) => acc + Number(i.partPrice || 0) * (i.qty || 1), 0)
    emit('addLines', [
      {
        lineType: 'KIT',
        refId: detail.id,
        sku: detail.sku,
        name: detail.name,
        qty: 1,
        unitPrice: sum || Number(detail.price || item.price),
        discountPct: 0,
        kitItems: items.map((i) => ({
          partId: i.partId,
          partSku: i.partSku,
          partName: i.partName,
          qty: i.qty || 1,
          partPrice: Number(i.partPrice || 0)
        }))
      }
    ])
    toast.ok('Комплект добавлен')
  } catch {
    emit('addLines', [toLine(item, 'KIT')])
    toast.ok('Комплект добавлен')
  }
}

function addPart(item: CatalogItem) {
  emit('addLines', [toLine(item, 'PART')])
  toast.ok('Запчасть добавлена')
}

function mergeUnique(lists: CatalogItem[][]): CatalogItem[] {
  const map = new Map<string, CatalogItem>()
  for (const list of lists) {
    for (const item of list) map.set(item.id, item)
  }
  return [...map.values()]
}

async function lookup(force = false) {
  const query = q.value.trim()
  const seq = ++requestSeq
  loading.value = true
  try {
    const queries = catalogSearchQueries(query)
    const results = await Promise.all(
      queries.map(async (term) => {
        const [kits, parts] = await Promise.all([searchKits(term), searchParts(term)])
        return { kits, parts }
      })
    )
    if (seq !== requestSeq && !force) return

    kitItems.value = rankCatalogItems(
      mergeUnique(results.map((r) => r.kits)),
      query
    ).slice(0, 40)

    partItems.value = rankCatalogItems(
      mergeUnique(results.map((r) => r.parts)),
      query
    ).slice(0, 60)

    searched.value = true
  } catch {
    if (seq === requestSeq) toast.error('Не удалось загрузить каталог')
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function scheduleLookup() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    lookup()
  }, 280)
}

function clearQuery() {
  q.value = ''
  lookup(true)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && q.value) {
    e.preventDefault()
    clearQuery()
  }
}

watch(q, () => scheduleLookup())

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})

defineExpose({ lookup })
</script>

<template>
  <div class="catalog">
    <div class="search">
      <div class="input-wrap">
        <input
          v-model="q"
          placeholder="Умный поиск: артикул, название, несколько слов…"
          autocomplete="off"
          @keydown="onKeydown"
          @keyup.enter="lookup(true)"
        />
        <button v-if="q" class="clear" type="button" title="Очистить" @click="clearQuery">×</button>
      </div>
      <button class="btn secondary" type="button" :disabled="loading" @click="lookup(true)">Найти</button>
    </div>

    <p class="hint muted">Ищет по артикулу и названию, несколько слов, раскладка EN/RU</p>
    <p v-if="loading" class="muted tiny">Ищем…</p>

    <div v-show="!loading" class="cols">
      <div>
        <p class="label">Запчасти <span v-if="searched">({{ partItems.length }})</span></p>
        <ul>
          <li v-for="p in partItems" :key="p.id">
            <button type="button" class="pick" @click="addPart(p)">
              <span class="pick-text">
                <strong>
                  <template v-for="(chunk, i) in highlightMatch(p.sku || '', q)" :key="`s-${i}`">
                    <mark v-if="chunk.hit">{{ chunk.text }}</mark>
                    <template v-else>{{ chunk.text }}</template>
                  </template>
                </strong>
                <span>
                  <template v-for="(chunk, i) in highlightMatch(p.name || '', q)" :key="`n-${i}`">
                    <mark v-if="chunk.hit">{{ chunk.text }}</mark>
                    <template v-else>{{ chunk.text }}</template>
                  </template>
                </span>
              </span>
              <em>{{ money(Number(p.price)) }} ₽</em>
            </button>
          </li>
          <li v-if="searched && !partItems.length" class="muted tiny">Ничего не найдено</li>
        </ul>
      </div>
      <div>
        <p class="label">Комплекты <span v-if="searched">({{ kitItems.length }})</span></p>
        <ul>
          <li v-for="k in kitItems" :key="k.id">
            <button type="button" class="pick" @click="addKit(k)">
              <span class="pick-text">
                <strong>
                  <template v-for="(chunk, i) in highlightMatch(k.sku || '', q)" :key="`ks-${i}`">
                    <mark v-if="chunk.hit">{{ chunk.text }}</mark>
                    <template v-else>{{ chunk.text }}</template>
                  </template>
                </strong>
                <span>
                  <template v-for="(chunk, i) in highlightMatch(k.name || '', q)" :key="`kn-${i}`">
                    <mark v-if="chunk.hit">{{ chunk.text }}</mark>
                    <template v-else>{{ chunk.text }}</template>
                  </template>
                </span>
              </span>
              <em>{{ money(Number(k.price)) }} ₽</em>
            </button>
          </li>
          <li v-if="searched && !kitItems.length" class="muted tiny">Ничего не найдено</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.catalog { display: grid; gap: 0.5rem; }
.search { display: grid; grid-template-columns: 1fr auto; gap: 0.45rem; }
.input-wrap {
  position: relative;
  min-width: 0;
}
.input-wrap input {
  width: 100%;
  min-height: 2.4rem;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: var(--input-bg);
  padding: 0.45rem 2rem 0.45rem 0.7rem;
}
.clear {
  position: absolute;
  right: 0.35rem;
  top: 50%;
  transform: translateY(-50%);
  width: 1.6rem;
  height: 1.6rem;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  font-size: 1.1rem;
  line-height: 1;
}
.clear:hover { color: var(--ink); }
.hint { margin: 0; font-size: 0.75rem; }
.cols { display: grid; grid-template-columns: 1.15fr 0.85fr; gap: 0.65rem; }
.label { margin: 0 0 0.3rem; font-size: 0.78rem; color: var(--muted); }
ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.3rem; max-height: 240px; overflow: auto; }
.pick {
  width: 100%;
  text-align: left;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: rgba(255,255,255,0.04);
  padding: 0.45rem 0.55rem;
  color: var(--ink);
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  align-items: flex-start;
  cursor: pointer;
}
.pick-text {
  display: grid;
  gap: 0.1rem;
  min-width: 0;
}
.pick-text strong {
  font-size: 0.78rem;
  color: var(--muted);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pick-text > span {
  font-size: 0.85rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pick em { font-style: normal; color: #d1ef98; font-size: 0.8rem; white-space: nowrap; margin-top: 0.1rem; }
.pick:hover { background: rgba(141,198,63,0.12); border-color: rgba(141,198,63,0.45); }
.pick mark {
  background: rgba(141, 198, 63, 0.35);
  color: inherit;
  border-radius: 2px;
  padding: 0 0.05em;
}
.tiny { font-size: 0.8rem; margin: 0; }
@media (max-width: 700px) {
  .cols, .search { grid-template-columns: 1fr; }
}
</style>
