import api from './http'

export interface KpDroneModel {
  id: string
  code: string
  name: string
  defaultPrice: number
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
  const { data } = await api.get(`/admin/kp/proposals/${id}/pdf`, { responseType: 'blob' })
  const url = URL.createObjectURL(data)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
