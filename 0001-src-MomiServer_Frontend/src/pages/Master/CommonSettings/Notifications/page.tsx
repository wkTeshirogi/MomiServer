import { NotificationBanner } from '@/components/ui/notification-banner'
import { getApiErrorMessage as getErrorMessage } from '@/lib/apiError'
import { useEffect, useState } from 'react'
import { Save } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import {
  fetchNotificationGlobalSetting,
  notificationLevelLabels,
  notificationLevelOptions,
  saveNotificationGlobalSetting,
  type NotificationGlobalSetting,
  type NotificationLevel,
} from './service'

type FieldErrors = Partial<Record<'security_event_aggregation_window_seconds' | 'retention_days', string>>


function FieldErrorMessage({ message }: { message?: string }) {
  if (!message) return null
  return <p className="text-xs text-red-600 dark:text-red-300">{message}</p>
}

function SettingSwitch({
  label,
  description,
  checked,
  onChange,
}: {
  label: string
  description: string
  checked: boolean
  onChange: (checked: boolean) => void
}) {
  return (
    <label className="flex items-center justify-between gap-4 rounded-lg border border-zinc-200 px-4 py-3 dark:border-zinc-800">
      <span className="min-w-0">
        <span className="block text-sm font-medium text-zinc-900 dark:text-zinc-100">{label}</span>
        <span className="mt-0.5 block text-xs text-zinc-500 dark:text-zinc-400">{description}</span>
      </span>
      <input
        type="checkbox"
        checked={checked}
        onChange={event => onChange(event.target.checked)}
        className="size-4 shrink-0 accent-purple-500"
      />
    </label>
  )
}

function scrollToPageTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

export function NotificationGlobalSettings() {
  const [form, setForm] = useState<NotificationGlobalSetting | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})

  useEffect(() => {
    let ignore = false

    async function loadSetting() {
      try {
        const setting = await fetchNotificationGlobalSetting()
        if (!ignore) setForm(setting)
      } catch (error) {
        if (!ignore) {
          setFormError(getErrorMessage(error) ?? '通知全体設定の取得に失敗しました。')
          scrollToPageTop()
        }
      } finally {
        if (!ignore) setLoading(false)
      }
    }

    void loadSetting()

    return () => {
      ignore = true
    }
  }, [])

  const patchForm = (patch: Partial<NotificationGlobalSetting>) => {
    setForm(current => current ? { ...current, ...patch } : current)
  }

  const validate = (): FieldErrors => {
    const errors: FieldErrors = {}

    if (!form) return errors

    if (!Number.isInteger(form.security_event_aggregation_window_seconds)
        || form.security_event_aggregation_window_seconds < 1) {
      errors.security_event_aggregation_window_seconds = '集約時間は1秒以上の整数で入力してください。'
    }

    if (!Number.isInteger(form.retention_days) || form.retention_days < 1) {
      errors.retention_days = '保持期間は1日以上の整数で入力してください。'
    }

    return errors
  }

  const handleSave = async () => {
    if (!form) return

    const validationErrors = validate()
    if (Object.keys(validationErrors).length > 0) {
      setFieldErrors(validationErrors)
      setFormError('入力内容を確認してください。')
      setMessage(null)
      scrollToPageTop()
      return
    }

    setSaving(true)
    setFieldErrors({})
    setFormError(null)
    setMessage(null)

    try {
      const saved = await saveNotificationGlobalSetting(form)
      setForm(saved)
      setMessage('通知全体設定を保存しました。')
      scrollToPageTop()
    } catch (error) {
      setFormError(getErrorMessage(error) ?? '通知全体設定の保存に失敗しました。')
      scrollToPageTop()
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="py-8 text-sm text-zinc-500 dark:text-zinc-400">
        通知全体設定を読み込み中...
      </div>
    )
  }

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            通知全体設定
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            通知配信全体の有効化、Discord通知、通知保持期間を管理します。
          </p>
        </div>
        <Button variant="ghost" href="/master/common-settings">
          共通設定に戻る
        </Button>
      </div>

      {formError && <NotificationBanner type="error" onClose={() => setFormError(null)}>{formError}</NotificationBanner>}
      {message && <NotificationBanner type="success" onClose={() => setMessage(null)}>{message}</NotificationBanner>}

      {!form ? (
        <NotificationBanner type="error">通知全体設定を表示できません。</NotificationBanner>
      ) : (
        <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
          <div className="space-y-6">
            <section className="space-y-3">
              <h2 className="text-base font-semibold text-zinc-900 dark:text-white">基本設定</h2>
              <div className="grid gap-3 lg:grid-cols-2">
                <SettingSwitch
                  label="通知配信"
                  description="OFFの場合、通知本体は記録しつつユーザー配信と外部配信を抑止します。"
                  checked={form.enabled}
                  onChange={checked => patchForm({ enabled: checked })}
                />
                <SettingSwitch
                  label="ブラウザ通知"
                  description="OFFの場合、画面上のユーザー別通知を作成しません。"
                  checked={form.browser_enabled}
                  onChange={checked => patchForm({ browser_enabled: checked })}
                />
              </div>
            </section>

            <section className="space-y-3 border-t border-zinc-200 pt-5 dark:border-zinc-800">
              <h2 className="text-base font-semibold text-zinc-900 dark:text-white">Discord通知</h2>
              <div className="grid gap-4 lg:grid-cols-2">
                <SettingSwitch
                  label="Discord通知"
                  description="ONの場合、最低通知レベル以上の通知をDiscord Webhookへ送信します。"
                  checked={form.discord_enabled}
                  onChange={checked => patchForm({ discord_enabled: checked })}
                />
                <div className="space-y-1.5">
                  <label className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                    Discord最低通知レベル
                  </label>
                  <Select
                    value={{
                      value: form.discord_minimum_level,
                      label: notificationLevelLabels[form.discord_minimum_level],
                    }}
                    options={notificationLevelOptions}
                    onChange={option => {
                      if (!option) return
                      patchForm({ discord_minimum_level: option.value as NotificationLevel })
                    }}
                    className="max-w-none"
                  />
                </div>
                <div className="space-y-1.5 lg:col-span-2">
                  <label htmlFor="discord_webhook_url" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                    Discord Webhook URL
                  </label>
                  <Input
                    id="discord_webhook_url"
                    type="password"
                    value={form.discord_webhook_url ?? ''}
                    onChange={event => patchForm({ discord_webhook_url: event.target.value })}
                    placeholder={form.discord_webhook_configured ? '設定済み' : 'https://discord.com/api/webhooks/...'}
                  />
                </div>
              </div>
            </section>

            <section className="space-y-3 border-t border-zinc-200 pt-5 dark:border-zinc-800">
              <h2 className="text-base font-semibold text-zinc-900 dark:text-white">セキュリティイベント通知</h2>
              <div className="grid gap-4 lg:grid-cols-3">
                <SettingSwitch
                  label="Discord通知"
                  description="不正アクセス監査イベントのDiscord通知を有効にします。"
                  checked={form.security_event_discord_enabled}
                  onChange={checked => patchForm({ security_event_discord_enabled: checked })}
                />
                <div className="space-y-1.5">
                  <label className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                    最低重要度
                  </label>
                  <Select
                    value={{
                      value: form.security_event_minimum_level,
                      label: notificationLevelLabels[form.security_event_minimum_level],
                    }}
                    options={notificationLevelOptions}
                    onChange={option => {
                      if (!option) return
                      patchForm({ security_event_minimum_level: option.value as NotificationLevel })
                    }}
                    className="max-w-none"
                  />
                </div>
                <div className="space-y-1.5">
                  <label htmlFor="security_event_aggregation_window_seconds" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                    集約時間 秒
                  </label>
                  <Input
                    id="security_event_aggregation_window_seconds"
                    type="number"
                    min={1}
                    value={form.security_event_aggregation_window_seconds}
                    onChange={event => {
                      patchForm({ security_event_aggregation_window_seconds: Number(event.target.value) })
                      setFieldErrors(current => ({ ...current, security_event_aggregation_window_seconds: undefined }))
                    }}
                    aria-invalid={Boolean(fieldErrors.security_event_aggregation_window_seconds)}
                  />
                  <FieldErrorMessage message={fieldErrors.security_event_aggregation_window_seconds} />
                </div>
              </div>
            </section>

            <section className="space-y-3 border-t border-zinc-200 pt-5 dark:border-zinc-800">
              <h2 className="text-base font-semibold text-zinc-900 dark:text-white">保持設定</h2>
              <div className="max-w-sm space-y-1.5">
                <label htmlFor="retention_days" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                  通知保持期間 日
                </label>
                <Input
                  id="retention_days"
                  type="number"
                  min={1}
                  value={form.retention_days}
                  onChange={event => {
                    patchForm({ retention_days: Number(event.target.value) })
                    setFieldErrors(current => ({ ...current, retention_days: undefined }))
                  }}
                  aria-invalid={Boolean(fieldErrors.retention_days)}
                />
                <FieldErrorMessage message={fieldErrors.retention_days} />
              </div>
            </section>

            <div className="flex flex-col gap-2 border-t border-zinc-200 pt-5 dark:border-zinc-800 sm:flex-row sm:justify-end">
              <Button variant="ghost" href="/master/common-settings">
                キャンセル
              </Button>
              <Button onClick={handleSave} disabled={saving} className="gap-2">
                <Save className="size-4" />
                {saving ? '保存中...' : '保存'}
              </Button>
            </div>
          </div>
        </Card>
      )}
    </div>
  )
}
