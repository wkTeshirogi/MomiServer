import { NotificationBanner } from '@/components/ui/notification-banner'
import { useState } from 'react'
import { Plus, RefreshCw } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { ListPagination, ListPaginationSummary } from '@/components/ui/list-pagination'
import { useListPagination } from '@/hooks/useListPagination'
import { useMachineMasterService, type MachineMasterItem } from './service'
import { getMachineStatusClass, getMachineStatusLabel } from '../status'

export function MachineMasterList() {
  const { machines, loading, error, reload } = useMachineMasterService()
  const [searchTerm, setSearchTerm] = useState('')

  const normalizedSearchTerm = searchTerm.toLowerCase()
  const filteredMachines = machines.filter(machine => {
    return [
      machine.devicename,
      machine.address,
      machine.location,
      machine.mqttClientId,
      machine.status,
      getMachineStatusLabel(machine.status),
    ].some(value => value.toLowerCase().includes(normalizedSearchTerm))
  })
  const { visibleItems, paginationProps } = useListPagination(filteredMachines, searchTerm)

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            端末マスタ一覧
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            監視対象端末の接続情報と状態を確認できます。
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative">
            <input
              type="text"
              placeholder="端末名、IP、設置場所で検索..."
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              className="w-72 rounded-lg border border-zinc-300 bg-white py-2 pl-10 pr-3 text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:ring-2 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
            />
            <svg className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>
          <Button variant="outline" disabled={loading} onClick={() => void reload()}>
            <RefreshCw className="size-4" />再読込
          </Button>
          <Button href="/master/machine/new">
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

      <ListPaginationSummary {...paginationProps} overallItems={machines.length} loading={loading} />

      <Card className="overflow-hidden rounded-lg border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[820px]">
            <thead className="bg-zinc-50 dark:bg-zinc-800/50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">端末名</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">アドレス</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">設置場所</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">MQTT Client ID</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-zinc-500 dark:text-zinc-400">状態</th>
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

              {!loading && filteredMachines.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-sm text-zinc-500 dark:text-zinc-400">
                    端末マスタが見つかりません。
                  </td>
                </tr>
              )}

              {!loading && visibleItems.map((machine: MachineMasterItem) => (
                <tr key={machine.id} className="transition-colors hover:bg-zinc-50 dark:hover:bg-zinc-800/50">
                  <td className="px-4 py-3 text-sm font-medium text-zinc-900 dark:text-zinc-100">{machine.devicename || '-'}</td>
                  <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{machine.address || '-'}</td>
                  <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{machine.location || '-'}</td>
                  <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{machine.mqttClientId || '-'}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${getMachineStatusClass(machine.status)}`}>
                      {getMachineStatusLabel(machine.status)}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <Button
                      variant="default"
                      size="sm"
                      href={`/master/machine/${machine.id}`}
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
