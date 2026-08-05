import api from '@/api/http'

export type CatalogStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type KitPriceMode = 'MANUAL' | 'SUM'

export interface PartCategory {
  id: string
  name: string
  slug: string
  parentId?: string | null
  sortOrder: number
}

export interface Drone {
  id: string
  name: string
  slug: string
  description?: string | null
  imageMediaId?: string | null
  imageUrl?: string | null
  status: CatalogStatus
  sortOrder: number
}

export interface Part {
  id: string
  name: string
  sku: string
  description?: string | null
  price: number | null
  currency: string
  droneId?: string | null
  droneName?: string | null
  categoryId?: string | null
  categoryName?: string | null
  coverMediaId?: string | null
  coverUrl?: string | null
  mediaIds: string[]
  mediaUrls: string[]
  status: CatalogStatus
  sortOrder: number
  externalSource?: string
  externalId?: string | null
  createdAt: string
  updatedAt: string
}

export interface KitItem {
  partId: string
  partSku: string
  partName: string
  qty: number
  partPrice: number
}

export interface Kit {
  id: string
  name: string
  sku: string
  description?: string | null
  price: number
  currency: string
  priceMode: KitPriceMode
  droneId?: string | null
  droneName?: string | null
  coverMediaId?: string | null
  coverUrl?: string | null
  mediaIds: string[]
  mediaUrls: string[]
  items: KitItem[]
  status: CatalogStatus
  sortOrder: number
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface PartPayload {
  name: string
  sku: string
  description?: string
  price: number | null
  currency?: string
  droneId?: string | null
  categoryId?: string | null
  coverMediaId?: string | null
  mediaIds?: string[]
  status?: CatalogStatus
  sortOrder?: number
  externalSource?: string
  externalId?: string | null
}

export interface KitPayload {
  name: string
  sku: string
  description?: string
  price: number | null
  currency?: string
  priceMode?: KitPriceMode
  droneId?: string | null
  coverMediaId?: string | null
  mediaIds?: string[]
  items: Array<{ partId: string; qty: number }>
  status?: CatalogStatus
  sortOrder?: number
}

export interface DronePayload {
  name: string
  slug?: string
  description?: string
  imageMediaId?: string | null
  status?: CatalogStatus
  sortOrder?: number
}

export type PartCatalogFilter =
  | 'NO_PRICE'
  | 'NO_NAME'
  | 'NO_PHOTO'
  | 'NO_DRONE'
  | 'NO_CATEGORY'
  | 'INCOMPLETE'

export async function fetchParts(params: {
  q?: string
  status?: string
  droneId?: string
  categoryId?: string
  catalogFilter?: PartCatalogFilter | ''
  page?: number
  size?: number
}) {
  const { data } = await api.get('/admin/parts', { params })
  return data.data as PageResponse<Part>
}

export async function fetchPart(id: string) {
  const { data } = await api.get(`/admin/parts/${id}`)
  return data.data as Part
}

export async function createPart(payload: PartPayload) {
  const { data } = await api.post('/admin/parts', payload)
  return data.data as Part
}

export async function updatePart(id: string, payload: PartPayload) {
  const { data } = await api.put(`/admin/parts/${id}`, payload)
  return data.data as Part
}

export async function publishPart(id: string) {
  const { data } = await api.post(`/admin/parts/${id}/publish`)
  return data.data as Part
}

export async function archivePart(id: string) {
  const { data } = await api.post(`/admin/parts/${id}/archive`)
  return data.data as Part
}

export async function deletePart(id: string) {
  await api.delete(`/admin/parts/${id}`)
}

export async function bulkParts(ids: string[], action: 'PUBLISH' | 'ARCHIVE' | 'DELETE') {
  const { data } = await api.post('/admin/parts/bulk', { ids, action })
  return data.data as { success: number; failed: number; errors: string[] }
}

export async function fetchKits(params: { q?: string; status?: string; droneId?: string; page?: number; size?: number }) {
  const { data } = await api.get('/admin/kits', { params })
  return data.data as PageResponse<Kit>
}

export async function fetchKit(id: string) {
  const { data } = await api.get(`/admin/kits/${id}`)
  return data.data as Kit
}

export async function createKit(payload: KitPayload) {
  const { data } = await api.post('/admin/kits', payload)
  return data.data as Kit
}

export async function updateKit(id: string, payload: KitPayload) {
  const { data } = await api.put(`/admin/kits/${id}`, payload)
  return data.data as Kit
}

export async function publishKit(id: string) {
  const { data } = await api.post(`/admin/kits/${id}/publish`)
  return data.data as Kit
}

export async function deleteKit(id: string) {
  await api.delete(`/admin/kits/${id}`)
}

export async function fetchDrones(params: { q?: string; status?: string; page?: number; size?: number } = {}) {
  const { data } = await api.get('/admin/drones', { params })
  return data.data as PageResponse<Drone>
}

export async function createDrone(payload: DronePayload) {
  const { data } = await api.post('/admin/drones', payload)
  return data.data as Drone
}

export async function updateDrone(id: string, payload: DronePayload) {
  const { data } = await api.put(`/admin/drones/${id}`, payload)
  return data.data as Drone
}

export async function publishDrone(id: string) {
  const { data } = await api.post(`/admin/drones/${id}/publish`)
  return data.data as Drone
}

export async function deleteDrone(id: string) {
  await api.delete(`/admin/drones/${id}`)
}

export async function fetchPartCategories() {
  const { data } = await api.get('/admin/part-categories')
  return data.data as PartCategory[]
}

export async function createPartCategory(payload: { name: string; slug?: string; parentId?: string | null; sortOrder?: number }) {
  const { data } = await api.post('/admin/part-categories', payload)
  return data.data as PartCategory
}

export async function updatePartCategory(
  id: string,
  payload: { name: string; slug?: string; parentId?: string | null; sortOrder?: number }
) {
  const { data } = await api.put(`/admin/part-categories/${id}`, payload)
  return data.data as PartCategory
}

export async function deletePartCategory(id: string) {
  await api.delete(`/admin/part-categories/${id}`)
}

export function formatPrice(value: number | null | undefined, currency = 'RUB') {
  if (value == null || Number.isNaN(value)) return '—'
  return new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency,
    maximumFractionDigits: 2
  }).format(value)
}

export type ImportTargetField =
  | 'SKIP'
  | 'SKU'
  | 'NAME'
  | 'PRICE'
  | 'DESCRIPTION'
  | 'DRONE'
  | 'CATEGORY'
  | 'KIT_SKU'
  | 'EXTERNAL_ID'
  | 'BARCODE'

export interface ColumnMapping {
  sourceColumn: string
  targetField: ImportTargetField
}

export interface ImportPreview {
  format: string
  headers: string[]
  suggestedMapping: ColumnMapping[]
  sampleRows: Record<string, string>[]
  totalRows: number
  stats: { valid: number; toCreate: number; toUpdate: number; invalid: number; withoutPrice: number; withoutName: number }
  issues: Array<{ rowNumber: number; message: string }>
}

export interface ImportApplyResult {
  created: number
  updated: number
  skipped: number
  kitsTouched: number
  errors: Array<{ rowNumber: number; message: string }>
}

export interface ImportOptions {
  mapping: ColumnMapping[]
  createMissingDrones: boolean
  createMissingCategories: boolean
  attachToKits: boolean
  defaultStatus: CatalogStatus
}

export async function previewPartsImport(file: File) {
  const form = new FormData()
  form.append('file', file)
  const { data } = await api.post('/admin/parts/import/preview', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return data.data as ImportPreview
}

export async function applyPartsImport(file: File, options: ImportOptions) {
  const form = new FormData()
  form.append('file', file)
  form.append('options', new Blob([JSON.stringify(options)], { type: 'application/json' }))
  const { data } = await api.post('/admin/parts/import/apply', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return data.data as ImportApplyResult
}

export function importTemplateUrl() {
  return '/admin/parts/import/template.csv'
}

