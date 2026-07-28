import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { MediaSection } from '@/api/media'

const FILTERS_KEY = 'media-library-filters-v2'
const WORKFLOW_KEY = 'media-library-workflow-v1'

interface StoredFilters {
  kind: string
  section: MediaSection | ''
  q: string
  pageSize: number
}

interface StoredWorkflow {
  autoSquare: boolean
  autoWatermark: boolean
  autoWebp: boolean
  squareBackground: string
  watermarkOpacity: number
  bgThreshold: number
  uploadSection: MediaSection
}

function readFilters(): StoredFilters {
  try {
    const raw = sessionStorage.getItem(FILTERS_KEY)
    if (!raw) return { kind: 'IMAGE', section: 'PARTS', q: '', pageSize: 48 }
    const parsed = JSON.parse(raw) as Partial<StoredFilters>
    return {
      kind: parsed.kind ?? 'IMAGE',
      section: parsed.section ?? 'PARTS',
      q: parsed.q ?? '',
      pageSize: parsed.pageSize ?? 48
    }
  } catch {
    return { kind: 'IMAGE', section: 'PARTS', q: '', pageSize: 48 }
  }
}

function readWorkflow(): StoredWorkflow {
  try {
    const raw = localStorage.getItem(WORKFLOW_KEY)
    if (!raw) {
      return {
        autoSquare: true,
        autoWatermark: true,
        autoWebp: true,
        squareBackground: '#ffffff',
        watermarkOpacity: 0.15,
        bgThreshold: 40,
        uploadSection: 'PARTS'
      }
    }
    const parsed = JSON.parse(raw) as Partial<StoredWorkflow>
    return {
      autoSquare: parsed.autoSquare ?? true,
      autoWatermark: parsed.autoWatermark ?? true,
      autoWebp: parsed.autoWebp ?? true,
      squareBackground: parsed.squareBackground ?? '#ffffff',
      watermarkOpacity: parsed.watermarkOpacity ?? 0.15,
      bgThreshold: parsed.bgThreshold ?? 40,
      uploadSection: parsed.uploadSection ?? 'PARTS'
    }
  } catch {
    return {
      autoSquare: true,
      autoWatermark: true,
      autoWebp: true,
      squareBackground: '#ffffff',
      watermarkOpacity: 0.15,
      bgThreshold: 40,
      uploadSection: 'PARTS'
    }
  }
}

export const useMediaLibraryStore = defineStore('mediaLibrary', () => {
  const initial = readFilters()
  const workflow = readWorkflow()

  const kind = ref(initial.kind)
  const section = ref<MediaSection | ''>(initial.section)
  const q = ref(initial.q)
  const page = ref(0)
  const pageSize = ref(initial.pageSize)

  const autoSquare = ref(workflow.autoSquare)
  const autoWatermark = ref(workflow.autoWatermark)
  const autoWebp = ref(workflow.autoWebp)
  const squareBackground = ref(workflow.squareBackground)
  const watermarkOpacity = ref(workflow.watermarkOpacity)
  const bgThreshold = ref(workflow.bgThreshold)
  const uploadSection = ref<MediaSection>(workflow.uploadSection)

  function persistFilters() {
    sessionStorage.setItem(
      FILTERS_KEY,
      JSON.stringify({
        kind: kind.value,
        section: section.value,
        q: q.value,
        pageSize: pageSize.value
      })
    )
  }

  function persistWorkflow() {
    localStorage.setItem(
      WORKFLOW_KEY,
      JSON.stringify({
        autoSquare: autoSquare.value,
        autoWatermark: autoWatermark.value,
        autoWebp: autoWebp.value,
        squareBackground: squareBackground.value,
        watermarkOpacity: watermarkOpacity.value,
        bgThreshold: bgThreshold.value,
        uploadSection: uploadSection.value
      })
    )
  }

  return {
    kind,
    section,
    q,
    page,
    pageSize,
    autoSquare,
    autoWatermark,
    autoWebp,
    squareBackground,
    watermarkOpacity,
    bgThreshold,
    uploadSection,
    persistFilters,
    persistWorkflow
  }
})
