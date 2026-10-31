import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'
import { hasAnyRole, type AuthRole } from '@/lib/authz'

type RoleRouteProps = {
  allowedRoles: AuthRole[]
  fallbackPath?: string
}

export function RoleRoute({ allowedRoles, fallbackPath = '/index' }: RoleRouteProps) {
  const { user } = useAuth()
  const location = useLocation()

  if (!hasAnyRole(user, allowedRoles)) {
    return <Navigate to={fallbackPath} state={{ from: location }} replace />
  }

  return <Outlet />
}
