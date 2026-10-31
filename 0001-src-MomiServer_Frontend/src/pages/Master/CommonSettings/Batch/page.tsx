import { sections } from './page.constants'
import { getApiErrorMessage as getErrorMessage } from '@/lib/apiError'
import type { FieldName } from './page.constants'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { useEffect, useState, type FormEvent } from 'react'
import { ArrowLeft, RefreshCw, Save } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  fetchBatchGlobalSetting,
  saveBatchGlobalSetting,
  type BatchGlobalSetting,
  type BatchGlobalSettingValues,
} from './service'
type FormValues = Record<FieldName, string>
type FieldErrors = Partial<Record<FieldName, string>>

function toForm(setting: BatchGlobalSetting): FormValues {
  return Object.fromEntries(
    sections.flatMap(section => section.fields.map(field => [field.name, String(setting[field.name])]))
  ) as FormValues
}

export function BatchGlobalSettings() {
  const [form, setForm] = useState<FormValues | null>(null)
  const [version, setVersion] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [loadAttempt, setLoadAttempt] = useState(0)
  const [notice, setNotice] = useState<{ type: 'error' | 'success'; text: string } | null>(null)
  const [errors, setErrors] = useState<FieldErrors>({})

  useEffect(() => {
    let ignore = false
    fetchBatchGlobalSetting()
      .then(setting => {
        if (ignore) return
        setForm(toForm(setting))
        setVersion(setting.setting_version)
      })
      .catch(error => {
        if (!ignore) setNotice({
          type: 'error',
          text: getErrorMessage(error, 'バッチ共通設定の取得に失敗しました。'),
        })
      })
      .finally(() => { if (!ignore) setLoading(false) })
    return () => { ignore = true }
  }, [loadAttempt])

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!form || saving) return
    const nextErrors: FieldErrors = {}
    const values = {} as BatchGlobalSettingValues
    for (const { fields } of sections) {
      for (const { name, min, max = 2147483647 } of fields) {
        const value = Number(form[name])
        if (!form[name].trim() || !Number.isInteger(value) || value < min || value > max) {
          nextErrors[name] = `${min}以上、${max}以下の整数で入力してください。`
        }
        values[name] = value
      }
    }
    if (!nextErrors.result_wait_timeout_seconds && !nextErrors.default_timeout_seconds
      && values.result_wait_timeout_seconds <= values.default_timeout_seconds) {
      nextErrors.result_wait_timeout_seconds = '標準実行タイムアウトより長くしてください。'
    }
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) {
      setNotice({ type: 'error', text: '入力内容を確認してください。' })
      return
    }

    setSaving(true)
    setNotice(null)
    try {
      const saved = await saveBatchGlobalSetting(values)
      setForm(toForm(saved))
      setVersion(saved.setting_version)
      setNotice({ type: 'success', text: 'バッチ共通設定を保存しました。' })
    } catch (error) {
      setNotice({ type: 'error', text: getErrorMessage(error, 'バッチ共通設定の保存に失敗しました。') })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6 text-left">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold text-zinc-900 dark:text-white">バッチ共通設定</h1>
        <Button variant="ghost" href="/master/common-settings">
          <ArrowLeft />共通設定に戻る
        </Button>
      </header>

      {notice && (
        <NotificationBanner type={notice.type} autoScroll onClose={() => setNotice(null)}>
          {notice.text}
        </NotificationBanner>
      )}

      {loading ? (
        <p role="status" className="text-sm text-zinc-500">読み込み中...</p>
      ) : !form ? (
        <Button variant="outline" onClick={() => {
          setLoading(true)
          setNotice(null)
          setLoadAttempt(current => current + 1)
        }}><RefreshCw />再読み込み</Button>
      ) : (
        <form noValidate onSubmit={handleSave} className="space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-zinc-500 dark:text-zinc-400">設定バージョン {version}</p>
            <Button type="submit" disabled={saving}><Save />{saving ? '保存中...' : '保存'}</Button>
          </div>
          <p className="text-sm text-zinc-500">上限引き下げ後も既存添付は削除されません。超過した定義は添付解除で解消してください。作業領域の保持期間は新しい実行要求から適用され、未送信結果の保持とは別に管理します。</p>
          <fieldset disabled={saving} className="min-w-0 space-y-6">
            {sections.map(section => (
              <section key={section.title} className="border-t border-zinc-200 pt-5 dark:border-zinc-800">
                <h2 className="mb-4 text-base font-semibold text-zinc-900 dark:text-white">{section.title}</h2>
                <div className="grid gap-x-8 gap-y-5 md:grid-cols-2">
                  {section.fields.map(({ name, label, min, max = 2147483647 }) => (
                    <div key={name} className="min-w-0 space-y-1.5">
                      <label htmlFor={name} className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                        {label}
                      </label>
                      <Input id={name} type="number" min={min} max={max} step={1} required
                        value={form[name]}
                        aria-invalid={Boolean(errors[name])}
                        aria-describedby={errors[name] ? `${name}-error` : undefined}
                        onChange={event => {
                          setForm(current => current ? { ...current, [name]: event.target.value } : current)
                          setErrors(current => ({ ...current, [name]: undefined }))
                          setNotice(null)
                        }} />
                      {errors[name] && <p id={`${name}-error`} className="text-xs text-red-600 dark:text-red-300">{errors[name]}</p>}
                    </div>
                  ))}
                </div>
              </section>
            ))}
          </fieldset>
        </form>
      )}
    </div>
  )
}
