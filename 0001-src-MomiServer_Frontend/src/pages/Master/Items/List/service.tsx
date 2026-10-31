import { itemStatusOptions } from '@/constants/inventory'
export { itemStatusOptions } from '@/constants/inventory'
import { useAsyncResource } from '@/hooks/useAsyncResource'
import { client } from '@/generated/client.gen'
import { formatJapanDateTime } from '@/lib/utils'

export type ItemMasterItem = {
  id: number
  item_name: string
  category: string
  unit: string
  min_quantity: number
  manufacturer: string
  model_number: string
  jan_code: string
  purchase_url: string
  status: string
  memo: string
  createdAt: string
  updatedAt: string
}

export type SaveItemMasterInput = {
  id?: number
  item_name: string
  category: string
  unit: string
  min_quantity: number
  manufacturer?: string
  model_number?: string
  jan_code?: string
  purchase_url?: string
  status: string
  memo?: string
}

const fallbackItems: ItemMasterItem[] = []

export function getItemStatusLabel(status: string) {
  return itemStatusOptions.find(option => option.value === status)?.label ?? status
}

export function getItemStatusClass(status: string) {
  switch (status) {
    case 'active':
      return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300'
    case 'disabled':
      return 'bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300'
    case 'deleted':
      return 'bg-red-100 text-red-700 dark:bg-red-950/50 dark:text-red-300'
    default:
      return 'bg-amber-100 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300'
  }
}

function normalizeItem(item: Partial<ItemMasterItem>): ItemMasterItem | null {
  if (item.id == null) return null

  return {
    id: item.id,
    item_name: item.item_name ?? '',
    category: item.category ?? '',
    unit: item.unit ?? '',
    min_quantity: item.min_quantity ?? 0,
    manufacturer: item.manufacturer ?? '',
    model_number: item.model_number ?? '',
    jan_code: item.jan_code ?? '',
    purchase_url: item.purchase_url ?? '',
    status: item.status ?? 'active',
    memo: item.memo ?? '',
    createdAt: formatJapanDateTime(item.createdAt),
    updatedAt: formatJapanDateTime(item.updatedAt),
  }
}

export async function fetchItemMasterList(signal?: AbortSignal): Promise<ItemMasterItem[]> {
  const response = await client.post<Partial<ItemMasterItem>[]>({
    security: [{ scheme: 'bearer', type: 'http' }],
    url: '/api/master/item/list',
    signal,
  })
  if (response.error) throw response.error

  const data = (response.data ?? []) as Partial<ItemMasterItem>[]

  return data
    .map((item: Partial<ItemMasterItem>) => normalizeItem(item))
    .filter((item): item is ItemMasterItem => item !== null)
}

export async function fetchItemMasterDetail(itemId: number): Promise<ItemMasterItem | null> {
  const response = await client.post<Partial<ItemMasterItem>>({
    security: [{ scheme: 'bearer', type: 'http' }],
    url: '/api/master/item/detail',
    query: { id: itemId },
  })
  if (response.error) throw response.error

  const data = response.data as Partial<ItemMasterItem> | undefined
  return data ? normalizeItem(data) : null
}

export async function saveItemMaster(input: SaveItemMasterInput): Promise<ItemMasterItem> {
  const response = await client.post<Partial<ItemMasterItem>>({
    security: [{ scheme: 'bearer', type: 'http' }],
    url: '/api/master/item/register',
    body: input,
    headers: {
      'Content-Type': 'application/json',
    },
  })
  if (response.error) throw response.error

  const data = response.data as Partial<ItemMasterItem> | undefined
  const item = data ? normalizeItem(data) : null
  if (!item) throw new Error('Invalid item master response.')
  return item
}

export function useItemMasterService() {
  const { data: items, loading, error } = useAsyncResource<ItemMasterItem[]>({
    load: fetchItemMasterList, initialData: fallbackItems, errorMessage: '在庫品目マスタ一覧の取得に失敗しました。',
  })
  return { items, loading, error }
}
