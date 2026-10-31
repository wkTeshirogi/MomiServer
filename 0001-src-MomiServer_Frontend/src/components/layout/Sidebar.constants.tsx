import { Monitor, Gamepad2, Bell, Settings, FileClock, GitBranch, Activity, Flame, Globe, PackageOpen, CarFront } from 'lucide-react'
import { type AuthRole } from '@/lib/authz'

// Fixed definitions scoped to components/layout.
export type NavItem = { label: string; icon: React.ReactNode; href: string; badge?: number }

export type NavSection = { title?: string; items: NavItem[] }

export type ExternalLink = { label: string; icon: React.ReactNode; href: string; allowedRoles: AuthRole[] }

export const mainNav: NavSection[] = [{
  items: [
	{ label: 'マシンステータス', icon: <Monitor size={16} />, href: '/machine/status' },
	{ label: 'サービスステータス', icon: <Gamepad2 size={16} />, href: '/service/status' },
	{ label: 'アラート', icon: <Bell size={16} />, href: '/alerts' },
	{ label: 'APIログ', icon: <FileClock size={16} />, href: '/logs/api' },
	{ label: '在庫管理', icon: <PackageOpen size={16} />, href: '/stock' },
	{ label: '車両管理', icon: <CarFront size={16} />, href: '/vehicles' },
	{ label: 'マスタ管理', icon: <Settings size={16} />, href: '/master' },
  ],
}]

export const externalLinks: ExternalLink[] = [
  { label: 'GitLab Repository', icon: <GitBranch size={16} className="text-orange-400" />, href: 'https://gitlab.com', allowedRoles: ['ADMIN', 'OPERATOR'] },
  { label: 'モニタリング (Grafana)', icon: <Activity size={16} className="text-orange-500" />, href: 'http://grafana.local', allowedRoles: ['ADMIN', 'OPERATOR'] },
  { label: 'Prometheus', icon: <Flame size={16} className="text-red-500" />, href: 'http://prometheus.local', allowedRoles: ['ADMIN', 'OPERATOR'] },
  { label: 'Swagger UI', icon: <Globe size={16} className="text-purple-500" />, href: 'http://localhost:8080/swagger-ui/index.html', allowedRoles: ['ADMIN'] },
]
