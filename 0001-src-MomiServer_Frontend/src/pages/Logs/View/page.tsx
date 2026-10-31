import { useCallback, useState } from 'react'
import { useAsyncResource } from '@/hooks/useAsyncResource'
import { formatJapanDateTime } from '@/lib/utils'
import { Eye, RefreshCw, Search } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ListPagination, ListPaginationSummary } from '@/components/ui/list-pagination'
import { DEFAULT_LIST_PAGE_SIZE, type ListPageSize } from '@/constants/pagination'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { cn } from '@/lib/utils'
import { ApiAccessLogDetailModal } from './ApiAccessLogDetailModal'
import {
  fetchApiAccessLogs,
  type ApiAccessLog,
  type ApiAccessLogSearch,
} from './service'

export function ApiAccessLogPage() {
  const [initialSearch] = useState<ApiAccessLogSearch>(() => createInitialSearch())
  const [inputs, setInputs] = useState<ApiAccessLogSearch>(initialSearch)
  const [search, setSearch] = useState<ApiAccessLogSearch>(initialSearch)
  const [pageSize, setPageSize] = useState<ListPageSize>(DEFAULT_LIST_PAGE_SIZE)
  const [pageIndex, setPageIndex] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [detailRequestId, setDetailRequestId] = useState<string | null>(null)
  const load = useCallback(async (signal: AbortSignal) => {
    const page = await fetchApiAccessLogs(search, pageIndex * pageSize, pageSize, signal)
    if (pageIndex > 0 && pageIndex * pageSize >= page.total_count) {

      // 保持削除などで現在のページが消えた場合は、旧位置の空結果を確定せず先頭を再取得する。
      setPageIndex(0)
      return fetchApiAccessLogs(search, 0, pageSize, signal)
    }
    return page
  }, [search, pageIndex, pageSize])
  const { data, loading, error } = useAsyncResource({ load,
    initialData: { logs: [] as ApiAccessLog[], has_more: false, total_count: 0 },
    errorMessage: 'APIログ一覧の取得に失敗しました。', clearOnReload: true, refreshKey: reloadKey,
  })
  const { logs, has_more: hasMore, total_count: totalCount } = data

  function applySearch(event: React.FormEvent) {
    event.preventDefault()
    setPageIndex(0)
    setSearch(inputs)
    setReloadKey(value => value + 1)
  }

  return (
    <div className="space-y-5 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">APIログ</h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            APIへのアクセス履歴と応答状況を確認します。
          </p>
        </div>
        <Button variant="outline" disabled={loading} onClick={() => setReloadKey(value => value + 1)}>
          <RefreshCw className="size-4" />
          再読込
        </Button>
      </div>

      {error && <NotificationBanner type="error">{error}</NotificationBanner>}

      <form onSubmit={applySearch} className="border-y border-zinc-200 py-4 dark:border-zinc-800">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <FilterInput label="開始日時" type="datetime-local" value={inputs.from}
            onChange={value => setInputs(current => ({ ...current, from: value }))} />
          <FilterInput label="終了日時" type="datetime-local" value={inputs.to}
            onChange={value => setInputs(current => ({ ...current, to: value }))} />
          <FilterSelect label="HTTPメソッド" value={inputs.http_method}
            onChange={value => setInputs(current => ({ ...current, http_method: value }))}
            options={[['', 'すべて'], ['GET', 'GET'], ['POST', 'POST'], ['PUT', 'PUT'],
              ['PATCH', 'PATCH'], ['DELETE', 'DELETE'], ['HEAD', 'HEAD']]} />
          <FilterSelect label="ステータス区分" value={inputs.status_category}
            onChange={value => setInputs(current => ({ ...current, status_category: value, status_code: '' }))}
            options={[['', 'すべて'], ['success', '成功（2xx・3xx）'],
              ['client_error', 'クライアントエラー（4xx）'], ['server_error', 'サーバーエラー（5xx）']]} />
          <FilterInput label="パス・エンドポイント" value={inputs.request_path} placeholder="/api/status"
            onChange={value => setInputs(current => ({ ...current, request_path: value }))} />
          <FilterSelect label="発生元" value={inputs.source_type}
            onChange={value => setInputs(current => ({ ...current, source_type: value }))}
            options={[['', 'すべて'], ['web', 'Web'], ['agent', 'Agent'], ['iot', 'IoT'],
              ['gateway', 'Gateway'], ['anonymous', '未認証']]} />
          <FilterInput label="操作者" value={inputs.principal} placeholder="operator@example.com"
            onChange={value => setInputs(current => ({ ...current, principal: value }))} />
          <FilterInput label="送信元IP" value={inputs.ip_address} placeholder="192.168.0.10"
            onChange={value => setInputs(current => ({ ...current, ip_address: value }))} />
          <FilterInput label="HTTPステータス" type="number" value={inputs.status_code} placeholder="404"
            onChange={value => setInputs(current => ({ ...current, status_code: value, status_category: '' }))} />
          <FilterInput label="最小処理時間（ms）" type="number" value={inputs.min_duration_ms} placeholder="1000"
            onChange={value => setInputs(current => ({ ...current, min_duration_ms: value }))} />
        </div>
        <div className="mt-3 flex justify-end">
          <Button type="submit" disabled={loading}>
            <Search className="size-4" />
            検索
          </Button>
        </div>
      </form>

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

      <div className="overflow-x-auto rounded-lg border border-zinc-200 dark:border-zinc-800">
        <table className="w-full min-w-[1080px] table-auto text-sm">
          <thead className="bg-zinc-50 dark:bg-zinc-900">
            <tr className="border-b border-zinc-200 dark:border-zinc-800">
              <Header>発生日時</Header><Header>メソッド</Header><Header>エンドポイント</Header>
              <Header>発生元・操作者</Header><Header>ステータス</Header><Header>処理時間</Header>
              <Header>送信元IP</Header><Header align="right">操作</Header>
            </tr>
          </thead>
          <tbody>
            {loading && <MessageRow message="APIログを読み込み中です。" />}
            {!loading && !error && logs.length === 0 && <MessageRow message="表示できるAPIログはありません。" />}
            {!loading && logs.map(log => (
              <tr key={log.request_id} className={cn(
                'border-b border-zinc-200 align-top dark:border-zinc-800',
                log.status_code >= 500 && 'bg-red-50/80 dark:bg-red-950/20',
                log.status_code >= 400 && log.status_code < 500 && 'bg-amber-50/80 dark:bg-amber-950/20',
                log.status_code < 400 && 'hover:bg-zinc-50 dark:hover:bg-zinc-900',
              )}>
                <Cell className="whitespace-nowrap">{formatJapanDateTime(log.occurred_at, { milliseconds: false })}</Cell>
                <Cell><MethodBadge method={log.http_method} /></Cell>
                <Cell>
                  <div className="font-medium text-zinc-900 dark:text-zinc-100">
                    {log.endpoint_pattern ?? log.request_path}
                  </div>
                  {log.endpoint_pattern && log.endpoint_pattern !== log.request_path && (
                    <div className="mt-1 max-w-sm truncate text-xs text-zinc-500" title={log.request_path}>
                      {log.request_path}
                    </div>
                  )}
                </Cell>
                <Cell>
                  <div>{sourceLabel(log.source_type)}</div>
                  <div className="mt-1 text-xs text-zinc-500">{log.principal ?? '-'}</div>
                </Cell>
                <Cell><StatusBadge status={log.status_code} /></Cell>
                <Cell className="whitespace-nowrap">{log.duration_ms} ms</Cell>
                <Cell className="whitespace-nowrap">{log.ip_address ?? '-'}</Cell>
                <Cell className="text-right">
                  <Button variant="outline" size="sm" onClick={() => setDetailRequestId(log.request_id)}>
                    <Eye className="size-4" />詳細
                  </Button>
                </Cell>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ListPagination
        pageIndex={pageIndex}
        hasNext={hasMore}
        loading={loading}
        onPrevious={() => setPageIndex(value => Math.max(0, value - 1))}
        onNext={() => setPageIndex(value => value + 1)}
      />

      {detailRequestId && (
        <ApiAccessLogDetailModal requestId={detailRequestId} onClose={() => setDetailRequestId(null)} />
      )}
    </div>
  )
}

function FilterInput({ label, value, onChange, type = 'text', placeholder }: {
  label: string; value: string; onChange: (value: string) => void; type?: string; placeholder?: string
}) {
  return <label className="grid gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300">
    {label}
    <input type={type} value={value} placeholder={placeholder} onChange={event => onChange(event.target.value)}
      className="h-10 rounded-md border border-zinc-300 bg-white px-3 text-zinc-900 focus:outline-none focus:ring-2 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100" />
  </label>
}

function FilterSelect({ label, value, onChange, options }: {
  label: string; value: string; onChange: (value: string) => void; options: Array<[string, string]>
}) {
  return <label className="grid gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300">
    {label}
    <select value={value} onChange={event => onChange(event.target.value)}
      className="h-10 rounded-md border border-zinc-300 bg-white px-3 text-zinc-900 focus:outline-none focus:ring-2 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100">
      {options.map(([optionValue, optionLabel]) => <option key={optionValue} value={optionValue}>{optionLabel}</option>)}
    </select>
  </label>
}

function Header({ children, align = 'left' }: { children: React.ReactNode; align?: 'left' | 'right' }) {
  return <th className={cn('px-3 py-2 font-semibold whitespace-nowrap',
    align === 'right' ? 'text-right' : 'text-left')}>{children}</th>
}

function Cell({ children, className }: { children: React.ReactNode; className?: string }) {
  return <td className={cn('px-3 py-3', className)}>{children}</td>
}

function MessageRow({ message }: { message: string }) {
  return <tr><td colSpan={8} className="px-3 py-8 text-center text-zinc-500">{message}</td></tr>
}

function MethodBadge({ method }: { method: string }) {
  const classes: Record<string, string> = {
    GET: 'bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300',
    POST: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300',
    PUT: 'bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300',
    PATCH: 'bg-orange-100 text-orange-700 dark:bg-orange-950 dark:text-orange-300',
    DELETE: 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300',
  }
  return <span className={cn('inline-flex rounded px-2 py-0.5 text-xs font-semibold',
    classes[method] ?? 'bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300')}>{method}</span>
}

function StatusBadge({ status }: { status: number }) {
  const className = status >= 500 ? 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300'
    : status >= 400 ? 'bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300'
      : 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300'
  return <span className={cn('inline-flex rounded-full px-2 py-0.5 text-xs font-semibold', className)}>{status}</span>
}

function createInitialSearch(): ApiAccessLogSearch {
  const to = new Date()
  const from = new Date(to.getTime() - 24 * 60 * 60 * 1000)
  return {
    from: toLocalDateTime(from), to: toLocalDateTime(to), http_method: '', request_path: '',
    status_category: '', status_code: '', source_type: '', principal: '', ip_address: '',
    min_duration_ms: '',
  }
}

function toLocalDateTime(value: Date): string {
  const local = new Date(value.getTime() - value.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function sourceLabel(value: ApiAccessLog['source_type']): string {
  return ({ web: 'Web', agent: 'Agent', iot: 'IoT', gateway: 'Gateway', anonymous: '未認証' })[value]
}
