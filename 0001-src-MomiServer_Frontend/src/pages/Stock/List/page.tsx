import { NotificationBanner } from '@/components/ui/notification-banner'
import { useState } from 'react'
import { Plus } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { ListPagination, ListPaginationSummary } from '@/components/ui/list-pagination'
import { useListPagination } from '@/hooks/useListPagination'
import {
  getInventoryStatusClass,
  getInventoryStatusLabel,
  getItemStatusClass,
  getItemStatusLabel,
  useStockListService,
  type StockListItem,
} from './service'

export function StockList() {
  const { items, loading, error } = useStockListService()
  const [searchTerm, setSearchTerm] = useState('')

  const normalizedSearchTerm = searchTerm.toLowerCase()
  const filteredItems = items.filter(item => {
    const master = item.master
    return [
      master?.manufacturer ?? '',
      master?.item_name ?? '',
      master?.model_number ?? '',
      master?.category ?? '',
      master?.unit ?? '',
      master?.status ?? '',
      getItemStatusLabel(master?.status ?? ''),
      item.storage_location,
      item.inventory_status,
      getInventoryStatusLabel(item.inventory_status),
      String(item.quantity),
      String(master?.min_quantity ?? ''),
      String(item.inventory_item_id),
    ].some(value => value.toLowerCase().includes(normalizedSearchTerm))
  })
  const { visibleItems, paginationProps } = useListPagination(filteredItems, searchTerm)

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
           在庫管理一覧
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            品目マスタと実在庫を合わせて、現在数量とステータスを確認できます。
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative">
            <input
              type="text"
              placeholder="品目名、メーカー、カテゴリで検索..."
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              className="w-72 rounded-lg border border-zinc-300 bg-white py-2 pl-10 pr-3 text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:ring-2 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
            />
            <svg className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>
          <Button href="/stock/new">
            <Plus className="size-4" />
            新規登録
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
          <table className="w-full min-w-[980px]">
            <thead className="bg-zinc-50 dark:bg-zinc-800/50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">メーカー</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">品目名</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">型番</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">カテゴリ</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">現在数量/最低数量</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">在庫ステータス/ステータス</th>
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
                    在庫情報が見つかりません。
                  </td>
                </tr>
              )}

              {!loading && visibleItems.map((item: StockListItem) => {
                const minimumQuantity = item.master?.min_quantity

                return (
                  <tr key={item.id} className="transition-colors hover:bg-zinc-50 dark:hover:bg-zinc-800/50">
                    <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{item.master?.manufacturer || '-'}</td>
                    <td className="px-4 py-3 text-sm font-medium text-zinc-900 dark:text-zinc-100">{item.master?.item_name || '-'}</td>
                    <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{item.master?.model_number || '-'}</td>
                    <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{item.master?.category || '-'}</td>
                    <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">
                      {item.quantity} / {minimumQuantity ?? '-'} {item.master?.unit || ''}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-2">
                        <span className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${getInventoryStatusClass(item.inventory_status)}`}>
                          {getInventoryStatusLabel(item.inventory_status) || '-'}
                        </span>
                        <span className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${getItemStatusClass(item.master?.status ?? '')}`}>
                          {getItemStatusLabel(item.master?.status ?? '') || '-'}
                        </span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <Button
                        variant="default"
                        size="sm"
                        href={`/stock/${item.id}`}
                      >
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
