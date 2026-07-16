import api from '@/api/http'

export interface Post {
  id: string
  title: string
  slug: string
  shortDescription: string
  content: string
  htmlContent: string
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  readingTime: number
  views: number
  likes: number
  authorId: string
  mediaObjectNames: string[]
  tags: string[]
  categories: string[]
  createdAt: string
  updatedAt: string
}

export interface PageResponse {
  content: Post[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface DashboardStats {
  drafts: number
  published: number
  archived: number
  total: number
}

export interface PostPayload {
  title: string
  shortDescription: string
  content: string
  tags: string[]
  categories: string[]
  mediaObjectNames: string[]
}

export async function fetchDashboard() {
  const { data } = await api.get('/admin/dashboard')
  return data.data as DashboardStats
}

export async function fetchPosts(params: { q?: string; status?: string; page?: number; size?: number }) {
  const { data } = await api.get('/admin/posts', { params })
  return data.data as PageResponse
}

export async function fetchPost(id: string) {
  const { data } = await api.get(`/admin/posts/${id}`)
  return data.data as Post
}

export async function createPost(payload: PostPayload) {
  const { data } = await api.post('/admin/posts', payload)
  return data.data as Post
}

export async function updatePost(id: string, payload: PostPayload) {
  const { data } = await api.put(`/admin/posts/${id}`, payload)
  return data.data as Post
}

export async function publishPost(id: string) {
  const { data } = await api.post(`/admin/posts/${id}/publish`)
  return data.data as Post
}

export async function archivePost(id: string) {
  const { data } = await api.post(`/admin/posts/${id}/archive`)
  return data.data as Post
}

export async function deletePost(id: string) {
  await api.delete(`/admin/posts/${id}`)
}

export async function createUser(payload: {
  username: string
  email: string
  password: string
  roles: string[]
}) {
  const { data } = await api.post('/auth/admin/users', payload)
  return data.data
}
