import { useCallback, useEffect, useMemo, useState } from 'react'
import { getDeviceMasterList } from '@/generated/sdk.gen'
import type { DeviceMasterDetailDto } from '@/generated/types.gen'
import { validateCronExpression } from '@/lib/cronSchedule'
import { fetchBatchDefinitionList, type BatchDefinitionItem } from '../../Difinition/List/service'
import {
  fetchBatchScheduleDetail,
  saveBatchSchedule,
  type BatchScheduleItem,
  type SaveBatchScheduleInput,
} from '../List/service'

export type { BatchScheduleItem, SaveBatchScheduleInput } from '../List/service'

export type MachineOption = {
  id: number
  name: string
  address: string
  status: string
}

export type FieldName =
  | 'batch_definition_id'
  | 'target_agent_id'
  | 'cron_expression'
  | 'priority'
  | 'date_range'

export type FieldErrors = Partial<Record<FieldName, string>>

export function validateBatchSchedule(input: SaveBatchScheduleInput): FieldErrors {
  const errors: FieldErrors = {}

  if (!Number.isFinite(input.batch_definition_id) || input.batch_definition_id <= 0) {
    errors.batch_definition_id = 'バッチ定義IDは必須です。'
  }
  if (!Number.isFinite(input.target_agent_id) || input.target_agent_id <= 0) {
    errors.target_agent_id = '対象マシンは必須です。'
  }
  if (!input.cron_expression.trim()) {
    errors.cron_expression = 'Cron式は必須です。'
  } else {
    const cronValidation = validateCronExpression(input.cron_expression)
    if (!cronValidation.valid) {
      errors.cron_expression = cronValidation.error ?? 'Cron式の書式が不正です。'
    }
  }
  if (!Number.isFinite(input.priority) || input.priority < 1 || input.priority > 9) {
    errors.priority = '1から9で入力してください。'
  }
  if (input.start_date && input.end_date && input.start_date > input.end_date) {
    errors.date_range = '開始日は終了日以前にしてください。'
  }

  return errors
}

export function createEmptyBatchSchedule(): SaveBatchScheduleInput {
  return {
    batch_definition_id: 0,
    target_agent_id: 1,
    cron_expression: '',
    start_date: '',
    end_date: '',
    allow_holiday_execution: false,
    enabled: true,
    priority: 5,
  }
}

export function toSaveInput(item: BatchScheduleItem | null): SaveBatchScheduleInput {
  if (!item) return createEmptyBatchSchedule()

  return {
    id: item.id,
    batch_definition_id: item.batch_definition_id,
    target_agent_id: item.target_agent_id,
    cron_expression: item.cron_expression,
    start_date: item.start_date,
    end_date: item.end_date,
    allow_holiday_execution: item.allow_holiday_execution,
    enabled: item.enabled,
    priority: item.priority,
  }
}

function normalizeMachineOption(device: DeviceMasterDetailDto): MachineOption | null {
  if (device.id == null) return null

  return {
    id: device.id,
    name: device.devicename ?? '',
    address: device.address ?? '',
    status: device.status ?? '',
  }
}

async function fetchMachineOptions(): Promise<MachineOption[]> {
  const response = await getDeviceMasterList({ throwOnError: true })
  return (response.data ?? [])
    .map(normalizeMachineOption)
    .filter((machine): machine is MachineOption => machine !== null)
}

export function useBatchScheduleDetailService(batchScheduleId?: string) {
  const isNew = !batchScheduleId || batchScheduleId === 'new'
  const [batchSchedule, setBatchSchedule] = useState<BatchScheduleItem | null>(null)
  const [batchDefinitions, setBatchDefinitions] = useState<BatchDefinitionItem[]>([])
  const [machines, setMachines] = useState<MachineOption[]>([])
  const [loading, setLoading] = useState(!isNew)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)

    try {
      const [definitions, machineOptions] = await Promise.all([
        fetchBatchDefinitionList(),
        fetchMachineOptions(),
      ])
      setBatchDefinitions(definitions)
      setMachines(machineOptions)

      if (isNew) {
        setBatchSchedule(null)
        return
      }

      const numericId = Number(batchScheduleId)
      if (!Number.isFinite(numericId)) {
        setError('バッチスケジュールIDが不正です。')
        return
      }

      setBatchSchedule(await fetchBatchScheduleDetail(numericId))
    } catch {
      setError('バッチスケジュール詳細の取得に失敗しました。')
    } finally {
      setLoading(false)
    }
  }, [batchScheduleId, isNew])

  useEffect(() => {
    const timer = window.setTimeout(() => { void reload() }, 0)
    return () => window.clearTimeout(timer)
  }, [reload])

  const save = useCallback(
    async (input: SaveBatchScheduleInput) => {
      const saved = await saveBatchSchedule({
        ...input,
        id: isNew ? undefined : batchSchedule?.id,
      })
      setBatchSchedule(saved)
      return saved
    },
    [batchSchedule?.id, isNew]
  )

  return useMemo(
    () => ({
      batchSchedule,
      batchDefinitions,
      machines,
      loading,
      error,
      isNew,
      reload,
      save,
    }),
    [batchSchedule, batchDefinitions, machines, loading, error, isNew, reload, save]
  )
}
