import { categoryOptions } from './Attachments.constants'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { useCallback, useEffect, useState } from 'react'
import { apiClient } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { fetchBatchGlobalSetting, type BatchGlobalSetting } from '@/pages/Master/CommonSettings/Batch/service'
import { getApiErrorMessage as getErrorMessage } from '@/lib/apiError'
import { formatJapanDateTime } from '@/lib/utils'

type Attachment = { id: number; file_name: string; destination_path: string; file_category: string; file_size: number; created_at: string }
type Pending = { id: string; requestId: string; file: File; path: string; category: string; replaces?: number }
type Props = { definitionId?: number; notice: (type: 'error' | 'success', text: string) => void }
export function Attachments({ definitionId, notice }: Props) {
  const [files, setFiles] = useState<Attachment[]>([])
  const [limits, setLimits] = useState<BatchGlobalSetting | null>(null)
  const [exceeded, setExceeded] = useState(false)
  const [pending, setPending] = useState<Pending[]>([])
  const [busy, setBusy] = useState(false)
  const [loaded, setLoaded] = useState(false)
  const reload = useCallback(async () => {
    if (!definitionId) return
    const [response, settings] = await Promise.all([
      apiClient.get<{ files: Attachment[]; exceeds_limits: boolean }>(`/master/batch/${definitionId}/files`),
      fetchBatchGlobalSetting(),
    ])
    setFiles(response.data.files)
    setExceeded(response.data.exceeds_limits)
    setLimits(settings)
    setLoaded(true)
  }, [definitionId])
  useEffect(() => {
    if (!definitionId) return
    let active = true
    Promise.all([
      apiClient.get<{ files: Attachment[]; exceeds_limits: boolean }>(`/master/batch/${definitionId}/files`),
      fetchBatchGlobalSetting(),
    ]).then(([response, settings]) => {
      if (!active) return
      setFiles(response.data.files); setExceeded(response.data.exceeds_limits); setLimits(settings); setLoaded(true)
    }).catch(error => { if (active) notice('error', getErrorMessage(error, '添付情報の取得に失敗しました。')) })
    return () => { active = false }
  }, [definitionId, notice])

  function choose(selected: FileList | null, replaces?: Attachment) {
    if (!selected || busy) return
    const chosen = Array.from(selected).map(file => ({ id: crypto.randomUUID(), requestId: crypto.randomUUID(), file, path: replaces?.destination_path ?? file.name, category: replaces?.file_category ?? 'input', replaces: replaces?.id }))
    setPending(current => [...current, ...chosen])
  }
  function patch(id: string, values: Partial<Pending>) {
    setPending(current => current.map(item => item.id === id ? { ...item, ...values, requestId: crypto.randomUUID() } : item))
  }
  async function upload() {
    if (!definitionId || !limits || busy) return
    setBusy(true)
    let completed = 0
    let validationError: string | null = null
    try {
      for (const item of pending) {
        if (item.file.size > limits.attachment_max_file_mib * 1048576) {
          validationError = '1ファイルの上限サイズを超えています。'
          throw new Error(validationError)
        }
        const data = new FormData()
        data.append('file', item.file)
        data.append('upload_request_id', item.requestId)
        data.append('destination_path', item.path)
        data.append('file_category', item.category)
        if (item.replaces) data.append('replaces_id', String(item.replaces))
        await apiClient.post(`/master/batch/${definitionId}/files`, data, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 600_000 })
        completed++
        setPending(current => current.filter(row => row.id !== item.id))
      }
      await reload()
      notice('success', `${completed}件のファイルを添付しました。`)
    } catch (error) {
      await reload().catch(() => {})
      const detail = validationError ?? 'アップロードに失敗しました。'
      notice('error', `${completed}件を保存しました。${getErrorMessage(error, detail)} 未完了分は再試行できます。`)
    } finally { setBusy(false) }
  }
  async function detach(file: Attachment) {
    if (busy) return
    setBusy(true)
    try {
      await apiClient.post(`/master/batch/${definitionId}/files/${file.id}/status`, null, { params: { status: 'deleted' } })
      await reload()
      notice('success', '添付を解除しました。作成済みの実行要求には影響しません。')
    } catch (error) { notice('error', getErrorMessage(error, '添付解除に失敗しました。')) }
    finally { setBusy(false) }
  }
  return <section className="space-y-4 rounded-lg border border-zinc-200 p-6 dark:border-zinc-800">
    <h2 className="text-lg font-semibold">実行用ファイル</h2>
    {!definitionId ? <p>先にバッチ定義を保存すると添付できます。</p> : <>
      <p className="text-sm text-zinc-500">ファイルは「添付を確定」で直ちに保存されます。実行は専用ディレクトリを起点とし、コマンドにインタープリターと相対パスを指定してください。</p>
      {limits && <p className="text-sm">上限: 1ファイル {limits.attachment_max_file_mib}MiB ／ 合計 {limits.attachment_max_total_mib}MiB ／ {limits.attachment_max_count}件。現在 {files.length}件、{(files.reduce((sum, f) => sum + f.file_size, 0) / 1048576).toFixed(2)}MiB</p>}
      {exceeded && <NotificationBanner type="warning">共通設定の上限を超えています。新規添付・実行要求作成はできません。添付を解除してください。</NotificationBanner>}
      {!loaded && <Button type="button" onClick={() => reload().catch(error => notice('error', getErrorMessage(error, '取得に失敗しました。')))}>再読み込み</Button>}
      <fieldset disabled={busy || !loaded} className="space-y-4">
        <div className="overflow-auto"><table className="w-full text-left text-sm"><thead><tr><th>配置先／元ファイル名</th><th>サイズ</th><th>登録日時</th><th>操作</th></tr></thead><tbody>
          {files.map(file => <tr key={file.id} className="border-t"><td className="py-3">{file.destination_path}<div className="text-xs text-zinc-500">{file.file_name}</div></td><td>{(file.file_size / 1048576).toFixed(2)}MiB</td><td>{formatJapanDateTime(file.created_at, { milliseconds: false })}</td><td>
            <Button type="button" variant="outline" onClick={() => detach(file)}>解除</Button>
            <label className="ml-2 inline-block">差し替え<input aria-label={`${file.destination_path}を差し替え`} type="file" disabled={exceeded} className="block max-w-48 text-xs" onChange={event => { choose(event.target.files, file); event.target.value = '' }} /></label>
          </td></tr>)}
        </tbody></table></div>
        <div className="rounded border border-dashed p-4" onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); if (!exceeded && loaded) choose(event.dataTransfer.files) }}>
          <label>添付ファイルを選択、またはここへドロップ<input aria-label="添付ファイルを選択" type="file" multiple disabled={exceeded} className="mt-2 block" onChange={event => { choose(event.target.files); event.target.value = '' }} /></label>
        </div>
        {pending.map(item => <div key={item.id} className="flex flex-wrap items-end gap-3">
          <span>{item.file.name}{item.replaces ? '（差し替え）' : ''}</span>
          <label>配置先相対パス<Input aria-label={`${item.file.name}の配置先`} value={item.path} onChange={event => patch(item.id, { path: event.target.value })} /></label>
          <Select
            label="種別"
            options={categoryOptions}
            value={categoryOptions.find(option => option.value === item.category) ?? null}
            onChange={option => { if (option) patch(item.id, { category: option.value }) }}
            disabled={busy || !loaded}
            className="w-40"
          />
          <Button type="button" variant="ghost" onClick={() => setPending(current => current.filter(row => row.id !== item.id))}>選択を取消</Button>
        </div>)}
        <Button type="button" disabled={exceeded || !pending.length} onClick={upload}>{busy ? '送信中...' : '添付を確定'}</Button>
      </fieldset>
    </>}
  </section>
}
