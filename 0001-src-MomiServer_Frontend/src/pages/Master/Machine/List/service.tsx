import { useAsyncResource } from '@/hooks/useAsyncResource'
import { getDeviceMasterList } from '@/generated/sdk.gen'
import type { DeviceMasterDetailDto } from '@/generated/types.gen'
import { formatJapanDateTime } from '@/lib/utils'
import { machineStatusClasses } from '../status'

export type MachineMasterItem = {
  id: number
  devicename: string
  address: string
  location: string
  mqttClientId: string
  note: string
  status: string
  createdAt: string
  updatedAt: string
}

const fallbackMachines: MachineMasterItem[] = []

function normalizeMachine(device: DeviceMasterDetailDto): MachineMasterItem | null {
  if (device.id == null) return null

  return {
    id: device.id,
    devicename: device.devicename ?? '',
    address: device.address ?? '',
    location: device.location ?? '',
    mqttClientId: device.mqtt_client_id ?? '',
    note: device.note ?? '',
    status: device.status ?? '',
    createdAt: formatJapanDateTime(device.createdAt),
    updatedAt: formatJapanDateTime(device.updatedAt),
  }
}

async function fetchMachines(signal: AbortSignal): Promise<MachineMasterItem[]> {
  const response = await getDeviceMasterList({ signal, throwOnError: true })
  return (response.data ?? []).map(normalizeMachine).filter((item): item is MachineMasterItem => item !== null)
}

export function useMachineMasterService() {
  const { data: machines, loading, error, reload } = useAsyncResource({
    load: fetchMachines, initialData: fallbackMachines, errorMessage: '端末マスタ一覧の取得に失敗しました。',
  })
  return { machines, loading, error, reload, statusClasses: machineStatusClasses }
}
