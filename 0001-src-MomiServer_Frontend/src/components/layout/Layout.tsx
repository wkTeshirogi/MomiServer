import { useState } from 'react'
import type { ReactNode } from 'react'
import { useLocation, Outlet } from 'react-router-dom'
import { Header } from './Header'
import { Sidebar } from './Sidebar'
import { usePersonalSettings } from '@/contexts/PersonalSettingsContext'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { Button } from '@/components/ui/button'
import { RefreshCw } from 'lucide-react'

type LayoutProps = {
  title?: ReactNode
}

/**
 * react-router-dom のネストルート対応
 * children の代わりに <Outlet /> を使用
 */
export function Layout({ title }: LayoutProps) {
  const [mobileOpen, setMobileOpen] = useState(false)
  const { pathname } = useLocation()
  const { notice, clearNotice, loadError, reload, loading } = usePersonalSettings()

  return (
    <div className="flex h-screen overflow-hidden bg-zinc-50 text-zinc-900 dark:bg-zinc-950 dark:text-zinc-100">
      <Sidebar
        activePath={pathname}
        mobileOpen={mobileOpen}
        onMobileClose={() => setMobileOpen(false)}
      />
      <div className="flex flex-1 flex-col overflow-hidden min-w-0">
        <Header title={title} onMenuClick={() => setMobileOpen(true)} />
        <main className="flex-1 overflow-y-auto p-4 md:p-6">
          {loadError && <div className="mb-4 space-y-2">
            <NotificationBanner type="error">個人設定を取得できませんでした。</NotificationBanner>
            <Button variant="outline" disabled={loading} onClick={reload}><RefreshCw className="size-4" />再取得</Button>
          </div>}
          {notice && <NotificationBanner type={notice.type} onClose={clearNotice} autoScroll className="mb-4">{notice.message}</NotificationBanner>}
          {/* ネストされたルートのコンテンツがここにレンダリングされる */}
          <Outlet />
        </main>
      </div>
    </div>
  )
}
