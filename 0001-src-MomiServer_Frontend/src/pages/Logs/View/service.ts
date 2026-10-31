import { apiClient } from '@/api/client'

export type ApiAccessLog = {
  id: number
  request_id: string
  occurred_at: string
  http_method: string
  request_path: string
  endpoint_pattern: string | null
  status_code: number
  duration_ms: number
  source_type: 'web' | 'agent' | 'iot' | 'gateway' | 'anonymous'
  principal: string | null
  ip_address: string | null
  user_agent: string | null
  error_code: string | null
}

export type ApiAccessLogPage = {
  logs: ApiAccessLog[]
  has_more: boolean
  total_count: number
}

export type ApiAccessLogSearch = {
  from: string
  to: string
  http_method: string
  request_path: string
  status_category: string
  status_code: string
  source_type: string
  principal: string
  ip_address: string
  min_duration_ms: string
}

export async function fetchApiAccessLogs(
  search: ApiAccessLogSearch,
  offset: number,
  limit: number,
  signal?: AbortSignal,
): Promise<ApiAccessLogPage> {
  const params = compactParams({
    from: toIso(search.from),
    to: toIso(search.to),
    http_method: search.http_method,
    request_path: search.request_path.trim(),
    status_category: search.status_category,
    status_code: search.status_code,
    source_type: search.source_type,
    principal: search.principal.trim(),
    ip_address: search.ip_address.trim(),
    min_duration_ms: search.min_duration_ms,
    offset,
    limit,
  })
  const response = await apiClient.get<ApiAccessLogPage>('/logs/access', { params, signal })
  return response.data
}

export async function fetchApiAccessLogDetail(requestId: string, signal?: AbortSignal): Promise<ApiAccessLog> {
  const response = await apiClient.get<ApiAccessLog>(`/logs/access/${requestId}`, { signal })
  return response.data
}

function compactParams(values: Record<string, string | number>) {
  return Object.fromEntries(Object.entries(values).filter(([, value]) => value !== ''))
}

function toIso(value: string): string {
  return value ? new Date(value).toISOString() : ''
}
