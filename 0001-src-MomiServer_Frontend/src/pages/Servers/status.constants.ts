import type { RuntimeStatus } from '@/api/statusApi'

// Fixed definitions scoped to pages/Servers.
export const statusLabel: Record<RuntimeStatus, string> = {
  online: 'Online',
  offline: 'Offline',
  maintenance: 'Maintenance',
  unknown: 'Unknown',
}

export const statusClass: Record<RuntimeStatus, string> = {
  online: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300',
  offline: 'bg-zinc-500/15 text-zinc-600 dark:text-zinc-300',
  maintenance: 'bg-amber-500/15 text-amber-600 dark:text-amber-300',
  unknown: 'bg-zinc-500/15 text-zinc-600 dark:text-zinc-300',
}
