import { useCallback } from 'react'
import { useAsyncResource } from '@/hooks/useAsyncResource'
import { formatJapanDateTime } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { fetchApiAccessLogDetail, type ApiAccessLog } from './service'

export function ApiAccessLogDetailModal({
  requestId,
  onClose,
}: {
  requestId: string
  onClose: () => void
}) {
  const load = useCallback((signal: AbortSignal) => fetchApiAccessLogDetail(requestId, signal), [requestId])
  const { data: log, loading, error } = useAsyncResource<ApiAccessLog | null>({
    load, initialData: null, errorMessage: 'APIログ詳細の取得に失敗しました。', clearOnReload: true,
  })

  return (
    <Modal
      open
      onClose={onClose}
      title="APIログ詳細"
      description={requestId}
      size="xl"
      footer={<Button variant="outline" onClick={onClose}>閉じる</Button>}
    >
      <div className="max-h-[70vh] overflow-y-auto">
        {error && <NotificationBanner type="error">{error}</NotificationBanner>}
        {loading && <p className="text-zinc-500">読み込み中...</p>}
        {!loading && log && <DetailRows log={log} />}
      </div>
    </Modal>
  )
}

function DetailRows({ log }: { log: ApiAccessLog }) {
  const rows: Array<[string, string | number | null]> = [
    ['発生日時', formatJapanDateTime(log.occurred_at, { milliseconds: false })],
    ['HTTPメソッド', log.http_method],
    ['リクエストパス', log.request_path],
    ['エンドポイント', log.endpoint_pattern],
    ['HTTPステータス', log.status_code],
    ['処理時間', `${log.duration_ms} ms`],
    ['発生元', sourceLabel(log.source_type)],
    ['操作者', log.principal],
    ['送信元IP', log.ip_address],
    ['User-Agent', log.user_agent],
    ['エラーコード', log.error_code],
    ['リクエストID', log.request_id],
  ]

  return (
    <dl className="divide-y divide-zinc-200 border-y border-zinc-200 dark:divide-zinc-800 dark:border-zinc-800">
      {rows.map(([label, value]) => (
        <div key={label} className="grid gap-1 py-3 sm:grid-cols-[10rem_1fr]">
          <dt className="font-semibold text-zinc-600 dark:text-zinc-300">{label}</dt>
          <dd className="min-w-0 break-all text-zinc-900 dark:text-zinc-100">{value ?? '-'}</dd>
        </div>
      ))}
    </dl>
  )
}

function sourceLabel(value: ApiAccessLog['source_type']): string {
  return ({ web: 'Web', agent: 'Agent', iot: 'IoT', gateway: 'Gateway', anonymous: '未認証' })[value]
}
