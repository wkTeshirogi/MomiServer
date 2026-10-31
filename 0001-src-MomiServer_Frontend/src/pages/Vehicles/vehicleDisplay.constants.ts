import type { VehicleInspectionStatus } from '@/api/vehicleApi'

// Fixed definitions scoped to pages/Vehicles.
export const statusLabels: Record<VehicleInspectionStatus, string> = {
  VALID: '有効',
  DUE_SOON: '期限接近',
  URGENT: '要対応',
  EXPIRED: '車検切れ',
  UNKNOWN: '未確認',
  ARCHIVED: '管理終了',
}

export const statusClasses: Record<VehicleInspectionStatus, string> = {
  VALID: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300',
  DUE_SOON: 'bg-amber-100 text-amber-800 dark:bg-amber-950/50 dark:text-amber-300',
  URGENT: 'bg-orange-100 text-orange-800 dark:bg-orange-950/50 dark:text-orange-300',
  EXPIRED: 'bg-red-100 text-red-700 dark:bg-red-950/50 dark:text-red-300',
  UNKNOWN: 'bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300',
  ARCHIVED: 'bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400',
}
