import { NotificationBanner } from '@/components/ui/notification-banner'
import { useState } from 'react'
import { Plus, Search } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { ListPagination, ListPaginationSummary } from '@/components/ui/list-pagination'
import { useListPagination } from '@/hooks/useListPagination'
import {
  getRouteSettingStatusClass,
  notificationLevelLabels,
  notificationSourceTypeLabels,
  notificationTargetTypeLabels,
  notificationTypeLabels,
  useNotificationRouteSettingListService,
} from '../service'

export function NotificationRouteSettingList() {
  const { items, loading, error } = useNotificationRouteSettingListService()
  const [searchTerm, setSearchTerm] = useState('')

  const normalizedSearchTerm = searchTerm.toLowerCase()
  const filteredItems = items.filter(item => {
    const target = item.target_type === 'user'
      ? `user ${item.target_user_id ?? ''}`
      : item.target_role ?? ''

    return [
      notificationTypeLabels[item.notification_type],
      notificationSourceTypeLabels[item.source_type],
      item.source_id ?? '全体',
      notificationTargetTypeLabels[item.target_type],
      target,
      notificationLevelLabels[item.minimum_level],
      item.enabled ? 'enabled' : 'disabled',
    ].some(value => value.toLowerCase().includes(normalizedSearchTerm))
  })
  const { visibleItems, paginationProps } = useListPagination(filteredItems, searchTerm)

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            通知ルート設定一覧
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            通知種別・発生元ごとの配送先と通知チャネルを管理します。
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-zinc-400" />
            <input
              type="text"
              placeholder="通知種別、発生元、宛先で検索..."
              value={searchTerm}
              onChange={event => setSearchTerm(event.target.value)}
              className="w-72 rounded-lg border border-zinc-300 bg-white py-2 pl-10 pr-3 text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:ring-2 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
            />
          </div>
          <Button href="/master/notification-route-settings/new" className="gap-2">
            <Plus className="size-4" />
            新規作成
          </Button>
        </div>
      </div>

      {error && (
        <NotificationBanner type="error">
          {error}
        </NotificationBanner>
      )}

      <ListPaginationSummary {...paginationProps} overallItems={items.length} loading={loading} />

      <Card className="overflow-hidden rounded-lg border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1040px]">
            <thead className="bg-zinc-50 dark:bg-zinc-800/50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">通知種別</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">発生元</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">発生元ID</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">宛先</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">最低レベル</th>
                <th className="px-4 py-3 text-center text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">通知先</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">状態</th>
                <th className="px-4 py-3 text-center text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
              {loading && (
                <tr>
                  <td colSpan={8} className="px-4 py-8 text-center text-sm text-zinc-500 dark:text-zinc-400">
                    読み込み中...
                  </td>
                </tr>
              )}

              {!loading && filteredItems.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-8 text-center text-sm text-zinc-500 dark:text-zinc-400">
                    通知ルート設定が見つかりません。
                  </td>
                </tr>
              )}

              {!loading && visibleItems.map(item => {
                const targetLabel = item.target_type === 'user'
                  ? `ユーザーID ${item.target_user_id ?? '-'}`
                  : item.target_role ?? '-'

                return (
                  <tr key={item.id} className="transition-colors hover:bg-zinc-50 dark:hover:bg-zinc-800/50">
                    <td className="px-4 py-3 text-sm font-medium text-zinc-900 dark:text-zinc-100">
                      {notificationTypeLabels[item.notification_type]}
                    </td>
                    <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">
                      {notificationSourceTypeLabels[item.source_type]}
                    </td>
                    <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">
                      {item.source_id || '全体'}
                    </td>
                    <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">
                      <div className="font-medium text-zinc-900 dark:text-zinc-100">
                        {notificationTargetTypeLabels[item.target_type]}
                      </div>
                      <div className="text-xs text-zinc-500 dark:text-zinc-400">
                        {targetLabel}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">
                      {notificationLevelLabels[item.minimum_level]}
                    </td>
                    <td className="px-4 py-3 text-center text-sm text-zinc-500 dark:text-zinc-400">
                      {item.browser_enabled ? 'Browser' : '-'} / {item.discord_enabled ? 'Discord' : '-'}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${getRouteSettingStatusClass(item.enabled)}`}>
                        {item.enabled ? 'ENABLED' : 'DISABLED'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <Button size="sm" href={`/master/notification-route-settings/${item.id}`}>
                        詳細
                      </Button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </Card>

      <ListPagination {...paginationProps} loading={loading} />
    </div>
  )
}
