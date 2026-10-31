import { NotificationBanner } from '@/components/ui/notification-banner'
import { Button } from '@/components/ui/button'
import { ListPagination, ListPaginationSummary } from '@/components/ui/list-pagination'
import { useListPagination } from '@/hooks/useListPagination'
import {
  getNotificationLevelClass,
  getNotificationLevelLabel,
  getNotificationTypeLabel,
  useNotificationList,
} from '@/pages/Notifications/service'

export function AlertsPage() {
  const {
    items,
    loading,
    error,
    updating,
    markRead,
    dismiss,
    refresh,
  } = useNotificationList({ loadAll: true })
  const { visibleItems, paginationProps } = useListPagination(items)

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            アラート
          </h1>
          <p className="text-sm text-zinc-500">
            システムからの通知・警告を確認します。
          </p>
        </div>
        <Button variant="outline" disabled={loading || updating} onClick={() => void refresh()}>
          再読込
        </Button>
      </div>

      {!loading && error && <NotificationBanner type="error">{error}</NotificationBanner>}

      <ListPaginationSummary {...paginationProps} loading={loading || updating} />

      <div className="overflow-x-auto rounded-lg border border-gray-700">
        <table className="w-full min-w-[760px] table-auto text-sm">
          <thead className="bg-white dark:bg-zinc-900">
            <tr className="border-b border-gray-700">
              <th className="w-44 px-3 py-2 text-left font-semibold whitespace-nowrap">時刻</th>
              <th className="w-24 px-3 py-2 text-left font-semibold whitespace-nowrap">重要度</th>
              <th className="w-32 px-3 py-2 text-left font-semibold whitespace-nowrap">種別</th>
              <th className="px-3 py-2 text-left font-semibold">内容</th>
              <th className="w-40 px-3 py-2 text-right font-semibold whitespace-nowrap">操作</th>
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr>
                <td className="px-3 py-6 text-zinc-500" colSpan={5}>通知を読み込み中です。</td>
              </tr>
            )}
            {!loading && !error && items.length === 0 && (
              <tr>
                <td className="px-3 py-6 text-zinc-500" colSpan={5}>表示できる通知はありません。</td>
              </tr>
            )}
            {!loading && visibleItems.map(item => (
              <tr key={item.id} className="border-b border-gray-700 align-top hover:bg-zinc-100 dark:hover:bg-zinc-800">
                <td className="px-3 py-3 whitespace-nowrap">{item.createdAt}</td>
                <td className="px-3 py-3 whitespace-nowrap">
                  <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${getNotificationLevelClass(item.level)}`}>
                    {getNotificationLevelLabel(item.level)}
                  </span>
                </td>
                <td className="px-3 py-3 whitespace-nowrap">{getNotificationTypeLabel(item.notification_type)}</td>
                <td className="px-3 py-3">
                  <div className="font-semibold text-zinc-900 dark:text-white">{item.title}</div>
                  <div className="mt-1 whitespace-pre-wrap text-zinc-600 dark:text-zinc-300">{item.message}</div>
                  {item.link_path && (
                    <a className="mt-2 inline-flex text-purple-500 hover:text-purple-300" href={item.link_path}>
                      関連画面を開く
                    </a>
                  )}
                </td>
                <td className="px-3 py-3 text-right">
                  <div className="flex justify-end gap-2">
                    {item.read_status === 'unread' && (
                      <Button variant="outline" size="sm" disabled={updating} onClick={() => void markRead(item.notification_id)}>
                        既読
                      </Button>
                    )}
                    <Button variant="ghost" size="sm" disabled={updating} onClick={() => void dismiss(item.notification_id)}>
                      非表示
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ListPagination {...paginationProps} loading={loading || updating} />
    </div>
  )
}
