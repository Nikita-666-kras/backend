import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_KEY = 'parts-catalog-filters-v2'

interface Filters {
  q: string
  status: string
  droneId: string
  categoryId: string
  pageSize: number
  columns: 1 | 2
}

function read(): Filters {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return { q: '', status: '', droneId: '', categoryId: '', pageSize: 50, columns: 1 }
    }
    const parsed = JSON.parse(raw) as Partial<Filters>
    const pageSize = [10, 50, 100, 500].includes(Number(parsed.pageSize)) ? Number(parsed.pageSize) : 50
    const columns = parsed.columns === 2 ? 2 : 1
    return {
      q: parsed.q ?? '',
      status: parsed.status ?? '',
      droneId: parsed.droneId ?? '',
      categoryId: parsed.categoryId ?? '',
      pageSize,
      columns
    }
  } catch {
    return { q: '', status: '', droneId: '', categoryId: '', pageSize: 50, columns: 1 }
  }
}

export const usePartsCatalogStore = defineStore('partsCatalog', () => {
  const initial = read()
  const q = ref(initial.q)
  const status = ref(initial.status)
  const droneId = ref(initial.droneId)
  const categoryId = ref(initial.categoryId)
  const page = ref(0)
  const pageSize = ref(initial.pageSize)
  const columns = ref<1 | 2>(initial.columns)

  function persist() {
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        q: q.value,
        status: status.value,
        droneId: droneId.value,
        categoryId: categoryId.value,
        pageSize: pageSize.value,
        columns: columns.value
      })
    )
  }

  return { q, status, droneId, categoryId, page, pageSize, columns, persist }
})
