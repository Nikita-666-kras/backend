import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ColumnMapping, ImportApplyResult, ImportPreview } from '@/api/parts'

export const useImportStore = defineStore('partsImport', () => {
  const fileName = ref('')
  const preview = ref<ImportPreview | null>(null)
  const mapping = ref<ColumnMapping[]>([])
  const createMissingDrones = ref(true)
  const createMissingCategories = ref(true)
  const attachToKits = ref(true)
  const defaultStatus = ref<'DRAFT' | 'PUBLISHED'>('DRAFT')
  const result = ref<ImportApplyResult | null>(null)

  function setPreview(next: ImportPreview, name: string) {
    preview.value = next
    fileName.value = name
    mapping.value = next.suggestedMapping.map((m) => ({ ...m }))
    result.value = null
  }

  function reset() {
    fileName.value = ''
    preview.value = null
    mapping.value = []
    result.value = null
  }

  return {
    fileName,
    preview,
    mapping,
    createMissingDrones,
    createMissingCategories,
    attachToKits,
    defaultStatus,
    result,
    setPreview,
    reset
  }
})
