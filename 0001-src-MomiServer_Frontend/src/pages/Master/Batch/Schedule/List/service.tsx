import { enabledLabels, enabledClasses } from '@/pages/Master/Batch/batch.constants'
export { enabledLabels, enabledClasses } from '@/pages/Master/Batch/batch.constants'
import { useCallback } from 'react'
import { useAsyncResource } from '@/hooks/useAsyncResource'
import { apiClient } from '@/api/client'
import {
  getAllBatchSchedules,
  getBatchScheduleDetail,
  getDeviceMasterList,
  registerBatchSchedule,
} from '@/generated/sdk.gen'
import type {
  BatchScheduleDetailDto,
  BatchScheduleRegistRequest,
  DeviceMasterDetailDto,
} from '@/generated/types.gen'
import { formatJapanDateTime } from '@/lib/utils'

export type BatchScheduleItem = {
  id: number
  batch_definition_id: number
  target_agent_id: number
  target_machine_name: string
  target_machine_address: string
  cron_expression: string
  start_date: string
  end_date: string
  allow_holiday_execution: boolean
  enabled: boolean
  priority: number
  createdAt: string
  updatedAt: string
}

export type BatchExecutionStatus = 'queued' | 'dispatched' | 'running' | 'success' | 'failed' | 'timeout' | 'cancelled' | 'expired'

export type BatchScheduleExecutionSummary = {
  schedule_id: number
  scheduled_date: string
  scheduled_hour: number
  execution_status: BatchExecutionStatus
  execution_count: number
}

export type BatchScheduleExecutionLog = {
  execution_id: string
  scheduled_fire_time: string
  execution_status: BatchExecutionStatus
  started_at: string | null
  finished_at: string | null
  exit_code: number | null
  message: string | null
  stdout: string | null
  stderr: string | null
  output_truncated: boolean | null
}

export type BatchScheduleExecutionLogPage = {
  logs: BatchScheduleExecutionLog[]
  has_more: boolean
  total_count: number
}

export type SaveBatchScheduleInput = Omit<
  BatchScheduleItem,
  'id' | 'target_machine_name' | 'target_machine_address' | 'createdAt' | 'updatedAt'
> & {
  id?: number
}

function normalizeMachineMap(devices: DeviceMasterDetailDto[]): Map<number, DeviceMasterDetailDto> {
  return new Map(
    devices
      .filter(device => device.id != null)
      .map(device => [device.id as number, device])
  )
}

export function normalizeBatchSchedule(
  dto: BatchScheduleDetailDto,
  machines: Map<number, DeviceMasterDetailDto> = new Map()
): BatchScheduleItem {
  const targetAgentId = dto.target_agent_id ?? 0
  const targetMachine = machines.get(targetAgentId)

  return {
    id: dto.id ?? 0,
    batch_definition_id: dto.batch_definition_id ?? 0,
    target_agent_id: targetAgentId,
    target_machine_name: targetMachine?.devicename ?? '',
    target_machine_address: targetMachine?.address ?? '',
    cron_expression: dto.cron_expression ?? '',
    start_date: dto.start_date ?? '',
    end_date: dto.end_date ?? '',
    allow_holiday_execution: dto.allow_holiday_execution ?? false,
    enabled: dto.enabled ?? false,
    priority: dto.priority ?? 5,
    createdAt: formatJapanDateTime(dto.createdAt),
    updatedAt: formatJapanDateTime(dto.updatedAt),
  }
}

export async function fetchBatchScheduleList(): Promise<BatchScheduleItem[]> {
  const [scheduleResponse, machineResponse] = await Promise.all([
    getAllBatchSchedules({ throwOnError: true }),
    getDeviceMasterList({ throwOnError: true }),
  ])
  const machines = normalizeMachineMap(machineResponse.data ?? [])

  return (scheduleResponse.data ?? []).map(schedule => normalizeBatchSchedule(schedule, machines))
}

export async function fetchBatchScheduleExecutions(from: string, to: string): Promise<BatchScheduleExecutionSummary[]> {
  const { data } = await apiClient.get<BatchScheduleExecutionSummary[]>('/master/batch/schedule/executions', {
    params: { from, to },
  })
  return data
}

export async function fetchBatchScheduleExecutionLogs(
  scheduleId: number, date: string, hour: number | undefined, offset: number, limit: number, signal?: AbortSignal
): Promise<BatchScheduleExecutionLogPage> {
  const { data } = await apiClient.get<BatchScheduleExecutionLogPage>('/master/batch/schedule/executions/logs', {
    params: { scheduleId, date, ...(hour == null ? {} : { hour }), offset, limit },
    signal,
  })
  return data
}

export async function fetchBatchScheduleDetail(batchScheduleId: number): Promise<BatchScheduleItem> {
  const response = await getBatchScheduleDetail({
    throwOnError: true,
    query: { id: batchScheduleId },
  })

  return normalizeBatchSchedule(response.data ?? {})
}

function toRequest(input: SaveBatchScheduleInput): BatchScheduleRegistRequest {
  return {
    id: input.id,
    batch_definition_id: input.batch_definition_id,
    target_agent_id: input.target_agent_id,
    cron_expression: input.cron_expression,
    start_date: input.start_date || undefined,
    end_date: input.end_date || undefined,
    allow_holiday_execution: input.allow_holiday_execution,
    enabled: input.enabled,
    priority: input.priority,
  }
}

export async function saveBatchSchedule(input: SaveBatchScheduleInput): Promise<BatchScheduleItem> {
  const response = await registerBatchSchedule({
    body: toRequest(input),
  })

  if (response.error) {
    throw response.error
  }

  return normalizeBatchSchedule(response.data ?? {})
}

export function useBatchScheduleListService(from: string, to: string) {
  const { data: batchSchedules, loading, error, reload: reloadSchedules } = useAsyncResource<BatchScheduleItem[]>({
    load: fetchBatchScheduleList, initialData: [], errorMessage: 'バッチスケジュール一覧の取得に失敗しました。',
  })
  const loadExecutions = useCallback(() => fetchBatchScheduleExecutions(from, to), [from, to])
  const { data: executionSummaries, loading: executionLoading, error: executionError, reload: reloadExecutions } = useAsyncResource<BatchScheduleExecutionSummary[]>({
    load: loadExecutions, initialData: [], errorMessage: 'バッチ実行状況の取得に失敗しました。', clearOnError: true,
  })

  const reload = useCallback(async () => {
    await Promise.all([reloadSchedules(), reloadExecutions()])
  }, [reloadSchedules, reloadExecutions])

  return {
    batchSchedules,
    executionSummaries,
    loading,
    executionLoading,
    error,
    executionError,
    reload,
    enabledLabels,
    enabledClasses,
  }
}
