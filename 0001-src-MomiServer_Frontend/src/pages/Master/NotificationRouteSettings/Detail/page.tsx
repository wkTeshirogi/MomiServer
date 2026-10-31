import { defaultForm } from './page.constants'
import { getApiErrorMessage as getErrorMessage } from '@/lib/apiError'
import type { FormState } from './page.constants'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Save } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import {
  fetchNotificationRouteSettingDetail,
  notificationLevelLabels,
  notificationLevelOptions,
  notificationRoleLabels,
  notificationRoleOptions,
  notificationSourceTypeLabels,
  notificationSourceTypeOptions,
  notificationTargetTypeLabels,
  notificationTargetTypeOptions,
  notificationTypeLabels,
  notificationTypeOptions,
  saveNotificationRouteSetting,
  type NotificationLevel,
  type NotificationRoleCode,
  type NotificationRouteSettingInput,
  type NotificationSourceType,
  type NotificationTargetType,
  type NotificationType,
} from '../service'

type FieldErrors = Partial<Record<keyof FormState, string>>


function FieldErrorMessage({ message }: { message?: string }) {
  if (!message) return null
  return <p className="text-xs text-red-600 dark:text-red-300">{message}</p>
}

function scrollToPageTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

export function NotificationRouteSettingDetail() {
  const { routeSettingId } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const isNew = !routeSettingId || routeSettingId === 'new'
  const numericId = Number(routeSettingId)

  const initialTargetUserId = searchParams.get('targetUserId') ?? ''
  const initialForm = useMemo<FormState>(() => {
    if (!isNew || !initialTargetUserId) return defaultForm
    return {
      ...defaultForm,
      target_type: 'user',
      target_role: '',
      target_user_id: initialTargetUserId,
    }
  }, [initialTargetUserId, isNew])

  const [form, setForm] = useState<FormState>(initialForm)
  const [loading, setLoading] = useState(!isNew)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})

  useEffect(() => {
    let ignore = false

    async function loadDetail() {
      if (isNew) {
        setForm(initialForm)
        setLoading(false)
        return
      }

      if (!Number.isFinite(numericId)) {
        setFormError('通知ルート設定IDが不正です。')
        setLoading(false)
        return
      }

      try {
        const detail = await fetchNotificationRouteSettingDetail(numericId)
        if (!ignore) {
          setForm({
            notification_type: detail.notification_type,
            source_type: detail.source_type,
            source_id: detail.source_id ?? '',
            target_type: detail.target_type,
            target_role: detail.target_role ?? '',
            target_user_id: detail.target_user_id ? String(detail.target_user_id) : '',
            minimum_level: detail.minimum_level,
            browser_enabled: detail.browser_enabled,
            discord_enabled: detail.discord_enabled,
            enabled: detail.enabled,
          })
        }
      } catch (error) {
        if (!ignore) {
          setFormError(getErrorMessage(error) ?? '通知ルート設定詳細の取得に失敗しました。')
          scrollToPageTop()
        }
      } finally {
        if (!ignore) setLoading(false)
      }
    }

    void loadDetail()

    return () => {
      ignore = true
    }
  }, [initialForm, isNew, numericId])

  const validate = (): FieldErrors => {
    const errors: FieldErrors = {}

    if (form.target_type === 'notification_role' && !form.target_role.trim()) {
      errors.target_role = '通知ロールを入力してください。'
    }

    if (form.target_type === 'user') {
      const targetUserId = Number(form.target_user_id)
      if (!form.target_user_id.trim() || !Number.isInteger(targetUserId) || targetUserId <= 0) {
        errors.target_user_id = '対象ユーザーIDは1以上の整数で入力してください。'
      }
    }

    return errors
  }

  const handleSave = async () => {
    const validationErrors = validate()
    if (Object.keys(validationErrors).length > 0) {
      setFieldErrors(validationErrors)
      setFormError('入力内容を確認してください。')
      setMessage(null)
      scrollToPageTop()
      return
    }

    const input: NotificationRouteSettingInput = {
      id: isNew ? undefined : numericId,
      notification_type: form.notification_type,
      source_type: form.source_type,
      source_id: form.source_id,
      target_type: form.target_type,
      target_role: form.target_role || null,
      target_user_id: form.target_type === 'user' ? Number(form.target_user_id) : null,
      minimum_level: form.minimum_level,
      browser_enabled: form.browser_enabled,
      discord_enabled: form.discord_enabled,
      enabled: form.enabled,
    }

    setSaving(true)
    setFieldErrors({})
    setFormError(null)
    setMessage(null)

    try {
      const saved = await saveNotificationRouteSetting(input)
      setMessage(isNew ? '通知ルート設定を作成しました。' : '通知ルート設定を保存しました。')
      scrollToPageTop()
      if (isNew) {
        navigate(`/master/notification-route-settings/${saved.id}`, { replace: true })
      }
    } catch (error) {
      setFormError(getErrorMessage(error) ?? '通知ルート設定の保存に失敗しました。')
      scrollToPageTop()
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="py-8 text-sm text-zinc-500 dark:text-zinc-400">
        通知ルート設定を読み込み中...
      </div>
    )
  }

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            {isNew ? '通知ルート設定作成' : '通知ルート設定詳細'}
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            通知種別・発生元・宛先・通知チャネルを設定します。
          </p>
        </div>
        <Button variant="ghost" href="/master/notification-route-settings">
          一覧に戻る
        </Button>
      </div>

      {formError && <NotificationBanner type="error" onClose={() => setFormError(null)}>{formError}</NotificationBanner>}
      {message && <NotificationBanner type="success" onClose={() => setMessage(null)}>{message}</NotificationBanner>}

      <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
        <div className="grid gap-5 lg:grid-cols-2">
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
              通知種別
            </label>
            <Select
              value={{ value: form.notification_type, label: notificationTypeLabels[form.notification_type] }}
              options={notificationTypeOptions}
              onChange={option => {
                if (!option) return
                setForm(current => ({ ...current, notification_type: option.value as NotificationType }))
              }}
              className="max-w-none"
            />
          </div>

          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
              発生元
            </label>
            <Select
              value={{ value: form.source_type, label: notificationSourceTypeLabels[form.source_type] }}
              options={notificationSourceTypeOptions}
              onChange={option => {
                if (!option) return
                setForm(current => ({ ...current, source_type: option.value as NotificationSourceType }))
              }}
              className="max-w-none"
            />
          </div>

          <div className="space-y-1.5">
            <label htmlFor="source_id" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
              発生元ID
            </label>
            <Input
              id="source_id"
              value={form.source_id}
              onChange={event => setForm(current => ({ ...current, source_id: event.target.value }))}
              placeholder="未指定の場合は全体"
            />
          </div>

          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
              宛先種別
            </label>
            <Select
              value={{ value: form.target_type, label: notificationTargetTypeLabels[form.target_type] }}
              options={notificationTargetTypeOptions}
              onChange={option => {
                if (!option) return
                const nextTargetType = option.value as NotificationTargetType
                setForm(current => ({
                  ...current,
                  target_type: nextTargetType,
                  target_role: nextTargetType === 'notification_role' ? current.target_role : '',
                  target_user_id: nextTargetType === 'user' ? current.target_user_id : '',
                }))
                setFieldErrors(current => ({ ...current, target_role: undefined, target_user_id: undefined }))
              }}
              className="max-w-none"
            />
          </div>

          {form.target_type === 'notification_role' ? (
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                通知ロール
              </label>
              <Select
                value={form.target_role
                  ? { value: form.target_role, label: notificationRoleLabels[form.target_role] }
                  : null}
                options={notificationRoleOptions}
                placeholder="通知ロールを選択"
                onChange={option => {
                  setForm(current => ({
                    ...current,
                    target_role: option ? option.value as NotificationRoleCode : '',
                  }))
                  setFieldErrors(current => ({ ...current, target_role: undefined }))
                }}
                className="max-w-none"
              />
              <FieldErrorMessage message={fieldErrors.target_role} />
            </div>
          ) : (
            <div className="space-y-1.5">
              <label htmlFor="target_user_id" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                対象ユーザーID
              </label>
              <Input
                id="target_user_id"
                type="number"
                min={1}
                value={form.target_user_id}
                onChange={event => {
                  setForm(current => ({ ...current, target_user_id: event.target.value }))
                  setFieldErrors(current => ({ ...current, target_user_id: undefined }))
                }}
                aria-invalid={Boolean(fieldErrors.target_user_id)}
              />
              <FieldErrorMessage message={fieldErrors.target_user_id} />
            </div>
          )}

          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
              最低通知レベル
            </label>
            <Select
              value={{ value: form.minimum_level, label: notificationLevelLabels[form.minimum_level] }}
              options={notificationLevelOptions}
              onChange={option => {
                if (!option) return
                setForm(current => ({ ...current, minimum_level: option.value as NotificationLevel }))
              }}
              className="max-w-none"
            />
          </div>
        </div>

        <div className="mt-6 grid gap-3 border-t border-zinc-200 pt-5 dark:border-zinc-800 sm:grid-cols-3">
          {[
            ['enabled', '有効'],
            ['browser_enabled', 'ブラウザ通知'],
            ['discord_enabled', 'Discord通知'],
          ].map(([key, label]) => (
            <label key={key} className="flex items-center justify-between rounded-lg border border-zinc-200 px-4 py-3 text-sm dark:border-zinc-800">
              <span className="font-medium text-zinc-900 dark:text-zinc-100">{label}</span>
              <input
                type="checkbox"
                checked={Boolean(form[key as keyof FormState])}
                onChange={event => setForm(current => ({ ...current, [key]: event.target.checked }))}
                className="size-4 accent-purple-500"
              />
            </label>
          ))}
        </div>

        <div className="mt-6 flex flex-col gap-2 border-t border-zinc-200 pt-5 dark:border-zinc-800 sm:flex-row sm:justify-end">
          <Button variant="ghost" href="/master/notification-route-settings">
            キャンセル
          </Button>
          <Button onClick={handleSave} disabled={saving} className="gap-2">
            <Save className="size-4" />
            {saving ? '保存中...' : '保存'}
          </Button>
        </div>
      </Card>
    </div>
  )
}
