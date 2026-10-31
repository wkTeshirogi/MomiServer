// Fixed definitions scoped to pages/Master notification settings.
export type NotificationType =
  | 'security_alert'
  | 'batch_completed'
  | 'batch_failed'
  | 'inventory_low'
  | 'vehicle_inspection_expiry'
  | 'system_notice'

export type NotificationLevel = 'info' | 'warning' | 'critical'

export type NotificationSourceType =
  | 'security_event'
  | 'batch_execution'
  | 'inventory'
  | 'vehicle'
  | 'system'

export type NotificationTargetType = 'notification_role' | 'user'

export type NotificationRoleCode = 'ROLE_ALL' | 'ROLE_ITEM_STOCK'

export const notificationTypeLabels: Record<NotificationType, string> = {
  security_alert: 'セキュリティ警告',
  batch_completed: 'バッチ完了',
  batch_failed: 'バッチ失敗',
  inventory_low: '在庫不足',
  vehicle_inspection_expiry: '車検期限',
  system_notice: 'システム通知',
}

export const notificationLevelLabels: Record<NotificationLevel, string> = {
  info: '情報以上',
  warning: '警告以上',
  critical: '重要のみ',
}

export const notificationSourceTypeLabels: Record<NotificationSourceType, string> = {
  security_event: 'セキュリティ',
  batch_execution: 'バッチ実行',
  inventory: '在庫',
  vehicle: '車両',
  system: 'システム',
}

export const notificationTargetTypeLabels: Record<NotificationTargetType, string> = {
  notification_role: '通知ロール',
  user: 'ユーザー直接',
}

export const notificationRoleLabels: Record<NotificationRoleCode, string> = {
  ROLE_ALL: '全体通知',
  ROLE_ITEM_STOCK: '在庫管理通知',
}

export const notificationTypeOptions = Object.entries(notificationTypeLabels).map(([value, label]) => ({ value, label }))

export const notificationLevelOptions = Object.entries(notificationLevelLabels).map(([value, label]) => ({ value, label }))

export const notificationSourceTypeOptions = Object.entries(notificationSourceTypeLabels).map(([value, label]) => ({ value, label }))

export const notificationTargetTypeOptions = Object.entries(notificationTargetTypeLabels).map(([value, label]) => ({ value, label }))

export const notificationRoleOptions = Object.entries(notificationRoleLabels).map(([value, label]) => ({ value, label }))

export const notificationTypes = Object.keys(notificationTypeLabels) as NotificationType[]
