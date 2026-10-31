import { NotificationBanner } from '@/components/ui/notification-banner'
import { useState } from 'react'
import { useNavigate, useLocation, Navigate } from 'react-router-dom'
import { Home, Eye, EyeOff, Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import { normalizeRole, type AuthRole } from '@/lib/authz'
import { useAuth } from '@/contexts/AuthContext'
// OpenAPI生成クライアントから直接インポート
import { login as loginApi } from '@/generated/sdk.gen'

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login, isAuthenticated, passwordChanged } = useAuth()

  const [email,    setEmail]    = useState('')
  const [password, setPassword] = useState('')
  const [showPass, setShowPass] = useState(false)
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState<string | null>(null)

  const from = (location.state as { from?: { pathname: string } })?.from?.pathname ?? '/'
  if (isAuthenticated) return <Navigate to={from} replace />

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      // 生成された login 関数を使用
      const res = await loginApi({
        body: { email, password },
      })

      if (res.error) {
        const status = res.response?.status
        setError(status === 401
          ? 'メールアドレスまたはパスワードが正しくありません'
          : status === 400
            ? '入力内容に誤りがあります'
            : 'サーバーエラーが発生しました。しばらくしてから再試行してください')
        return
      }

      const { accessToken, refreshToken } = res.data ?? {}
      if (!accessToken || !refreshToken) {
        setError('サーバーからトークンが返されませんでした')
        return
      }

      let role: AuthRole = 'BAN_USER'
      try {
        const payload = JSON.parse(atob(accessToken.split('.')[1]))
        role = normalizeRole(payload.role)
      } catch { /* 解析不能なトークンはアクセス禁止として扱う */ }
      if (role === 'BAN_USER') {
        setError('メールアドレスまたはパスワードが正しくありません')
        return
      }
      login({ email, role }, accessToken, refreshToken)
      navigate(from, { replace: true })

    } catch {
      setError('サーバーエラーが発生しました。しばらくしてから再試行してください')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-50 dark:bg-zinc-950 px-4">
      <div className={cn(
        'w-full max-w-sm rounded-2xl border p-8 shadow-sm',
        'bg-white border-zinc-200',
        'dark:bg-zinc-900 dark:border-zinc-800',
      )}>
        {/* ロゴ */}
        <div className="mb-8 flex flex-col items-center gap-3">
          <div className="rounded-xl bg-purple-500/15 p-3">
            <Home size={24} className="text-purple-500" />
          </div>
          <div className="text-center">
            <h1 className="text-lg font-semibold text-zinc-900 dark:text-white">SmartHome</h1>
            <p className="mt-0.5 text-sm text-zinc-500">管理画面にログイン</p>
          </div>
        </div>

        {/* フォーム */}
        <form onSubmit={handleSubmit} className="space-y-4">
          {passwordChanged && !error && <NotificationBanner type="success">パスワードを変更しました。再ログインしてください。</NotificationBanner>}
          {error && (
            <NotificationBanner type="error" onClose={() => setError(null)}>
              {error}
            </NotificationBanner>
          )}

          {/* メールアドレス */}
          <div className="space-y-1.5">
            <label htmlFor="email" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
              メールアドレス
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={e => setEmail(e.target.value)}
              className={cn(
                'w-full rounded-lg border px-3 py-2 text-sm outline-none transition-colors',
                'bg-white border-zinc-300 text-zinc-900 placeholder:text-zinc-400',
                'focus:border-purple-500 focus:ring-1 focus:ring-purple-500',
                'dark:bg-zinc-800 dark:border-zinc-700 dark:text-zinc-100 dark:placeholder:text-zinc-500',
                'dark:focus:border-purple-500',
              )}
              placeholder="admin@example.com"
            />
          </div>

          {/* パスワード */}
          <div className="space-y-1.5">
            <label htmlFor="password" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
              パスワード
            </label>
            <div className="relative">
              <input
                id="password"
                type={showPass ? 'text' : 'password'}
                autoComplete="current-password"
                required
                value={password}
                onChange={e => setPassword(e.target.value)}
                className={cn(
                  'w-full rounded-lg border px-3 py-2 pr-10 text-sm outline-none transition-colors',
                  'bg-white border-zinc-300 text-zinc-900',
                  'focus:border-purple-500 focus:ring-1 focus:ring-purple-500',
                  'dark:bg-zinc-800 dark:border-zinc-700 dark:text-zinc-100',
                  'dark:focus:border-purple-500',
                )}
                placeholder="••••••••"
              />
              <button
                type="button"
                onClick={() => setShowPass(s => !s)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300"
                aria-label={showPass ? 'パスワードを隠す' : 'パスワードを表示'}
              >
                {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </div>

          {/* ログインボタン */}
          <button
            type="submit"
            disabled={loading}
            className={cn(
              'flex w-full items-center justify-center gap-2 rounded-lg px-4 py-2.5',
              'bg-purple-600 text-sm font-medium text-white',
              'hover:bg-purple-500 transition-colors',
              'disabled:cursor-not-allowed disabled:opacity-60',
            )}
          >
            {loading && <Loader2 size={15} className="animate-spin" />}
            {loading ? 'ログイン中...' : 'ログイン'}
          </button>
        </form>
      </div>
    </div>
  )
}
