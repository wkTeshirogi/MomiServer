import { roleOptions } from './page.constants'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Ban, Plus, Save } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import {
  roleClasses,
  roleLabels,
  collectFieldErrors,
  fetchUserNotificationPreferences,
  notificationLevelLabels,
  notificationLevelOptions,
  notificationTypeOptions,
  notificationTypeLabels,
  notificationTypes,
  saveUserNotificationPreferences,
  useUserMasterDetailService,
  type FieldErrors,
  type NotificationLevel,
  type NotificationType,
  type UserRole,
  type UserMasterItem,
  type UserNotificationPreferenceItem,
  type UserNotificationRouteSettingItem,
  fetchUserNotificationRouteSettings,
  notificationSourceTypeLabels,
} from './service'

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid grid-cols-[100px_1fr] gap-2 text-sm">
      <span className="text-zinc-500 dark:text-zinc-400">{label}</span>
      <span className="text-zinc-900 dark:text-zinc-100">{value}</span>
    </div>
  )
}

function FieldErrorMessage({ message }: { message?: string }) {
  if (!message) return null

  return (
    <p className="text-xs text-red-600 dark:text-red-300">
      {message}
    </p>
  )
}


type UserFormState = {
  email: string
  password: string
  role: UserRole
}

function createInitialFormState(user: UserMasterItem | null): UserFormState {
  return {
    email: user?.email ?? '',
    password: '',
    role: user?.role ?? 'USER',
  }
}

export function UserMasterDetail() {
  const { userId } = useParams()
  const navigate = useNavigate()
  const {
    user,
    loading,
    error,
    isNew,
    save,
    reload,
  } = useUserMasterDetailService(userId)

  if (loading) {
    return (
      <div className="space-y-6 text-left">
        <PageHeader isNew={isNew} onBack={() => navigate('/master/users')} />
        <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
          <div className="py-8 text-sm text-zinc-500 dark:text-zinc-400">読み込み中...</div>
        </Card>
      </div>
    )
  }

  return (
    <UserMasterDetailForm
      key={isNew ? 'new' : user?.id ?? 'missing'}
      user={user}
      error={error}
      isNew={isNew}
      onBack={() => navigate('/master/users')}
      onCreateComplete={() => navigate('/master/users')}
      reload={reload}
      save={save}
    />
  )
}

function PageHeader({ isNew, onBack }: { isNew: boolean; onBack: () => void }) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
          {isNew ? 'ユーザー新規作成' : 'ユーザーマスター詳細'}
        </h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          ユーザー情報の確認、作成、編集、利用停止を行います。
        </p>
      </div>
      <div className="flex items-center gap-3">
        <Button variant="ghost" onClick={onBack}>一覧に戻る</Button>
      </div>
    </div>
  )
}

type UserMasterDetailFormProps = {
  user: UserMasterItem | null
  error: string | null
  isNew: boolean
  onBack: () => void
  onCreateComplete: () => void
  reload: () => Promise<void>
  save: (input: { id?: number; email: string; password?: string; role: UserRole }) => Promise<void>
}

function UserMasterDetailForm({
  user,
  error,
  isNew,
  onBack,
  onCreateComplete,
  reload,
  save,
}: UserMasterDetailFormProps) {
  const [form, setForm] = useState<UserFormState>(() => createInitialFormState(user))
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [notificationPreferences, setNotificationPreferences] = useState<UserNotificationPreferenceItem[]>([])
  const [notificationRouteSettings, setNotificationRouteSettings] = useState<UserNotificationRouteSettingItem[]>([])
  const [notificationLoading, setNotificationLoading] = useState(!isNew && Boolean(user?.id))
  const [notificationSaving, setNotificationSaving] = useState(false)
  const [addNotificationType, setAddNotificationType] = useState<NotificationType | null>(null)

  const availableNotificationTypes = useMemo(
    () => notificationTypes.filter(type =>
      !notificationPreferences.some(preference => preference.notification_type === type)
    ),
    [notificationPreferences]
  )

  const addNotificationTypeOptions = useMemo(
    () => notificationTypeOptions.filter(option =>
      availableNotificationTypes.includes(option.value as NotificationType)
    ),
    [availableNotificationTypes]
  )

  const selectedAddNotificationType = useMemo(() => {
    const selectedType = addNotificationType ?? availableNotificationTypes[0]
    return addNotificationTypeOptions.find(option => option.value === selectedType) ?? null
  }, [addNotificationType, addNotificationTypeOptions, availableNotificationTypes])

  useEffect(() => {
    let ignore = false

    async function loadNotificationPreferences() {
      if (isNew || !user?.id) {
        setNotificationPreferences([])
        setNotificationLoading(false)
        return
      }

      try {
        setNotificationLoading(true)
        const [preferences, routeSettings] = await Promise.all([
          fetchUserNotificationPreferences(user.id),
          fetchUserNotificationRouteSettings(user.id),
        ])
        if (!ignore) {
          setNotificationPreferences(preferences)
          setNotificationRouteSettings(routeSettings)
        }
      } catch {
        if (!ignore) {
          setFormError('ユーザー別通知設定の取得に失敗しました。')
          scrollToPageTop()
        }
      } finally {
        if (!ignore) setNotificationLoading(false)
      }
    }

    void loadNotificationPreferences()

    return () => {
      ignore = true
    }
  }, [isNew, user?.id])

  const validate = (): FieldErrors => {
    const errors: FieldErrors = {}

    if (!form.email.trim()) {
      errors.email = 'メールアドレスを入力してください。'
    }

    if (isNew && form.password.length < 8) {
      errors.password = '新規作成時は8文字以上のパスワードを入力してください。'
    }

    if (!isNew && form.password && form.password.length < 8) {
      errors.password = 'パスワードを変更する場合は8文字以上で入力してください。'
    }

    return errors
  }

  const hasFieldErrors = (errors: FieldErrors) => Object.keys(errors).length > 0

  const applyApiError = (error: unknown, fallbackMessage: string) => {
    const parsed = collectFieldErrors(error)

    setFieldErrors(parsed.fieldErrors)
    setFormError(hasFieldErrors(parsed.fieldErrors) ? '入力内容を確認してください。' : parsed.formError ?? fallbackMessage)
    scrollToPageTop()
  }

  const handleSave = async () => {
    const validationErrors = validate()
    if (hasFieldErrors(validationErrors)) {
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
      await save({
        id: user?.id,
        email: form.email.trim(),
        password: form.password || undefined,
        role: form.role,
      })
      setForm(current => ({ ...current, password: '' }))
      setMessage(isNew ? 'ユーザーを作成しました。' : 'ユーザー情報を保存しました。')
      scrollToPageTop()

      if (isNew) {
        onCreateComplete()
        return
      }

      await reload()
    } catch (error) {
      applyApiError(error, isNew ? 'ユーザーの作成に失敗しました。' : 'ユーザー情報の保存に失敗しました。')
    } finally {
      setSaving(false)
    }
  }

  const handleDisable = async () => {
    if (!user || form.role === 'BAN_USER') return

    const validationErrors = validate()
    if (hasFieldErrors(validationErrors)) {
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
      await save({
        id: user.id,
        email: form.email.trim(),
        password: form.password || undefined,
        role: 'BAN_USER',
      })
      setForm(current => ({ ...current, password: '', role: 'BAN_USER' }))
      setMessage('ユーザーを利用停止にしました。')
      scrollToPageTop()
      await reload()
    } catch (error) {
      applyApiError(error, 'ユーザーの利用停止に失敗しました。')
    } finally {
      setSaving(false)
    }
  }

  const patchNotificationPreference = (
    notificationType: UserNotificationPreferenceItem['notification_type'],
    patch: Partial<UserNotificationPreferenceItem>
  ) => {
    setNotificationPreferences(currentPreferences => currentPreferences.map(preference =>
      preference.notification_type === notificationType
        ? { ...preference, ...patch }
        : preference
    ))
  }

  const handleAddNotificationPreference = () => {
    const nextNotificationType = (selectedAddNotificationType?.value ?? availableNotificationTypes[0]) as NotificationType | undefined
    if (!nextNotificationType) return

    setNotificationPreferences(currentPreferences => [
      ...currentPreferences,
      {
        notification_type: nextNotificationType,
        minimum_level: 'info',
        browser_enabled: true,
        discord_enabled: true,
        enabled: true,
      },
    ])
    setAddNotificationType(null)
  }

  const handleSaveNotificationPreferences = async () => {
    if (!user?.id) return

    setNotificationSaving(true)
    setFormError(null)
    setMessage(null)

    try {
      const savedPreferences = await saveUserNotificationPreferences(user.id, notificationPreferences)
      setNotificationPreferences(savedPreferences)
      setMessage('ユーザー別通知設定を保存しました。')
      scrollToPageTop()
    } catch {
      setFormError('ユーザー別通知設定の保存に失敗しました。')
      scrollToPageTop()
    } finally {
      setNotificationSaving(false)
    }
  }

  return (
    <div className="space-y-6 text-left">
      <PageHeader isNew={isNew} onBack={onBack} />

      {error && (
        <NotificationBanner type="error">
          {error}
        </NotificationBanner>
      )}
      {formError && (
        <NotificationBanner type="error" onClose={() => setFormError(null)}>
          {formError}
        </NotificationBanner>
      )}
      {message && (
        <NotificationBanner type="success" onClose={() => setMessage(null)}>
          {message}
        </NotificationBanner>
      )}

      <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
        <div className="max-w-md space-y-4">
          <div className="space-y-1.5">
            <label htmlFor="email" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
              メールアドレス
            </label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              required
              value={form.email}
              onChange={event => {
                setForm(current => ({ ...current, email: event.target.value }))
                setFieldErrors(current => ({ ...current, email: undefined }))
              }}
              className="w-full"
              placeholder="admin@example.com"
              aria-invalid={Boolean(fieldErrors.email)}
            />
            <FieldErrorMessage message={fieldErrors.email} />
          </div>

            <div className="space-y-1.5">
              <label htmlFor="password" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                パスワード
              </label>
              <Input
                id="password"
                type="password"
                autoComplete={isNew ? 'new-password' : 'off'}
                value={form.password}
                onChange={event => {
                  setForm(current => ({ ...current, password: event.target.value }))
                  setFieldErrors(current => ({ ...current, password: undefined }))
                }}
                className="w-full"
                placeholder={isNew ? '8文字以上' : '変更しない場合は空欄'}
                aria-invalid={Boolean(fieldErrors.password)}
              />
              <FieldErrorMessage message={fieldErrors.password} />
            </div>

            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                権限/状態
              </label>
              <Select
                value={roleOptions.find(option => option.value === form.role) ?? null}
                options={roleOptions}
                onChange={option => {
                  if (!option) return

                  setForm(current => ({ ...current, role: option.value as UserRole }))
                  setFieldErrors(current => ({ ...current, role: undefined }))
                }}
              />
              <span className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${roleClasses[form.role]}`}>
                {roleLabels[form.role]}
              </span>
              <FieldErrorMessage message={fieldErrors.role} />
            </div>

            {!isNew && user && (
              <div className="space-y-3 border-t border-zinc-200 pt-4 dark:border-zinc-800">
                <DetailRow label="作成日時" value={user.createdAt} />
                <DetailRow label="更新日時" value={user.updatedAt} />
                <DetailRow label="最終ログイン" value={user.lastLoginAt ?? '未ログイン'} />
              </div>
            )}

            <div className="flex flex-col gap-2 border-t border-zinc-200 pt-4 dark:border-zinc-800 sm:flex-row sm:justify-end">
              <Button variant="ghost" onClick={onBack}>キャンセル</Button>
              {!isNew && form.role !== 'BAN_USER' && (
                <Button
                  variant="destructive"
                  onClick={handleDisable}
                  disabled={saving}
                  className="gap-2"
                >
                  <Ban className="size-4" />
                  利用停止
                </Button>
              )}
              <Button
                onClick={handleSave}
                disabled={saving}
                className="gap-2"
              >
                <Save className="size-4" />
                {saving ? '保存中...' : isNew ? '作成' : '保存'}
              </Button>
            </div>

          </div>
      </Card>

      {!isNew && user && (
        <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
          <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h2 className="text-lg font-semibold text-zinc-900 dark:text-white">
                ユーザー別通知設定
              </h2>
              <p className="text-sm text-zinc-500 dark:text-zinc-400">
                通知種別ごとの受信可否と最低通知レベルを設定します。
              </p>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <Select
                value={selectedAddNotificationType}
                options={addNotificationTypeOptions}
                placeholder="追加する通知種別"
                disabled={notificationLoading || notificationSaving || addNotificationTypeOptions.length === 0}
                onChange={option => setAddNotificationType((option?.value as NotificationType | undefined) ?? null)}
              />
              <Button
                variant="outline"
                onClick={handleAddNotificationPreference}
                disabled={notificationLoading || notificationSaving || addNotificationTypeOptions.length === 0}
                className="gap-2"
              >
                <Plus className="size-4" />
                追加
              </Button>
              <Button
                onClick={handleSaveNotificationPreferences}
                disabled={notificationLoading || notificationSaving || notificationPreferences.length === 0}
                className="gap-2"
              >
                <Save className="size-4" />
                {notificationSaving ? '保存中...' : '通知設定を保存'}
              </Button>
            </div>
          </div>

          {notificationLoading ? (
            <div className="py-8 text-sm text-zinc-500 dark:text-zinc-400">通知設定を読み込み中...</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] table-auto text-sm">
                <thead>
                  <tr className="border-b border-zinc-200 dark:border-zinc-800">
                    <th className="w-44 px-3 py-2 text-left font-semibold">通知種別</th>
                    <th className="w-40 px-3 py-2 text-left font-semibold">最低レベル</th>
                    <th className="w-28 px-3 py-2 text-center font-semibold">有効</th>
                    <th className="w-32 px-3 py-2 text-center font-semibold">ブラウザ</th>
                    <th className="w-32 px-3 py-2 text-center font-semibold">Discord</th>
                  </tr>
                </thead>
                <tbody>
                  {notificationPreferences.length === 0 && (
                    <tr>
                      <td className="px-3 py-8 text-zinc-500 dark:text-zinc-400" colSpan={5}>
                        通知設定はまだ追加されていません。追加する通知種別を選択して追加してください。
                      </td>
                    </tr>
                  )}
                  {notificationPreferences.map(preference => (
                    <tr key={preference.notification_type} className="border-b border-zinc-200 dark:border-zinc-800">
                      <td className="px-3 py-3 font-medium text-zinc-900 dark:text-white">
                        {notificationTypeLabels[preference.notification_type]}
                      </td>
                      <td className="px-3 py-3">
                        <Select
                          value={{
                            value: preference.minimum_level,
                            label: notificationLevelLabels[preference.minimum_level],
                          }}
                          options={notificationLevelOptions}
                          onChange={option => {
                            if (!option) return
                            patchNotificationPreference(preference.notification_type, {
                              minimum_level: option.value as NotificationLevel,
                            })
                          }}
                        />
                      </td>
                      <td className="px-3 py-3 text-center">
                        <input
                          type="checkbox"
                          checked={preference.enabled}
                          onChange={event => patchNotificationPreference(preference.notification_type, {
                            enabled: event.target.checked,
                          })}
                          className="size-4 accent-purple-500"
                          aria-label={`${notificationTypeLabels[preference.notification_type]}を有効にする`}
                        />
                      </td>
                      <td className="px-3 py-3 text-center">
                        <input
                          type="checkbox"
                          checked={preference.browser_enabled}
                          onChange={event => patchNotificationPreference(preference.notification_type, {
                            browser_enabled: event.target.checked,
                          })}
                          className="size-4 accent-purple-500"
                          aria-label={`${notificationTypeLabels[preference.notification_type]}のブラウザ通知を有効にする`}
                        />
                      </td>
                      <td className="px-3 py-3 text-center">
                        <input
                          type="checkbox"
                          checked={preference.discord_enabled}
                          onChange={event => patchNotificationPreference(preference.notification_type, {
                            discord_enabled: event.target.checked,
                          })}
                          className="size-4 accent-purple-500"
                          aria-label={`${notificationTypeLabels[preference.notification_type]}のDiscord通知を有効にする`}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="mt-8 border-t border-zinc-200 pt-6 dark:border-zinc-800">
            <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <h3 className="text-base font-semibold text-zinc-900 dark:text-white">
                  ユーザー直接通知ルート
                </h3>
                <p className="text-sm text-zinc-500 dark:text-zinc-400">
                  通知ルート設定でこのユーザーIDが直接宛先になっている定義です。
                </p>
              </div>
              <Button
                variant="outline"
                href={`/master/notification-route-settings/new?targetUserId=${user.id}`}
                className="gap-2"
              >
                <Plus className="size-4" />
                直接ルートを追加
              </Button>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] table-auto text-sm">
                <thead>
                  <tr className="border-b border-zinc-200 dark:border-zinc-800">
                    <th className="w-44 px-3 py-2 text-left font-semibold">通知種別</th>
                    <th className="w-36 px-3 py-2 text-left font-semibold">発生元</th>
                    <th className="w-36 px-3 py-2 text-left font-semibold">発生元ID</th>
                    <th className="w-32 px-3 py-2 text-left font-semibold">最低レベル</th>
                    <th className="w-24 px-3 py-2 text-center font-semibold">有効</th>
                    <th className="w-28 px-3 py-2 text-center font-semibold">ブラウザ</th>
                    <th className="w-28 px-3 py-2 text-center font-semibold">Discord</th>
                  </tr>
                </thead>
                <tbody>
                  {notificationRouteSettings.length === 0 && (
                    <tr>
                      <td className="px-3 py-8 text-zinc-500 dark:text-zinc-400" colSpan={7}>
                        このユーザーを直接宛先にしている通知ルート設定はありません。
                      </td>
                    </tr>
                  )}
                  {notificationRouteSettings.map(routeSetting => (
                    <tr key={routeSetting.id} className="border-b border-zinc-200 dark:border-zinc-800">
                      <td className="px-3 py-3 font-medium text-zinc-900 dark:text-white">
                        {notificationTypeLabels[routeSetting.notification_type]}
                      </td>
                      <td className="px-3 py-3">
                        {notificationSourceTypeLabels[routeSetting.source_type]}
                      </td>
                      <td className="px-3 py-3">
                        {routeSetting.source_id || '全体'}
                      </td>
                      <td className="px-3 py-3">
                        {notificationLevelLabels[routeSetting.minimum_level]}
                      </td>
                      <td className="px-3 py-3 text-center">
                        {routeSetting.enabled ? 'ON' : 'OFF'}
                      </td>
                      <td className="px-3 py-3 text-center">
                        {routeSetting.browser_enabled ? 'ON' : 'OFF'}
                      </td>
                      <td className="px-3 py-3 text-center">
                        {routeSetting.discord_enabled ? 'ON' : 'OFF'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </Card>
      )}
    </div>
  )
}

function scrollToPageTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
