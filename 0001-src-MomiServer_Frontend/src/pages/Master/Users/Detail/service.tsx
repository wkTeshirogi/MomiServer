import type { NotificationType, NotificationLevel, NotificationSourceType } from '@/pages/Master/notification.constants'
export { notificationTypeLabels, notificationLevelLabels, notificationSourceTypeLabels, notificationLevelOptions, notificationTypes, notificationTypeOptions } from '@/pages/Master/notification.constants'
export type { NotificationType, NotificationLevel, NotificationSourceType } from '@/pages/Master/notification.constants'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAsyncRequest } from '@/hooks/useAsyncRequest'
import { apiClient } from '@/api/client'
import { getApiErrorMessage, getApiFieldErrors } from '@/lib/apiError'
import {
  createUser,
  disableUser,
  fetchUserMasterDetail,
  roleClasses,
  roleLabels,
  updateUser,
  type SaveUserInput,
  type UserMasterItem,
} from '../List/service'

export type { SaveUserInput, UserMasterItem, UserRole } from '../List/service'
export { createUser, disableUser, roleClasses, roleLabels, updateUser }

export type FieldName = 'email' | 'password' | 'role'
export type FieldErrors = Partial<Record<FieldName, string>>

export type UserNotificationPreferenceItem = {
  notification_type: NotificationType
  minimum_level: NotificationLevel
  browser_enabled: boolean
  discord_enabled: boolean
  enabled: boolean
}

export type UserNotificationRouteSettingItem = {
  id: number
  notification_type: NotificationType
  source_type: NotificationSourceType
  source_id?: string
  target_user_id: number
  minimum_level: NotificationLevel
  browser_enabled: boolean
  discord_enabled: boolean
  enabled: boolean
}

type UserNotificationPreferenceResponse = Partial<UserNotificationPreferenceItem>
type UserNotificationRouteSettingResponse = Partial<UserNotificationRouteSettingItem>

function normalizeNotificationPreference(
  item: UserNotificationPreferenceResponse,
  fallbackType: NotificationType
): UserNotificationPreferenceItem {
  return {
    notification_type: item.notification_type ?? fallbackType,
    minimum_level: item.minimum_level ?? 'info',
    browser_enabled: item.browser_enabled ?? true,
    discord_enabled: item.discord_enabled ?? true,
    enabled: item.enabled ?? true,
  }
}

function normalizeNotificationRouteSetting(
  item: UserNotificationRouteSettingResponse
): UserNotificationRouteSettingItem | null {
  if (!item.id || !item.notification_type || !item.source_type || !item.target_user_id) {
    return null
  }

  return {
    id: item.id,
    notification_type: item.notification_type,
    source_type: item.source_type,
    source_id: item.source_id,
    target_user_id: item.target_user_id,
    minimum_level: item.minimum_level ?? 'info',
    browser_enabled: item.browser_enabled ?? true,
    discord_enabled: item.discord_enabled ?? false,
    enabled: item.enabled ?? true,
  }
}

export async function fetchUserNotificationPreferences(userId: number): Promise<UserNotificationPreferenceItem[]> {
  const { data } = await apiClient.get<UserNotificationPreferenceResponse[]>(
    `/master/user/${userId}/notification-preferences`
  )
  return data
    .filter((item): item is UserNotificationPreferenceResponse & { notification_type: NotificationType } =>
      Boolean(item.notification_type)
    )
    .map(item => normalizeNotificationPreference(item, item.notification_type))
}

export async function saveUserNotificationPreferences(
  userId: number,
  preferences: UserNotificationPreferenceItem[]
): Promise<UserNotificationPreferenceItem[]> {
  const { data } = await apiClient.post<UserNotificationPreferenceResponse[]>(
    `/master/user/${userId}/notification-preferences`,
    { preferences }
  )
  return data
    .filter((item): item is UserNotificationPreferenceResponse & { notification_type: NotificationType } =>
      Boolean(item.notification_type)
    )
    .map(item => normalizeNotificationPreference(item, item.notification_type))
}

export async function fetchUserNotificationRouteSettings(userId: number): Promise<UserNotificationRouteSettingItem[]> {
  const { data } = await apiClient.get<UserNotificationRouteSettingResponse[]>(
    `/master/user/${userId}/notification-route-settings`
  )

  return data
    .map(normalizeNotificationRouteSetting)
    .filter((item): item is UserNotificationRouteSettingItem => item !== null)
}

function normalizeFieldName(field: unknown): FieldName | null {
  if (typeof field !== 'string') return null

  const lowerField = field.toLowerCase()
  if (lowerField.endsWith('email') || lowerField === 'mail') return 'email'
  if (lowerField.endsWith('password')) return 'password'
  if (lowerField.endsWith('role') || lowerField.endsWith('status')) return 'role'

  return null
}

export function collectFieldErrors(source: unknown): { fieldErrors: FieldErrors; formError: string | null } {
  const fieldErrors: FieldErrors = {}
  for (const [field, message] of Object.entries(getApiFieldErrors(source))) {
    const name = normalizeFieldName(field)
    if (name && !fieldErrors[name]) fieldErrors[name] = message
  }
  return {
    fieldErrors,
    formError: getApiErrorMessage(source),
  }
}

export function useUserMasterDetailService(userId?: string) {
  const { run, cancel } = useAsyncRequest()
  const isNew = !userId || userId === 'new'
  const [user, setUser] = useState<UserMasterItem | null>(null)
  const [loading, setLoading] = useState(!isNew)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    if (isNew) {
      setUser(null)
      setLoading(false)
      setError(null)
      return
    }

    const numericUserId = Number(userId)
    if (!Number.isFinite(numericUserId)) {
      setError('ユーザーIDが不正です。')
      setLoading(false)
      return
    }

    await run(() => fetchUserMasterDetail(numericUserId), {
      onStart: () => { setLoading(true); setError(null) },
      onSuccess: setUser,
      onError: () => setError('ユーザー詳細の取得に失敗しました。'),
      onSettled: () => setLoading(false),
    })
  }, [isNew, userId, run])

  useEffect(() => {
    const timer = window.setTimeout(() => { void reload() }, 0)
    return () => { window.clearTimeout(timer); cancel() }
  }, [reload, cancel])

  const save = useCallback(
    async (input: SaveUserInput) => {
      if (isNew) {
        await createUser(input)
        return
      }

      await updateUser({
        ...input,
        id: user?.id,
      })
    },
    [isNew, user?.id]
  )

  return useMemo(
    () => ({
      user,
      loading,
      error,
      isNew,
      reload,
      save,
      disableUser,
      roleLabels,
      roleClasses,
    }),
    [user, loading, error, isNew, reload, save]
  )
}
