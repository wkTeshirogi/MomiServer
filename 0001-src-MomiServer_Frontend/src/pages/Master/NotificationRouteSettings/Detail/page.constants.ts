import { type NotificationLevel, type NotificationRoleCode, type NotificationSourceType, type NotificationTargetType, type NotificationType } from '../service'

// Fixed definitions scoped to pages/Master/NotificationRouteSettings/Detail.
export type FormState = {
  notification_type: NotificationType
  source_type: NotificationSourceType
  source_id: string
  target_type: NotificationTargetType
  target_role: NotificationRoleCode | ''
  target_user_id: string
  minimum_level: NotificationLevel
  browser_enabled: boolean
  discord_enabled: boolean
  enabled: boolean
}

export const defaultForm: FormState = {
  notification_type: 'system_notice',
  source_type: 'system',
  source_id: '',
  target_type: 'notification_role',
  target_role: '',
  target_user_id: '',
  minimum_level: 'info',
  browser_enabled: true,
  discord_enabled: false,
  enabled: true,
}
