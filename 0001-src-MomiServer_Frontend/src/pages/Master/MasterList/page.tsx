import { masterMenus } from './page.constants'
import { Navigate, useNavigate } from 'react-router-dom'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/contexts/AuthContext'
import { canAccessAdmin } from '@/lib/authz'


export function MasterList() {
  const navigate = useNavigate()
  const { user } = useAuth()

  if (!canAccessAdmin(user)) {
    return <Navigate to="/index" replace />
  }

  return (
    <div className="space-y-6 text-left">
      <div>
        <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
          マスタ管理
        </h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          各マスタデータの一覧・編集画面へ遷移します。
        </p>
      </div>

      {masterMenus.map(({ category, items }) => (
        <section key={category} className="space-y-3">
          <h2 className="text-sm font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">
            {category}
          </h2>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {items.map(({ path, label, description ,disabled ,icon: Icon }) => (
              <Card
                key={`${path}-${label}`}
                className="rounded-lg border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900 hover:border-purple-300 dark:hover:border-purple-700 transition-colors cursor-pointer"
              >
                <Button
                  variant="ghost"
                  className="w-full justify-start gap-3 p-4"
                  onClick={() => navigate(path)}
                  disabled={disabled}
                >
                  <Icon className="w-6 h-6 text-purple-600 dark:text-purple-400 shrink-0" />
                  <div className="text-left">
                    <p className="font-medium text-zinc-900 dark:text-white">{label}</p>
                    <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">{description}</p>
                  </div>
                </Button>
              </Card>
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}
