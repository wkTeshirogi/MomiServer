import { enabledLabels, enabledClasses } from '@/pages/Master/Batch/batch.constants'
export { enabledLabels, enabledClasses } from '@/pages/Master/Batch/batch.constants'
import { useAsyncResource } from '@/hooks/useAsyncResource'
import {
  getAllBatchDefinitions,
  getBatchDefinitionDetail,
  registerBatchDefinition,
} from '@/generated/sdk.gen'
import type {
  BatchDefinitionDetailDto,
  BatchDefinitionRegistRequest,
} from '@/generated/types.gen'
import { formatJapanDateTime } from '@/lib/utils'

export type BatchDefinitionItem = {
  id: number
  version: number
  status: string
  batch_name: string
  view_name: string
  description: string
  enabled: boolean
  category: string
  tags: string
  execution_type: string
  execution_command: string
  working_directory: string
  timeout_seconds: number
  max_retry_count: number
  retry_interval_seconds: number | null
  execution_user: string
  required_role: string
  priority: number
  allow_parallel_execution: boolean
  log_retention_date: string
  log_path: string
  success_notification: string
  failure_notification: string
  createdAt: string
  updatedAt: string
}

export type SaveBatchDefinitionInput = Omit<BatchDefinitionItem, 'id' | 'createdAt' | 'updatedAt'> & {
  id?: number
}

export function normalizeBatchDefinition(dto: BatchDefinitionDetailDto): BatchDefinitionItem {
  return {
    id: dto.id ?? 0,
    version: dto.version ?? 1,
    status: dto.status ?? 'active',
    batch_name: dto.batch_name ?? '',
    view_name: dto.view_name ?? '',
    description: dto.description ?? '',
    enabled: dto.enabled ?? false,
    category: dto.category ?? '',
    tags: dto.tags ?? '',
    execution_type: dto.execution_type ?? '',
    execution_command: dto.execution_command ?? '',
    working_directory: dto.working_directory ?? '',
    timeout_seconds: dto.timeout_seconds ?? 0,
    max_retry_count: dto.max_retry_count ?? 0,
    retry_interval_seconds: dto.retry_interval_seconds ?? null,
    execution_user: dto.execution_user ?? '',
    required_role: dto.required_role ?? '',
    priority: dto.priority ?? 5,
    allow_parallel_execution: dto.allow_parallel_execution ?? false,
    log_retention_date: dto.log_retention_date ?? '2999-12-31',
    log_path: dto.log_path ?? '',
    success_notification: dto.success_notification ?? 'none',
    failure_notification: dto.failure_notification ?? 'none',
    createdAt: formatJapanDateTime(dto.createdAt),
    updatedAt: formatJapanDateTime(dto.updatedAt),
  }
}

export async function fetchBatchDefinitionList(signal?: AbortSignal): Promise<BatchDefinitionItem[]> {
  const response = await getAllBatchDefinitions({ throwOnError: true, signal })
  return (response.data ?? []).map(normalizeBatchDefinition)
}

export async function fetchBatchDefinitionDetail(batchDefinitionId: number): Promise<BatchDefinitionItem> {
  const response = await getBatchDefinitionDetail({
    throwOnError: true,
    query: { id: batchDefinitionId },
  })

  return normalizeBatchDefinition(response.data ?? {})
}

function toRequest(input: SaveBatchDefinitionInput): BatchDefinitionRegistRequest {
  return {
    id: input.id,
    version: input.version,
    status: input.status,
    batch_name: input.batch_name,
    view_name: input.view_name,
    description: input.description || undefined,
    enabled: input.enabled,
    category: input.category,
    tags: input.tags || undefined,
    execution_type: input.execution_type,
    execution_command: input.execution_command,
    working_directory: input.working_directory || undefined,
    timeout_seconds: input.timeout_seconds,
    max_retry_count: input.max_retry_count,
    retry_interval_seconds: input.retry_interval_seconds ?? undefined,
    execution_user: input.execution_user || undefined,
    required_role: input.required_role || undefined,
    priority: input.priority,
    allow_parallel_execution: input.allow_parallel_execution,
    log_retention_date: input.log_retention_date,
    log_path: input.log_path,
    success_notification: input.success_notification,
    failure_notification: input.failure_notification,
  }
}

export async function saveBatchDefinition(input: SaveBatchDefinitionInput): Promise<BatchDefinitionItem> {
  const response = await registerBatchDefinition({
    body: toRequest(input),
  })

  if (response.response?.status === 409) {
    throw new BatchDefinitionConflictError()
  }

  if (response.error) {
    throw response.error
  }

  return normalizeBatchDefinition(response.data ?? {})
}

export class BatchDefinitionConflictError extends Error {
  constructor() {
    super('バッチ定義は他の操作で更新されています。')
    this.name = 'BatchDefinitionConflictError'
  }
}

export function useBatchDefinitionListService() {
  const { data: batchDefinitions, loading, error, reload } = useAsyncResource<BatchDefinitionItem[]>({
    load: fetchBatchDefinitionList, initialData: [], errorMessage: 'バッチ定義一覧の取得に失敗しました。',
  })
  return { batchDefinitions, loading, error, reload, enabledLabels, enabledClasses }
}
