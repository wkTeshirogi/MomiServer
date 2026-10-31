import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { setupApiClient } from '@/api/setup'
import App from './App'
import './index.css'

// APIクライアントをアプリ起動前に初期化
setupApiClient()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
