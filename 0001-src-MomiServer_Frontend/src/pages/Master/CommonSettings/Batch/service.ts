import { apiClient } from '@/api/client'

export type BatchGlobalSettingValues = {
  attachment_max_file_mib: number
  attachment_max_total_mib: number
  attachment_max_count: number
  work_directory_retention_days: number
  max_concurrent_executions: number
  default_timeout_seconds: number
  result_retry_interval_seconds: number
  result_retry_count: number
  unsent_result_retention_days: number
  result_wait_timeout_seconds: number
  start_wait_timeout_seconds: number
  cancel_grace_seconds: number
}

export type BatchGlobalSetting = BatchGlobalSettingValues & {
  setting_version: number
  createdAt: string
  updatedAt: string
}

export async function fetchBatchGlobalSetting(): Promise<BatchGlobalSetting> {
  const { data } = await apiClient.get<BatchGlobalSetting>('/master/common-settings/batch')
  return data
}

export async function saveBatchGlobalSetting(input: BatchGlobalSettingValues): Promise<BatchGlobalSetting> {
  const { data } = await apiClient.post<BatchGlobalSetting>('/master/common-settings/batch', input)
  return data
}
