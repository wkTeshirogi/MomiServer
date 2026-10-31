export { emptyForm } from '../vehicle.constants'

export const renewalYearOptions = [1, 2, 3] as const
export const defaultRenewalYears = 2

// Fixed definitions scoped to pages/Vehicles/Detail.
export const usageOptions = [
  { value: '', label: '未設定' },
  { value: 'private', label: '自家用' },
  { value: 'business', label: '事業用' },
  { value: 'other', label: 'その他' },
]

export const purposeOptions = [
  { value: '', label: '未設定' },
  { value: 'passenger', label: '乗用' },
  { value: 'cargo', label: '貨物' },
  { value: 'special', label: '特種用途' },
  { value: 'other', label: 'その他' },
]
