import { apiClient } from '@/api/client'

export type VehicleInspectionStatus =
  | 'VALID'
  | 'DUE_SOON'
  | 'URGENT'
  | 'EXPIRED'
  | 'UNKNOWN'
  | 'ARCHIVED'

export type Vehicle = {
  id: number
  display_name: string
  registration_number: string
  chassis_number: string
  vehicle_identification_code?: string
  vehicle_name: string
  model_code?: string
  first_registration_month?: string
  inspection_expiry_date: string
  issued_date?: string
  usage_category?: string
  purpose?: string
  status: VehicleInspectionStatus
  days_remaining?: number
  notes?: string
  created_by: string
  updated_by: string
  createdAt: string
  updatedAt: string
}

export type VehicleSaveInput = {
  display_name: string
  registration_number: string
  chassis_number: string
  vehicle_identification_code?: string
  vehicle_name: string
  model_code?: string
  first_registration_month?: string
  inspection_expiry_date: string
  issued_date?: string
  usage_category?: string
  purpose?: string
  notes?: string
}

export type VehicleInspectionHistory = {
  id: number
  vehicle_id: number
  change_type: 'CREATED' | 'EXPIRY_UPDATED' | 'ARCHIVED'
  previous_expiry_date?: string
  new_expiry_date?: string
  changed_by: string
  changedAt: string
}

export type VehicleInspectionRenewalInput = {
  previous_expiry_date: string
  inspection_expiry_date: string
  issued_date?: string
}

export type VehicleImportCandidate = Partial<Pick<VehicleSaveInput,
  'display_name' | 'registration_number' | 'chassis_number' | 'vehicle_name' | 'model_code' |
  'first_registration_month' | 'inspection_expiry_date' | 'issued_date'>>

export type VehicleImport = {
  id: string
  status: 'PENDING_REVIEW' | 'REGISTERED' | 'CANCELLED' | 'PARSE_FAILED' | 'EXPIRED'
  display_filename?: string
  detected_media_type: string
  extraction_method?: 'PDF_TEXT' | 'OCR'
  candidate: VehicleImportCandidate
  warnings: string[]
  expires_at: string
  confirmed_vehicle_id?: number
}

export async function getVehicles(signal?: AbortSignal) {
  const response = await apiClient.get<Vehicle[]>('/vehicles', { signal })
  return response.data
}

export async function getVehicle(id: number) {
  const response = await apiClient.get<Vehicle>(`/vehicles/${id}`)
  return response.data
}

export async function getVehicleHistory(id: number) {
  const response = await apiClient.get<VehicleInspectionHistory[]>(`/vehicles/${id}/history`)
  return response.data
}

export async function createVehicle(input: VehicleSaveInput) {
  const response = await apiClient.post<Vehicle>('/vehicles', input)
  return response.data
}

export async function updateVehicle(id: number, input: VehicleSaveInput) {
  const response = await apiClient.put<Vehicle>(`/vehicles/${id}`, input)
  return response.data
}

export async function archiveVehicle(id: number) {
  const response = await apiClient.post<Vehicle>(`/vehicles/${id}/archive`)
  return response.data
}

export async function renewVehicleInspection(id: number, input: VehicleInspectionRenewalInput) {
  const response = await apiClient.put<Vehicle>(`/vehicles/${id}/inspection`, input)
  return response.data
}

export async function uploadVehicleImport(file: File) {
  const form = new FormData()
  form.append('file', file)
  const response = await apiClient.post<VehicleImport>('/vehicle-imports', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 360_000,
  })
  return response.data
}

export async function getVehicleImport(id: string) {
  const response = await apiClient.get<VehicleImport>(`/vehicle-imports/${id}`)
  return response.data
}

export async function getVehicleImportSource(id: string) {
  const response = await apiClient.get<Blob>(`/vehicle-imports/${id}/source`, { responseType: 'blob' })
  return response.data
}

export async function confirmVehicleImport(id: string, input: VehicleSaveInput) {
  const response = await apiClient.post<Vehicle>(`/vehicle-imports/${id}/confirm`, input)
  return response.data
}

export async function cancelVehicleImport(id: string) {
  await apiClient.post(`/vehicle-imports/${id}/cancel`)
}
