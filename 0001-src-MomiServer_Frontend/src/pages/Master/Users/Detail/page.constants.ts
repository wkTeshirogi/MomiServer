import { type SelectOption } from '@/components/ui/select'
import { roleLabels } from '../user.constants'

// Fixed definitions scoped to pages/Master/Users/Detail.
export const roleOptions: SelectOption[] = [
  { value: 'ADMIN', label: roleLabels.ADMIN },
  { value: 'OPERATOR', label: roleLabels.OPERATOR },
  { value: 'USER', label: roleLabels.USER },
  { value: 'BAN_USER', label: roleLabels.BAN_USER },
]
