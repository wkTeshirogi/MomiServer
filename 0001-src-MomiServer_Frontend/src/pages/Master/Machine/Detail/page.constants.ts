import { type SelectOption } from '@/components/ui/select'
import { type MachineMasterItem } from './service'

// Fixed definitions scoped to pages/Master/Machine/Detail.
export const statusOptions: SelectOption[] = [
  { value: '1', label: '有効' },
  { value: '10', label: '稼働中' },
  { value: '20', label: '停止中' },
  { value: '90', label: 'メンテナンス' },
  { value: '99', label: '異常' },
  { value: '0', label: '無効' },
]

export const emptyMachine: MachineMasterItem = {
  id: 0,
  devicename: '',
  address: '',
  location: '',
  mqttClientId: '',
  note: '',
  status: '1',
  createdAt: '',
  updatedAt: '',
}
