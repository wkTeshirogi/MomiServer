export const machineStatusLabels: Record<string, string> = {
  '0': '無効',
  '1': '有効',
  '10': '稼働中',
  '20': '停止中',
  '90': 'メンテナンス',
  '99': '異常',
  active: '有効',
  enabled: '有効',
  online: '稼働中',
  inactive: '無効',
  disabled: '無効',
  offline: '停止中',
  maintenance: 'メンテナンス',
  error: '異常',
}

export const machineStatusClasses: Record<string, string> = {
  '0': 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-300',
  '1': 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
  '10': 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
  '20': 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-300',
  '90': 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
  '99': 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
  active: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
  enabled: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
  online: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
  inactive: 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-300',
  disabled: 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-300',
  offline: 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-300',
  maintenance: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
  error: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
}

export function getMachineStatusLabel(status: string) {
  if (!status) return '不明'
  return machineStatusLabels[status.toLowerCase()] ?? status
}

export function getMachineStatusClass(status: string) {
  return machineStatusClasses[status.toLowerCase()] ?? 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-300'
}
