import { ROLE_RANK } from '@/constants/auth'

export type AuthRole = 'ADMIN' | 'OPERATOR' | 'USER' | 'BAN_USER'

export type RoleProtectedUser = {
  role?: AuthRole | string | null
} | null | undefined

export function normalizeRole(role: unknown): AuthRole {
  if (typeof role !== 'string') return 'BAN_USER'

  const normalized = role.trim().replace(/_ROLE$/, '').replace(/^ROLE_/, '')

  if (normalized === 'ADMIN' || normalized === 'OPERATOR' || normalized === 'USER' || normalized === 'BAN_USER') {
    return normalized
  }

  return 'BAN_USER'
}

export function hasAnyRole(user: RoleProtectedUser, allowedRoles: AuthRole[]): boolean {
  if (!user?.role) return false
  const role = normalizeRole(user.role)
  if (role === 'BAN_USER') return false
  return allowedRoles.includes(role)
}

export function canAccessAdmin(user: RoleProtectedUser): boolean {
  return hasAnyRole(user, ['ADMIN'])
}

export function canExecuteBatch(user: RoleProtectedUser, requiredRole: string | null | undefined): boolean {
  if (!user?.role) return false

  const role = normalizeRole(user.role)
  if (role === 'ADMIN') return true
  if (!requiredRole?.trim()) return false

  const required = normalizeRole(requiredRole)
  if (role === 'BAN_USER' || required === 'BAN_USER') return false

  return ROLE_RANK[role] >= ROLE_RANK[required]
}
