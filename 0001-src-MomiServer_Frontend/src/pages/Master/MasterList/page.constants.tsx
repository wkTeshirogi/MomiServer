import {
  Boxes,
  BellRing,
  CodeXml,
  Cpu,
  FileText,
  Gauge,
  GitBranch,
  Globe2,
  KeyRound,
  Layers,
  MonitorCog,
  Settings,
  Tags,
  Users,
  Workflow,
  Wrench,
  ServerCog,
} from 'lucide-react'

// Fixed definitions scoped to pages/Master/MasterList.
export const masterMenus = [
  {
    category: 'ユーザー管理',
    items: [
      { path: '/master/users', label: 'ユーザーマスタ一覧', description: 'ユーザーの登録・編集・削除',disabled: false, icon: Users },
    ],
  },
  {
    category: 'IoT関連',
    items: [
      {path: '/master/api', label: '外部APIキー管理', description: '外部連携キーの発行・無効化', disabled: false, icon: KeyRound  },
      {path: '/master/iot_machine', label: 'IoT機器管理', description: 'IoT機器の管理', disabled: true, icon: Cpu  },
      {path: '/master/sensor', label: 'センサー種別管理', description: 'IoTセンサーの種別管理', disabled: true, icon: Gauge  },
    ]
  },
  {
    category: '各種管理',
    items: [
      { path: '/master/services', label: 'サービスマスタ', description: '監視対象のサービス設定',disabled: false , icon: ServerCog  },
      { path: '/master/batch/difinition', label: 'バッチ定義', description: 'バッチ処理定義マスタ',disabled: false , icon: Workflow  },
      { path: '/master/batch/schedule', label: 'バッチスケジュール定義', description: 'バッチスケジュール定義マスタ',disabled: false , icon: Workflow  },
      { path: '/master/machine', label: '端末マスタ', description: '監視対象の端末のマスタ',disabled: false , icon: MonitorCog },
      { path: '/master/items', label: '在庫品目マスタ', description: '常備品設定のマスタ',disabled: false , icon: Boxes },
      { path: '/master/notification-route-settings', label: '通知ルート設定', description: '通知種別・発生元ごとの配送先設定',disabled: false , icon: BellRing },

    ],
  },
  {
      category  : 'Git管理',
      items: [
        { path: '/master/buildtools', label: 'ビルドツールマスタ', description: '監視対象のサービス設定',disabled: true , icon: Wrench  },
        { path: '/master/cicd', label: 'CI/CDマスタ', description: 'CI/CDの実行等の設定値の設定',disabled: true , icon: GitBranch  },
        { path: '/master/docs', label: 'Docsマスタ', description: '開発ドキュメントに関する設定',disabled: true , icon: FileText  },
        { path: '/master/language', label: '開発言語マスタ', description: '開発言語に関する設定',disabled: true , icon: CodeXml  },
        { path: '/master/tags', label: 'タグマスタ', description: '公開先当に使用するタグ設定',disabled: true , icon: Tags  },
        { path: '/master/types', label: '開発種別マスタ', description: '開発種別マスタ',disabled: true , icon: Layers  },
        { path: '/master/public', label: '公開先設定', description: '公開先のGitの設定及び実行方法の設定',disabled: true , icon: Globe2  },
      ]
  },
  {
    category: '共通設定',
    items: [
      { path: '/master/common-settings', label: '共通設定', description: '通知・セキュリティ・バッチの共通設定',disabled: false , icon: Settings },
    ],
  },
]
