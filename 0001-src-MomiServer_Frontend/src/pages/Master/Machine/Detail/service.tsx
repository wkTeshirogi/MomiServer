import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAsyncRequest } from '@/hooks/useAsyncRequest'
import { getDeviceMasterDetail } from '@/generated/sdk.gen'
import type { DeviceMasterDetailDto } from '@/generated/types.gen'
import { formatJapanDateTime } from '@/lib/utils'

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

export function useMachineMasterService(machineId?: number) {
  const { run, cancel } = useAsyncRequest()
  const hasValidMachineId = typeof machineId === 'number' && Number.isFinite(machineId)
  const [machine, setMachine] = useState<MachineMasterItem | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    if (!hasValidMachineId) {
      setMachine(null)
      setLoading(false)
      return
    }

    await run(async signal => {
      const response = await getDeviceMasterDetail({
        throwOnError: true,
        query: { id: machineId },
        signal,
      })
      return response.data ? normalizeMachine(response.data) : null
    }, {
      onStart: () => { setLoading(true); setError(null) },
      onSuccess: nextMachine => { setMachine(nextMachine); if (!nextMachine) setError('端末マスタが見つかりません。') },
      onError: () => setError('端末マスタ詳細の取得に失敗しました。'),
      onSettled: () => setLoading(false),
    })
  }, [machineId, hasValidMachineId, run])

  useEffect(() => {
    const timer = window.setTimeout(() => { void reload() }, 0)
    return () => { window.clearTimeout(timer); cancel() }
  }, [reload, cancel])

  return useMemo(
    () => ({
      machine: hasValidMachineId ? machine : null,
      loading: hasValidMachineId ? loading : false,
      error: hasValidMachineId ? error : '端末IDが指定されていません。',
      reload,
    }),
    [machine, loading, error, hasValidMachineId, reload]
  )
}
