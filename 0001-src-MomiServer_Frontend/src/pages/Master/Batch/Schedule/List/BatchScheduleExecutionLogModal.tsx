import { statusLabels } from './BatchScheduleExecutionLogModal.constants'
import { formatJapanDateTime } from '@/lib/utils'
import { useCallback, useState } from 'react'
import { useAsyncResource } from '@/hooks/useAsyncResource'
import { Button } from '@/components/ui/button'
import { ListPagination, ListPaginationSummary } from '@/components/ui/list-pagination'
import { DEFAULT_LIST_PAGE_SIZE, type ListPageSize } from '@/constants/pagination'
import { Modal } from '@/components/ui/modal'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { fetchBatchScheduleExecutionLogs, type BatchScheduleExecutionLog } from './service'

type LogSelection = { scheduleId: number; definitionId: number; date: string; hour?: number }

function displayDateTime(value: string | null): string {
  return formatJapanDateTime(value, { milliseconds: false })
}

export function BatchScheduleExecutionLogModal({
  selection,
  onClose,
}: {
  selection: LogSelection
  onClose: () => void
}) {
  const [pageSize, setPageSize] = useState<ListPageSize>(DEFAULT_LIST_PAGE_SIZE)
  const [pageIndex, setPageIndex] = useState(0)
  const load = useCallback(async (signal: AbortSignal) => {
    const page = await fetchBatchScheduleExecutionLogs(
      selection.scheduleId, selection.date, selection.hour, pageIndex * pageSize, pageSize, signal
    )
    if (pageIndex > 0 && pageIndex * pageSize >= page.total_count) {
      // 保持削除などで範囲外になった空ページを確定せず、先頭へ戻して取り直す。
      setPageIndex(0)
      return fetchBatchScheduleExecutionLogs(selection.scheduleId, selection.date, selection.hour, 0, pageSize, signal)
    }
    return page
  }, [pageIndex, pageSize, selection.scheduleId, selection.date, selection.hour])
  const { data, loading, error, reload } = useAsyncResource({ load,
    initialData: { logs: [] as BatchScheduleExecutionLog[], has_more: false, total_count: 0 },
    errorMessage: '実行ログの取得に失敗しました。', clearOnReload: true,
  })
  const { logs, has_more: hasMore, total_count: totalCount } = data

  return (
    <Modal
      open
      onClose={onClose}
      title={`スケジュールID ${selection.scheduleId} の実行ログ`}
      description={`定義ID ${selection.definitionId} / ${selection.date}${selection.hour == null ? '' : ` ${selection.hour.toString().padStart(2, '0')}時台`}`}
      size="xl"
    >
      <div className="max-h-[70vh] space-y-4 overflow-y-auto">
        {error && <NotificationBanner type="error">{error}</NotificationBanner>}
        <ListPaginationSummary
          pageSize={pageSize}
          pageIndex={pageIndex}
          itemCount={logs.length}
          totalItems={totalCount}
          loading={loading}
          onPageSizeChange={value => {
            setPageSize(value)
            setPageIndex(0)
          }}
        />
        {logs.map(log => (
          <section key={log.execution_id} className="rounded border border-zinc-200 p-3 dark:border-zinc-700">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-semibold text-zinc-900 dark:text-zinc-100">
                {statusLabels[log.execution_status] ?? log.execution_status}
              </span>
              <span className="text-xs text-zinc-500 dark:text-zinc-400">
                予定 {displayDateTime(log.scheduled_fire_time)}
              </span>
            </div>
            <dl className="mt-2 grid gap-1 text-xs text-zinc-600 dark:text-zinc-300">
              <div>開始: {displayDateTime(log.started_at)}</div>
              <div>終了: {displayDateTime(log.finished_at)}</div>
              <div>終了コード: {log.exit_code ?? '-'}</div>
              {log.message && <div className="break-words">結果: {log.message}</div>}
              <div className="break-all text-zinc-500 dark:text-zinc-400">実行ID: {log.execution_id}</div>
            </dl>
            {log.stdout && <LogOutput title="標準出力" value={log.stdout} />}
            {log.stderr && <LogOutput title="標準エラー" value={log.stderr} />}
            {!log.stdout && !log.stderr && (
              <p className="mt-2 text-xs text-zinc-500">標準出力・標準エラーはありません。</p>
            )}
            {log.output_truncated && (
              <p className="mt-2 text-xs text-amber-700 dark:text-amber-300">ログは保存時に一部省略されています。</p>
            )}
          </section>
        ))}
        {loading && <p className="text-sm text-zinc-500">読み込み中...</p>}
        {!loading && !error && logs.length === 0 && (
          <p className="text-sm text-zinc-500">実行ログがありません。</p>
        )}
        {error && (
          <Button variant="outline" size="sm" onClick={() => void reload()}>再試行</Button>
        )}
        <ListPagination
          pageIndex={pageIndex}
          hasNext={hasMore}
          loading={loading}
          onPrevious={() => setPageIndex(value => Math.max(0, value - 1))}
          onNext={() => setPageIndex(value => value + 1)}
        />
      </div>
    </Modal>
  )
}

function LogOutput({ title, value }: { title: string; value: string }) {
  return (
    <div className="mt-3">
      <div className="mb-1 text-xs font-semibold text-zinc-600 dark:text-zinc-300">{title}</div>
      <pre className="max-h-52 overflow-auto rounded bg-zinc-100 p-2 text-xs whitespace-pre-wrap break-all dark:bg-zinc-950">
        {value}
      </pre>
    </div>
  )
}
