import { statusLabel, statusClass } from '@/pages/Servers/status.constants'
import { useState }                                      from 'react'
import { useParams }                                     from 'react-router-dom'
import { Button }         from '@/components/ui/button'
import { Input }         from '@/components/ui/input'
import { Card }                                          from '@/components/ui/card'
import { Select, type SelectOption }               from '@/components/ui/select'
import { useMergedServerStatus, useLiveEvent } from './service'
import { useAuth } from '@/contexts/AuthContext'
import { canAccessAdmin } from '@/lib/authz'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { formatJapanDateTime } from '@/lib/utils'

function UsageBar({ label, value, available = true }: { label: string; value: number; available?: boolean }) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-xs">
        <span className="font-medium text-zinc-600 dark:text-zinc-300">{label}</span>
        <span className="tabular-nums text-zinc-500 dark:text-zinc-400">{available ? `${value}%` : '未取得'}</span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-zinc-200 dark:bg-zinc-800">
        <div
          className="h-full rounded-full bg-purple-500"
          style={{ width: available ? `${value}%` : '0%' }}
        />
      </div>
    </div>
  )
}

function CustomUsageBar({ label, max, value, available = true }: { label: string; max: number; value: number; available?: boolean }) {
	if(label=='none'){
		return(<div className="space-y-1.5"></div>)
	}else{
	  return (
	    <div className="space-y-1.5">
	      <div className="flex items-center justify-between text-xs">
	        <span className="font-medium text-zinc-600 dark:text-zinc-300">{label}</span>
	        <span className="tabular-nums text-zinc-500 dark:text-zinc-400">{available ? `${value} / ${max}` : '未取得'}</span>
	      </div>
	      <div className="h-2 overflow-hidden rounded-full bg-zinc-200 dark:bg-zinc-800">
	        <div
	          className="h-full rounded-full bg-purple-500"
	          style={{ width: available && max > 0 ? `${value / max * 100}%` : '0%' }}
	        />
	      </div>
	    </div>
	  )
	}
}

function DetailRow({ label, value, websocket = false, connected = false }: { label: string; value: string; websocket?: boolean; connected?: boolean }) {
  if(label == "Status"){
    return (
    <div className="grid grid-cols-[7rem_minmax(0,1fr)] gap-3 border-b border-zinc-200 py-2 text-sm last:border-b-0 dark:border-zinc-800">
      <div className="font-medium text-zinc-500 dark:text-zinc-400">{label}</div>
      <div className="min-w-0 break-words text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
        {websocket && <span aria-label={connected ? 'WebSocket接続中' : 'WebSocket未接続'} className={`h-2.5 w-2.5 rounded-full ${connected ? 'bg-emerald-500' : 'bg-red-500'}`} />}
        {value}
      </div>
    </div>
    )
  }
  return (
    <div className="grid grid-cols-[7rem_minmax(0,1fr)] gap-3 border-b border-zinc-200 py-2 text-sm last:border-b-0 dark:border-zinc-800">
      <div className="font-medium text-zinc-500 dark:text-zinc-400">{label}</div>
      <div className="min-w-0 break-words text-zinc-900 dark:text-zinc-100">{value}</div>
    </div>
  )
}

export function ServiceStatus() {
  const { user } = useAuth()
  const { serverId } = useParams()
  const { servers, serverOptions, loading, error, reload } = useMergedServerStatus()
  const { isWebSocket, connected, connectionError, toggle } = useLiveEvent()

  const [selectedValue, setSelectedValue] = useState<string | null>(null)
  const selectedOption = serverOptions.find((option) => option.value === serverId)
    ?? serverOptions.find((option) => option.value === selectedValue)
    ?? serverOptions[0]
    ?? null
  const selectedServer = servers.find(server => String(server.id) === selectedOption?.value) ?? servers[0]
  const disabled = (serverId != undefined)

  if (loading && !selectedServer) {
    return <Card className="rounded-sm p-5">サービスステータスを読み込んでいます。</Card>
  }
  if (!selectedServer) {
    return (
      <div className="space-y-4">
        {error && <NotificationBanner type="error">{error}</NotificationBanner>}
        <Card className="rounded-sm p-5">表示できるサービスがありません。</Card>
        {error && <Button label="再読み込み" onClick={() => void reload()} />}
      </div>
    )
  }

  return (
    <div className="space-y-5 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            サービスステータス
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            サーバーの稼働状況とリソース使用率を確認できます。
          </p>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <span className="shrink-0 text-sm font-medium text-zinc-600 dark:text-zinc-300">Service:</span>
            <Select
              value={selectedOption}
              options={serverOptions}
              placeholder="Select Service"
              onChange={(option: SelectOption | null) => setSelectedValue(option?.value ?? null)}
              disabled={disabled || connected}
            />
          </div>
          {canAccessAdmin(user) && (
            <Button href="/master/services" variant="default" label="設定" className="shrink-0" />
          )}
        </div>

      </div>

      {error && <NotificationBanner type="error">{error}</NotificationBanner>}
      {isWebSocket && connectionError && (
        <NotificationBanner type="error">リアルタイム更新に接続できません。再接続しています。</NotificationBanner>
      )}

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,2fr)_22rem_22rem]">
        <Card className="rounded-sm border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <h2 className="text-lg font-semibold text-zinc-900 dark:text-white">
                {selectedServer.name}
              </h2>
              <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
                {selectedServer.address}
              </p>
            </div>
            <div>
              <div className="text-center">
                <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ${statusClass[selectedServer.status]}`}>
                  {statusLabel[selectedServer.status]}
                </span>
              </div>
              <div>
              </div>
            </div>
          </div>

          <div className="mt-6 grid gap-4 sm:grid-cols-2">
            <CustomUsageBar label="全体ヘルススコア" max={100} value={0} available={false} />

            <UsageBar label="サービス別リソース使用率" value={selectedServer.cpu} available={selectedServer.metricsAvailable} />
            <UsageBar label="Memory" value={selectedServer.mem} available={selectedServer.metricsAvailable} />
            <CustomUsageBar label={selectedServer.custom_name} max={selectedServer.custom_max} value={selectedServer.custom_now} available={selectedServer.metricsAvailable} />

            {/*<UsageBar label="Memory" value={selectedServer.mem} />*/}
          </div>
        </Card>

        <Card className="rounded-sm border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
          <h2 className="text-base font-semibold text-zinc-900 dark:text-white">
            Details
          </h2>
          <div className="mt-3">
            <div className="grid grid-cols-[7rem_minmax(0,1fr)] gap-3 border-b border-zinc-200 py-2 text-sm last:border-b-0 dark:border-zinc-800">
              <div className="font-medium text-zinc-500 dark:text-zinc-400">リアルタイム更新</div>
              <div className="min-w-0 break-words text-zinc-900 dark:text-zinc-100">
                <Input type="checkbox" checked={isWebSocket} onChange={(e) => toggle(e.target.checked)}/>
              </div>
            </div>
            <DetailRow label="最終更新" value={selectedServer.observedAt ? formatJapanDateTime(selectedServer.observedAt) : '未取得'} />
            <DetailRow label="稼働時間" value={selectedServer.worktime || '未取得'} />
            <DetailRow label="Status" value={statusLabel[selectedServer.status]} websocket={isWebSocket} connected={connected}/>
            <DetailRow label="PID" value={selectedServer.pid} />
          </div>
        </Card>
        <Card className="rounded-sm border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
          <h2 className="text-base font-semibold text-zinc-900 dark:text-white">
            Details
          </h2>
          <div className="mt-3">
            <DetailRow label="Version" value={selectedServer.version} />
            <CustomUsageBar label="プレイヤー数" max={selectedServer.custom_max} value={selectedServer.custom_now} available={selectedServer.custom_max > 0} />
            <CustomUsageBar label="tickレート/FPS" max={0} value={0} available={false} />
            <CustomUsageBar label="平均レイテンシ" max={0} value={0} available={false} />
          </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {/* 本体関連 (Service毎) */}
        <Card
            // key={server.id}
            className="rounded-sm border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
          >
            <div className="flex items-start justify-between gap-3">
              <h3>
                使用率
              </h3>
            </div>
            <div className="mt-4 space-y-3">
              <UsageBar label="CPU" value={selectedServer.cpu} available={selectedServer.metricsAvailable} />
              <UsageBar label="Memory" value={selectedServer.mem} available={selectedServer.metricsAvailable} />
              <UsageBar label="Disk IO" value={0} available={false} />
              <UsageBar label="Network IO" value={0} available={false} />
            </div>
          </Card>

        {/* 本体関連 (Service毎) */}
        <Card
            // key={server.id}
            className="rounded-sm border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
          >
            <div className="flex items-start justify-between gap-3">
              <h3>
                ログ関連
              </h3>
            </div>
            <div className="mt-4 space-y-3">
              <UsageBar label="エラー率" value={5} />
              <DetailRow label="INFO件数" value="0000 件" />
              <DetailRow label="ERROR件数" value="0000 件" />
              <DetailRow label="WARN件数" value="0000 件" />
            </div>
        </Card>
        {/* 本体関連 (Service毎) */}
        <Card
            // key={server.id}
            className="rounded-sm border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
          >
            <div className="flex items-start justify-between gap-3">
              <h3>
                ジョブ履歴
              </h3>
            </div>
            <div className="mt-4 space-y-3">
              <div className="overflow-x-auto w-full border border-gray-700 rounded-lg">
                <div className="max-h-[180px] overflow-y-auto">
                  <table className="w-full table-auto text-sm">
                    <thead className="sticky top-0 z-10 bg-white dark:bg-zinc-900">
                      <tr className="border-b border-gray-700">
                        <th className="w-40 px-2 py-2 text-left font-semibold whitespace-nowrap">実行時刻</th>
                        <th className="w-24 px-2 py-2 text-left font-semibold whitespace-nowrap">終了時刻</th>
                        <th className="px-2 py-2 text-left font-semibold">結果</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr className="border-b border-gray-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer">
                        <td className="w-40 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="w-24 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="px-2 py-2">成功</td>
                      </tr>
                      <tr className="border-b border-gray-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer">
                        <td className="w-40 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="w-24 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="px-2 py-2">失敗</td>
                      </tr>
                      <tr className="border-b border-gray-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer">
                        <td className="w-40 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="w-24 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="px-2 py-2">0件</td>
                      </tr>
                      <tr className="border-b border-gray-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer">
                        <td className="w-40 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="w-24 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="px-2 py-2">成功</td>
                      </tr>
                      <tr className="border-b border-gray-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer">
                        <td className="w-40 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="w-24 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="px-2 py-2">成功</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
        </Card>
        {/* 本体関連 (Service毎) */}
        <Card
            // key={server.id}
            className="rounded-sm border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
          >
            <div className="flex items-start justify-between gap-3">
              <h3>
                バックアップ履歴
              </h3>
            </div>
            <div className="mt-4 space-y-3">
              <div>最終バックアップ: yyyy/mm/dd hh:mm:ss</div>
              <div className="overflow-x-auto w-full border border-gray-700 rounded-lg">
                <div className="max-h-[150px] overflow-y-auto">
                  <table className="w-full table-auto text-sm">
                    <thead className="sticky top-0 z-10 bg-white dark:bg-zinc-900">
                      <tr className="border-b border-gray-700">
                        <th className="w-40 px-2 py-2 text-left font-semibold whitespace-nowrap">実行時刻</th>
                        <th className="w-24 px-2 py-2 text-left font-semibold whitespace-nowrap">終了時刻</th>
                        <th className="px-2 py-2 text-left font-semibold">結果</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr className="border-b border-gray-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer">
                        <td className="w-40 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="w-24 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="px-2 py-2">成功</td>
                      </tr>
                      <tr className="border-b border-gray-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer">
                        <td className="w-40 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="w-24 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="px-2 py-2">失敗</td>
                      </tr>
                      <tr className="border-b border-gray-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer">
                        <td className="w-40 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="w-24 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="px-2 py-2">0件</td>
                      </tr>
                      <tr className="border-b border-gray-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer">
                        <td className="w-40 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="w-24 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="px-2 py-2">成功</td>
                      </tr>
                      <tr className="border-b border-gray-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors cursor-pointer">
                        <td className="w-40 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="w-24 px-2 py-2 whitespace-nowrap">yyyy/mm/dd <br/> hh:mm:ss</td>
                        <td className="px-2 py-2">成功</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
        </Card>
      </div>
    </div>
  )
}
