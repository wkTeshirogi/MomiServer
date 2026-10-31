import { NOTIFICATION_CHANGED_EVENT } from './service.constants'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { apiClient } from '@/api/client'
import { useAsyncRequest } from '@/hooks/useAsyncRequest'
import { formatJapanDateTime } from '@/lib/utils'

export type NotificationLevel = 'info' | 'warning' | 'critical'
export type NotificationReadStatus = 'unread' | 'read' | 'dismissed'

export type NotificationItem = {
  id: number
  notification_id: string
  delivery_status: string
  read_status: NotificationReadStatus
  notification_type: string
  level: NotificationLevel
  title: string
  message: string
  source_type: string
  source_id?: string
  link_path?: string
  metadata_json?: string
  createdAt: string
  updatedAt: string
}

type NotificationResponse = {
  id?: number
  notification_id?: string
  delivery_status?: string
  read_status?: NotificationReadStatus
  notification_type?: string
  level?: NotificationLevel
  title?: string
  message?: string
  source_type?: string
  source_id?: string
  link_path?: string
  metadata_json?: string
  createdAt?: string
  updatedAt?: string
}

type UseNotificationListOptions = {
  limit?: number
  unreadOnly?: boolean
  loadAll?: boolean
}

type NotificationUnreadCountResponse = {
  unread_count?: number
}

function dispatchNotificationChanged() {

  // 更新に成功した操作だけが発火し、別コンポーネントの未読バッジを次の定期取得を待たずに再取得させる。
  window.dispatchEvent(new CustomEvent(NOTIFICATION_CHANGED_EVENT))
}

function normalizeNotification(item: NotificationResponse): NotificationItem | null {
  if (!item.id || !item.notification_id) return null

  return {
    id: item.id,
    notification_id: item.notification_id,
    delivery_status: item.delivery_status ?? 'pending',
    read_status: item.read_status ?? 'unread',
    notification_type: item.notification_type ?? '',
    level: item.level ?? 'info',
    title: item.title ?? '',
    message: item.message ?? '',
    source_type: item.source_type ?? '',
    source_id: item.source_id,
    link_path: item.link_path,
    metadata_json: item.metadata_json,
    createdAt: formatJapanDateTime(item.createdAt),
    updatedAt: formatJapanDateTime(item.updatedAt),
  }
}

export function getNotificationLevelLabel(level: string) {
  switch (level) {
    case 'critical':
      return '重要'
    case 'warning':
      return '警告'
    case 'info':
      return '情報'
    default:
      return level
  }
}

export function getNotificationLevelClass(level: string) {
  switch (level) {
    case 'critical':
      return 'bg-red-100 text-red-700 dark:bg-red-950/50 dark:text-red-300'
    case 'warning':
      return 'bg-amber-100 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300'
    case 'info':
      return 'bg-sky-100 text-sky-700 dark:bg-sky-950/50 dark:text-sky-300'
    default:
      return 'bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300'
  }
}

export function getNotificationTypeLabel(type: string) {
  switch (type) {
    case 'security_alert':
      return 'セキュリティ'
    case 'batch_completed':
      return 'バッチ完了'
    case 'batch_failed':
      return 'バッチ失敗'
    case 'inventory_low':
      return '在庫'
    case 'vehicle_inspection_expiry':
      return '車検期限'
    case 'system_notice':
      return 'システム'
    default:
      return type
  }
}

export async function fetchNotifications(limit = 20, offset = 0, signal?: AbortSignal): Promise<NotificationItem[]> {
  const response = await apiClient.get<NotificationResponse[]>('/notifications', {
    params: { limit, offset }, signal,
  })
  const data: NotificationResponse[] = response.data

  return data
    .map(normalizeNotification)
    .filter((item): item is NotificationItem => item !== null)
}

export async function fetchUnreadNotifications(limit = 20, offset = 0, signal?: AbortSignal): Promise<NotificationItem[]> {
  const response = await apiClient.get<NotificationResponse[]>('/notifications/unread', {
    params: { limit, offset }, signal,
  })
  const data: NotificationResponse[] = response.data

  return data
    .map(normalizeNotification)
    .filter((item): item is NotificationItem => item !== null)
}

async function fetchAllNotifications(unreadOnly: boolean, signal: AbortSignal): Promise<NotificationItem[]> {
  const pageSize = 100
  const items: NotificationItem[] = []
  let cursor: string | undefined
  const seenCursors = new Set<string>()

  // 新着は次の再読込で取得する。取得途中の削除・追加でoffsetがずれるのを避ける。
  for (;;) {
    const { data } = await apiClient.get<{ items: NotificationResponse[]; next_cursor?: string | null }>('/notifications/page', {
      params: { limit: pageSize, unreadOnly, cursor }, signal,
    })
    items.push(...data.items.map(normalizeNotification).filter((item): item is NotificationItem => item !== null))
    if (!data.next_cursor) return items
    if (seenCursors.has(data.next_cursor)) throw new Error('Repeated notification cursor')
    seenCursors.add(data.next_cursor)
    cursor = data.next_cursor
  }
}

export async function fetchUnreadNotificationCount(): Promise<number> {
  const { data } = await apiClient.get<NotificationUnreadCountResponse>('/notifications/unread/count')
  return data.unread_count ?? 0
}

export async function markNotificationRead(notificationId: string): Promise<void> {
  await apiClient.post(`/notifications/${notificationId}/read`)
  dispatchNotificationChanged()
}

export async function dismissNotification(notificationId: string): Promise<void> {
  await apiClient.post(`/notifications/${notificationId}/dismiss`)
  dispatchNotificationChanged()
}

export function useNotificationList(options: UseNotificationListOptions = {}) {
  const { limit = 20, unreadOnly = false, loadAll = false } = options
  const [items, setItems] = useState<NotificationItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [updating, setUpdating] = useState(false)
  const updateInProgress = useRef(false)
  const { run, cancel } = useAsyncRequest()

  const refresh = useCallback(() => run(signal => loadAll
    ? fetchAllNotifications(unreadOnly, signal)
    : unreadOnly ? fetchUnreadNotifications(limit, 0, signal) : fetchNotifications(limit, 0, signal), {
    onStart: () => { setLoading(true); setError(null) },
    onSuccess: setItems,
    onError: () => setError('通知一覧の取得に失敗しました。'),
    onSettled: () => setLoading(false),
  }), [limit, loadAll, unreadOnly, run])

  const markRead = useCallback(async (notificationId: string) => {

    // stateの再描画を待たずrefで連打を防ぎ、既読・非表示操作が同時に送られるのを避ける。
    if (updateInProgress.current) return
    updateInProgress.current = true
    setUpdating(true)
    setError(null)
    try {
      await markNotificationRead(notificationId)
      setItems(currentItems => unreadOnly
        ? currentItems.filter(item => item.notification_id !== notificationId)
        : currentItems.map(item => item.notification_id === notificationId
          ? { ...item, read_status: 'read' }
          : item
        )
      )
    } catch {
      setError('通知の既読更新に失敗しました。もう一度お試しください。')
    } finally {
      updateInProgress.current = false
      setUpdating(false)
    }
  }, [unreadOnly])

  const dismiss = useCallback(async (notificationId: string) => {
    if (updateInProgress.current) return
    updateInProgress.current = true
    setUpdating(true)
    setError(null)
    try {
      await dismissNotification(notificationId)
      setItems(currentItems => currentItems.filter(item => item.notification_id !== notificationId))
    } catch {
      setError('通知の非表示更新に失敗しました。もう一度お試しください。')
    } finally {
      updateInProgress.current = false
      setUpdating(false)
    }
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(() => { void refresh() }, 0)
    return () => { window.clearTimeout(timer); cancel() }
  }, [refresh, cancel])

  return useMemo(
    () => ({
      items,
      loading,
      error,
      updating,
      refresh,
      markRead,
      dismiss,
    }),
    [items, loading, error, updating, refresh, markRead, dismiss]
  )
}

export function useUnreadNotificationCount(pollingIntervalMs = 30_000) {
  const [count, setCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const requestSequence = useRef(0)

  const refresh = useCallback(async () => {
    const sequence = ++requestSequence.current

    // 定期取得・タブ復帰・更新イベントが重なった場合も、最後に開始した要求の件数だけを採用する。
    try {
      const nextCount = await fetchUnreadNotificationCount()
      if (sequence === requestSequence.current) setCount(nextCount)
    } catch {
      // Keep the last confirmed count when a polling request fails.
    } finally {
      if (sequence === requestSequence.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(() => { void refresh() }, 0)

    const intervalId = window.setInterval(() => {
      void refresh()
    }, pollingIntervalMs)

    const handleVisibilityChange = () => {
      if (!document.hidden) {
        void refresh()
      }
    }

    const handleNotificationChanged = () => {
      void refresh()
    }

    document.addEventListener('visibilitychange', handleVisibilityChange)
    window.addEventListener(NOTIFICATION_CHANGED_EVENT, handleNotificationChanged)

    return () => {
      window.clearTimeout(timer)
      window.clearInterval(intervalId)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
      window.removeEventListener(NOTIFICATION_CHANGED_EVENT, handleNotificationChanged)
    }
  }, [pollingIntervalMs, refresh])

  return useMemo(
    () => ({
      count,
      loading,
      refresh,
    }),
    [count, loading, refresh]
  )
}
