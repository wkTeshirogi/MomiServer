import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { getPersonalSettings, savePersonalTheme, savePersonalUsername, type PersonalSettings } from '@/api/personalSettingsApi'
import { useAuth } from './AuthContext'
import { useTheme, type Theme } from './ThemeContext'

type Snapshot = { owner: object | null; settings: PersonalSettings | null; loadError: boolean }
type SettingsContextValue = {
  settings: PersonalSettings | null
  loading: boolean
  saving: boolean
  loadError: boolean
  notice: { type: 'error' | 'success'; message: string } | null
  clearNotice: () => void
  reload: () => void
  updateUsername: (username: string) => Promise<void>
  updateTheme: (theme: Theme) => Promise<void>
}

const PersonalSettingsContext = createContext<SettingsContextValue | null>(null)

export function PersonalSettingsProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const { setTheme } = useTheme()
  const [snapshot, setSnapshot] = useState<Snapshot>({ owner: null, settings: null, loadError: false })
  const [revision, setRevision] = useState(0)
  const [refreshing, setRefreshing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState<SettingsContextValue['notice']>(null)
  const generation = useRef(0)
  const mutationPending = useRef(false)

  // メールアドレス一致ではなく認証ユーザーの参照で所有者を区別し、切替直後に前ユーザーの設定を表示しない。
  const owned = snapshot.owner === user
  const settings = owned ? snapshot.settings : null
  const loading = !!user && (!owned || refreshing)

  useEffect(() => {
    if (!user) return
    const version = generation.current
    let active = true
    void getPersonalSettings().then(result => {
      if (!active) return
      setSnapshot({ owner: user, settings: result, loadError: false })
      setTheme(result.theme)
      setNotice(null)
      setSaving(false)
      mutationPending.current = false
      setRefreshing(false)
    }).catch(() => {
      if (!active) return
      setSnapshot({ owner: user, settings: null, loadError: true })
      setNotice(null)
      setSaving(false)
      mutationPending.current = false
      setRefreshing(false)
    })
    return () => { active = false; generation.current = version + 1 }
  }, [user, revision, setTheme])

  const updateUsername = async (username: string) => {
    if (!settings || loading || mutationPending.current) throw new Error('Settings unavailable')
    const version = generation.current
    mutationPending.current = true
    setSaving(true)
    setNotice(null)
    try {
      const result = await savePersonalUsername(username)

      // 保存中にログアウト・ユーザー切替・再取得が起きた場合、以前の応答を新しい設定へ反映しない。
      if (version === generation.current) setSnapshot(current => ({ ...current,
        settings: current.settings ? { ...current.settings, username: result.username } : null }))
    } finally {
      if (version === generation.current) { mutationPending.current = false; setSaving(false) }
    }
  }

  const updateTheme = async (nextTheme: Theme) => {
    if (!settings || loading || mutationPending.current) return
    const previous = settings.theme
    const version = generation.current
    mutationPending.current = true
    setSaving(true)
    setNotice(null)

    // テーマだけは先にプレビューする。保存失敗時の復元も同じ世代に限定し、切替後のテーマを戻さない。
    setTheme(nextTheme)
    try {
      const result = await savePersonalTheme(nextTheme)
      if (version !== generation.current) return
      setSnapshot(current => ({ ...current,
        settings: current.settings ? { ...current.settings, theme: result.theme } : null }))
      setTheme(result.theme)
      setNotice({ type: 'success', message: 'テーマを保存しました。' })
    } catch {
      if (version !== generation.current) return
      setTheme(previous)
      setNotice({ type: 'error', message: 'テーマを保存できませんでした。変更前のテーマに戻しました。' })
    } finally {
      if (version === generation.current) { mutationPending.current = false; setSaving(false) }
    }
  }

  return <PersonalSettingsContext.Provider value={{ settings, loading, saving,
    loadError: !!user && owned && snapshot.loadError,
    notice: !!user && owned ? notice : null,
    clearNotice: () => setNotice(null),
    reload: () => { if (!mutationPending.current) { setRefreshing(true); setRevision(value => value + 1) } },
    updateUsername, updateTheme,
  }}>{children}</PersonalSettingsContext.Provider>
}

export function usePersonalSettings() {
  const context = useContext(PersonalSettingsContext)
  if (!context) throw new Error('PersonalSettingsProvider is required')
  return context
}
