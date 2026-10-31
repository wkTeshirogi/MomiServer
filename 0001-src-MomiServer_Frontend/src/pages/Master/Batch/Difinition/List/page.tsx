import { NotificationBanner } from '@/components/ui/notification-banner'
import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Search, Plus, RefreshCw } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { ListPagination, ListPaginationSummary } from '@/components/ui/list-pagination'
import { useListPagination } from '@/hooks/useListPagination'
import { useBatchDefinitionListService } from './service'

export function BatchDefinitionList() {
	const location = useLocation()
	const navigate = useNavigate()
	const [notice, setNotice] = useState<string | null>(() => {
		const state = location.state as { notice?: string } | null
		return state?.notice ?? null
	})
  const {
    batchDefinitions,
    loading,
    error,
    reload,
    enabledClasses,
    enabledLabels,
  } = useBatchDefinitionListService()
  const [searchTerm, setSearchTerm] = useState('')

	useEffect(() => {
		if (location.state) navigate(location.pathname, { replace: true, state: null })
	}, [location.pathname, location.state, navigate])

  const filteredBatchDefinitions = useMemo(() => {
    const term = searchTerm.trim().toLowerCase()
    if (!term) return batchDefinitions

    return batchDefinitions.filter(item =>
      [
        item.batch_name,
        item.view_name,
        item.category,
        item.execution_type,
        item.execution_command,
        item.tags,
      ].some(value => value.toLowerCase().includes(term))
    )
  }, [batchDefinitions, searchTerm])
  const { visibleItems, paginationProps } = useListPagination(filteredBatchDefinitions, searchTerm)

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            バッチ定義一覧
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            登録されているバッチ定義の一覧を表示します。
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative">
            <input
              type="text"
              placeholder="バッチ名・表示名で検索..."
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              className="w-64 rounded-lg border border-zinc-300 bg-white py-2 pl-10 pr-3 text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:ring-2 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
            />
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-zinc-400" />
          </div>
          <Button variant="outline" onClick={() => void reload()} disabled={loading}>
            <RefreshCw className="size-4" />
            再読込
          </Button>
          <Button href="/master/batch/difinition/new">
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
		{notice && (
			<NotificationBanner type="success" onClose={() => setNotice(null)}>
				{notice}
			</NotificationBanner>
		)}

      <ListPaginationSummary {...paginationProps} overallItems={batchDefinitions.length} loading={loading} />

      <Card className="overflow-hidden rounded-lg border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1120px]">
            <thead className="bg-zinc-50 dark:bg-zinc-800/50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">ID</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">バッチ名</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">表示名</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">カテゴリ</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">実行種別</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">実行コマンド</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">優先度</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">状態</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">更新日時</th>
                <th className="px-4 py-3 text-center text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
              {loading && (
                <tr>
                  <td colSpan={10} className="px-4 py-8 text-center text-sm text-zinc-500 dark:text-zinc-400">
                    読み込み中...
                  </td>
                </tr>
              )}

              {!loading && filteredBatchDefinitions.length === 0 && (
                <tr>
                  <td colSpan={10} className="px-4 py-8 text-center text-sm text-zinc-500 dark:text-zinc-400">
                    表示できるバッチ定義がありません。
                  </td>
                </tr>
              )}

              {!loading && visibleItems.map(item => {
                const enabledKey = String(item.enabled)

                return (
                  <tr key={item.id} className="transition-colors hover:bg-zinc-50 dark:hover:bg-zinc-800/50">
                    <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{item.id}</td>
                    <td className="px-4 py-3 text-sm font-medium text-zinc-900 dark:text-zinc-100">{item.batch_name}</td>
                    <td className="px-4 py-3 text-sm text-zinc-900 dark:text-zinc-100">{item.view_name}</td>
                    <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{item.category}</td>
                    <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{item.execution_type}</td>
                    <td className="max-w-[280px] truncate px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400" title={item.execution_command}>
                      {item.execution_command || '-'}
                    </td>
                    <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{item.priority}</td>
                    <td className="px-4 py-3 text-sm">
                      <span className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${enabledClasses[enabledKey]}`}>
                        {enabledLabels[enabledKey]}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{item.updatedAt}</td>
                    <td className="px-4 py-3 text-center">
                      <Button
                        variant="default"
                        size="sm"
                        href={`/master/batch/difinition/${item.id}`}
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
