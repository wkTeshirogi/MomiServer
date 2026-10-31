import { statusClasses } from './service.constants'
export { statusClasses } from './service.constants'
import { useAsyncResource } from '@/hooks/useAsyncResource'
import { getServiceMasterList, getDeviceMasterList } from '@/generated/sdk.gen'
import type { DeviceMasterDetailDto, ServiceMasterDetailDto } from '@/generated/types.gen'
import { formatJapanDateTime } from '@/lib/utils'

export type ServicesMasterItem = {
  id: number
  view_name: string
  target_machine: number
  service_name: string
  createdAt: string
  updatedAt: string
  status: string
}
export type DeviceMasterItem = {
  id: number,
  devicename: string,
  address: string,
  location: string,
  mqtt_client_id: string,
  note: string,
  status: string,
  createdAt: string,
  updatedAt: string,
}

export const users: ServicesMasterItem[] = [
  // {
  //   id: 1,
  //   view_name: '7Days To Die for Zunda',
  //   service_name: 'G_7DaysToDie',
  //   target_machine: '192.168.0.6',
  //   createdAt: '2024/01/15 10:30:00',
  //   updatedAt: '2024/06/10 14:22:00',
  //   status: 'watch',
  // },
  // {
  //   id: 2,
  //   view_name: 'Minecraft JE',
  //   service_name: 'G_Minecraft',
  //   target_machine: '192.168.0.6',
  //   createdAt: '2024/02/20 09:15:00',
  //   updatedAt: '2024/05/28 11:45:00',
  //   status: 'watch',
  // },
  // {
  //   id: 3,
  //   view_name: 'CoreKeeper',
  //   service_name: 'G_CoreKeeper',
  //   target_machine: '192.168.0.6',
  //   createdAt: '2024/03/10 14:00:00',
  //   updatedAt: '2024/06/01 10:00:00',
  //   status: 'watch',
  // },
  // {
  //   id: 4,
  //   view_name: 'Rust',
  //   service_name: 'G_Rust',
  //   target_machine: '192.168.0.6',
  //   createdAt: '2024/01/05 11:20:00',
  //   updatedAt: '2024/04/15 09:30:00',
  //   status: 'stop',
  // },
  // {
  //   id: 5,
  //   view_name: 'DiscordBot',
  //   service_name: 'O_Discord',
  //   target_machine: '192.168.0.6',
  //   createdAt: '2024/05/01 16:45:00',
  //   updatedAt: '2024/06/05 13:20:00',
  //   status: 'watch',
  // },
]

function normalizeService(service: ServiceMasterDetailDto): ServicesMasterItem | null {
  if (service.id == null) return null

  return {
    id: service.id,
    view_name: service.view_name ?? '',
    target_machine: service.target_machine ?? 0,
    service_name: service.service_name ?? '',
    createdAt: formatJapanDateTime(service.createdAt),
    updatedAt: formatJapanDateTime(service.updatedAt),
    status: service.status ?? '',
  }
}

function normalizeDevice(device: DeviceMasterDetailDto): DeviceMasterItem | null {
  if (device.id == null) return null

  return {
    id: device.id,
    devicename: device.devicename ?? '',
    address: device.address ?? '',
    location: device.location ?? '',
    mqtt_client_id: device.mqtt_client_id ?? '',
    note: device.note ?? '',
    status: device.status ?? '',
    createdAt: formatJapanDateTime(device.createdAt),
    updatedAt: formatJapanDateTime(device.updatedAt),
  }
}

async function fetchServices(signal: AbortSignal): Promise<ServicesMasterItem[]> {
  const response = await getServiceMasterList({ signal, throwOnError: true })
  return (response.data ?? []).map(normalizeService).filter((item): item is ServicesMasterItem => item !== null)
}

async function fetchDevices(signal: AbortSignal): Promise<DeviceMasterItem[]> {
  const response = await getDeviceMasterList({ signal, throwOnError: true })
  return (response.data ?? []).map(normalizeDevice).filter((item): item is DeviceMasterItem => item !== null)
}

export function useServicesMasterService() {
  const services = useAsyncResource<ServicesMasterItem[]>({ load: fetchServices, initialData: [], errorMessage: 'サービス一覧の取得に失敗しました。' })
  const devices = useAsyncResource<DeviceMasterItem[]>({ load: fetchDevices, initialData: [], errorMessage: '端末一覧の取得に失敗しました。' })
  return { users: services.data, devices: devices.data, loading: services.loading || devices.loading,
    error: services.error ?? devices.error, reload: () => { void services.reload(); void devices.reload() }, statusClasses }
}
