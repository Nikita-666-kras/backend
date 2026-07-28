import axios from 'axios'

const tokenKey = 'manager_access_token'
const refreshKey = 'manager_refresh_token'

const api = axios.create({ baseURL: '' })

api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(tokenKey)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export interface Item { id: string; sku: string; name: string; price: number; currency: string }
export interface Model { id: string; code: string; name: string; defaultPrice: number }

export async function login(username: string, password: string) {
  const { data } = await api.post('/auth/login', { username, password })
  sessionStorage.setItem(tokenKey, data.data.accessToken)
  sessionStorage.setItem(refreshKey, data.data.refreshToken)
}

export async function ensureAuth() {
  if (sessionStorage.getItem(tokenKey)) return
  throw new Error('not-auth')
}

export async function models(): Promise<Model[]> {
  const { data } = await api.get('/manager/kp/drone-models')
  return data.data
}
export async function kits(q = ''): Promise<Item[]> {
  const { data } = await api.get('/manager/kp/catalog/kits', { params: { q, page: 0, size: 100 } })
  return data.data
}
export async function parts(q = ''): Promise<Item[]> {
  const { data } = await api.get('/manager/kp/catalog/parts', { params: { q, page: 0, size: 100 } })
  return data.data
}
export async function saveProposal(payload: any) {
  const { data } = await api.post('/manager/kp/proposals', payload)
  return data.data
}
export async function finalizeProposal(id: string) {
  const { data } = await api.post(`/manager/kp/proposals/${id}/finalize`)
  return data.data
}
export async function myProposals() {
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
