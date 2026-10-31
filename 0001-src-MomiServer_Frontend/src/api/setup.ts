import { client } from '@/generated/client.gen'

import { ACCESS_TOKEN_KEY } from '@/constants/auth'
import { API_BASE_URL } from '@/constants/api'

/**
 * 生成された @hey-api クライアントの初期設定
 * main.tsx でアプリ起動前に呼ぶこと
 */
export function setupApiClient() {
  client.setConfig({
    baseUrl: API_BASE_URL,
  })

  // リクエスト毎に localStorage からトークンを取得して付与
  // ログイン前は token が null のためヘッダーは付かない
  client.interceptors.request.use(config => {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY)
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`)
    }
    return config
  })
}
