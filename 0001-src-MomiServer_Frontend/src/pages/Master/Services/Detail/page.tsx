import { statusOptions, noticeLevelOptions } from './page.constants'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { registerService } from '@/generated/sdk.gen'
import { useServicesMasterService } from './service'

type ServiceFieldErrors = Partial<Record<'serviceName' | 'viewName' | 'targetMachine' | 'status' | 'logPath', string>>

export function ServicesMasterDetail() {
  const { serverId } = useParams()
  const navigate = useNavigate()
  const isNew = serverId === 'new'
  const parsedServiceId = Number(serverId)
  const { users, deviceOptions, reload } = useServicesMasterService(!isNew && Number.isFinite(parsedServiceId) ? parsedServiceId : undefined)

  // users is an array, get the first item
  const user = users[0]

  const [serviceName, setServiceName] = useState('')
  const [viewName, setViewName] = useState('')
  const [targetMachine, setTargetMachine] = useState('')
  const [logPath, setLogPath] = useState('')
  const [useJournalctl, setUseJournalctl] = useState(false)
  const [status, setStatus] = useState('')
  const [allowStart, setAllowStart] = useState(false)
  const [allowStop, setAllowStop] = useState(false)
  const [allowRestart, setAllowRestart] = useState(false)
  const [autoRestart, setAutoRestart] = useState(false)
  const [noticeLevel, setNoticeLevel] = useState('none')
  const [description, setDescription] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<ServiceFieldErrors>({})

  // Sync state when user data loads
  useEffect(() => {
    let ignore = false

    async function syncUser() {
      if (!user) return

      await Promise.resolve()
      if (ignore) return

      setServiceName(user.service_name ?? '')
      setViewName(user.view_name ?? '')
      setTargetMachine(user.target_machine == null ? '' : String(user.target_machine))
      setLogPath(user.log_path ?? '')
      setUseJournalctl(user.use_journalctl ?? false)
      setStatus(user.status ?? '')
      setAllowStart(user.allow_start ?? false)
      setAllowStop(user.allow_stop ?? false)
      setAllowRestart(user.allow_restart ?? false)
      setAutoRestart(user.auto_restart ?? false)
      setNoticeLevel(user.notice_level ?? 'none')
      setDescription(user.description ?? '')
      setFieldErrors({})
    }

    void syncUser()

    return () => {
      ignore = true
    }
  }, [user])

  const handleSave = async () => {
    const errors: ServiceFieldErrors = {}
    if (!serviceName.trim()) errors.serviceName = 'systemdユニット名は必須です。'
    else if (serviceName.length > 255) errors.serviceName = 'systemdユニット名は255文字以内で入力してください。'
    if (!viewName.trim()) errors.viewName = 'サービス名は必須です。'
    else if (viewName.length > 50) errors.viewName = 'サービス名は50文字以内で入力してください。'
    if (!Number.isInteger(Number(targetMachine)) || Number(targetMachine) <= 0) {
      errors.targetMachine = '監視対象端末を選択してください。'
    }
    if (!status.trim()) errors.status = '管理状態を選択してください。'
    if (!logPath.trim()) errors.logPath = 'ログパスは必須です。'
    else if (logPath.length > 255) errors.logPath = 'ログパスは255文字以内で入力してください。'

    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      setSaved(false)
      setSaveError('入力内容を確認してください。')
      scrollToPageTop()
      return
    }

    setSaving(true)
    setSaved(false)
    setSaveError(null)
    try {
      const response = await registerService({
        body: {
          id: user?.id,
          service_name: serviceName,
          view_name: viewName,
          target_machine: Number(targetMachine),
          status,
          log_path: logPath,
          use_journalctl: useJournalctl,
          allow_start: allowStart,
          allow_stop: allowStop,
          allow_restart: allowRestart,
          auto_restart: autoRestart,
          notice_level: noticeLevel,
          description: description || undefined,
        },
      })

      if (response.error) throw response.error

      setSaved(true)
      scrollToPageTop()
      if (isNew && response.data?.id) {
        navigate(`/master/services/${response.data.id}`, { replace: true })
      } else {
        await reload()
      }
    } catch {
      setSaveError('サービスマスタの保存に失敗しました。')
      scrollToPageTop()
    } finally {
      setSaving(false)
    }
  }
  const handleBack = () => navigate('/master/services')

  // const deviceMasterMap = useMemo(() => {
  //   if(loading){
  //     return ""
  //   }
  //   return new Map(devices.map(master => [master.id, master]))
  // }, [devices])


  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            サービスマスタ詳細
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            サービス情報の確認・編集を行います。
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="ghost" onClick={handleBack}>一覧に戻る</Button>
        </div>
      </div>

      {saveError && (
        <NotificationBanner type="error" onClose={() => setSaveError(null)}>
          {saveError}
        </NotificationBanner>
      )}
      {saved && (
        <NotificationBanner type="success" onClose={() => setSaved(false)}>
          保存しました
        </NotificationBanner>
      )}

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <Card className="rounded-lg border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900 p-6">
          <h2 className="mb-4 text-lg font-semibold text-zinc-900 dark:text-white">基本設定</h2>
          <div className="space-y-4 max-w-md">
            {/* systemdユニット名 */}
            <div className="space-y-1.5">
              <label htmlFor="service_name" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                systemdユニット名
              </label>
              <Input
                id="service_name"
                type="text"
                required
                value={serviceName}
                onChange={e => {
                  setServiceName(e.target.value)
                  setFieldErrors(current => ({ ...current, serviceName: undefined }))
                }}
                aria-invalid={Boolean(fieldErrors.serviceName)}
                className="w-full"
                placeholder="G_XXXXXXX"
              />
              {fieldErrors.serviceName && <p className="text-xs text-red-500">{fieldErrors.serviceName}</p>}
            </div>

            {/* サービス名(表示名) */}
            <div className="space-y-1.5">
              <label htmlFor="view_name" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                サービス名(表示名)
              </label>
              <Input
                id="view_name"
                type="text"
                required
                value={viewName}
                onChange={e => {
                  setViewName(e.target.value)
                  setFieldErrors(current => ({ ...current, viewName: undefined }))
                }}
                aria-invalid={Boolean(fieldErrors.viewName)}
                className="w-full"
                placeholder="表示用サービス名"
              />
              {fieldErrors.viewName && <p className="text-xs text-red-500">{fieldErrors.viewName}</p>}
            </div>

            {/* 監視対象アドレス */}
            {/*TODO: 端末マスタから取得*/}
            <div className="space-y-1.5">
              <label htmlFor="server_address" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                監視対象アドレス
              </label>
              <Select
                value={deviceOptions.find(opt => opt.value === String(targetMachine)) ?? null}
                options={deviceOptions}
                onChange={opt => {
                  if (!opt) return
                  setTargetMachine(opt.value)
                  setFieldErrors(current => ({ ...current, targetMachine: undefined }))
                }}
              />
              <Input
                id="server_address"
                type="text"
                required
                value={targetMachine}
                onChange={e => {
                  setTargetMachine(e.target.value)
                  setFieldErrors(current => ({ ...current, targetMachine: undefined }))
                }}
                aria-invalid={Boolean(fieldErrors.targetMachine)}
                className="w-full"
                placeholder="XXX.XXX.XXX.XXX"
              />
              {fieldErrors.targetMachine && <p className="text-xs text-red-500">{fieldErrors.targetMachine}</p>}
            </div>

            {/* ログパス */}
            <div className="space-y-1.5">
              <label htmlFor="log_path" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                ログパス
              </label>
              <div className="flex items-center gap-3">
                <Input
                  id="log_path"
                  type="text"
                  value={logPath}
                  onChange={e => {
                    setLogPath(e.target.value)
                    setFieldErrors(current => ({ ...current, logPath: undefined }))
                  }}
                  aria-invalid={Boolean(fieldErrors.logPath)}
                  className="flex-1"
                  placeholder="/var/log/xxxx.log"
                />
                <label className="flex items-center gap-2 text-sm text-zinc-700 dark:text-zinc-300 whitespace-nowrap">
                  <input
                    type="checkbox"
                    checked={useJournalctl}
                    onChange={e => setUseJournalctl(e.target.checked)}
                    className="rounded border-zinc-300 text-purple-600 focus:ring-purple-500"
                  />
                  <span>journalctlで取得する</span>
                </label>
              </div>
              {fieldErrors.logPath && <p className="text-xs text-red-500">{fieldErrors.logPath}</p>}
            </div>

            {/* ステータス */}
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                管理状態
              </label>
              <Select
                value={statusOptions.find(opt => opt.value === status) ?? null}
                options={statusOptions}
                onChange={opt => {
                  if (!opt) return
                  setStatus(opt.value)
                  setFieldErrors(current => ({ ...current, status: undefined }))
                }}
              />
              {fieldErrors.status && <p className="text-xs text-red-500">{fieldErrors.status}</p>}
            </div>

            {/* 操作権限 */}
            <div className="space-y-2 pt-2 border-t border-zinc-200 dark:border-zinc-800">
              <h3 className="text-sm font-medium text-zinc-700 dark:text-zinc-300">操作権限</h3>
              <div className="grid grid-cols-2 gap-3">
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={allowStart}
                    onChange={e => setAllowStart(e.target.checked)}
                    className="rounded border-zinc-300 text-purple-600 focus:ring-purple-500"
                  />
                  <span className="text-zinc-700 dark:text-zinc-300">起動許可</span>
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={allowStop}
                    onChange={e => setAllowStop(e.target.checked)}
                    className="rounded border-zinc-300 text-purple-600 focus:ring-purple-500"
                  />
                  <span className="text-zinc-700 dark:text-zinc-300">停止許可</span>
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={allowRestart}
                    onChange={e => setAllowRestart(e.target.checked)}
                    className="rounded border-zinc-300 text-purple-600 focus:ring-purple-500"
                  />
                  <span className="text-zinc-700 dark:text-zinc-300">再起動許可</span>
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={autoRestart}
                    onChange={e => setAutoRestart(e.target.checked)}
                    className="rounded border-zinc-300 text-purple-600 focus:ring-purple-500"
                  />
                  <span className="text-zinc-700 dark:text-zinc-300">自動再起動</span>
                </label>
              </div>
            </div>

            {/* 通知レベル */}
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                通知重要度
              </label>
              <Select
                value={noticeLevelOptions.find(opt => opt.value === noticeLevel) ?? null}
                options={noticeLevelOptions}
                onChange={opt => opt && setNoticeLevel(opt.value)}
              />
            </div>

            {/* 備考 */}
            <div className="space-y-1.5">
              <label htmlFor="description" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                備考
              </label>
              <textarea
                id="description"
                value={description}
                onChange={e => setDescription(e.target.value)}
                className="w-full min-h-[80px] rounded-lg border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 px-3 py-2 text-sm outline-none transition-colors focus:border-purple-500 focus:ring-1 focus:ring-purple-500"
                placeholder="備考を入力..."
              />
            </div>

            {/* 保存ボタン */}
            <div className="flex justify-end gap-3 pt-4 border-t border-zinc-200 dark:border-zinc-800">
              <Button variant="ghost" onClick={handleBack}>キャンセル</Button>
              <Button
                onClick={handleSave}
                disabled={saving}
              >
                {saving ? '保存中...' : '保存'}
              </Button>
            </div>

          </div>
        </Card>

        <Card className="rounded-lg border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900 p-6">
          <h2 className="mb-4 text-lg font-semibold text-zinc-900 dark:text-white">読み取り専用情報</h2>
          <div className="space-y-3">
            <div className="grid grid-cols-[100px_1fr] gap-2 text-sm">
              <span className="text-zinc-500 dark:text-zinc-400">サービスID</span>
              <span className="text-zinc-900 dark:text-zinc-100">{user?.id ?? ''}</span>
            </div>
            <div className="grid grid-cols-[100px_1fr] gap-2 text-sm">
              <span className="text-zinc-500 dark:text-zinc-400">作成日時</span>
              <span className="text-zinc-900 dark:text-zinc-100">{user?.createdAt ?? ''}</span>
            </div>
            <div className="grid grid-cols-[100px_1fr] gap-2 text-sm">
              <span className="text-zinc-500 dark:text-zinc-400">更新日時</span>
              <span className="text-zinc-900 dark:text-zinc-100">{user?.updatedAt ?? ''}</span>
            </div>
            <div className="grid grid-cols-[100px_1fr] gap-2 text-sm">
              <span className="text-zinc-500 dark:text-zinc-400">該当端末</span>
              <span className="text-zinc-900 dark:text-zinc-100">{user?.target_machine ?? ''}</span>
            </div>
          </div>
        </Card>
      </div>
    </div>
  )
}

function scrollToPageTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
