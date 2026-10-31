import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getApiErrorMessage, getApiErrorStatus } from '@/lib/apiError'
import { KeyRound, Save, Sun, Moon } from 'lucide-react'
import { changePersonalPassword, type PasswordChange, type PersonalSettings } from '@/api/personalSettingsApi'
import { usePersonalSettings } from '@/contexts/PersonalSettingsContext'
import { useAuth } from '@/contexts/AuthContext'
import { useTheme } from '@/contexts/ThemeContext'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { usernameMaxLength, passwordMinLength, passwordMaxBytes } from './page.constants'

export function PersonalSettingsPage() {
  const { settings, loading } = usePersonalSettings()
  const { user } = useAuth()
  return <div className="space-y-6 text-left">
    <h1 className="text-xl font-semibold">個人設定</h1>
    {loading && <p role="status" className="py-8 text-center text-sm text-zinc-500">読み込み中...</p>}
    {!loading && settings && <SettingsForm key={user?.email} initial={settings} />}
  </div>
}

function SettingsForm({ initial }: { initial: PersonalSettings }) {
  const { updateUsername, updateTheme, saving, clearNotice } = usePersonalSettings()
  const { completePasswordChange } = useAuth()
  const { theme } = useTheme()
  const navigate = useNavigate()
  const [username, setUsername] = useState(initial.username ?? '')
  const [password, setPassword] = useState<PasswordChange>({ current_password: '', new_password: '', confirm_password: '' })
  const [passwordSaving, setPasswordSaving] = useState(false)
  const [notice, setNotice] = useState<{ type: 'error' | 'success'; message: string } | null>(null)
  const busy = saving || passwordSaving
  const showNotice = (type: 'error' | 'success', message: string) => {
    clearNotice()
    setNotice({ type, message })
  }

  const saveUsername = async (event: React.FormEvent) => {
    event.preventDefault()
    if (busy) return
    if (!username.trim() || username.trim().length > usernameMaxLength) {
      showNotice('error', 'ユーザー名は1～100文字で入力してください。')
      return
    }
    setNotice(null)
    try {
      await updateUsername(username.trim())
      setUsername(username.trim())
      showNotice('success', 'ユーザー名を保存しました。')
    } catch {
      showNotice('error', 'ユーザー名を保存できませんでした。')
    }
  }

  const savePassword = async (event: React.FormEvent) => {
    event.preventDefault()
    if (busy) return
    if (!password.current_password || !password.new_password.trim()
      || password.new_password.length < passwordMinLength
      || new TextEncoder().encode(password.new_password).length > passwordMaxBytes) {
      showNotice('error', '現在のパスワードを入力し、新しいパスワードは8文字以上・UTF-8で72バイト以内にしてください。')
      return
    }
    if (password.new_password !== password.confirm_password) {
      showNotice('error', '新しいパスワードと確認用パスワードが一致しません。')
      return
    }
    if (password.new_password === password.current_password) {
      showNotice('error', '現在とは異なるパスワードを指定してください。')
      return
    }
    clearNotice()
    setNotice(null)
    setPasswordSaving(true)
    try {
      await changePersonalPassword(password)
      setPassword({ current_password: '', new_password: '', confirm_password: '' })
      completePasswordChange()
      navigate('/login', { replace: true, state: { passwordChanged: true } })
    } catch (cause) {
      const message = getApiErrorStatus(cause) === 400 ? getApiErrorMessage(cause) : null
      showNotice('error', message ?? 'パスワードを変更できませんでした。')
    } finally {
      setPasswordSaving(false)
    }
  }

  return <>
    {notice && <NotificationBanner type={notice.type} autoScroll onClose={() => setNotice(null)}>{notice.message}</NotificationBanner>}
    <section aria-labelledby="profile-heading" className="border-t border-zinc-200 py-5 dark:border-zinc-800">
      <h2 id="profile-heading" className="mb-4 text-base font-semibold">ユーザー名</h2>
      <form noValidate onSubmit={event => void saveUsername(event)} className="max-w-xl space-y-4">
        <label htmlFor="personal-username" className="block text-sm font-medium">ユーザー名</label>
        <Input id="personal-username" autoComplete="nickname" maxLength={usernameMaxLength} value={username}
          disabled={busy} onChange={event => setUsername(event.target.value)} />
        <Button type="submit" disabled={busy}><Save className="size-4" />ユーザー名を保存</Button>
      </form>
    </section>

    <section aria-labelledby="password-heading" className="border-t border-zinc-200 py-5 dark:border-zinc-800">
      <h2 id="password-heading" className="mb-4 text-base font-semibold">パスワード変更</h2>
      <form noValidate onSubmit={event => void savePassword(event)} className="grid max-w-xl gap-4">
        <div className="space-y-2">
          <label htmlFor="current-password" className="text-sm font-medium">現在のパスワード</label>
          <Input id="current-password" type="password" autoComplete="current-password" disabled={busy}
            maxLength={passwordMaxBytes} value={password.current_password}
            onChange={event => setPassword(current => ({ ...current, current_password: event.target.value }))} />
        </div>
        <div className="space-y-2">
          <label htmlFor="new-password" className="text-sm font-medium">新しいパスワード</label>
          <Input id="new-password" type="password" autoComplete="new-password" disabled={busy}
            minLength={passwordMinLength} maxLength={passwordMaxBytes} value={password.new_password}
            onChange={event => setPassword(current => ({ ...current, new_password: event.target.value }))} />
        </div>
        <div className="space-y-2">
          <label htmlFor="confirm-password" className="text-sm font-medium">新しいパスワード（確認）</label>
          <Input id="confirm-password" type="password" autoComplete="new-password" disabled={busy}
            maxLength={passwordMaxBytes} value={password.confirm_password}
            onChange={event => setPassword(current => ({ ...current, confirm_password: event.target.value }))} />
        </div>
        <Button type="submit" disabled={busy} className="justify-self-start"><KeyRound className="size-4" />{passwordSaving ? '変更中...' : 'パスワードを変更'}</Button>
      </form>
    </section>

    <section aria-labelledby="appearance-heading" className="border-t border-zinc-200 py-5 dark:border-zinc-800">
      <h2 id="appearance-heading" className="mb-4 text-base font-semibold">表示設定</h2>
      <fieldset disabled={busy}>
        <legend className="mb-3 text-sm font-medium">テーマ</legend>
        <div className="inline-flex flex-wrap gap-1 rounded-lg border border-zinc-300 p-1 dark:border-zinc-700">
          <label className="flex cursor-pointer items-center gap-2 rounded px-3 py-2 text-sm has-[:checked]:bg-zinc-200 dark:has-[:checked]:bg-zinc-700">
            <input type="radio" name="personal-theme" value="light" checked={theme === 'light'}
              onChange={() => { setNotice(null); void updateTheme('light') }} /><Sun className="size-4" />ライト
          </label>
          <label className="flex cursor-pointer items-center gap-2 rounded px-3 py-2 text-sm has-[:checked]:bg-zinc-200 dark:has-[:checked]:bg-zinc-700">
            <input type="radio" name="personal-theme" value="dark" checked={theme === 'dark'}
              onChange={() => { setNotice(null); void updateTheme('dark') }} /><Moon className="size-4" />ダーク
          </label>
        </div>
      </fieldset>
    </section>
  </>
}
