import { apiClient } from '@/api/client'

export type InventoryScope = 'inventory:read' | 'inventory:write'

export type ExternalApiKey = {
  id: string
  name: string
  keyPrefix: string
  scopes: InventoryScope[]
  status: 'ACTIVE' | 'REVOKED'
  expiresAt: string | null
  lastUsedAt: string | null
  createdAt: string
  createdBy: string
  revokedAt: string | null
  revokedBy: string | null
}

export async function getExternalApiKeys(signal?: AbortSignal) {
  return (await apiClient.get<ExternalApiKey[]>('/external-api-keys', { signal })).data
}

export async function issueExternalApiKey(input: { name: string; scopes: InventoryScope[]; expiresAt: string | null }) {
  return (await apiClient.post<{ key: ExternalApiKey; secret: string }>('/external-api-keys', input)).data
}

export async function revokeExternalApiKey(id: string) {
  return (await apiClient.post<ExternalApiKey>(`/external-api-keys/${id}/revoke`)).data
}
