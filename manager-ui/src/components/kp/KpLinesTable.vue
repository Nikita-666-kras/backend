<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ProposalLine } from '@/types/kp'
import { formatMoney } from '@/utils/format'

const props = defineProps<{
  lines: ProposalLine[]
}>()

const emit = defineEmits<{
  remove: [idx: number]
  dirty: []
}>()
const expanded = ref<Record<number, boolean>>({})

const totals = computed(() =>
  props.lines.map((l) => {
    const mult = l.lineType === 'KIT' ? (100 - l.discountPct) / 100 : 1
    return l.unitPrice * l.qty * mult
  })
)

function setQty(idx: number, next: number) {
  const value = Math.max(1, Math.floor(next || 1))
  props.lines[idx].qty = value
  emit('dirty')
}

function toggleExpand(idx: number) {
  expanded.value[idx] = !expanded.value[idx]
}

function hasKitItems(line: ProposalLine) {
  return line.lineType === 'KIT' && Array.isArray(line.kitItems) && line.kitItems.length > 0
}

function setKitItemQty(line: ProposalLine, partId: string, qty: number) {
  if (!line.kitItems) return
  const value = Math.max(1, Math.floor(qty || 1))
  line.kitItems = line.kitItems.map((i) => (i.partId === partId ? { ...i, qty: value } : i))
  syncKitPrice(line)
  emit('dirty')
}

function removeKitItem(line: ProposalLine, partId: string) {
  if (!line.kitItems) return
  line.kitItems = line.kitItems.filter((i) => i.partId !== partId)
  syncKitPrice(line)
  emit('dirty')
}

function syncKitPrice(line: ProposalLine) {
  if (!line.kitItems || !line.kitItems.length) return
  line.unitPrice = line.kitItems.reduce((acc, i) => acc + Number(i.partPrice || 0) * i.qty, 0)
}
</script>

<template>
  <div class="surface-light lines-wrap">
    <div class="caption">
      <h3>Позиции КП</h3>
      <p>{{ props.lines.length }} строк</p>
    </div>
    <table class="data-table">
      <colgroup>
        <col class="col-type" />
        <col class="col-sku" />
        <col class="col-name" />
        <col class="col-qty" />
        <col class="col-price" />
        <col class="col-discount" />
        <col class="col-sum" />
        <col class="col-actions" />
      </colgroup>
      <thead>
        <tr>
          <th>Тип</th><th>SKU</th><th>Название</th><th>Qty</th><th>Цена</th><th>Скидка kit</th><th>Сумма</th><th></th>
        </tr>
      </thead>
      <tbody>
        <template v-for="(l, idx) in props.lines" :key="idx">
        <tr>
          <td><span class="line-type" :class="l.lineType">{{ l.lineType }}</span></td>
          <td>{{ l.sku || '—' }}</td>
          <td><input v-model="l.name" class="line-input" @input="emit('dirty')" /></td>
          <td>
            <div class="qty-box">
              <button class="qty-btn" type="button" @click="setQty(idx, l.qty - 1)">−</button>
              <input :value="l.qty" type="number" min="1" @change="setQty(idx, Number(($event.target as HTMLInputElement).value))" />
              <button class="qty-btn" type="button" @click="setQty(idx, l.qty + 1)">+</button>
            </div>
          </td>
          <td><input v-model.number="l.unitPrice" class="price-input" type="number" min="0" step="0.01" @input="emit('dirty')" /></td>
          <td>
            <select v-model.number="l.discountPct" class="discount-select" :disabled="l.lineType !== 'KIT'" @change="emit('dirty')">
              <option :value="0">0%</option><option :value="5">5%</option><option :value="10">10%</option><option :value="15">15%</option><option :value="20">20%</option>
            </select>
          </td>
          <td class="sum">{{ formatMoney(totals[idx]) }}</td>
          <td class="actions">
            <div class="actions-controls">
              <button
                v-if="hasKitItems(l)"
                class="btn secondary compact"
                type="button"
                @click="toggleExpand(idx)"
              >
                {{ expanded[idx] ? '−' : '+' }}
              </button>
              <button class="btn danger compact" type="button" @click="emit('remove', idx)">Удалить</button>
            </div>
          </td>
        </tr>
        <tr v-if="hasKitItems(l) && expanded[idx]" class="kit-row">
          <td colspan="8">
            <div class="kit-editor">
              <p class="kit-title">Состав комплекта {{ l.sku || l.name }}</p>
              <table class="kit-table">
                <thead>
                  <tr>
                    <th>Запчасть</th>
                    <th>Цена</th>
                    <th>Qty</th>
                    <th>Сумма</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in l.kitItems" :key="item.partId">
                    <td><strong>{{ item.partSku || '—' }}</strong><p>{{ item.partName }}</p></td>
                    <td>{{ formatMoney(Number(item.partPrice || 0)) }}</td>
                    <td>
                      <input
                        :value="item.qty"
                        type="number"
                        min="1"
                        @change="setKitItemQty(l, item.partId, Number(($event.target as HTMLInputElement).value))"
                      />
                    </td>
                    <td>{{ formatMoney(Number(item.partPrice || 0) * item.qty) }}</td>
                    <td>
                      <button class="btn ghost compact danger-text" type="button" @click="removeKitItem(l, item.partId)">Убрать</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </td>
        </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.lines-wrap { border-radius: 12px; padding: 0.75rem; border: 1px solid var(--on-light-line); overflow-x: auto; }
.caption { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.75rem; }
.caption h3 { margin: 0; font-size: 1rem; }
.caption p { margin: 0; color: var(--on-light-muted); font-size: 0.82rem; }
.line-type { display: inline-flex; padding: 0.12rem 0.45rem; border-radius: 999px; font-size: 0.72rem; font-weight: 700; }
.line-type.KIT { background: #dbeafe; color: #0a4f92; }
.line-type.PART { background: #e5f4df; color: #2a5d24; }
.data-table { table-layout: fixed; min-width: 1040px; }
.data-table td { vertical-align: middle; overflow: hidden; }
.data-table .col-type { width: 70px; }
.data-table .col-sku { width: 120px; }
.data-table .col-name { width: auto; }
.data-table .col-qty { width: 130px; }
.data-table .col-price { width: 190px; }
.data-table .col-discount { width: 120px; }
.data-table .col-sum { width: 150px; }
.data-table .col-actions { width: 170px; }
.line-input, .price-input { width: 100%; min-width: 0; min-height: 34px; }
.price-input { text-align: left; }
.discount-select { width: 100%; min-width: 0; min-height: 34px; }
.qty-box { display: flex; align-items: center; justify-content: center; gap: 0.3rem; width: 100%; min-width: 0; }
.qty-box input {
  width: 52px;
  min-width: 0;
  min-height: 34px;
  text-align: center;
  padding: 0.25rem;
  appearance: textfield;
  -moz-appearance: textfield;
}
.qty-box input::-webkit-outer-spin-button,
.qty-box input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
.qty-btn { border: 1px solid #aebdcd; background: #ffffff; border-radius: 8px; width: 30px; height: 30px; font-weight: 700; color: #163b60; }
.qty-btn:hover { background: #eef3f9; border-color: #8ea4bb; }
.sum { font-weight: 700; text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
.actions { text-align: right; white-space: nowrap; }
.actions-controls { display: inline-flex; gap: 0.5rem; align-items: center; justify-content: flex-end; }
.kit-row td { background: #f2f6fb; }
.kit-editor { border: 1px dashed #9cb0c5; border-radius: 10px; padding: 0.75rem; background: #fbfdff; }
.kit-title { margin: 0 0 0.45rem; color: #31485f; font-size: 0.82rem; font-weight: 700; }
.kit-table { width: 100%; border-collapse: collapse; }
.kit-table th, .kit-table td { border-bottom: 1px solid var(--on-light-line); padding: 0.4rem; text-align: left; }
.kit-table th { font-size: 0.72rem; text-transform: uppercase; color: var(--on-light-muted); letter-spacing: 0.05em; }
.kit-table p { margin: 0.1rem 0 0; font-size: 0.8rem; color: #3f5972; }
.kit-table input { width: 72px; }
.danger-text { color: #8f1d1d; }
@media (max-width: 768px) {
  .caption { flex-direction: column; align-items: flex-start; gap: 0.2rem; }
  .lines-wrap { padding: 0.55rem; }
  .data-table { min-width: 920px; }
}
@media (max-width: 560px) {
  .actions-controls { gap: 0.25rem; }
}
</style>
