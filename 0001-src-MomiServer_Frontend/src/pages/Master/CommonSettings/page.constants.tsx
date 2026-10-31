import { BellRing, ShieldCheck, Workflow } from 'lucide-react'

// Fixed definitions scoped to pages/Master/CommonSettings.
export const commonSettingMenus = [
  {
    path: '/master/common-settings/notifications',
    label: '通知全体設定',
    description: '通知機能の有効化、保持期間、既定通知レベルなどを管理します。',
    disabled: false,
    icon: BellRing,
  },
  {
    path: '/master/common-settings/security',
    label: 'セキュリティ設定',
    description: 'セッション期限、ログイン制御、API制限などを管理します。',
    disabled: false,
    icon: ShieldCheck,
  },
  {
    path: '/master/common-settings/batch',
    label: 'バッチ共通設定',
    description: '同時実行数、タイムアウト、結果再送、開始待ち期限の共通設定。',
    disabled: false,
    icon: Workflow,
  },
]
