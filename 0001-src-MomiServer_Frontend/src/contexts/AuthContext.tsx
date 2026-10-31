import { createContext, useContext, useState, useCallback, useEffect } from 'react'
import { normalizeRole, type AuthRole } from '@/lib/authz'
import { SERVER_URL as API_BASE_URL } from '@/constants/api'
import { ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY, USER_EMAIL_KEY } from '@/constants/auth'

// ────────────────────────────────────────────────
// 型定義
// ────────────────────────────────────────────────
type AuthUser = {
  email: string
  role: AuthRole
}

type AuthContextValue = {
  user: AuthUser | null
  isAuthenticated: boolean
  login:  (user: AuthUser, accessToken: string, refreshToken: string) => void
  logout: () => Promise<void>
  completePasswordChange: () => void
  passwordChanged: boolean
  getAccessToken: () => string | null
  hasStoredAuth: () => boolean
  syncAuthFromStorage: () => void
}

function decodeJwtPayload(token: string): Record<string, unknown> | null {

  // ここでの展開は表示・有効期限管理用であり、署名検証やAPIの認可を置き換えるものではない。
  try {
    const payload = token.split('.')[1]
    if (!payload) return null

    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const paddedBase64 = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
    return JSON.parse(atob(paddedBase64))
  } catch {
    return null
  }
}

function isTokenExpired(token: string): boolean {
  const payload = decodeJwtPayload(token)
  const exp = payload?.exp

  if (typeof exp !== 'number') return true
  return Date.now() >= exp * 1000
}

function getTokenExpiresAt(token: string): number | null {
  const payload = decodeJwtPayload(token)
  const exp = payload?.exp

  if (typeof exp !== 'number') return null
  return exp * 1000
}

function getStoredAuthExpiresAt(): number | null {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY)
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)

  if (!token || !refreshToken) return null

  const tokenExpiresAt = getTokenExpiresAt(token)
  const refreshTokenExpiresAt = getTokenExpiresAt(refreshToken)

  if (!tokenExpiresAt || !refreshTokenExpiresAt) return null
  return Math.min(tokenExpiresAt, refreshTokenExpiresAt)
}

function clearStoredAuth() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(USER_EMAIL_KEY)
}

async function requestLogout() {
  const accessToken = localStorage.getItem(ACCESS_TOKEN_KEY)
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)

  if (!accessToken && !refreshToken) return

  const headers = new Headers({ 'Content-Type': 'application/json' })
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  await fetch(`${API_BASE_URL}/api/auth/logout`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ refreshToken }),
  })
}

// ────────────────────────────────────────────────
// JWTペイロードからロールを取り出す
// ────────────────────────────────────────────────
function parseRole(token: string): AuthUser['role'] {
  const payload = decodeJwtPayload(token)
  return normalizeRole(payload?.role)
}

function getStoredUser(): AuthUser | null {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY)
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
  const email = localStorage.getItem(USER_EMAIL_KEY)

  if (!token || !refreshToken || !email) return null
  if (isTokenExpired(token) || isTokenExpired(refreshToken)) return null

  if (parseRole(token) === 'BAN_USER') return null

  return { email, role: parseRole(token) }
}

// ────────────────────────────────────────────────
// Context
// ────────────────────────────────────────────────
const AuthContext = createContext<AuthContextValue | null>(null)

// ────────────────────────────────────────────────
// Provider
// ────────────────────────────────────────────────
type AuthProviderProps = { children: React.ReactNode }

export function AuthProvider({ children }: AuthProviderProps) {
  const [passwordChanged, setPasswordChanged] = useState(false)
  // 既存トークンがあればセッション復元
  const [user, setUser] = useState<AuthUser | null>(() => {
    return getStoredUser()
  })

  const login = useCallback((user: AuthUser, accessToken: string, refreshToken: string) => {
    setPasswordChanged(false)
    localStorage.setItem(ACCESS_TOKEN_KEY,  accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    localStorage.setItem(USER_EMAIL_KEY, user.email)
    setUser(user)
  }, [])

  const logout = useCallback(async () => {
    try {
      await requestLogout()
    } finally {

      // 失効要求の通信に失敗しても、このブラウザーで認証情報を使い続けない。BE側の失効完了を保証する処理ではない。
      clearStoredAuth()
      setUser(null)
    }
  }, [])

  const completePasswordChange = useCallback(() => {
    clearStoredAuth()
    setPasswordChanged(true)
    setUser(null)
  }, [])

  const getAccessToken = useCallback(
    () => localStorage.getItem(ACCESS_TOKEN_KEY),
    []
  )

  const hasStoredAuth = useCallback(() => getStoredUser() !== null, [])

  const syncAuthFromStorage = useCallback(() => {
    const storedUser = getStoredUser()
    if (!storedUser) clearStoredAuth()
    setUser(current => current?.email === storedUser?.email && current?.role === storedUser?.role ? current : storedUser)
  }, [])

  const isAuthenticated = user !== null

  useEffect(() => {
    if (!isAuthenticated) return

    let timeoutId: number | undefined
    const scheduleExpiry = () => {
      const expiresAt = getStoredAuthExpiresAt()
      if (expiresAt === null) return
      timeoutId = window.setTimeout(() => {
        syncAuthFromStorage()
        // Token rotation can extend expiry without changing the authenticated identity.
        scheduleExpiry()
      }, Math.max(expiresAt - Date.now(), 0))
    }
    scheduleExpiry()

    return () => window.clearTimeout(timeoutId)
  }, [isAuthenticated, user, syncAuthFromStorage])

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated,
        login,
        logout,
        completePasswordChange,
        passwordChanged,
        getAccessToken,
        hasStoredAuth,
        syncAuthFromStorage,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

// ────────────────────────────────────────────────
// Hook
// ────────────────────────────────────────────────
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth は AuthProvider の中で使用してください')
  return ctx
}
