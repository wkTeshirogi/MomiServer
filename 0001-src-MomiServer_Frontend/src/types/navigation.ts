export type NavItem = {
  label: string
  icon: string          // Lucide アイコン名
  href: string
  badge?: number        // 通知バッジ
}

export type NavSection = {
  title?: string        // セクションタイトル (省略可)
  items: NavItem[]
}

export type ExternalLink = {
  label: string
  icon: string          // Lucide アイコン名 or カスタム色クラス
  href: string
  iconColor?: string    // Tailwind text-* クラス
}
