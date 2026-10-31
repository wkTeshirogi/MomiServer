import { apiClient } from '@/api/client'
import { WEBSOCKET_SERVER_URL } from '@/constants/api'

export type RuntimeStatus = 'online' | 'offline' | 'maintenance' | 'unknown'

export type StorageStatus = {
  id: number
  name: string
  max: number
  value: number
}

export type MachineStatusDto = {
  id: number
  name: string
  status: RuntimeStatus
  cpu: number
  mem: number
  healthScore: number
  cpuTemp?: number | null
  gpuTemp?: number | null
  storage: StorageStatus[]
  worktime: string
  version: string
  ipAddress: string
  hostname: string
  location: string
  metricsAvailable: boolean
  observedAt?: string
}

export type ServiceStatusDto = {
  id: number
  name: string
  status: RuntimeStatus
  cpu: number
  mem: number
  worktime: string
  version: string
  players: string
  address: string
  custom_now: number
  custom_max: number
  custom_name: string
  pid: string
  metricsAvailable: boolean
  observedAt?: string
}

export type MachineUpdateMessage = {
  type: 'machine_update'
  machines: MachineStatusDto[]
  generated_at: string
}

export type ServiceUpdateMessage = {
  type: 'service_update'
  services: ServiceStatusDto[]
  generated_at: string
}

type StatusChannel = 'machines' | 'services'
type TicketResponse = { ticket: string; websocket_path: string; expires_at: string }

export async function getMachineStatuses(signal?: AbortSignal) {
  const response = await apiClient.get<MachineStatusDto[]>('/status/machines', { signal })
  return response.data
}

export async function getServiceStatuses(signal?: AbortSignal) {
  const response = await apiClient.get<ServiceStatusDto[]>('/status/services', { signal })
  return response.data
}

export async function createStatusWebSocketUrl(channel: StatusChannel) {
  const response = await apiClient.post<TicketResponse>('/status/websocket-ticket', { channel })
  const { ticket, websocket_path: path } = response.data
  return `${WEBSOCKET_SERVER_URL}${path}?ticket=${encodeURIComponent(ticket)}`
}
