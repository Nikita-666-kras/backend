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

export interface MediaBatchUploadResult {
  uploaded: MediaAsset[]
  failed: number
  errors: string[]
}

export const MEDIA_UPLOAD_BATCH_SIZE = 25

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

export async function uploadMediaBatch(
  files: File[],
  section?: MediaSection | '',
  onProgress?: (pct: number) => void
) {
  const form = new FormData()
  for (const file of files) {
    form.append('files', file)
  }
  if (section) {
    form.append('section', section)
  }
  const { data } = await api.post('/admin/media/batch', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (e) => {
      if (!onProgress || !e.total) return
      onProgress(Math.round((e.loaded / e.total) * 100))
    }
  })
  return data.data as MediaBatchUploadResult
}

export async function deleteMedia(id: string) {
  await api.delete(`/admin/media/${id}`)
}

export async function moveMedia(id: string, section: MediaSection) {
  const { data } = await api.patch(`/admin/media/${id}/section`, { section })
  return data.data as MediaAsset
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
  const raw = String(idOrUrl).trim()
  const UUID_RE = /[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i

  let path = raw
  if (path.startsWith('http://')) {
    path = `https://${path.slice('http://'.length)}`
  } else if (!path.startsWith('https://')) {
    const uuidMatch = path.match(UUID_RE)
    const uuid = uuidMatch ? uuidMatch[0] : ''
    if (uuid) {
      path = `/media/${uuid}`
    } else {
      path = path.startsWith('/media/') ? path : `/media/${path}`
    }
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
