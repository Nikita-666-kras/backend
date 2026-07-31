import api from '@/api/http'
import type {
  CalcPreview,
  CatalogItem,
  DroneModel,
  KitCatalogDetail,
  KitPreset,
  Proposal,
  ProposalUpsertRequest,
  ZipPackage
} from '@/types/kp'

export async function listModels(): Promise<DroneModel[]> {
  const { data } = await api.get('/manager/kp/drone-models')
  return data.data
}

export async function fetchZipPackage(modelId: string): Promise<ZipPackage> {
  const { data } = await api.get(`/manager/kp/drone-models/${modelId}/zip-package`)
  return data.data
}

export async function kitPreset(modelId: string): Promise<KitPreset> {
  const { data } = await api.get('/manager/kp/kit-preset', { params: { modelId } })
  return data.data
}

export async function calculateKp(payload: {
  droneModelId: string
  kitQty: number
  unitKitPrice: number
  droneVatPct?: 0 | 22
}): Promise<CalcPreview> {
  const { data } = await api.post('/manager/kp/calculate', payload)
  return data.data
}

export async function searchKits(q = ''): Promise<CatalogItem[]> {
  const { data } = await api.get('/manager/kp/catalog/kits', { params: { q, page: 0, size: 100 } })
  return data.data
}

export async function searchParts(q = ''): Promise<CatalogItem[]> {
  const { data } = await api.get('/manager/kp/catalog/parts', { params: { q, page: 0, size: 100 } })
  return data.data
}

export async function getKitDetail(id: string): Promise<KitCatalogDetail> {
  const { data } = await api.get(`/manager/kp/catalog/kits/${id}`)
  return data.data
}

export async function createProposal(payload: ProposalUpsertRequest): Promise<Proposal> {
  const { data } = await api.post('/manager/kp/proposals', payload)
  return data.data
}

export async function createFromPreset(recipient: string, droneModelId: string): Promise<Proposal> {
  const { data } = await api.post('/manager/kp/proposals/from-preset', { recipient, droneModelId })
  return data.data
}

export async function updateProposal(id: string, payload: ProposalUpsertRequest): Promise<Proposal> {
  const { data } = await api.put(`/manager/kp/proposals/${id}`, payload)
  return data.data
}

export async function getProposal(id: string): Promise<Proposal> {
  const { data } = await api.get(`/manager/kp/proposals/${id}`)
  return data.data
}

export async function finalizeProposal(id: string): Promise<Proposal> {
  const { data } = await api.post(`/manager/kp/proposals/${id}/finalize`)
  return data.data
}

export async function listMyProposals(): Promise<Proposal[]> {
  const { data } = await api.get('/manager/kp/proposals')
  return data.data
}

export async function downloadPdf(id: string, filename = 'kp.pdf') {
  const res = await api.get(`/manager/kp/proposals/${id}/pdf`, { responseType: 'blob' })
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

/** Имя файла как на бэкенде: Коммерческое предложение на {дрон} от компании ATRIS {дата} КП №{номер}.pdf */
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
