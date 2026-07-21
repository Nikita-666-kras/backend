export interface MediaItem {
  id: string
  url: string
  kind: string
  originalName: string
}

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
  coverMediaId?: string | null
  coverUrl?: string | null
  mediaObjectNames: string[]
  media?: MediaItem[]
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

interface ApiResponse<T> {
  data: T
}

export function mediaUrl(url?: string | null): string {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return url.startsWith('/') ? url : `/${url}`
}

export async function fetchPosts(page = 0, size = 9): Promise<PageResponse> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size)
  })
  const response = await fetch(`/posts?${params.toString()}`)
  if (!response.ok) {
    throw new Error('Failed to load posts')
  }
  const body = (await response.json()) as ApiResponse<PageResponse>
  return body.data
}

export async function fetchPostBySlug(slug: string): Promise<Post> {
  const response = await fetch(`/posts/${encodeURIComponent(slug)}`)
  if (!response.ok) {
    throw new Error('Post not found')
  }
  const body = (await response.json()) as ApiResponse<Post>
  return body.data
}

export function formatDate(value: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric',
    month: 'long',
    year: 'numeric'
  }).format(new Date(value))
}

export function formatReadingTime(minutes?: number): string {
  const value = minutes && minutes > 0 ? minutes : 1
  return `${value} мин чтения`
}
