import { ArrowLeft, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { useAsyncResource } from '@/hooks/useAsyncResource'
import { fetchSecuritySettings, type SecuritySetting } from './service'

export function SecuritySettings() {
  const { data, loading, error, reload } = useAsyncResource<SecuritySetting | null>({
    load: fetchSecuritySettings, initialData: null, errorMessage: 'セキュリティ設定の取得に失敗しました。',
  })
  return <div className="space-y-6 text-left">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h1 className="text-xl font-semibold">セキュリティ設定</h1>
      <div className="flex gap-2">
        <Button variant="outline" href="/master/common-settings"><ArrowLeft className="size-4" />共通設定</Button>
        <Button variant="outline" disabled={loading} onClick={() => void reload()}><RefreshCw className="size-4" />再読込</Button>
      </div>
    </div>
    {error && <NotificationBanner type="error">{error}</NotificationBanner>}
    {loading && <p role="status" className="py-8 text-center">読み込み中...</p>}
    {data && <dl className="divide-y divide-zinc-200 border-y border-zinc-200 dark:divide-zinc-800 dark:border-zinc-800">
      <Setting label="アクセストークン有効期限" value={`${data.accessTokenSeconds} 秒`} />
      <Setting label="リフレッシュトークン有効期限" value={`${data.refreshTokenSeconds} 秒`} />
      <Setting label="IoT APIレート制限" value={data.iotRateLimitEnabled ? '有効' : '無効'} />
      <Setting label="IoT API許可回数" value={`${data.iotPerMinute} 回 / 分`} />
      <Setting label="外部在庫API許可回数" value={`${data.externalInventoryHourlyLimit.toLocaleString()} 回 / キー / 時`} />
      <Setting label="ログイン失敗の許容回数" value={`${data.loginFailureTolerance} 回`} />
      <Setting label="一時ロック時間" value={`${data.loginLockMinutes} 分`} />
    </dl>}
  </div>
}

function Setting({ label, value }: { label: string; value: string }) {
  return <div className="grid gap-2 py-4 sm:grid-cols-2">
    <dt className="text-sm text-zinc-500 dark:text-zinc-400">{label}</dt>
    <dd className="break-words text-sm font-medium">{value}</dd>
  </div>
}
