import { commonSettingMenus } from './page.constants'
import { useNavigate } from 'react-router-dom'

import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

export function CommonSettings() {
  const navigate = useNavigate()

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            共通設定
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            通知・セキュリティ・バッチなど、システム全体に影響する設定を管理します。
          </p>
        </div>
        <Button variant="ghost" href="/master">
          マスタ一覧に戻る
        </Button>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {commonSettingMenus.map(({ path, label, description, disabled, icon: Icon }) => (
          <Card
            key={path}
            className="rounded-lg border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900 hover:border-purple-300 dark:hover:border-purple-700 transition-colors"
          >
            <Button
              variant="ghost"
              className="h-full w-full justify-start gap-3 p-4"
              onClick={() => navigate(path)}
              disabled={disabled}
            >
              <Icon className="w-6 h-6 text-purple-600 dark:text-purple-400 shrink-0" />
              <div className="min-w-0 text-left">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-medium text-zinc-900 dark:text-white">{label}</p>
                  {disabled && (
                    <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400">
                      未実装
                    </span>
                  )}
                </div>
                <p className="mt-0.5 whitespace-normal text-xs text-zinc-500 dark:text-zinc-400">
                  {description}
                </p>
              </div>
            </Button>
          </Card>
        ))}
      </div>
    </div>
  )
}
