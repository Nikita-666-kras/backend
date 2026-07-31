<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getKitDetail, searchKits, searchParts } from '@/api/kp'
import type { CatalogItem, ProposalLine, KitCatalogDetail } from '@/types/kp'
import { useToastStore } from '@/stores/toast'

const emit = defineEmits<{
  addLines: [lines: ProposalLine[]]
}>()

const toast = useToastStore()
const q = ref('')
const kitItems = ref<CatalogItem[]>([])
const partItems = ref<CatalogItem[]>([])
const loading = ref(false)

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

async function lookup() {
  loading.value = true
  try {
    ;[kitItems.value, partItems.value] = await Promise.all([searchKits(q.value), searchParts(q.value)])
  } catch {
    toast.error('Не удалось загрузить каталог')
  } finally {
    loading.value = false
  }
}

onMounted(lookup)
defineExpose({ lookup })
</script>

<template>
  <div class="catalog">
    <div class="search">
      <input v-model="q" placeholder="Поиск запчасти или комплекта" @keyup.enter="lookup" />
      <button class="btn secondary" type="button" :disabled="loading" @click="lookup">Найти</button>
    </div>

    <p v-if="loading" class="muted tiny">Загрузка…</p>

    <div v-else class="cols">
      <div>
        <p class="label">Комплекты</p>
        <ul>
          <li v-for="k in kitItems" :key="k.id">
            <button type="button" class="pick" @click="addKit(k)">
              <span>{{ k.name }}</span>
              <em>{{ money(Number(k.price)) }} ₽</em>
            </button>
          </li>
          <li v-if="!kitItems.length" class="muted tiny">Ничего не найдено</li>
        </ul>
      </div>
      <div>
        <p class="label">Запчасти</p>
        <ul>
          <li v-for="p in partItems" :key="p.id">
            <button type="button" class="pick" @click="emit('addLines', [toLine(p, 'PART')])">
              <span>{{ p.name }}</span>
              <em>{{ money(Number(p.price)) }} ₽</em>
            </button>
          </li>
          <li v-if="!partItems.length" class="muted tiny">Ничего не найдено</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.catalog { display: grid; gap: 0.65rem; }
.search { display: grid; grid-template-columns: 1fr auto; gap: 0.45rem; }
.search input {
  min-height: 2.4rem;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: var(--input-bg);
  padding: 0.45rem 0.7rem;
}
.cols { display: grid; grid-template-columns: 1fr 1fr; gap: 0.65rem; }
.label { margin: 0 0 0.3rem; font-size: 0.78rem; color: var(--muted); }
ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.3rem; max-height: 180px; overflow: auto; }
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
  align-items: baseline;
}
.pick span { font-size: 0.85rem; }
.pick em { font-style: normal; color: #d1ef98; font-size: 0.8rem; white-space: nowrap; }
.pick:hover { background: rgba(141,198,63,0.12); border-color: rgba(141,198,63,0.45); }
.tiny { font-size: 0.8rem; margin: 0; }
@media (max-width: 700px) {
  .cols, .search { grid-template-columns: 1fr; }
}
</style>
