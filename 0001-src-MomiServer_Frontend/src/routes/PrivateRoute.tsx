import { useEffect } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'
import { hasAnyRole } from '@/lib/authz'

/**
 * 認証済みユーザーのみアクセス可能なルートラッパー
 *
 * 未認証の場合は /login へリダイレクトし、
 * ログイン後に元のページへ戻れるよう state に from を渡す
 *
 * 使用例:
 * ```tsx
 * <Route element={<PrivateRoute />}>
 *   <Route path="/status" element={<ServerStatusPage />} />
 * </Route>
 * ```
 */
export function PrivateRoute() {
  const { isAuthenticated, user, syncAuthFromStorage } = useAuth()
  const location = useLocation()

  useEffect(() => {
    syncAuthFromStorage()
  }, [location.pathname, location.search, location.hash, syncAuthFromStorage])

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (!hasAnyRole(user, ['ADMIN', 'OPERATOR', 'USER'])) {
    return <Navigate to="/login" replace />
  }

  return <Outlet />
}
