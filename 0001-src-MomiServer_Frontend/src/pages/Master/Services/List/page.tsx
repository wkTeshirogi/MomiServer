import { useState, useMemo } from 'react'
import { Plus, RefreshCw } from 'lucide-react'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { ListPagination, ListPaginationSummary } from '@/components/ui/list-pagination'
import { useListPagination } from '@/hooks/useListPagination'
import { useServicesMasterService } from './service'

export function ServicesMasterList() {
  const { users, devices, loading, error, reload, statusClasses } = useServicesMasterService()
  const [searchTerm, setSearchTerm] = useState('')

  const filteredUsers = users.filter(user =>
    user.view_name.toLowerCase().includes(searchTerm.toLowerCase())
  )
  const deviceMasterMap = useMemo(() => {
  return new Map(devices.map(master => [master.id, master]))
}, [devices])
  const { visibleItems: visibleUsers, paginationProps } = useListPagination(filteredUsers, searchTerm)

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            サービスマスタ一覧
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            登録されているサービスの一覧を表示します。
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" disabled={loading} onClick={reload}><RefreshCw className="size-4" />再読込</Button>
          <div className="relative">
            <input
              type="text"
              placeholder="サービス名で検索..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-64 pl-10 pr-3 py-2 rounded-lg border bg-white dark:bg-zinc-800 border-zinc-300 dark:border-zinc-700 text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 focus:outline-none focus:ring-2 focus:ring-purple-500"
            />
            <svg className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>
          <Button href="/master/services/new">
            <Plus className="size-4" />
            新規登録
          </Button>
        </div>
      </div>

      {error && <NotificationBanner type="error">{error}</NotificationBanner>}
      <ListPaginationSummary {...paginationProps} overallItems={users.length} loading={loading} />

      <Card className="rounded-lg border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[700px]">
            <thead className="bg-zinc-50 dark:bg-zinc-800/50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">サービス表示名</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">該当端末</th>
                <th className="px-2 py-2 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">状況</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">作成日時</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">更新日時</th>
                <th className="px-4 py-3 text-center text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
              {loading && <tr><td colSpan={6} className="p-6 text-center" role="status">読み込み中...</td></tr>}
              {!loading && !error && visibleUsers.length === 0 && <tr><td colSpan={6} className="p-6 text-center">表示できるサービスはありません。</td></tr>}
              {visibleUsers.map(user => (
                <tr key={user.id} className="hover:bg-zinc-50 dark:hover:bg-zinc-800/50 transition-colors">
                  <td className="px-4 py-3 text-sm text-zinc-900 dark:text-zinc-100">{user.view_name}</td>
                  <td className="px-4 py-3">
                     {deviceMasterMap.get(user.target_machine)?.devicename ?? '未登録'}
                     ({deviceMasterMap.get(user.target_machine)?.address ?? 'XXX.XXX.XXX.XXX'})
                  </td>
                  <td className="px-2 py-2 text-sm text-zinc-500 dark:text-zinc-400">
                  <span className={`inline-flex px-2 py-1 rounded-full text-xs font-medium ${statusClasses[user.status] ?? statusClasses.stop}`}>
                        {(user.status || 'STOP').toUpperCase()}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{user.createdAt}</td>
                  <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{user.updatedAt}</td>
                  <td className="px-4 py-3 text-center">
                    <Button
                      variant="default"
                      size="sm"
                      href={"/master/services/" + user.id}
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
