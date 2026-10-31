import type { NotificationLevel } from '@/pages/Master/notification.constants'
export type { NotificationLevel } from '@/pages/Master/notification.constants'
export { notificationLevelLabels, notificationLevelOptions } from '@/pages/Master/notification.constants'


export type NotificationGlobalSetting = {
  enabled: boolean
  browser_enabled: boolean
  discord_enabled: boolean
  discord_webhook_configured?: boolean
  discord_webhook_url?: string | null
  discord_minimum_level: NotificationLevel
  security_event_discord_enabled: boolean
  security_event_minimum_level: NotificationLevel
  security_event_aggregation_window_seconds: number
  retention_days: number
  createdAt?: string | null
  updatedAt?: string | null
}

export const defaultSetting: NotificationGlobalSetting = {
  enabled: true,
  browser_enabled: true,
  discord_enabled: false,
  discord_webhook_configured: false,
  discord_webhook_url: '',
  discord_minimum_level: 'warning',
  security_event_discord_enabled: false,
  security_event_minimum_level: 'warning',
  security_event_aggregation_window_seconds: 300,
  retention_days: 365,
}
