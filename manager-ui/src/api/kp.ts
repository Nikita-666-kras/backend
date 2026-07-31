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
  const { data } = await api.get(`/manager/kp/proposals/${id}/pdf`, { responseType: 'blob' })
  const url = URL.createObjectURL(data)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
