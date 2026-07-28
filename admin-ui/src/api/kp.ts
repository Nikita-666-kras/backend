import api from './http'

export interface KpDroneModel {
  id: string
  code: string
  name: string
  defaultPrice: number
  sortOrder: number
  active: boolean
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

export async function fetchDroneModels() {
  const { data } = await api.get('/admin/kp/drone-models')
  return data.data as KpDroneModel[]
}

export async function createDroneModel(payload: Omit<KpDroneModel, 'id'>) {
  const { data } = await api.post('/admin/kp/drone-models', payload)
  return data.data as KpDroneModel
}

export async function updateDroneModel(id: string, payload: Omit<KpDroneModel, 'id'>) {
  const { data } = await api.put(`/admin/kp/drone-models/${id}`, payload)
  return data.data as KpDroneModel
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
