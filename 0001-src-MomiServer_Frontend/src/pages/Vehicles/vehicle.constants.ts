import type { VehicleSaveInput } from '@/api/vehicleApi'

// Shared initial values for manual entry and import confirmation.
export const emptyForm: VehicleSaveInput = {
  display_name: '',
  registration_number: '',
  chassis_number: '',
  vehicle_name: '',
  inspection_expiry_date: '',
}
