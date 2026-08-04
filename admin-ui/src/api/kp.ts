import api from './http'

export interface KpPriceComponent {
  name: string
  unitPrice: number
  qtyPerKit: number
}

export interface KpDroneModel {
  id: string
  code: string
  name: string
  /** Цена комплекта в прайсе КП (start_price). */
  defaultPrice: number
  /** Базовая цена дрона в прайсе КП. */
  dronePrice: number
  vatMode?: string
  components?: KpPriceComponent[]
  sortOrder: number
  active: boolean
  hasZipPackage?: boolean
}

export interface KpZipItem {
  id?: string
  name: string
  sku?: string | null
  qty: number
  unitPrice: number
  sortOrder?: number
}

export interface KpZipPackage {
  droneModelId: string
  name: string
  price: number | null
  items: KpZipItem[]
}

export interface KpProposal {
  id: string
  number: number
  managerUsername: string
  recipient: string
  droneModelName: string
  status: string
  grandTotal: number
  ndsTotal: number
  pdfPath?: string | null
}

export type DroneModelPayload = Omit<KpDroneModel, 'id' | 'hasZipPackage'>

export async function fetchDroneModels() {
  const { data } = await api.get('/admin/kp/drone-models')
  return data.data as KpDroneModel[]
}

export async function createDroneModel(payload: DroneModelPayload) {
  const { data } = await api.post('/admin/kp/drone-models', payload)
  return data.data as KpDroneModel
}

export async function updateDroneModel(id: string, payload: DroneModelPayload) {
  const { data } = await api.put(`/admin/kp/drone-models/${id}`, payload)
  return data.data as KpDroneModel
}

export async function deleteDroneModel(id: string) {
  await api.delete(`/admin/kp/drone-models/${id}`)
}

export async function fetchZipPackage(modelId: string) {
  const { data } = await api.get(`/admin/kp/drone-models/${modelId}/zip-package`)
  return data.data as KpZipPackage
}

export async function saveZipPackage(
  modelId: string,
  payload: { name: string; price: number | null; items: KpZipItem[] }
) {
  const { data } = await api.put(`/admin/kp/drone-models/${modelId}/zip-package`, payload)
  return data.data as KpZipPackage
}

export async function fetchAllProposals() {
  const { data } = await api.get('/admin/kp/proposals')
  return data.data as KpProposal[]
}

export async function downloadProposalPdf(id: string, filename = 'kp.pdf') {
  const res = await api.get(`/admin/kp/proposals/${id}/pdf`, { responseType: 'blob' })
  const name = filenameFromContentDisposition(res.headers['content-disposition']) || filename
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export function buildKpPdfFilename(droneModelName: string | null | undefined, number: number, date = new Date()) {
  const drone = String(droneModelName || 'дрон')
    .replace(/[\\/:*?"<>|]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  const dd = String(date.getDate()).padStart(2, '0')
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const yy = String(date.getFullYear()).slice(-2)
  return `Коммерческое предложение на ${drone} от компании ATRIS ${dd}.${mm}.${yy} КП №${number}.pdf`
}

function filenameFromContentDisposition(header?: string): string | null {
  if (!header) return null
  const utf8 = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(header)
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1].trim().replace(/^"|"$/g, ''))
    } catch {
      /* fall through */
    }
  }
  const plain = /filename\s*=\s*"([^"]+)"/i.exec(header) || /filename\s*=\s*([^;]+)/i.exec(header)
  return plain?.[1]?.trim() || null
}
