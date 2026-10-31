import { apiClient } from '@/api/client'

export type SecuritySetting = {
  accessTokenSeconds: number
  refreshTokenSeconds: number
  iotRateLimitEnabled: boolean
  iotPerMinute: number
  externalInventoryHourlyLimit: number
  loginFailureTolerance: number
  loginLockMinutes: number
}

export async function fetchSecuritySettings(signal: AbortSignal): Promise<SecuritySetting> {
  const { data } = await apiClient.get<SecuritySetting>('/master/common-settings/security', { signal })
  return data
}
