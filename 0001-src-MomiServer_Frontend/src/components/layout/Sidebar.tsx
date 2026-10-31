import { mainNav, externalLinks } from './Sidebar.constants'
import type { NavSection, NavItem } from './Sidebar.constants'
import { useState, useEffect } from 'react'
import { ChevronDown, ChevronLeft, ChevronRight, Home, X } from 'lucide-react'
import { useAuth } from '@/contexts/AuthContext'
import { canAccessAdmin, hasAnyRole } from '@/lib/authz'
import { cn } from '@/lib/utils'
import { useUnreadNotificationCount } from '@/pages/Notifications/service'

// ── NavLink ──────────────────────────────────────
type NavLinkProps = { item: NavItem; active?: boolean; collapsed: boolean; onNavigate?: () => void }

function NavLink({ item, active = false, collapsed, onNavigate }: NavLinkProps) {
  return (
	<a
	  href={item.href}
	  onClick={onNavigate}
	  title={collapsed ? item.label : undefined}
	  className={cn(
		'relative flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
		// ライト
		active
		  ? 'bg-zinc-100 text-zinc-900 font-medium'
		  : 'text-zinc-500 hover:bg-zinc-100 hover:text-zinc-800',
		// ダーク
		active
		  ? 'dark:bg-zinc-800 dark:text-white'
		  : 'dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-white',
		collapsed && 'justify-center px-2',
	  )}
	>
	  <span className={cn(active && 'text-purple-500')}>{item.icon}</span>
	  {!collapsed && <span className="flex-1 truncate">{item.label}</span>}
	  {!collapsed && item.badge !== undefined && (
		<span className="ml-auto rounded-full bg-purple-500/20 px-1.5 py-0.5 text-[10px] font-medium text-purple-600 dark:text-purple-400 leading-none">
		  {item.badge}
		</span>
	  )}
	  {collapsed && item.badge !== undefined && (
		<span className="absolute right-1 top-1 rounded-full bg-purple-500 px-1 text-[9px] font-semibold leading-3 text-white">
		  {item.badge > 99 ? '99+' : item.badge}
		</span>
	  )}
	</a>
  )
}

// ── NavSectionBlock ───────────────────────────────
type SectionProps = { section: NavSection; collapsed: boolean; activePath: string; expandable?: boolean; onNavigate?: () => void }

function NavSectionBlock({ section, collapsed, activePath, expandable = false, onNavigate }: SectionProps) {
  const [expanded, setExpanded] = useState(true)
  return (
	<div className="space-y-0.5">
	  {section.title && !collapsed && (
		<button
		  onClick={() => expandable && setExpanded(e => !e)}
		  className={cn(
			'flex w-full items-center justify-between px-3 py-1.5',
			'text-[11px] font-semibold uppercase tracking-widest',
			'text-zinc-400 dark:text-zinc-500',
			expandable && 'cursor-pointer hover:text-zinc-600 dark:hover:text-zinc-400',
		  )}
		>
		  {section.title}
		  {expandable && <ChevronDown size={12} className={cn('transition-transform', !expanded && '-rotate-90')} />}
		</button>
	  )}
	  {(!expandable || expanded) && section.items.map(item => (
		<NavLink
		  key={item.href}
		  item={item}
		  active={activePath === item.href || activePath.startsWith(`${item.href}/`)}
		  collapsed={collapsed}
		  onNavigate={onNavigate}
		/>
	  ))}
	</div>
  )
}

// ── SidebarContent ────────────────────────────────
type SidebarContentProps = {
  collapsed: boolean
  activePath: string
  unreadNotificationCount: number
  onCollapse?: () => void
  onNavigate?: () => void
  showCloseButton?: boolean
}

function SidebarContent({
  collapsed,
  activePath,
  unreadNotificationCount,
  onCollapse,
  onNavigate,
  showCloseButton = false,
}: SidebarContentProps) {
  const { user } = useAuth()
  const allowedMainNav = mainNav.map(section => ({
	...section,
	items: section.items
	  .filter(item => (item.href !== '/master' || canAccessAdmin(user))
	    && (item.href !== '/machine/status' || hasAnyRole(user, ['ADMIN', 'OPERATOR']))
	    && (item.href !== '/logs/api' || hasAnyRole(user, ['ADMIN', 'OPERATOR'])))
	  .map(item => item.href === '/alerts'
		? { ...item, badge: unreadNotificationCount > 0 ? unreadNotificationCount : undefined }
		: item
	  ),
  }))
  const allowedExternalLinks = externalLinks.filter(link => hasAnyRole(user, link.allowedRoles))

  return (
	<>
	  {/* ロゴ */}
	  <a href="/index">
		<div className={cn(
		'flex items-center gap-2.5 px-4 py-4',
		'border-b border-zinc-200 dark:border-zinc-800',
		collapsed && 'justify-center px-2',
	  )}>
		<div className="flex-shrink-0 rounded-lg bg-purple-500/15 p-1.5">
		  <Home size={16} className="text-purple-500" />
		</div>
		{!collapsed && (
		  <span className="flex-1 truncate text-sm font-semibold tracking-tight text-zinc-800 dark:text-white">
			も<span className="truncate text-red-500">み</span>サーバ
		  </span>
		)}
		{showCloseButton && (
		  <button
			onClick={onNavigate}
			className="ml-auto rounded-md p-1 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 transition-colors"
			aria-label="メニューを閉じる"
		  >
			<X size={16} />
		  </button>
		)}
	  </div>
	  </a>

	  {/* ナビゲーション */}
	  <nav className="flex-1 overflow-y-auto p-2 space-y-4">
		{allowedMainNav.map((section, i) => (
		  <NavSectionBlock key={i} section={section} collapsed={collapsed} activePath={activePath} onNavigate={onNavigate} />
		))}
		{!collapsed && allowedExternalLinks.length > 0 && (
		  <div className="space-y-0.5 border-t border-zinc-200 pt-4 dark:border-zinc-800">
			{allowedExternalLinks.map(link => (
			  <a
				key={link.href}
				href={link.href}
				target="_blank"
				rel="noopener noreferrer"
				className="flex items-center gap-3 rounded-md px-3 py-2 text-sm text-zinc-500 hover:bg-zinc-100 hover:text-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-white transition-colors"
			  >
				{link.icon}
				<span className="truncate">{link.label}</span>
			  </a>
			))}
		  </div>
		)}
	  </nav>

	  {/* 折りたたみボタン */}
	  {!showCloseButton && (
		<div className="border-t border-zinc-200 dark:border-zinc-800 p-2">
		  <button
			onClick={onCollapse}
			className={cn(
			  'flex w-full items-center gap-2 rounded-md px-3 py-2 transition-colors',
			  'text-xs text-zinc-400 hover:bg-zinc-100 hover:text-zinc-600',
			  'dark:text-zinc-500 dark:hover:bg-zinc-800 dark:hover:text-zinc-300',
			  collapsed && 'justify-center px-2',
			)}
		  >
			{collapsed ? <ChevronRight size={14} /> : <ChevronLeft size={14} />}
			{!collapsed && 'サイドバーを閉じる'}
		  </button>
		</div>
	  )}
	</>
  )
}

// ── Sidebar 本体 ──────────────────────────────────
type SidebarProps = { activePath?: string; mobileOpen?: boolean; onMobileClose?: () => void }

export function Sidebar({ activePath = '/status', mobileOpen = false, onMobileClose }: SidebarProps) {
  const [collapsed, setCollapsed] = useState(false)
  const { count: unreadNotificationCount } = useUnreadNotificationCount()

  useEffect(() => {
	document.body.style.overflow = mobileOpen ? 'hidden' : ''
	return () => { document.body.style.overflow = '' }
  }, [mobileOpen])

  const sidebarBase = cn(
	'flex flex-col h-screen sticky top-0',
	'bg-white border-r border-zinc-200',
	'dark:bg-zinc-950 dark:border-zinc-800',
	'transition-all duration-300 ease-in-out',
  )

  return (
	<>
	  {/* デスクトップ (md以上) */}
	  <aside className={cn(sidebarBase, 'hidden md:flex', collapsed ? 'w-14' : 'w-60')}>
		<SidebarContent
		  collapsed={collapsed}
		  activePath={activePath}
		  unreadNotificationCount={unreadNotificationCount}
		  onCollapse={() => setCollapsed(c => !c)}
		/>
	  </aside>

	  {/* モバイルオーバーレイ */}
	  <div
		className={cn(
		  'fixed inset-0 z-50 bg-black/60 backdrop-blur-sm md:hidden transition-opacity duration-300',
		  mobileOpen ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none',
		)}
		onClick={onMobileClose}
		aria-hidden="true"
	  />

	  {/* モバイルドロワー */}
	  <aside className={cn(
		sidebarBase,
		'fixed inset-y-0 left-0 z-50 w-72 md:hidden',
		'transition-transform duration-300 ease-in-out',
		mobileOpen ? 'translate-x-0' : '-translate-x-full',
	  )}>
		<SidebarContent
		  collapsed={false}
		  activePath={activePath}
		  unreadNotificationCount={unreadNotificationCount}
		  onNavigate={onMobileClose}
		  showCloseButton
		/>
	  </aside>
	</>
  )
}
