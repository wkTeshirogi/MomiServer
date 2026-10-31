import { NotificationBanner } from '@/components/ui/notification-banner'
import { useState } from 'react'
import { Plus } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { ListPagination, ListPaginationSummary } from '@/components/ui/list-pagination'
import { useListPagination } from '@/hooks/useListPagination'
import {
  getItemStatusClass,
  getItemStatusLabel,
  useItemMasterService,
  type ItemMasterItem,
} from './service'

export function ItemMasterList() {
  const { items, loading, error } = useItemMasterService()
  const [searchTerm, setSearchTerm] = useState('')

  const normalizedSearchTerm = searchTerm.toLowerCase()
  const filteredItems = items.filter(item => {
    return [
      item.item_name,
      item.category,
      item.unit,
      String(item.min_quantity),
      item.manufacturer,
      item.model_number,
      item.jan_code,
      item.status,
      getItemStatusLabel(item.status),
    ].some(value => value.toLowerCase().includes(normalizedSearchTerm))
  })
  const { visibleItems, paginationProps } = useListPagination(filteredItems, searchTerm)

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            在庫品目マスタ一覧
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            備品・消耗品の品目名、カテゴリ、単位、購入情報を確認できます。
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative">
            <input
              type="text"
              placeholder="品目名、カテゴリ、JANコードで検索..."
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              className="w-72 rounded-lg border border-zinc-300 bg-white py-2 pl-10 pr-3 text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:ring-2 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
            />
            <svg className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>
          <Button href="/master/items/new">
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
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">品目名</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">カテゴリ</th>

                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">最低数量/単位</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">メーカー</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">型番</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">JANコード</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">ステータス</th>
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
                    在庫品目マスタが見つかりません。
                  </td>
                </tr>
              )}

              {!loading && visibleItems.map((item: ItemMasterItem) => (
                <tr key={item.id} className="transition-colors hover:bg-zinc-50 dark:hover:bg-zinc-800/50">
                  <td className="px-4 py-3 text-sm font-medium text-zinc-900 dark:text-zinc-100">{item.item_name || '-'}</td>
                  <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{item.category || '-'}</td>
                  <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{item.min_quantity} {item.unit || '-'}</td>
                  <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{item.manufacturer || '-'}</td>
                  <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{item.model_number || '-'}</td>
                  <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{item.jan_code || '-'}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${getItemStatusClass(item.status)}`}>
                      {getItemStatusLabel(item.status)}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <Button
                      variant="default"
                      size="sm"
                      href={`/master/items/${item.id}`}
                    >
                      詳細
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <ListPagination {...paginationProps} loading={loading} />
    </div>
  )
}
