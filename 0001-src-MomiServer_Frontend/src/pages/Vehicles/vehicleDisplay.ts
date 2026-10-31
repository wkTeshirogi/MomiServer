import { statusLabels, statusClasses } from './vehicleDisplay.constants'
import type { VehicleInspectionStatus } from '@/api/vehicleApi'

export function vehicleStatusLabel(status: VehicleInspectionStatus) {
  return statusLabels[status]
}

export function vehicleStatusClass(status: VehicleInspectionStatus) {
  return statusClasses[status]
}

export function remainingDaysLabel(days?: number) {
  if (days == null) return '-'
  if (days < 0) return `${Math.abs(days)}日超過`
  if (days === 0) return '本日まで'
  return `残り${days}日`
}
