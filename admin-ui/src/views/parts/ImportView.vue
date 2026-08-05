<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import api from '@/api/http'
import {
  applyPartsImport,
  importTemplateUrl,
  previewPartsImport,
  type ImportTargetField
} from '@/api/parts'
import { useImportStore } from '@/stores/import'

const store = useImportStore()
const {
  fileName,
  preview,
  mapping,
  createMissingDrones,
  createMissingCategories,
  attachToKits,
  defaultStatus,
  result
} = storeToRefs(store)

const file = ref<File | null>(null)
const loading = ref(false)
const applying = ref(false)
const error = ref('')
const message = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

const targetOptions: Array<{ value: ImportTargetField; label: string }> = [
  { value: 'SKIP', label: '— пропустить —' },
  { value: 'SKU', label: 'Артикул запчасти' },
  { value: 'NAME', label: 'Название' },
  { value: 'PRICE', label: 'Цена' },
  { value: 'DRONE', label: 'Дрон' },
  { value: 'CATEGORY', label: 'Категория' },
  { value: 'KIT_SKU', label: 'Артикул комплекта' },
  { value: 'DESCRIPTION', label: 'Описание' },
  { value: 'EXTERNAL_ID', label: 'Внешний ID (GBS UID)' },
  { value: 'BARCODE', label: 'Штрихкод (fallback артикула)' }
]

const formatLabel = computed(() => {
  switch (preview.value?.format) {
    case 'CSV':
      return 'CSV'
    case 'XLSX':
      return 'Excel'
    case 'GBS_JSON':
      return 'GBS.Market JSON'
    case 'JSON':
      return 'JSON'
    default:
      return preview.value?.format || ''
  }
})

async function onFiles(files: FileList | null) {
  if (!files?.length) return
  file.value = files[0]
  loading.value = true
  error.value = ''
  message.value = ''
  result.value = null
  try {
    const data = await previewPartsImport(files[0])
    store.setPreview(data, files[0].name)
    message.value = `Файл распознан: ${formatLabel.value || data.format}`
  } catch (e: any) {
    store.reset()
    file.value = null
    error.value = e?.response?.data?.message || e?.message || 'Не удалось разобрать файл'
  } finally {
    loading.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  onFiles(e.dataTransfer?.files ?? null)
}

async function downloadTemplate() {
  try {
    const { data } = await api.get(importTemplateUrl(), { responseType: 'blob' })
    const url = URL.createObjectURL(data)
    const a = document.createElement('a')
    a.href = url
    a.download = 'parts-import-template.csv'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Не удалось скачать шаблон'
  }
}

async function applyImport() {
  if (!file.value || !preview.value) return
  applying.value = true
  error.value = ''
  message.value = ''
  try {
    const data = await applyPartsImport(file.value, {
      mapping: mapping.value,
      createMissingDrones: createMissingDrones.value,
      createMissingCategories: createMissingCategories.value,
      attachToKits: attachToKits.value,
      defaultStatus: defaultStatus.value
    })
    result.value = data
    message.value = `Импорт готов: +${data.created} · ~${data.updated} · пропуск ${data.skipped}`
  } catch (e: any) {
    error.value = e?.response?.data?.message || e?.message || 'Ошибка импорта'
  } finally {
    applying.value = false
  }
}

function resetAll() {
  store.reset()
  file.value = null
  error.value = ''
  message.value = ''
}
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <p class="eyebrow">Витрина</p>
        <h1>Импорт запчастей</h1>
        <p class="muted">CSV, Excel или JSON из GBS.Market 6 — с предпросмотром и маппингом колонок</p>
      </div>
      <div class="actions">
        <button class="btn secondary" type="button" @click="downloadTemplate">Скачать шаблон CSV</button>
        <button v-if="preview" class="btn secondary" type="button" @click="resetAll">Сбросить</button>
      </div>
    </header>

    <div class="drop card" @dragover.prevent @drop="onDrop">
      <p>Перетащите файл сюда или выберите</p>
      <p class="muted tiny">.csv · .xlsx · .json (в т.ч. выгрузка GBS.Market)</p>
      <label class="btn">
        {{ loading ? 'Чтение…' : 'Выбрать файл' }}
        <input
          ref="fileInput"
          type="file"
          accept=".csv,.xlsx,.xls,.json,text/csv,application/json,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          hidden
          :disabled="loading || applying"
          @change="onFiles(($event.target as HTMLInputElement).files)"
        />
      </label>
      <p v-if="fileName" class="file-name">{{ fileName }} · {{ formatLabel }}</p>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="ok">{{ message }}</p>

    <template v-if="preview">
      <div class="stats card">
        <div><strong>{{ preview.totalRows }}</strong><span>строк</span></div>
        <div><strong>{{ preview.stats.toCreate }}</strong><span>новых</span></div>
        <div><strong>{{ preview.stats.toUpdate }}</strong><span>обновлений</span></div>
        <div><strong>{{ preview.stats.withoutPrice }}</strong><span>без цены</span></div>
        <div><strong>{{ preview.stats.withoutName }}</strong><span>без названия</span></div>
        <div><strong>{{ preview.stats.invalid }}</strong><span>ошибок</span></div>
      </div>

      <div class="layout">
        <div class="card panel">
          <h3>Маппинг колонок</h3>
          <div class="map-table">
            <div v-for="(col, idx) in mapping" :key="col.sourceColumn" class="map-row">
              <code>{{ col.sourceColumn }}</code>
              <select v-model="mapping[idx].targetField">
                <option v-for="opt in targetOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </select>
            </div>
          </div>

          <div class="options">
            <label><input v-model="createMissingDrones" type="checkbox" /> Создавать дроны по имени</label>
            <label><input v-model="createMissingCategories" type="checkbox" /> Создавать категории</label>
            <label><input v-model="attachToKits" type="checkbox" /> Привязывать к комплектам по артикулу</label>
            <label>
              Статус новых
              <select v-model="defaultStatus">
                <option value="DRAFT">Черновик</option>
                <option value="PUBLISHED">Сразу на сайт</option>
              </select>
            </label>
          </div>

          <button class="btn" :disabled="applying" @click="applyImport">
            {{ applying ? 'Импорт…' : 'Импортировать' }}
          </button>
        </div>

        <div class="card panel">
          <h3>Предпросмотр (до 8 строк)</h3>
          <div class="preview-scroll">
            <table>
              <thead>
                <tr>
                  <th v-for="h in preview.headers" :key="h">{{ h }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in preview.sampleRows" :key="i">
                  <td v-for="h in preview.headers" :key="h">{{ row[h] }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="preview.issues.length" class="issues">
            <h4>Проблемы предпросмотра</h4>
            <p v-for="issue in preview.issues" :key="issue.rowNumber + issue.message" class="muted tiny">
              Строка {{ issue.rowNumber }}: {{ issue.message }}
            </p>
          </div>
        </div>
      </div>
    </template>

    <div v-if="result" class="card result">
      <h3>Результат</h3>
      <p>
        Создано <strong>{{ result.created }}</strong>,
        обновлено <strong>{{ result.updated }}</strong>,
        пропущено <strong>{{ result.skipped }}</strong>,
        комплектов затронуто <strong>{{ result.kitsTouched }}</strong>
      </p>
      <div v-if="result.errors.length" class="issues">
        <p v-for="err in result.errors" :key="err.rowNumber + err.message" class="error tiny">
          Строка {{ err.rowNumber }}: {{ err.message }}
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.actions { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.drop {
  padding: 1.5rem;
  text-align: center;
  border-style: dashed;
  display: grid;
  gap: 0.55rem;
  justify-items: center;
  margin-bottom: 1rem;
}
.file-name { margin: 0; font-size: 0.9rem; }
.tiny { font-size: 0.82rem; }
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.75rem;
  margin-bottom: 1rem;
  padding: 0.9rem 1rem;
}
.stats div { display: grid; gap: 0.15rem; }
.stats span { color: var(--muted); font-size: 0.85rem; }
.layout { display: grid; grid-template-columns: 1fr 1.2fr; gap: 1rem; align-items: start; }
.panel { padding: 1rem; display: grid; gap: 0.85rem; }
.panel h3, .panel h4 { margin: 0; font-size: 0.95rem; }
.map-table { display: grid; gap: 0.45rem; }
.map-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
  align-items: center;
  padding: 0.45rem 0.55rem;
  border-radius: 8px;
  background: var(--on-light-bg, #f4f6f8);
  color: var(--on-light-ink, #111);
  color-scheme: light;
}
.map-row code {
  color: var(--on-light-ink, #111);
  font-size: 0.85rem;
  word-break: break-all;
}
.map-row select {
  color: var(--on-light-ink, #111);
  background: #fff;
  border: 1px solid var(--on-light-line, #c5ced8);
  border-radius: 8px;
  padding: 0.45rem 0.55rem;
  color-scheme: light;
}
.options { display: grid; gap: 0.45rem; }
.options label { display: flex; gap: 0.45rem; align-items: center; flex-wrap: wrap; }
.preview-scroll { overflow: auto; max-height: 360px; }
table { width: 100%; border-collapse: collapse; font-size: 0.82rem; }
th, td { border-bottom: 1px solid var(--line, #d8e3df); padding: 0.4rem 0.5rem; text-align: left; white-space: nowrap; }
.issues { display: grid; gap: 0.2rem; }
.result { padding: 1rem; margin-top: 1rem; }
@media (max-width: 960px) {
  .layout, .stats, .map-row { grid-template-columns: 1fr; }
}
</style>
