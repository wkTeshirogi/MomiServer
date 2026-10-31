import { apiClient } from './client'
import type { Theme } from '@/contexts/ThemeContext'

export type PersonalSettings = { username: string | null; theme: Theme }
export type PasswordChange = { current_password: string; new_password: string; confirm_password: string }

export async function getPersonalSettings() {
  return (await apiClient.get<PersonalSettings>('/users/me/settings')).data
}

export async function savePersonalUsername(username: string) {
  return (await apiClient.put<PersonalSettings>('/users/me/settings/profile', { username })).data
}

export async function savePersonalTheme(theme: Theme) {
  return (await apiClient.put<PersonalSettings>('/users/me/settings/theme', { theme })).data
}

export async function changePersonalPassword(input: PasswordChange) {
  await apiClient.post('/users/me/settings/password', input)
}
