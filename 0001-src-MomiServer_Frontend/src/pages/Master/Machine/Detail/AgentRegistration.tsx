import { labels } from './AgentRegistration.constants'
import { getApiErrorMessage } from '@/lib/apiError'
import { formatJapanDateTime } from '@/lib/utils'
import type { AgentStatus } from './AgentRegistration.constants'
import { useEffect, useState } from 'react'
import { apiClient } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'

type RegistrationCode = { registrationCode: string; expiresAt: string }
type Notification = { type: 'success' | 'error'; message: string } | null

export function AgentRegistration({ deviceId, onNotify }: {
  deviceId: number
  onNotify: (value: Notification) => void
}) {
  const [agent, setAgent] = useState<AgentStatus | null>(null)
  const [code, setCode] = useState<RegistrationCode | null>(null)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const path = `/master/device/${deviceId}/agent`

  useEffect(() => {
    let active = true
    apiClient.get<AgentStatus>(path).then(({ data }) => {
      if (active) setAgent(data)
    }).catch(() => {
      if (active) {
        setLoadError(true)
        onNotify({ type: 'error', message: 'Agent登録情報を取得できませんでした。' })
      }
    })
    return () => { active = false }
  }, [path, onNotify])

  useEffect(() => {
    if (!code) return
    const timeout = window.setTimeout(() => setCode(null), Math.max(0, Date.parse(code.expiresAt) - Date.now()))
    return () => window.clearTimeout(timeout)
  }, [code])

  const operate = async (action: 'issue' | 'pending' | 'disabled' | 'reload') => {
    if (action === 'pending' || action === 'disabled') {
      const text = action === 'pending'
        ? '現在のAgent認証情報を失効させ、再登録を開始します。実行中のバッチは自動停止しません。続行しますか？'
        : 'Agent認証情報を失効させます。実行中のバッチは自動停止しません。続行しますか？'
      if (!window.confirm(text)) return
    }
    setBusy(true)
    setCode(null)
    onNotify(null)
    try {
      if (action === 'issue') {
        const { data } = await apiClient.post<RegistrationCode>(`${path}/registration-code`)
        setCode(data)
      } else if (action !== 'reload') {
        await apiClient.post(`${path}/status`, { status: action, reason: reason.trim() })
      }
      const { data } = await apiClient.get<AgentStatus>(path)
      setAgent(data)
      setLoadError(false)
      onNotify({ type: 'success', message: action === 'issue'
        ? '登録コードを発行しました。対象端末で入力してください。'
        : action === 'reload' ? 'Agent登録状態を更新しました。' : 'Agent登録状態を変更しました。' })
    } catch (error) {
      onNotify({ type: 'error', message: getApiErrorMessage(error, 'Agent登録情報の操作に失敗しました。') })
    } finally {
      setBusy(false)
      window.scrollTo({ top: 0, behavior: 'smooth' })
    }
  }

  return (
    <Card className="space-y-4 rounded-lg border p-6">
      <h2 className="text-lg font-semibold">Agent登録</h2>
      <p>登録状態: {agent ? labels[agent.status] : loadError ? '取得失敗' : '読み込み中...'}</p>
      {agent?.agentId && <p className="break-all text-sm">Agent ID: {agent.agentId} / 登録世代: {agent.generation}</p>}
      {agent?.mqttClientId && <p className="break-all text-sm">Agent MQTT Client ID: {agent.mqttClientId}</p>}
      {agent?.lastSeenAt && <p className="text-sm">最終認証確認: {formatJapanDateTime(agent.lastSeenAt, { milliseconds: false })}</p>}
      <p className="text-sm text-zinc-500">MQTT設定: {agent?.mqttStatus === 'ready' ? '設定済み' : agent?.mqttStatus === 'pending' ? '同期待ち' : agent?.mqttStatus === 'revocation_pending' ? '失効同期待ち' : agent?.mqttStatus === 'revoked' ? '失効済み' : '未構成'}。設定済みでも接続中を意味しません。</p>
      {agent?.heartbeatAt && <p className="text-sm">ハートビート: {formatJapanDateTime(agent.heartbeatAt, { milliseconds: false })} / {agent.connectionStatus === 'online' ? '応答あり' : '応答期限超過'} / MQTT最終報告: {agent.mqttConnected ? '接続' : '切断'}</p>}
      <div className="flex flex-wrap gap-3">
        <Button disabled={busy} variant="ghost" onClick={() => void operate('reload')}>状態を更新</Button>
        {(agent?.status === 'unregistered' || agent?.status === 'pending') && (
          <Button disabled={busy} onClick={() => void operate('issue')}>Agent登録コード発行</Button>
        )}
      </div>
      {code && (
        <div className="space-y-2 rounded border p-4">
          <p className="text-sm">登録コード（再表示できません。必要なら再発行してください）</p>
          <code className="block break-all select-all">{code.registrationCode}</code>
          <p className="text-sm">有効期限: {formatJapanDateTime(code.expiresAt, { milliseconds: false })}</p>
          <p className="text-sm">対象端末で下記を実行し、非表示の入力欄にコードを貼り付けてください。</p>
          <code className="block break-all">momiserver-agent register --server &lt;バックエンドのベースURL&gt;</code>
          <p className="text-sm">引数形式: momiserver-agent register &lt;登録コード&gt;</p>
        </div>
      )}
      {agent?.agentId && (
        <div className="space-y-3 border-t pt-4">
          <label htmlFor="agent-status-reason" className="block text-sm">無効化・再登録の理由</label>
          <Input id="agent-status-reason" value={reason} maxLength={500} onChange={event => setReason(event.target.value)} />
          <div className="flex flex-wrap gap-3">
            <Button variant="ghost" disabled={busy || !reason.trim()} onClick={() => void operate('pending')}>再登録を開始</Button>
            <Button variant="ghost" disabled={busy || !reason.trim() || agent.status === 'disabled'} onClick={() => void operate('disabled')}>Agentを無効化</Button>
          </div>
        </div>
      )}
    </Card>
  )
}
