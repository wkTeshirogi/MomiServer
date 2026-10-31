import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
} from 'react'
import {
  createStatusWebSocketUrl,
  getServiceStatuses,
  type ServiceStatusDto,
  type ServiceUpdateMessage,
} from '@/api/statusApi'
import { useWebSocket } from '@/hooks/useWebSocket'
import { useAsyncResource } from '@/hooks/useAsyncResource'

type WebSocketContextValue = {
  connected: boolean
  error: Event | null
  connect: () => void
  disconnect: () => void
  latestData: ServiceUpdateMessage | null
}

const WebSocketContext = createContext<WebSocketContextValue | null>(null)

export type ServerStatusItem = ServiceStatusDto

export function WebSocketProvider({ children }: { children: React.ReactNode }) {
  const [latestData, setLatestData] = useState<ServiceUpdateMessage | null>(null)
  const createUrl = useCallback(() => createStatusWebSocketUrl('services'), [])
  const handleMessage = useCallback((message: ServiceUpdateMessage) => {
    setLatestData(message)
  }, [])
  const { connected, error, connect, disconnect } = useWebSocket<ServiceUpdateMessage>(createUrl, {
    onMessage: handleMessage,
    reconnect: true,
  })

  return (
    <WebSocketContext.Provider value={{ connected, error, connect, disconnect, latestData }}>
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
  const { data: servers, loading, error, reload } = useAsyncResource<ServerStatusItem[]>({
    load: getServiceStatuses, initialData: [], errorMessage: 'サービスステータスを取得できませんでした。',
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
  const servers = latestData?.type === 'service_update' ? latestData.services : base.servers
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
