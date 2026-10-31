import { externalInventoryApis, scopeLabels } from './page.constants'
import { defaultExpiry } from './page.utils'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useAsyncRequest } from '@/hooks/useAsyncRequest'
import { getApiErrorMessage } from '@/lib/apiError'
import { formatJapanDateTime } from '@/lib/utils'
import { BookOpen, ChevronDown, Copy, KeyRound, RefreshCw, ShieldOff } from 'lucide-react'
import { getExternalApiKeys, issueExternalApiKey, revokeExternalApiKey, type ExternalApiKey, type InventoryScope } from '@/api/externalApiKeyApi'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Modal } from '@/components/ui/modal'
import { NotificationBanner, type NotificationType } from '@/components/ui/notification-banner'
import { ListPaginationSummary, ListPagination } from '@/components/ui/list-pagination'
import { useListPagination } from '@/hooks/useListPagination'
const dateLabel = (value: string | null) => formatJapanDateTime(value, { milliseconds: false, emptyValue: '—' })
const errorMessage = (error: unknown) => getApiErrorMessage(error, '処理に失敗しました。時間をおいて再度お試しください。')

export function ExternalApiKeyPage() {
  const { run, cancel } = useAsyncRequest()
  const [keys, setKeys] = useState<ExternalApiKey[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<{ type: NotificationType; message: string } | null>(null)
  const [name, setName] = useState('')
  const [read, setRead] = useState(true)
  const [write, setWrite] = useState(false)
  const [expiry, setExpiry] = useState(defaultExpiry)
  const [unlimited, setUnlimited] = useState(false)
  const [issueOpen, setIssueOpen] = useState(false)
  const [issueError, setIssueError] = useState<string | null>(null)
  const [apiTarget, setApiTarget] = useState<ExternalApiKey | 'all' | null>(null)
  const [secret, setSecret] = useState<string | null>(null)
  const [revokeTarget, setRevokeTarget] = useState<ExternalApiKey | null>(null)
  const [query, setQuery] = useState('')
  const [now, setNow] = useState(() => Date.now())
  const filtered = keys.filter(key => key.name.toLowerCase().includes(query.toLowerCase()))
  const { visibleItems, paginationProps } = useListPagination(filtered, query)
  const availableApis = externalInventoryApis.filter(api => apiTarget === 'all' || apiTarget?.scopes.includes(api.scope))

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 60_000)
    return () => window.clearInterval(timer)
  }, [])

  const reload = useCallback(() => run(getExternalApiKeys, {
    onStart: () => setLoading(true), onSuccess: setKeys,
    onError: error => setNotice({ type: 'error', message: errorMessage(error) }),
    onSettled: () => setLoading(false),
  }), [run])

  useEffect(() => {
    const timer = window.setTimeout(() => { void reload() }, 0)
    return () => { window.clearTimeout(timer); cancel() }
  }, [reload, cancel])

  async function issue(event: FormEvent) {
    event.preventDefault()
    const scopes: InventoryScope[] = [...(read ? ['inventory:read' as const] : []), ...(write ? ['inventory:write' as const] : [])]
    const expiryTime = new Date(expiry).getTime()
    if (scopes.length === 0 || !name.trim() || (!unlimited && (!Number.isFinite(expiryTime) || expiryTime <= Date.now()))) {
      const message = '連携先名、権限、有効期限を確認してください。'
      setIssueError(message)
      return
    }
    setIssueError(null)

    // 発行前に始まった一覧GETだけを無効化し、発行結果を古い一覧で消させない。更新POSTを中断する処理ではない。
    cancel()
    setLoading(false)
    setBusy(true)
    try {
      const issued = await issueExternalApiKey({ name: name.trim(), scopes, expiresAt: unlimited ? null : new Date(expiryTime).toISOString() })
      setKeys(current => [issued.key, ...current])
      setIssueOpen(false)

      // 原文は発行応答にしか含まれないため、モーダル用stateにだけ保持し、一覧の再取得から復元しようとしない。
      setSecret(issued.secret)
      setName('')
      setExpiry(defaultExpiry())
      setUnlimited(false)
      setRead(true)
      setWrite(false)
    } catch (error) {
      const message = errorMessage(error)
      setIssueError(message)
    }
    finally { setBusy(false) }
  }

  async function revoke() {
    if (!revokeTarget) return

    // 無効化前のGETが後着して、有効なキーに見える古い状態へ戻るのを防ぐ。
    cancel()
    setLoading(false)
    setBusy(true)
    try {
      const revoked = await revokeExternalApiKey(revokeTarget.id)
      setKeys(current => current.map(key => key.id === revoked.id ? revoked : key))
      setRevokeTarget(null)
      setNotice({ type: 'success', message: 'APIキーを無効化しました。' })
    } catch (error) {
      setRevokeTarget(null)
      setNotice({ type: 'error', message: errorMessage(error) })
    } finally { setBusy(false) }
  }

  async function copySecret() {
    if (!secret) return
    try {
      await navigator.clipboard.writeText(secret)
      setSecret(null)
      setNotice({ type: 'success', message: 'APIキーをクリップボードにコピーしました。安全な場所で管理してください。' })
    } catch {
      setSecret(null)
      setNotice({ type: 'error', message: 'コピーできませんでした。キーを保存できていない場合は、無効化して再発行してください。' })
    }
  }

  return (
    <div className="space-y-6 text-left text-zinc-900 dark:text-zinc-100">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold">外部APIキー管理</h1>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" disabled={busy} onClick={() => setApiTarget('all')}><BookOpen className="size-4" />利用可能なAPI</Button>
          <Button disabled={busy || loading} onClick={() => { setIssueError(null); setIssueOpen(true) }}><KeyRound className="size-4" />APIキー発行</Button>
        </div>
      </div>
      {notice && <NotificationBanner type={notice.type} autoScroll onClose={() => setNotice(null)}>{notice.message}</NotificationBanner>}

      <Modal open={issueOpen} onClose={() => { if (!busy) setIssueOpen(false) }} title="APIキー発行" size="lg"
        closeOnOverlay={!busy} closeOnEsc={!busy}
        footer={<><Button variant="outline" disabled={busy} onClick={() => setIssueOpen(false)}>キャンセル</Button><Button type="submit" form="external-api-key-issue" disabled={busy || loading}><KeyRound className="size-4" />発行</Button></>}>
        <form id="external-api-key-issue" onSubmit={issue} noValidate className="max-h-[60vh] space-y-4 overflow-y-auto">
          {issueError && <NotificationBanner type="error" autoScroll onClose={() => setIssueError(null)}>{issueError}</NotificationBanner>}
          <label className="flex min-w-0 flex-col gap-2 text-sm"><span>連携先名</span>
            <Input aria-label="連携先名" value={name} onChange={event => setName(event.target.value)} maxLength={100} required disabled={busy} autoFocus />
          </label>
          <fieldset disabled={busy} className="space-y-2 text-sm">
            <legend className="mb-2">権限</legend>
            <div className="flex flex-wrap gap-4 pb-2">
              <label className="flex items-center gap-2"><input type="checkbox" checked={read} onChange={event => setRead(event.target.checked)} />在庫参照</label>
              <label className="flex items-center gap-2"><input type="checkbox" checked={write} onChange={event => setWrite(event.target.checked)} />在庫数量更新</label>
            </div>
          </fieldset>
          <div className="min-w-0 space-y-2 text-sm">
            <label className="flex min-w-0 flex-col gap-2"><span>有効期限</span>
              <Input aria-label="有効期限" type="datetime-local" value={expiry} onChange={event => setExpiry(event.target.value)} required={!unlimited} disabled={busy || unlimited} />
            </label>
            <label className="flex items-center gap-2"><input type="checkbox" checked={unlimited} onChange={event => setUnlimited(event.target.checked)} disabled={busy} />無期限</label>
          </div>
        </form>
      </Modal>

      <Modal open={apiTarget !== null} onClose={() => setApiTarget(null)} title="利用可能なAPI" size="xl"
        footer={<Button variant="outline" onClick={() => setApiTarget(null)}>閉じる</Button>}>
        <div className="max-h-[60vh] space-y-4 overflow-y-auto">
          {apiTarget && apiTarget !== 'all' && <p className="break-words font-medium">{apiTarget.name}</p>}
          {apiTarget && apiTarget !== 'all' && (apiTarget.status === 'REVOKED' || (apiTarget.expiresAt !== null && new Date(apiTarget.expiresAt).getTime() <= now)) &&
            <p role="status" className="text-amber-700 dark:text-amber-300">このキーは無効化済み、または有効期限切れのため利用できません。</p>}
          <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm">
            <dt>認証ヘッダー</dt><dd><code>X-API-Key</code></dd>
          </dl>
          {availableApis.map(api => <section key={api.path} className="space-y-2 border-t border-zinc-200 pt-4 dark:border-zinc-800">
            <h3 className="text-sm font-semibold">{api.name}</h3>
            <div className="flex items-start gap-3"><span className="shrink-0 font-mono font-semibold">{api.method}</span><code className="min-w-0 break-all">{api.path}</code></div>
            <div className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-zinc-600 dark:text-zinc-400"><span>{scopeLabels[api.scope]}</span><code>{api.scope}</code></div>
            {api.requestBody && <details className="group border-t border-zinc-200 dark:border-zinc-800">
              <summary className="flex cursor-pointer list-none items-center justify-between gap-2 py-2 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 [&::-webkit-details-marker]:hidden">
                <span>Request body</span><ChevronDown aria-hidden="true" className="size-4 shrink-0 transition-transform group-open:rotate-180" />
              </summary>
              <div className="min-w-0 space-y-3 pb-2">
                <div className="flex flex-wrap gap-2 text-xs"><span>Content-Type</span><code>{api.requestBody.contentType}</code></div>
                <dl className="space-y-3">
                  {api.requestBody.fields.map(field => <div key={field.name}>
                    <dt className="flex flex-wrap items-center gap-x-3 gap-y-1"><code className="font-semibold">{field.name}</code><span className="text-xs">{field.type}</span><span className="text-xs text-zinc-500 dark:text-zinc-400">{field.required ? '必須' : '任意'}</span></dt>
                    <dd className="mt-1 break-words text-xs text-zinc-600 dark:text-zinc-400">{field.description}</dd>
                  </div>)}
                </dl>
                <div className="space-y-2"><h4 className="text-xs font-semibold">JSON例</h4><pre className="whitespace-pre-wrap break-all text-xs"><code>{JSON.stringify(api.requestBody.example, null, 2)}</code></pre></div>
              </div>
            </details>}
          </section>)}
        </div>
      </Modal>

      <section className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-base font-semibold">発行済みキー</h2>
          <div className="flex max-w-full items-center gap-2">
            <Input aria-label="連携先名で検索" placeholder="連携先名で検索" value={query} onChange={event => setQuery(event.target.value)} disabled={busy} className="min-w-0" />
            <Button variant="outline" size="icon" aria-label="再読込" title="再読込" disabled={loading || busy} onClick={() => void reload()}><RefreshCw className="size-4" /></Button>
          </div>
        </div>
        <ListPaginationSummary {...paginationProps} loading={loading || busy} overallItems={keys.length} />
        <div className="overflow-x-auto border-y border-zinc-200 dark:border-zinc-800">
          <table className="w-full min-w-[960px] text-sm">
            <thead className="bg-zinc-100 text-zinc-600 dark:bg-zinc-900 dark:text-zinc-400"><tr>
              {['連携先名 / 識別子', '権限', '状態', '有効期限', '最終利用', '発行日時 / 発行者', '操作'].map(label => <th key={label} className="px-3 py-3 text-left font-medium">{label}</th>)}
            </tr></thead>
            <tbody>
              {loading ? <tr><td colSpan={7} className="p-6 text-center">読み込み中</td></tr> : visibleItems.map(key => {
                const expired = key.expiresAt !== null && new Date(key.expiresAt).getTime() <= now
                return <tr key={key.id} className="border-t border-zinc-200 dark:border-zinc-800">
                  <td className="max-w-56 break-words px-3 py-3"><div>{key.name}</div><code className="text-xs text-zinc-500">{key.keyPrefix}…</code></td>
                  <td className="px-3 py-3">{key.scopes.map(scope => <div key={scope}>{scopeLabels[scope]}</div>)}</td>
                  <td className="px-3 py-3"><span className={key.status === 'REVOKED' ? 'text-zinc-500' : expired ? 'text-amber-600 dark:text-amber-400' : 'text-emerald-600 dark:text-emerald-400'}>{key.status === 'REVOKED' ? '無効' : expired ? '期限切れ' : '有効'}</span></td>
                  <td className="px-3 py-3">{key.expiresAt ? dateLabel(key.expiresAt) : '無期限'}</td>
                  <td className="px-3 py-3">{dateLabel(key.lastUsedAt)}</td>
                  <td className="max-w-56 break-words px-3 py-3"><div>{dateLabel(key.createdAt)}</div><div className="text-xs text-zinc-500">{key.createdBy}</div></td>
                  <td className="px-3 py-3"><div className="flex items-center gap-2">
                    <Button variant="outline" size="icon-sm" aria-label={`${key.name}の利用可能なAPI`} title="利用可能なAPI" disabled={busy} onClick={() => setApiTarget(key)}><BookOpen className="size-4" /></Button>
                    <Button variant="outline" size="sm" disabled={busy || key.status === 'REVOKED'} onClick={() => setRevokeTarget(key)}><ShieldOff className="size-4" />無効化</Button>
                  </div></td>
                </tr>
              })}
              {!loading && visibleItems.length === 0 && <tr><td colSpan={7} className="p-6 text-center text-zinc-500">該当するAPIキーはありません。</td></tr>}
            </tbody>
          </table>
        </div>
        <ListPagination {...paginationProps} loading={loading || busy} />
      </section>

      <Modal open={secret !== null} onClose={() => setSecret(null)} title="発行したAPIキー" size="lg"
        footer={<><Button variant="outline" onClick={() => setSecret(null)}>閉じる</Button><Button onClick={() => void copySecret()}><Copy className="size-4" />コピーして閉じる</Button></>}>
        <div className="mb-4"><NotificationBanner type="success">APIキーを発行しました。</NotificationBanner></div>
        <p className="mb-4 text-sm text-amber-700 dark:text-amber-300">このキーは再表示できません。安全な場所に保管してください。</p>
        <code className="block select-all break-all rounded border border-zinc-300 p-3 text-sm dark:border-zinc-700">{secret}</code>
      </Modal>
      <Modal open={revokeTarget !== null} onClose={() => { if (!busy) setRevokeTarget(null) }} title="APIキーの無効化" variant="danger"
        closeOnOverlay={!busy} closeOnEsc={!busy}
        footer={<><Button variant="outline" disabled={busy} onClick={() => setRevokeTarget(null)}>キャンセル</Button><Button disabled={busy} onClick={() => void revoke()}><ShieldOff className="size-4" />無効化する</Button></>}>
        <p className="break-words text-sm">「{revokeTarget?.name}」を無効化します。再有効化はできません。</p>
        <dl className="mt-4 grid min-w-0 grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm">
          <dt>識別用プレフィックス</dt><dd className="break-all font-mono">{revokeTarget?.keyPrefix}</dd>
          <dt>最終利用日時</dt><dd className="break-words">{revokeTarget?.lastUsedAt ? dateLabel(revokeTarget.lastUsedAt) : '未利用'}</dd>
        </dl>
      </Modal>
    </div>
  )
}
