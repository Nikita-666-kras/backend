import api from '@/api/http'

export type MediaKind = 'IMAGE' | 'VIDEO'
export type MediaSection = 'PARTS' | 'DRONES' | 'ARTICLES' | 'SERVICE' | 'TRAILERS' | 'EDUCATION' | 'OTHER'

export interface MediaAsset {
  id: string
  originalName: string
  contentType: string
  sizeBytes: number
  kind: MediaKind
  section: MediaSection
  url: string
  uploadedBy: string
  createdAt: string
  updatedAt: string
  square: boolean
  watermark: boolean
}

export interface MediaPage {
  content: MediaAsset[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface MediaProcessOptions {
  square?: boolean
  watermark?: boolean
  convertToWebp?: boolean
  backgroundColor?: string
  opacity?: number
  bgThreshold?: number
}

export interface MediaBatchProcessResult {
  processed: number
  failed: number
  errors: string[]
}

export interface ProcessingSettings {
  squareBackground: string
  logoPath: string
  opacity: number
  bgThreshold: number
  logoAvailable: boolean
}

export async function fetchMedia(params: {
  kind?: string
  section?: MediaSection | ''
  q?: string
  square?: boolean
  watermark?: boolean
  incomplete?: boolean
  page?: number
  size?: number
}) {
  const { data } = await api.get('/admin/media', { params })
  return data.data as MediaPage
}

export async function uploadMedia(file: File, section?: MediaSection | '', onProgress?: (pct: number) => void) {
  const form = new FormData()
  form.append('file', file)
  if (section) {
    form.append('section', section)
  }
  const { data } = await api.post('/admin/media', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (e) => {
      if (!onProgress || !e.total) return
      onProgress(Math.round((e.loaded / e.total) * 100))
    }
  })
  return data.data as MediaAsset
}

export async function deleteMedia(id: string) {
  await api.delete(`/admin/media/${id}`)
}

export async function processMedia(id: string, options: MediaProcessOptions) {
  const { data } = await api.post(`/admin/media/${id}/process`, {
    square: !!options.square,
    watermark: !!options.watermark,
    convertToWebp: !!options.convertToWebp,
    backgroundColor: options.backgroundColor,
    opacity: options.opacity,
    bgThreshold: options.bgThreshold
  })
  return data.data as MediaAsset
}

export async function processMediaBatch(ids: string[], options: MediaProcessOptions) {
  const { data } = await api.post('/admin/media/process-batch', {
    ids,
    square: !!options.square,
    watermark: !!options.watermark,
    convertToWebp: !!options.convertToWebp,
    backgroundColor: options.backgroundColor,
    opacity: options.opacity,
    bgThreshold: options.bgThreshold
  })
  return data.data as MediaBatchProcessResult
}

export async function fetchProcessingSettings() {
  const { data } = await api.get('/admin/media/processing-settings')
  return data.data as ProcessingSettings
}

export function mediaPublicUrl(idOrUrl: string | null | undefined, cacheBust?: string | number) {
  if (!idOrUrl) return ''
  let path = idOrUrl
  if (path.startsWith('http://')) {
    path = `https://${path.slice('http://'.length)}`
  } else if (!path.startsWith('https://')) {
    path = path.startsWith('/media/') ? path : `/media/${path}`
    path = `${window.location.origin}${path}`
  }
  if (cacheBust !== undefined && cacheBust !== '') {
    const sep = path.includes('?') ? '&' : '?'
    path = `${path}${sep}v=${encodeURIComponent(String(cacheBust))}`
  }
  return path
}

export function isProcessableImage(item: Pick<MediaAsset, 'kind' | 'contentType'>) {
  if (item.kind !== 'IMAGE') return false
  const type = item.contentType?.toLowerCase() || ''
  return (
    type === 'image/jpeg' ||
    type === 'image/png' ||
    type === 'image/webp' ||
    type === 'image/gif' ||
    type === 'image/tiff' ||
    type === 'image/tif'
  )
}

export function isWebp(item: Pick<MediaAsset, 'contentType'>) {
  return item.contentType?.toLowerCase() === 'image/webp'
}
