import { useState, useRef, useEffect } from 'react'
import type { ReactNode } from 'react'
import {
  Search,
  Sun,
  Moon,
  User,
  LogOut,
  Settings,
  Command,
  Menu,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { useTheme } from '@/contexts/ThemeContext'
import { useAuth } from '@/contexts/AuthContext'
import { usePersonalSettings } from '@/contexts/PersonalSettingsContext'
import { Link } from 'react-router-dom'

// ────────────────────────────────────────────────
// 型定義
// ────────────────────────────────────────────────
type HeaderProps = {
  title?: ReactNode
  onSearch?: (query: string) => void
  onMenuClick?: () => void
}

export function SiteTitle() {
  return (
    <>
      も
      <span className="text-red-500">み</span>
      サーバ
    </>
  )
}

// ────────────────────────────────────────────────
// ハンバーガーメニュー（モバイル専用）
// ────────────────────────────────────────────────
function MenuButton({ onClick }: { onClick?: () => void }) {
  return (
    <button
      onClick={onClick}
      className="flex md:hidden items-center justify-center rounded-lg border border-zinc-700 p-1.5 text-zinc-400 hover:border-zinc-600 hover:text-zinc-200 dark:border-zinc-700 dark:hover:border-zinc-600 transition-colors"
      aria-label="メニューを開く"
    >
      <Menu size={18} />
    </button>
  )
}

// ────────────────────────────────────────────────
// テーマトグル（Context経由）
// ────────────────────────────────────────────────
function ThemeToggle() {
  const { theme } = useTheme()
  const { updateTheme, loading, saving, settings } = usePersonalSettings()

  return (
    <button
      onClick={() => void updateTheme(theme === 'dark' ? 'light' : 'dark')}
      disabled={loading || saving || !settings}
      className={cn(
        'flex items-center justify-center rounded-lg border p-1.5 transition-colors',
        'border-zinc-700 text-zinc-400',
        'hover:border-zinc-500 hover:text-zinc-200',
        'dark:border-zinc-700 dark:text-zinc-400 dark:hover:border-zinc-500 dark:hover:text-zinc-200',
      )}
      aria-label={theme === 'dark' ? 'ライトモードに切り替え' : 'ダークモードに切り替え'}
    >
      {theme === 'dark'
        ? <Sun size={16} />
        : <Moon size={16} />
      }
    </button>
  )
}

// ────────────────────────────────────────────────
// 検索バー
// ────────────────────────────────────────────────
function SearchBar({ onSearch }: { onSearch?: (query: string) => void }) {
  const [focused, setFocused] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault()
        inputRef.current?.focus()
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  return (
    <div
      className={cn(
        'flex items-center gap-2 rounded-lg border px-3 py-1.5 transition-colors',
        // ダーク
        'dark:bg-zinc-900',
        focused ? 'dark:border-zinc-600' : 'dark:border-zinc-800',
        // ライト
        'bg-white',
        focused ? 'border-zinc-400' : 'border-zinc-200',
      )}
    >
      <Search size={14} className="flex-shrink-0 text-zinc-400 dark:text-zinc-500" />
      <input
        ref={inputRef}
        type="text"
        placeholder="検索..."
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        onChange={e => onSearch?.(e.target.value)}
        className={cn(
          'w-28 sm:w-40 md:w-48 bg-transparent text-sm outline-none',
          'text-zinc-800 placeholder:text-zinc-400',
          'dark:text-zinc-200 dark:placeholder:text-zinc-600',
        )}
      />
      <div className="hidden sm:flex items-center gap-0.5 ml-2">
        <kbd className="rounded border border-zinc-300 dark:border-zinc-700 px-1 py-0.5 text-[10px] text-zinc-400 dark:text-zinc-600 font-mono">
          <Command size={10} className="inline" />
        </kbd>
        <kbd className="rounded border border-zinc-300 dark:border-zinc-700 px-1 py-0.5 text-[10px] text-zinc-400 dark:text-zinc-600 font-mono">
          K
        </kbd>
      </div>
    </div>
  )
}

// ────────────────────────────────────────────────
// ユーザーメニュー
// ────────────────────────────────────────────────
function UserMenu() {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const { logout, user } = useAuth()
  const { settings } = usePersonalSettings()

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(o => !o)}
        className={cn(
          'flex items-center justify-center rounded-lg border p-1.5 transition-colors',
          'border-zinc-300 text-zinc-500 hover:border-zinc-400 hover:text-zinc-700',
          'dark:border-zinc-700 dark:text-zinc-400 dark:hover:border-zinc-600 dark:hover:text-zinc-200',
        )}
        aria-label="ユーザーメニュー"
      >
        <User size={16} />
      </button>

      {open && (
        <div className={cn(
          'absolute right-0 top-full z-50 mt-2 w-48 rounded-lg border shadow-xl py-1',
          'bg-white border-zinc-200',
          'dark:bg-zinc-900 dark:border-zinc-800',
        )}>
          <div className="border-b border-zinc-200 dark:border-zinc-800 px-3 py-2">
            {settings?.username && <p className="break-words text-sm font-medium text-zinc-900 dark:text-zinc-100">{settings.username}</p>}
            <p className="text-xs font-medium text-zinc-800 dark:text-zinc-200">{user?.role ?? 'ユーザー'}</p>
            <p className="text-[11px] text-zinc-500 truncate">{user?.email ?? 'admin@example.com'}</p>
          </div>
          <Link
            to="/settings"
            onClick={() => setOpen(false)}
            className="flex items-center gap-2.5 px-3 py-2 text-sm text-zinc-500 hover:bg-zinc-100 hover:text-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors"
          >
            <Settings size={14} />
            設定
          </Link>
          <button
            onClick={() => { void logout(); setOpen(false); }}
            className="flex w-full items-center gap-2.5 px-3 py-2 text-sm text-zinc-500 hover:bg-zinc-100 hover:text-red-500 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-red-400 transition-colors"
          >
            <LogOut size={14} />
            ログアウト
          </button>
        </div>
      )}
    </div>
  )
}

// ────────────────────────────────────────────────
// Header 本体
// ────────────────────────────────────────────────
export function Header({ title = <SiteTitle />, onSearch, onMenuClick }: HeaderProps) {
  return (
    <header className={cn(
      'sticky top-0 z-40 flex h-14 items-center gap-3 px-4 backdrop-blur-sm',
      'border-b border-zinc-200 bg-white/90',
      'dark:border-zinc-800 dark:bg-zinc-950/90',
    )}>
      <MenuButton onClick={onMenuClick} />

      <span className="hidden sm:block text-sm font-semibold truncate text-zinc-800 dark:text-zinc-200">
        {title}
      </span>

      <div className="flex-1" />

      <SearchBar onSearch={onSearch} />
      <ThemeToggle />
      <UserMenu />
    </header>
  )
}
