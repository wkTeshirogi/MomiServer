import { useCallback, useEffect, useMemo, useState } from 'react'
import { getServiceMasterDetail, getDeviceMasterList } from '@/generated/sdk.gen'
import type { DeviceMasterDetailDto, ServiceMasterDetailDto } from '@/generated/types.gen'
import { formatJapanDateTime } from '@/lib/utils'

// APIから返されるフィールドに合わせた型定義
export type ServicesMasterItem = {
  id: number
  view_name: string
  target_machine: string | number
  service_name: string
  createdAt: string
  updatedAt: string
  status: string | null
  log_path: string
  use_journalctl: boolean

  allow_start: boolean
  allow_stop: boolean
  allow_restart: boolean
  auto_restart: boolean
  notice_level: string
  description: string
}

export type DeviceMasterItem = {
  id: number
  devicename: string
  address: string
  location: string
  mqtt_client_id: string
  note: string
  status: string
  createdat: string
  updatedat: string
}

export type DeviceSelectOption = {
  value: string
  label: string
}

function normalizeService(service: ServiceMasterDetailDto): ServicesMasterItem | null {
  if (service.id == null) return null

  return {
    id: service.id,
    view_name: service.view_name ?? '',
    target_machine: service.target_machine ?? '',
    service_name: service.service_name ?? '',
    createdAt: formatJapanDateTime(service.createdAt),
    updatedAt: formatJapanDateTime(service.updatedAt),
    status: service.status ?? '',
    log_path: service.log_path ?? '',
    use_journalctl: service.use_journalctl ?? false,
    allow_start: service.allow_start ?? false,
    allow_stop: service.allow_stop ?? false,
    allow_restart: service.allow_restart ?? false,
    auto_restart: service.auto_restart ?? false,
    notice_level: service.notice_level ?? 'none',
    description: service.description ?? '',
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
    createdat: formatJapanDateTime(device.createdAt),
    updatedat: formatJapanDateTime(device.updatedAt),
  }
}

export function useServicesMasterService(serviceId?: number) {
  const [users, setUsers] = useState<ServicesMasterItem[]>([])
  const [deviceOptions, setDeviceOptions] = useState<DeviceSelectOption[]>([])
  const [loading, setLoading] = useState(true)

  const reload = useCallback(async () => {
    setLoading(true)

    try {
      if (serviceId) {
        const response = await getServiceMasterDetail({
          query: { id: serviceId }
        })
        const data = response.data ? normalizeService(response.data) : null
        setUsers(data ? [data] : [])
      } else {
        setUsers([])
      }

      const response = await getDeviceMasterList()
      const deviceData = (response.data ?? [])
        .map(normalizeDevice)
        .filter((device): device is DeviceMasterItem => device !== null)
      const options = deviceData.map(d => ({
        value: String(d.id),
        label: d.devicename
      }))
      setDeviceOptions(options)
    } finally {
      setLoading(false)
    }
  }, [serviceId])

  useEffect(() => {
    const timer = window.setTimeout(() => { void reload() }, 0)
    return () => window.clearTimeout(timer)
  }, [reload])

  return useMemo(() => ({ users, deviceOptions, loading, reload }), [users, deviceOptions, loading, reload])
}
