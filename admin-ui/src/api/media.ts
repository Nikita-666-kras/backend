import api from '@/api/http'

export type MediaKind = 'IMAGE' | 'VIDEO'

export interface MediaAsset {
  id: string
  originalName: string
  contentType: string
  sizeBytes: number
  kind: MediaKind
  url: string
  uploadedBy: string
  createdAt: string
}

export interface MediaPage {
  content: MediaAsset[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export async function fetchMedia(params: { kind?: string; q?: string; page?: number; size?: number }) {
  const { data } = await api.get('/admin/media', { params })
  return data.data as MediaPage
}

export async function uploadMedia(file: File, onProgress?: (pct: number) => void) {
  const form = new FormData()
  form.append('file', file)
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

export function mediaPublicUrl(idOrUrl: string | null | undefined) {
  if (!idOrUrl) return ''
  let path = idOrUrl
  if (path.startsWith('http://')) {
    path = `https://${path.slice('http://'.length)}`
  } else if (!path.startsWith('https://')) {
    path = path.startsWith('/media/') ? path : `/media/${path}`
    path = `${window.location.origin}${path}`
  }
  return path
}
