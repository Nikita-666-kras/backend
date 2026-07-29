<script setup lang="ts">
import { ref } from 'vue'
import { getKitDetail, searchKits, searchParts } from '@/api/kp'
import type { CatalogItem, ProposalLine, KitCatalogDetail } from '@/types/kp'
import { useToastStore } from '@/stores/toast'

const emit = defineEmits<{
  addLines: [lines: ProposalLine[]]
}>()

const toast = useToastStore()
const kitQ = ref('')
const partQ = ref('')
const kitItems = ref<CatalogItem[]>([])
const partItems = ref<CatalogItem[]>([])
const loading = ref(false)

function toLine(item: CatalogItem, lineType: 'KIT' | 'PART'): ProposalLine {
  return { lineType, refId: item.id, sku: item.sku, name: item.name, qty: 1, unitPrice: Number(item.price), discountPct: 0 }
}

function kitDetailToLines(kit: KitCatalogDetail): ProposalLine[] {
  return (kit.items || []).map((i) => ({
    lineType: 'PART',
    refId: i.partId,
    sku: i.partSku || '',
    name: `${i.partName} (из комплекта ${kit.sku})`,
    qty: i.qty || 1,
    unitPrice: Number(i.partPrice || 0),
    discountPct: 0
  }))
}

async function addKitAsEditableLines(item: CatalogItem) {
  try {
    const detail = await getKitDetail(item.id)
    const kitItems = detail.items || []
    if (!kitItems.length) {
      toast.info('В комплекте нет позиций для добавления')
      return
    }
    const sum = kitItems.reduce((acc, i) => acc + Number(i.partPrice || 0) * (i.qty || 1), 0)
    const kitLine: ProposalLine = {
      lineType: 'KIT',
      refId: detail.id,
      sku: detail.sku,
      name: detail.name,
      qty: 1,
      unitPrice: sum,
      discountPct: 0,
      kitItems: kitItems.map((i) => ({
        partId: i.partId,
        partSku: i.partSku,
        partName: i.partName,
        qty: i.qty || 1,
        partPrice: Number(i.partPrice || 0)
      }))
    }
    emit('addLines', [kitLine])
    toast.ok(`Комплект ${item.sku} добавлен. Нажмите + в строке для редактирования состава.`)
  } catch {
    // fallback to single KIT line if detail unavailable
    emit('addLines', [toLine(item, 'KIT')])
    toast.info('Добавлен как строка комплекта')
  }
}

async function lookup() {
  loading.value = true
  try {
    ;[kitItems.value, partItems.value] = await Promise.all([searchKits(kitQ.value), searchParts(partQ.value)])
  } catch {
    toast.error('Не удалось загрузить каталог')
  } finally {
    loading.value = false
  }
}

defineExpose({ lookup })
</script>

<template>
  <div class="catalog card section-pad">
    <div class="catalog-head">
      <h3>Каталог</h3>
      <p class="subtitle">Добавляйте комплекты и запчасти в КП</p>
    </div>
    <div class="search-row">
      <input v-model="kitQ" placeholder="Поиск комплекта" @keyup.enter="lookup" />
      <input v-model="partQ" placeholder="Поиск запчасти" @keyup.enter="lookup" />
      <button class="btn secondary" type="button" @click="lookup">Найти</button>
    </div>

    <div v-if="loading" class="muted">Загрузка каталога…</div>
    <div class="results">
      <div>
        <h4>Комплекты</h4>
        <ul>
          <li v-for="k in kitItems" :key="k.id">
            <button type="button" class="pick" @click="addKitAsEditableLines(k)">
              <strong>{{ k.sku }}</strong>
              <span>{{ k.name }}</span>
              <em>{{ k.price }}</em>
            </button>
          </li>
        </ul>
      </div>
      <div>
        <h4>Запчасти</h4>
        <ul>
          <li v-for="p in partItems" :key="p.id">
            <button type="button" class="pick" @click="emit('addLines', [toLine(p, 'PART')])">
              <strong>{{ p.sku }}</strong>
              <span>{{ p.name }}</span>
              <em>{{ p.price }}</em>
            </button>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.catalog { padding-top: 0.75rem; padding-bottom: 0.75rem; }
.catalog-head { margin-bottom: 0.55rem; }
.catalog-head h3 { margin: 0; font-size: 1rem; }
.catalog-head p { margin: 0.2rem 0 0; font-size: 0.8rem; }
.search-row { display: grid; grid-template-columns: 1fr 1fr auto; gap: 0.45rem; margin-bottom: 0.6rem; }
.results { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.8rem; }
.results h4 { margin: 0 0 0.35rem; font-size: 0.85rem; color: var(--muted); }
ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.35rem; max-height: 210px; overflow: auto; }
.pick { width: 100%; text-align: left; border: 1px solid var(--line); border-radius: 10px; background: rgba(255,255,255,0.04); padding: 0.45rem 0.55rem; color: var(--ink); display: grid; gap: 0.12rem; }
.pick strong { font-size: 0.78rem; color: #b8d7ff; }
.pick span { font-size: 0.83rem; }
.pick em { font-style: normal; color: #d1ef98; font-size: 0.8rem; }
.pick:hover { background: rgba(141,198,63,0.12); border-color: rgba(141,198,63,0.45); }
@media (max-width: 960px) { .search-row { grid-template-columns: 1fr; } .results { grid-template-columns: 1fr; } }
@media (max-width: 560px) {
  .catalog { padding-top: 0.6rem; padding-bottom: 0.6rem; }
  .search-row .btn { width: 100%; }
  ul { max-height: 180px; }
}
</style>
