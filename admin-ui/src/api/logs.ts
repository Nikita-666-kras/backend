import api from './http'

export interface LogEntry {
  id: number
  createdAt: string
  lastSeenAt: string
  service: string
  category: string
  level: string
  message: string
  detailsJson?: string | null
  actorId?: string | null
  actorUsername?: string | null
  requestId?: string | null
  count: number
}

export interface LogsPageResponse {
  items: LogEntry[]
  totalElements: number
  page: number
  size: number
}

export interface LogsStatsResponse {
  byLevel: Record<string, number>
  byCategory: Record<string, number>
}

export interface LogsQuery {
  from?: string
  to?: string
  level?: string
  category?: string
  service?: string
  q?: string
  page?: number
  size?: number
}

export async function fetchLogs(query: LogsQuery) {
  const { data } = await api.get('/admin/logs', { params: query })
  return data.data as LogsPageResponse
}

export async function fetchLogsStats(from?: string, to?: string) {
  const { data } = await api.get('/admin/logs/stats', { params: { from, to } })
  return data.data as LogsStatsResponse
}
