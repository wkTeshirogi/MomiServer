import { type ReactNode } from 'react'
import { useAuth } from '@/contexts/AuthContext'
import { canExecuteBatch, hasAnyRole, type AuthRole } from '@/lib/authz'

type RequireRoleProps = {
  allowedRoles: AuthRole[]
  children: ReactNode
  fallback?: ReactNode
}

export function RequireRole({ allowedRoles, children, fallback = null }: RequireRoleProps) {
  const { user } = useAuth()

  if (!hasAnyRole(user, allowedRoles)) {
    return fallback
  }

  return children
}

type RequireBatchExecutionProps = {
  requiredRole?: string | null
  children: ReactNode
  fallback?: ReactNode
}

export function RequireBatchExecution({
  requiredRole,
  children,
  fallback = null,
}: RequireBatchExecutionProps) {
  const { user } = useAuth()

  if (!canExecuteBatch(user, requiredRole)) {
    return fallback
  }

  return children
}
