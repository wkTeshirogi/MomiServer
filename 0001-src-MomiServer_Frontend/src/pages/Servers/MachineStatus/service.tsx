import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
} from 'react'
import {
  createStatusWebSocketUrl,
  getMachineStatuses,
  type MachineStatusDto,
  type MachineUpdateMessage,
} from '@/api/statusApi'
import { useWebSocket } from '@/hooks/useWebSocket'
import { useAsyncResource } from '@/hooks/useAsyncResource'

type WebSocketContextValue = {
  connected: boolean
  error: Event | null
  connect: () => void
  disconnect: () => void
  latestData: MachineUpdateMessage | null
}

const WebSocketContext = createContext<WebSocketContextValue | null>(null)

export type MachineStatusItem = MachineStatusDto

export function WebSocketProvider({ children }: { children: React.ReactNode }) {
  const [latestData, setLatestData] = useState<MachineUpdateMessage | null>(null)
  const createUrl = useCallback(() => createStatusWebSocketUrl('machines'), [])
  const handleMessage = useCallback((message: MachineUpdateMessage) => {
    setLatestData(message)
  }, [])
  const { connected, error, connect, disconnect } = useWebSocket<MachineUpdateMessage>(createUrl, {
    onMessage: handleMessage,
    reconnect: true,
  })
  const disconnectAndClear = useCallback(() => {
    disconnect()

    // リアルタイム表示をOFFにした後は、保持していたWS値ではなくHTTP取得側へ戻す。
    setLatestData(null)
  }, [disconnect])

  return (
    <WebSocketContext.Provider value={{ connected, error, connect, disconnect: disconnectAndClear, latestData }}>
      {children}
    </WebSocketContext.Provider>
  )
}

export function useWebSocketService() {
  const context = useContext(WebSocketContext)
  if (!context) throw new Error('useWebSocketService must be used within WebSocketProvider')
  return context
}

export function useServerStatusService() {
  const { data: servers, loading, error, reload } = useAsyncResource<MachineStatusItem[]>({
    load: getMachineStatuses, initialData: [], errorMessage: 'マシンステータスを取得できませんでした。',
  })

  const serverOptions = useMemo(
    () => servers.map((server) => ({ value: String(server.id), label: server.name })),
    [servers],
  )

  return { servers, serverOptions, loading, error, reload }
}

export function useMergedServerStatus() {
  const base = useServerStatusService()
  const { latestData } = useWebSocketService()

  // WSメッセージは全マシンのスナップショットなので差分結合せず、受信後はHTTP初期値より優先する。
  const servers = latestData?.type === 'machine_update' ? latestData.machines : base.servers
  const serverOptions = useMemo(
    () => servers.map((server) => ({ value: String(server.id), label: server.name })),
    [servers],
  )

  return { ...base, servers, serverOptions }
}

export function useLiveEvent() {
  const { connected, error, connect, disconnect } = useWebSocketService()
  const [isWebSocket, setIsWebSocket] = useState(false)

  const toggle = useCallback((checked: boolean) => {
    setIsWebSocket(checked)
    if (checked) {
      connect()
    } else {
      disconnect()
    }
  }, [connect, disconnect])

  return { isWebSocket, connected, connectionError: error !== null, toggle }
}
