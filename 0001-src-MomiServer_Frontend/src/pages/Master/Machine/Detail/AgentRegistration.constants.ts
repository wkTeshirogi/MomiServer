// Fixed definitions scoped to pages/Master/Machine/Detail.
export type AgentStatus = {
  agentId: string | null
  generation: number
  status: 'unregistered' | 'pending' | 'registered' | 'disabled'
  mqttClientId: string | null
  mqttStatus: string
  heartbeatAt: string | null
  connectionStatus: string
  mqttConnected: boolean
  lastSeenAt: string | null
}

export const labels: Record<AgentStatus['status'], string> = {
  unregistered: '未登録', pending: '登録待ち', registered: '登録済み', disabled: '無効',
}
