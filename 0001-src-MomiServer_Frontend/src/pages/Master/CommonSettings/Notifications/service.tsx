import { defaultSetting } from './service.constants'
import type { NotificationGlobalSetting } from './service.constants'
export { notificationLevelLabels, notificationLevelOptions } from './service.constants'
export type { NotificationLevel, NotificationGlobalSetting } from './service.constants'
import { apiClient } from '@/api/client'

type NotificationGlobalSettingResponse = Partial<NotificationGlobalSetting>

function normalizeSetting(item: NotificationGlobalSettingResponse): NotificationGlobalSetting {
  return {
    enabled: item.enabled ?? defaultSetting.enabled,
    browser_enabled: item.browser_enabled ?? defaultSetting.browser_enabled,
    discord_enabled: item.discord_enabled ?? defaultSetting.discord_enabled,
    discord_webhook_configured: item.discord_webhook_configured ?? Boolean(item.discord_webhook_url),
    discord_webhook_url: item.discord_webhook_url ?? '',
    discord_minimum_level: item.discord_minimum_level ?? defaultSetting.discord_minimum_level,
    security_event_discord_enabled: item.security_event_discord_enabled ?? defaultSetting.security_event_discord_enabled,
    security_event_minimum_level: item.security_event_minimum_level ?? defaultSetting.security_event_minimum_level,
    security_event_aggregation_window_seconds:
      item.security_event_aggregation_window_seconds ?? defaultSetting.security_event_aggregation_window_seconds,
    retention_days: item.retention_days ?? defaultSetting.retention_days,
    createdAt: item.createdAt ?? null,
    updatedAt: item.updatedAt ?? null,
  }
}

export async function fetchNotificationGlobalSetting(): Promise<NotificationGlobalSetting> {
  const { data } = await apiClient.get<NotificationGlobalSettingResponse>('/master/common-settings/notifications')
  return normalizeSetting(data)
}

export async function saveNotificationGlobalSetting(
  input: NotificationGlobalSetting
): Promise<NotificationGlobalSetting> {
  const { data } = await apiClient.post<NotificationGlobalSettingResponse>('/master/common-settings/notifications', {
    enabled: input.enabled,
    browser_enabled: input.browser_enabled,
    discord_enabled: input.discord_enabled,
    discord_webhook_url: input.discord_webhook_url?.trim() || null,
    discord_minimum_level: input.discord_minimum_level,
    security_event_discord_enabled: input.security_event_discord_enabled,
    security_event_minimum_level: input.security_event_minimum_level,
    security_event_aggregation_window_seconds: input.security_event_aggregation_window_seconds,
    retention_days: input.retention_days,
  })
  return normalizeSetting(data)
}
