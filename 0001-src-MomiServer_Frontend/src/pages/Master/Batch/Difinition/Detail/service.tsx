import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAsyncRequest } from '@/hooks/useAsyncRequest'
import {
  fetchBatchDefinitionDetail,
  saveBatchDefinition,
  type BatchDefinitionItem,
  type SaveBatchDefinitionInput,
} from '../List/service'

export type { BatchDefinitionItem, SaveBatchDefinitionInput } from '../List/service'

export type FieldName =
  | 'batch_name'
  | 'view_name'
  | 'category'
  | 'execution_type'
  | 'execution_command'
  | 'timeout_seconds'
  | 'max_retry_count'
  | 'priority'
  | 'log_retention_date'
  | 'log_path'
  | 'success_notification'
  | 'failure_notification'

export type FieldErrors = Partial<Record<FieldName, string>>

export function validateBatchDefinition(input: SaveBatchDefinitionInput): FieldErrors {
  const errors: FieldErrors = {}

  if (!input.execution_command.trim()) errors.execution_command = 'execution_command は必須です。'

  if (!input.batch_name.trim()) errors.batch_name = 'バッチ名は必須です。'
  if (!input.view_name.trim()) errors.view_name = '表示名は必須です。'
  if (!input.category.trim()) errors.category = 'カテゴリは必須です。'
  if (!input.execution_type.trim()) errors.execution_type = '実行種別は必須です。'
  if (!Number.isFinite(input.timeout_seconds) || input.timeout_seconds < 0) errors.timeout_seconds = '0以上で入力してください。'
  if (!Number.isFinite(input.max_retry_count) || input.max_retry_count < 0 || input.max_retry_count > 9) errors.max_retry_count = '0から9で入力してください。'
  if (!Number.isFinite(input.priority) || input.priority < 1 || input.priority > 9) errors.priority = '1から9で入力してください。'
  if (!input.log_retention_date) errors.log_retention_date = 'ログ保存期限は必須です。'
  if (!input.log_path.trim()) errors.log_path = 'ログパスは必須です。'
  if (!input.success_notification.trim()) errors.success_notification = '成功通知は必須です。'
  if (!input.failure_notification.trim()) errors.failure_notification = '失敗通知は必須です。'

  return errors
}

export function createEmptyBatchDefinition(): SaveBatchDefinitionInput {
  return {
    version: 1,
    status: 'active',
    batch_name: '',
    view_name: '',
    description: '',
    enabled: true,
    category: '',
    tags: '',
    execution_type: '',
    execution_command: '',
    working_directory: '',
    timeout_seconds: 0,
    max_retry_count: 0,
    retry_interval_seconds: null,
    execution_user: '',
    required_role: '',
    priority: 5,
    allow_parallel_execution: false,
    log_retention_date: '2999-12-31',
    log_path: '',
    success_notification: 'none',
    failure_notification: 'none',
  }
}

export function toSaveInput(item: BatchDefinitionItem | null): SaveBatchDefinitionInput {
  if (!item) return createEmptyBatchDefinition()

  return {
    id: item.id,
    version: item.version,
    status: item.status,
    batch_name: item.batch_name,
    view_name: item.view_name,
    description: item.description,
    enabled: item.enabled,
    category: item.category,
    tags: item.tags,
    execution_type: item.execution_type,
    execution_command: item.execution_command,
    working_directory: item.working_directory,
    timeout_seconds: item.timeout_seconds,
    max_retry_count: item.max_retry_count,
    retry_interval_seconds: item.retry_interval_seconds,
    execution_user: item.execution_user,
    required_role: item.required_role,
    priority: item.priority,
    allow_parallel_execution: item.allow_parallel_execution,
    log_retention_date: item.log_retention_date,
    log_path: item.log_path,
    success_notification: item.success_notification,
    failure_notification: item.failure_notification,
  }
}

export function useBatchDefinitionDetailService(batchDefinitionId?: string) {
  const { run, cancel } = useAsyncRequest()
  const isNew = !batchDefinitionId || batchDefinitionId === 'new'
  const [batchDefinition, setBatchDefinition] = useState<BatchDefinitionItem | null>(null)
  const [loading, setLoading] = useState(!isNew)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    if (isNew) {
      setBatchDefinition(null)
      setLoading(false)
      setError(null)
      return
    }

    const numericId = Number(batchDefinitionId)
    if (!Number.isFinite(numericId)) {
      setError('バッチ定義IDが不正です。')
      setLoading(false)
      return
    }

    await run(() => fetchBatchDefinitionDetail(numericId), {
      onStart: () => { setLoading(true); setError(null) },
      onSuccess: setBatchDefinition,
      onError: () => setError('バッチ定義詳細の取得に失敗しました。'),
      onSettled: () => setLoading(false),
    })
  }, [batchDefinitionId, isNew, run])

  useEffect(() => {
    const timer = window.setTimeout(() => { void reload() }, 0)
    return () => { window.clearTimeout(timer); cancel() }
  }, [reload, cancel])

  const save = useCallback(
    async (input: SaveBatchDefinitionInput) => {
      const saved = await saveBatchDefinition({
        ...input,
        id: isNew ? undefined : batchDefinition?.id,
      })
      cancel()
      setLoading(false)
      setBatchDefinition(saved)
      return saved
    },
    [batchDefinition?.id, isNew, cancel]
  )

  return useMemo(
    () => ({
      batchDefinition,
      loading,
      error,
      isNew,
      reload,
      save,
    }),
    [batchDefinition, loading, error, isNew, reload, save]
  )
}
