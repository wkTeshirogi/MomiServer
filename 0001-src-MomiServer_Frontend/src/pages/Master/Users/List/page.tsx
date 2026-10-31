import { NotificationBanner } from '@/components/ui/notification-banner'
import { useState } from 'react'
import { RefreshCw, Search, UserPlus } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { ListPagination, ListPaginationSummary } from '@/components/ui/list-pagination'
import { useListPagination } from '@/hooks/useListPagination'
import { useUserMasterService, type UserMasterItem } from './service'

export function UserMasterList() {
  const { users, loading, error, reload, roleLabels, roleClasses } = useUserMasterService()
  const [searchTerm, setSearchTerm] = useState('')

  const filteredUsers = users.filter(user =>
    [user.email, roleLabels[user.role]]
      .some(value => value.toLowerCase().includes(searchTerm.toLowerCase()))
  )
  const { visibleItems: visibleUsers, paginationProps } = useListPagination(filteredUsers, searchTerm)

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            ユーザーマスター一覧
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            登録されているユーザーの確認、作成、編集、利用停止を行います。
          </p>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          <div className="relative">
            <input
              type="text"
              placeholder="メールアドレス・権限で検索..."
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              className="h-9 w-full rounded-full border border-zinc-300 bg-white py-2 pl-9 pr-3 text-sm text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:ring-2 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100 sm:w-72"
            />
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-zinc-400" />
          </div>
          <Button variant="outline" disabled={loading} onClick={() => void reload()}>
            <RefreshCw className="size-4" />再読込
          </Button>
          <Button href="/master/users/new" className="gap-2">
            <UserPlus className="size-4" />
            新規作成
          </Button>
        </div>
      </div>

      {error && (
        <NotificationBanner type="error">
          {error}
        </NotificationBanner>
      )}

      <ListPaginationSummary {...paginationProps} overallItems={users.length} loading={loading} />

      <Card className="overflow-hidden rounded-lg border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px]">
            <thead className="bg-zinc-50 dark:bg-zinc-800/50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">メールアドレス</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">権限/状態</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">作成日時</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">更新日時</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">最終ログイン</th>
                <th className="px-4 py-3 text-center text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
              {loading && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-sm text-zinc-500 dark:text-zinc-400">
                    読み込み中...
                  </td>
                </tr>
              )}

              {!loading && filteredUsers.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-sm text-zinc-500 dark:text-zinc-400">
                    対象のユーザーはありません。
                  </td>
                </tr>
              )}

              {!loading && visibleUsers.map((user: UserMasterItem) => (
                <tr key={user.id} className="transition-colors hover:bg-zinc-50 dark:hover:bg-zinc-800/50">
                  <td className="px-4 py-3 text-sm text-zinc-900 dark:text-zinc-100">{user.email}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${roleClasses[user.role]}`}>
                      {roleLabels[user.role]}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{user.createdAt}</td>
                  <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{user.updatedAt}</td>
                  <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">
                    {user.lastLoginAt ?? '未ログイン'}
                  </td>
                  <td className="px-4 py-3 text-center">
                    <Button
                      variant="default"
                      size="sm"
                      href={`/master/users/${user.id}`}
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
