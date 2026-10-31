import { apiClient } from './client'

// ────────────────────────────────────────────────
// 型定義
// ────────────────────────────────────────────────
export type LoginRequest = {
  email:    string
  password: string
}

export type AuthResponse = {
  accessToken:  string
  refreshToken: string
  expiresIn:    number
}

// ────────────────────────────────────────────────
// API 関数
// ────────────────────────────────────────────────
export const authApi = {
  login: (body: LoginRequest) =>
    apiClient.post<AuthResponse>('/auth/login', body).then(r => r.data),

  refresh: (refreshToken: string) =>
    apiClient.post<AuthResponse>('/auth/refresh', { refreshToken }).then(r => r.data),
}
