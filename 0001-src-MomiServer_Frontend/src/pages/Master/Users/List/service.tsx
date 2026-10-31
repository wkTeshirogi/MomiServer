import { roleLabels, roleClasses } from '../user.constants'
import type { UserRole } from '../user.constants'
export { roleLabels, roleClasses } from '../user.constants'
export type { UserRole } from '../user.constants'
import { useAsyncResource } from '@/hooks/useAsyncResource'
import {
  changeUserSettings,
  getMasterUserDetail,
  getMasterUserList,
  registerMasterUser,
} from '@/generated/sdk.gen'
import type { UserMasterDetailDto } from '@/generated/types.gen'
import { formatJapanDateTime } from '@/lib/utils'

export type UserMasterItem = {
  id: number
  email: string
  role: UserRole
  createdAt: string
  updatedAt: string
  lastLoginAt: string | null
}

export type SaveUserInput = {
  id?: number
  email: string
  password?: string
  role: UserRole
}

function normalizeRole(role?: string): UserRole {
  switch (role) {
    case 'ADMIN':
    case 'ADMIN_ROLE':
    case '999':
      return 'ADMIN'
    case 'OPERATOR':
    case 'OPERATOR_ROLE':
    case '100':
      return 'OPERATOR'
    case 'BAN_USER':
    case 'BAN_USER_ROLE':
    case '0':
      return 'BAN_USER'
    case 'USER':
    case 'USER_ROLE':
    case '10':
      return 'USER'
    default:
      return 'USER'
  }
}

export function normalizeUser(dto: UserMasterDetailDto): UserMasterItem {
  return {
    id: dto.id ?? 0,
    email: dto.email ?? '',
    role: normalizeRole(dto.role),
    createdAt: formatJapanDateTime(dto.createdDt),
    updatedAt: formatJapanDateTime(dto.updatedDt),
    lastLoginAt: dto.lastLoginAt ? formatJapanDateTime(dto.lastLoginAt) : null,
  }
}

export async function fetchUserMasterList(signal?: AbortSignal): Promise<UserMasterItem[]> {
  const response = await getMasterUserList({ signal, throwOnError: true })
  return (response.data ?? []).map(normalizeUser)
}

export async function fetchUserMasterDetail(userId: number): Promise<UserMasterItem> {
  const response = await getMasterUserDetail({
    query: { id: userId },
  })

  return normalizeUser(response.data ?? {})
}

async function saveMasterUser(input: SaveUserInput) {
  const body = {
    id: input.id,
    email: input.email,
    password: input.password ?? '',
    role: input.role,
  }

  const response = await registerMasterUser({ body })

  if (response.error) {
    throw response.error
  }
}

export async function createUser(input: SaveUserInput) {
  await saveMasterUser(input)
}

export async function updateUser(input: SaveUserInput) {
  const response = await changeUserSettings({
    body: {
      email: input.email,
      password: input.password || undefined,
      role: input.role,
    },
  })

  if (response.error) {
    throw response.error
  }
}

export async function disableUser(user: UserMasterItem) {
  await updateUser({
    id: user.id,
    email: user.email,
    role: 'BAN_USER',
  })
}

export function useUserMasterService() {
  const { data: users, loading, error, reload } = useAsyncResource<UserMasterItem[]>({
    load: fetchUserMasterList, initialData: [], errorMessage: 'ユーザー一覧の取得に失敗しました。',
  })
  return { users, loading, error, reload, roleLabels, roleClasses }
}
