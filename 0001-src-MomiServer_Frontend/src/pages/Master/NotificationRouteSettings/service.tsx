import type { NotificationType, NotificationLevel, NotificationSourceType, NotificationTargetType, NotificationRoleCode } from '@/pages/Master/notification.constants'
export { notificationTypeLabels, notificationLevelLabels, notificationSourceTypeLabels, notificationTargetTypeLabels, notificationRoleLabels, notificationTypeOptions, notificationLevelOptions, notificationSourceTypeOptions, notificationTargetTypeOptions, notificationRoleOptions } from '@/pages/Master/notification.constants'
export type { NotificationType, NotificationLevel, NotificationSourceType, NotificationTargetType, NotificationRoleCode } from '@/pages/Master/notification.constants'
import { useAsyncResource } from '@/hooks/useAsyncResource'
import { apiClient } from '@/api/client'

export type NotificationRouteSettingItem = {
  id: number
  notification_type: NotificationType
  source_type: NotificationSourceType
  source_id?: string | null
  target_type: NotificationTargetType
  target_role?: NotificationRoleCode | null
  target_user_id?: number | null
  minimum_level: NotificationLevel
  browser_enabled: boolean
  discord_enabled: boolean
  enabled: boolean
  createdAt?: string | null
  updatedAt?: string | null
}

export type NotificationRouteSettingInput = Omit<NotificationRouteSettingItem, 'id' | 'createdAt' | 'updatedAt'> & {
  id?: number
}

type NotificationRouteSettingResponse = Partial<NotificationRouteSettingItem>

function normalizeRouteSetting(item: NotificationRouteSettingResponse): NotificationRouteSettingItem | null {
  if (!item.id || !item.notification_type || !item.source_type || !item.target_type || !item.minimum_level) {
    return null
  }

  return {
    id: item.id,
    notification_type: item.notification_type,
    source_type: item.source_type,
    source_id: item.source_id ?? null,
    target_type: item.target_type,
    target_role: item.target_role ?? null,
    target_user_id: item.target_user_id ?? null,
    minimum_level: item.minimum_level,
    browser_enabled: item.browser_enabled ?? true,
    discord_enabled: item.discord_enabled ?? false,
    enabled: item.enabled ?? true,
    createdAt: item.createdAt ?? null,
    updatedAt: item.updatedAt ?? null,
  }
}

export async function fetchNotificationRouteSettings(signal?: AbortSignal): Promise<NotificationRouteSettingItem[]> {
  const { data } = await apiClient.get<NotificationRouteSettingResponse[]>('/master/notification-route-settings', { signal })
  return data
    .map(normalizeRouteSetting)
    .filter((item): item is NotificationRouteSettingItem => item !== null)
}

export async function fetchNotificationRouteSettingDetail(id: number): Promise<NotificationRouteSettingItem> {
  const { data } = await apiClient.get<NotificationRouteSettingResponse>(`/master/notification-route-settings/${id}`)
  const item = normalizeRouteSetting(data)
  if (!item) throw new Error('通知ルート設定の取得結果が不正です。')
  return item
}

export async function saveNotificationRouteSetting(
  input: NotificationRouteSettingInput
): Promise<NotificationRouteSettingItem> {
  const payload = {
    ...input,
    source_id: input.source_id?.trim() || null,
    target_role: input.target_type === 'notification_role' ? input.target_role?.trim() || null : null,
    target_user_id: input.target_type === 'user' ? input.target_user_id ?? null : null,
  }
  const { data } = await apiClient.post<NotificationRouteSettingResponse>('/master/notification-route-settings', payload)
  const item = normalizeRouteSetting(data)
  if (!item) throw new Error('通知ルート設定の保存結果が不正です。')
  return item
}

export function useNotificationRouteSettingListService() {
  const { data: items, loading, error, reload } = useAsyncResource<NotificationRouteSettingItem[]>({
    load: fetchNotificationRouteSettings, initialData: [],
    errorMessage: '通知ルート設定一覧の取得に失敗しました。', apiErrorMessage: true,
  })
  return { items, loading, error, reload }
}

export function getRouteSettingStatusClass(enabled: boolean): string {
  return enabled
    ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300'
    : 'bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300'
}
