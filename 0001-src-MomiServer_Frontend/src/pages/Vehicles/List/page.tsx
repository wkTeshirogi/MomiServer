import { useEffect, useMemo, useState } from 'react'
import { CarFront, FileUp, Plus, Search } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'
import { getVehicles, type Vehicle } from '@/api/vehicleApi'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ListPagination, ListPaginationSummary } from '@/components/ui/list-pagination'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { useAuth } from '@/contexts/AuthContext'
import { useListPagination } from '@/hooks/useListPagination'
import { useAsyncResource } from '@/hooks/useAsyncResource'
import { hasAnyRole } from '@/lib/authz'
import { formatJapanDateTime } from '@/lib/utils'
import { remainingDaysLabel, vehicleStatusClass, vehicleStatusLabel } from '../vehicleDisplay'

export function VehicleListPage() {
  const { user } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const { data: vehicles, loading, error } = useAsyncResource<Vehicle[]>({
    load: getVehicles, initialData: [], errorMessage: '車両一覧の取得に失敗しました。',
  })
  const [searchTerm, setSearchTerm] = useState('')
  const [notice, setNotice] = useState<string | null>((location.state as { notice?: string } | null)?.notice ?? null)
  const canEdit = hasAnyRole(user, ['ADMIN', 'OPERATOR'])

  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }, [location.pathname, location.state, navigate])

  const filteredVehicles = useMemo(() => {
    const keyword = searchTerm.trim().toLowerCase()
    if (!keyword) return vehicles

    return vehicles.filter(vehicle => [
      vehicle.display_name,
      vehicle.registration_number,
      vehicle.chassis_number,
      vehicle.vehicle_name,
      vehicle.model_code ?? '',
      vehicleStatusLabel(vehicle.status),
    ].some(value => value.toLowerCase().includes(keyword)))
  }, [searchTerm, vehicles])
  const { visibleItems, paginationProps } = useListPagination(filteredVehicles, searchTerm)

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="mb-1 flex items-center gap-2 text-xl font-semibold text-zinc-900 dark:text-white">
            <CarFront className="size-5 text-purple-500" />
            車両管理一覧
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            車検期限の近い車両から優先して表示します。
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <label className="relative">
            <span className="sr-only">車両を検索</span>
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-zinc-400" />
            <input
              type="search"
              value={searchTerm}
              onChange={event => setSearchTerm(event.target.value)}
              placeholder="表示名、登録番号、車名で検索..."
              className="h-9 w-72 rounded-lg border border-zinc-300 bg-white pl-9 pr-3 text-sm text-zinc-900 outline-none focus:border-purple-500 focus:ring-1 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
            />
          </label>
          {canEdit && (
            <>
              <Button variant="outline" href="/vehicles/import">
                <FileUp className="size-4" />
                PDF・画像から登録
              </Button>
              <Button href="/vehicles/new">
                <Plus className="size-4" />
                新規登録
              </Button>
            </>
          )}
        </div>
      </div>

      {notice && <NotificationBanner type="success" onClose={() => setNotice(null)}>{notice}</NotificationBanner>}
      {error && <NotificationBanner type="error">{error}</NotificationBanner>}

      <ListPaginationSummary {...paginationProps} overallItems={vehicles.length} loading={loading} />

      <Card className="overflow-hidden rounded-lg border border-zinc-200 bg-white py-0 shadow-none dark:border-zinc-800 dark:bg-zinc-900">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1080px]">
            <thead className="bg-zinc-50 dark:bg-zinc-800/50">
              <tr>
                {['車検状態', '満了日', '残日数', '表示名', '登録番号', '車名', '型式', '最終更新', '操作'].map(label => (
                  <th key={label} className="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400">
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
              {loading && (
                <tr><td colSpan={9} className="px-4 py-10 text-center text-sm text-zinc-500">読み込み中...</td></tr>
              )}
              {!loading && visibleItems.length === 0 && (
                <tr><td colSpan={9} className="px-4 py-10 text-center text-sm text-zinc-500">車両が見つかりません。</td></tr>
              )}
              {!loading && visibleItems.map(vehicle => (
                <tr key={vehicle.id} className="transition-colors hover:bg-zinc-50 dark:hover:bg-zinc-800/50">
                  <td className="px-4 py-3">
                    <span className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${vehicleStatusClass(vehicle.status)}`}>
                      {vehicleStatusLabel(vehicle.status)}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm font-medium text-zinc-900 dark:text-zinc-100">{vehicle.inspection_expiry_date}</td>
                  <td className="px-4 py-3 text-sm text-zinc-600 dark:text-zinc-300">{remainingDaysLabel(vehicle.days_remaining)}</td>
                  <td className="px-4 py-3 text-sm font-medium text-zinc-900 dark:text-zinc-100">{vehicle.display_name}</td>
                  <td className="px-4 py-3 text-sm text-zinc-600 dark:text-zinc-300">{vehicle.registration_number}</td>
                  <td className="px-4 py-3 text-sm text-zinc-600 dark:text-zinc-300">{vehicle.vehicle_name}</td>
                  <td className="px-4 py-3 text-sm text-zinc-600 dark:text-zinc-300">{vehicle.model_code || '-'}</td>
                  <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{formatJapanDateTime(vehicle.updatedAt)}</td>
                  <td className="px-4 py-3"><Button size="sm" href={`/vehicles/${vehicle.id}`}>詳細</Button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <ListPagination {...paginationProps} loading={loading} />
    </div>
  )
}
