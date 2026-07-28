<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { downloadPdf, finalizeProposal, kits, login, models, myProposals, parts, saveProposal } from './api'

type Line = { lineType: 'KIT' | 'PART'; refId: string; sku: string; name: string; qty: number; unitPrice: number; discountPct: number }

const isAuth = ref(false)
const user = ref('')
const pass = ref('')
const recipient = ref('')
const modelId = ref('')
const dronePrice = ref(0)
const modelsList = ref<any[]>([])
const kitQ = ref('')
const partQ = ref('')
const kitItems = ref<any[]>([])
const partItems = ref<any[]>([])
const lines = ref<Line[]>([])
const proposals = ref<any[]>([])
const currentProposalId = ref('')

const total = computed(() => {
  const linesTotal = lines.value.reduce((sum, l) => {
    const mult = l.lineType === 'KIT' ? (100 - l.discountPct) / 100 : 1
    return sum + l.unitPrice * l.qty * mult
  }, 0)
  return dronePrice.value + linesTotal
})

async function doLogin() {
  await login(user.value, pass.value)
  isAuth.value = true
  await loadBase()
}
async function loadBase() {
  modelsList.value = await models()
  await searchKits()
  await searchParts()
  proposals.value = await myProposals()
  if (modelsList.value.length && !modelId.value) {
    modelId.value = modelsList.value[0].id
    dronePrice.value = Number(modelsList.value[0].defaultPrice)
  }
}
async function searchKits() { kitItems.value = await kits(kitQ.value) }
async function searchParts() { partItems.value = await parts(partQ.value) }
function addKit(item: any) { lines.value.push({ lineType: 'KIT', refId: item.id, sku: item.sku, name: item.name, qty: 1, unitPrice: Number(item.price), discountPct: 0 }) }
function addPart(item: any) { lines.value.push({ lineType: 'PART', refId: item.id, sku: item.sku, name: item.name, qty: 1, unitPrice: Number(item.price), discountPct: 0 }) }
async function saveDraft() {
  const p = await saveProposal({ recipient: recipient.value, droneModelId: modelId.value, dronePrice: dronePrice.value, lines: lines.value })
  currentProposalId.value = p.id
  proposals.value = await myProposals()
}
async function finalize() {
  if (!currentProposalId.value) {
    await saveDraft()
  }
  if (!currentProposalId.value) return
  const p = await finalizeProposal(currentProposalId.value)
  proposals.value = await myProposals()
  await downloadPdf(p.id, `KP_${p.number}.pdf`)
}

async function downloadProposalPdf(p: any) {
  try {
    let id = p.id
    if (p.status !== 'FINAL') {
      const finalized = await finalizeProposal(id)
      id = finalized.id
      proposals.value = await myProposals()
    }
    await downloadPdf(id, `KP_${p.number}.pdf`)
  } catch (e: any) {
    alert(e?.response?.data?.message || 'Не удалось скачать PDF')
  }
}
onMounted(async () => { try { await loadBase(); isAuth.value = true } catch { isAuth.value = false } })
</script>

<template>
  <main class="app">
    <h1>АТРИС · Калькулятор КП</h1>
    <div v-if="!isAuth" class="card row">
      <input v-model="user" placeholder="Логин" />
      <input v-model="pass" placeholder="Пароль" type="password" />
      <button @click="doLogin">Войти</button>
    </div>
    <template v-else>
      <div class="card row">
        <input v-model="recipient" placeholder="Для кого" />
        <select v-model="modelId" @change="dronePrice = Number(modelsList.find((m) => m.id === modelId)?.defaultPrice || 0)">
          <option v-for="m in modelsList" :key="m.id" :value="m.id">{{ m.name }}</option>
        </select>
        <input v-model.number="dronePrice" type="number" min="0" step="0.01" />
      </div>
      <div class="card">
        <div class="row">
          <input v-model="kitQ" placeholder="Поиск комплекта" @keyup.enter="searchKits" />
          <button class="secondary" @click="searchKits">Найти</button>
          <input v-model="partQ" placeholder="Поиск запчасти" @keyup.enter="searchParts" />
          <button class="secondary" @click="searchParts">Найти</button>
        </div>
        <div class="row">
          <select @change="addKit(kitItems.find((k) => k.id === ($event.target as HTMLSelectElement).value))">
            <option value="">Добавить комплект…</option>
            <option v-for="k in kitItems" :key="k.id" :value="k.id">{{ k.sku }} · {{ k.name }} · {{ k.price }}</option>
          </select>
          <select @change="addPart(partItems.find((p) => p.id === ($event.target as HTMLSelectElement).value))">
            <option value="">Добавить запчасть…</option>
            <option v-for="p in partItems" :key="p.id" :value="p.id">{{ p.sku }} · {{ p.name }} · {{ p.price }}</option>
          </select>
        </div>
      </div>
      <div class="card">
        <table>
          <thead><tr><th>Тип</th><th>SKU</th><th>Название</th><th>Qty</th><th>Цена</th><th>Скидка kit</th></tr></thead>
          <tbody>
            <tr v-for="(l, idx) in lines" :key="idx">
              <td>{{ l.lineType }}</td><td>{{ l.sku }}</td><td>{{ l.name }}</td>
              <td><input v-model.number="l.qty" type="number" min="1" /></td>
              <td><input v-model.number="l.unitPrice" type="number" min="0" step="0.01" /></td>
              <td>
                <select v-model.number="l.discountPct" :disabled="l.lineType !== 'KIT'">
                  <option :value="0">0%</option><option :value="5">5%</option><option :value="10">10%</option><option :value="15">15%</option><option :value="20">20%</option>
                </select>
              </td>
            </tr>
          </tbody>
        </table>
        <p><b>Итого:</b> {{ total.toFixed(2) }}</p>
        <div class="row">
          <button @click="saveDraft">Сохранить черновик</button>
          <button class="secondary" @click="finalize">Сформировать PDF</button>
        </div>
      </div>
      <div class="card">
        <h3>Мои КП</h3>
        <table>
          <thead><tr><th>№</th><th>Кому</th><th>Модель</th><th>Статус</th><th>PDF</th></tr></thead>
          <tbody>
            <tr v-for="p in proposals" :key="p.id">
              <td>{{ p.number }}</td><td>{{ p.recipient }}</td><td>{{ p.droneModelName }}</td><td>{{ p.status }}</td>
              <td><button class="secondary" type="button" @click="downloadProposalPdf(p)">Скачать</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </main>
</template>
