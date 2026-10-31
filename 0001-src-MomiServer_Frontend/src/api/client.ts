import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'

// ────────────────────────────────────────────────
// 定数
// ────────────────────────────────────────────────
// 生成クライアントと同じサーバールート設定から、手書きAPI用の接頭辞を組み立てる。
import { BASE_URL } from '@/constants/api'
import { ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY, USER_EMAIL_KEY } from '@/constants/auth'

// ────────────────────────────────────────────────
// トークン管理
// ────────────────────────────────────────────────
export const tokenStorage = {
  getAccess:   () => localStorage.getItem(ACCESS_TOKEN_KEY),
  getRefresh:  () => localStorage.getItem(REFRESH_TOKEN_KEY),
  setTokens:   (access: string, refresh: string) => {
    localStorage.setItem(ACCESS_TOKEN_KEY, access)
    localStorage.setItem(REFRESH_TOKEN_KEY, refresh)
  },
  clearTokens: () => {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(USER_EMAIL_KEY)
  },
}

// ────────────────────────────────────────────────
// リダイレクトコールバック
// react-router-dom の useNavigate は React ツリー外では使えないため
// interceptor からは登録済みのコールバックを呼ぶ形にする
// ────────────────────────────────────────────────
type NavigateCallback = (path: string) => void
let navigateCallback: NavigateCallback | null = null

export function setNavigateCallback(fn: NavigateCallback) {
  navigateCallback = fn
}

// ────────────────────────────────────────────────
// axios インスタンス
// ────────────────────────────────────────────────
export const apiClient: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10_000,
})

// ────────────────────────────────────────────────
// Request interceptor — Authorization ヘッダーを自動付与
// ────────────────────────────────────────────────
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStorage.getAccess()
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ────────────────────────────────────────────────
// Response interceptor — 401 時にリフレッシュして自動リトライ
// ────────────────────────────────────────────────
let isRefreshing = false
let failedQueue: Array<{
  resolve: (token: string) => void
  reject:  (err: unknown)  => void
}> = []

const processQueue = (error: unknown, token: string | null) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error)
    else       resolve(token!)
  })
  failedQueue = []
}

apiClient.interceptors.response.use(
  response => response,
  async error => {
    const originalRequest = error.config

    if (error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error)
    }

    const refreshToken = tokenStorage.getRefresh()
    if (!refreshToken) {
      tokenStorage.clearTokens()
      navigateCallback?.('/login')
      return Promise.reject(error)
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject })
      }).then(token => {
        originalRequest.headers.Authorization = `Bearer ${token}`
        return apiClient(originalRequest)
      })
    }

    originalRequest._retry = true
    isRefreshing = true

    try {
      const { data } = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken })
      const { accessToken, refreshToken: newRefreshToken } = data

      tokenStorage.setTokens(accessToken, newRefreshToken)
      processQueue(null, accessToken)

      originalRequest.headers.Authorization = `Bearer ${accessToken}`
      return apiClient(originalRequest)
    } catch (refreshError) {
      processQueue(refreshError, null)
      tokenStorage.clearTokens()
      navigateCallback?.('/login')
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  },
)
