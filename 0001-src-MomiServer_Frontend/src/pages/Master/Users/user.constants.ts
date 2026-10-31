// Role presentation shared by user list and detail screens.
export type UserRole = 'ADMIN' | 'OPERATOR' | 'USER' | 'BAN_USER'

export const roleLabels: Record<UserRole, string> = {
  ADMIN: '管理者',
  OPERATOR: '運用担当者',
  USER: '一般ユーザー',
  BAN_USER: '利用停止',
}

export const roleClasses: Record<UserRole, string> = {
  ADMIN: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300',
  OPERATOR: 'bg-sky-100 text-sky-800 dark:bg-sky-900/30 dark:text-sky-300',
  USER: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-300',
  BAN_USER: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
}
