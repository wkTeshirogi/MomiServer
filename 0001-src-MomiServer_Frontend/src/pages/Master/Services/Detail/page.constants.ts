import { type SelectOption } from '@/components/ui/select'

// Fixed definitions scoped to pages/Master/Services/Detail.
export const statusOptions: SelectOption[] = [
  { value: 'watch', label: '監視対象' },
  { value: 'stop', label: '非監視対象' },
  { value: 'deleted', label: '削除済み' },
]

export const noticeLevelOptions: SelectOption[] = [
  { value: 'none', label: '通知しない' },
  { value: 'browser', label: 'ブラウザ上でのみ通知' },
  { value: 'all', label: '全てに通知' },
]
