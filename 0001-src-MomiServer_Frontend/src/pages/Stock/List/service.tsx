import { itemStatusOptions } from '@/constants/inventory'
export { itemStatusOptions } from '@/constants/inventory'
import { useAsyncResource } from '@/hooks/useAsyncResource'
import { getItemMasterDetail, getItemMasterList, getItemStockList, registerItemMaster } from '@/generated/sdk.gen'
import type { ItemMasterDetailDto, ItemMasterRegistRequest, ItemStockDetailDto } from '@/generated/types.gen'
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

export type ItemStockItem = {
  id: number
  inventory_item_id: number
  storage_location: string
  quantity: number
  replenish_quantity?: number
  inventory_status: string
  expiration_date: string
  memo: string
  createdAt: string
  updatedAt: string
}

export type StockListItem = ItemStockItem & {
  master?: ItemMasterItem
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

const fallbackItems: StockListItem[] = []

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

export function getInventoryStatusClass(status: string) {
  switch (status) {
    case 'available':
      return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300'
    case 'low':
      return 'bg-amber-100 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300'
    case 'out_of_stock':
      return 'bg-red-100 text-red-700 dark:bg-red-950/50 dark:text-red-300'
    case 'deleted':
      return 'bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300'
    default:
      return 'bg-sky-100 text-sky-700 dark:bg-sky-950/50 dark:text-sky-300'
  }
}

export function getInventoryStatusLabel(status: string) {
  switch (status) {
    case 'available':
      return '在庫あり'
    case 'low':
      return '在庫注意'
    case 'out_of_stock':
      return '在庫なし'
    case 'deleted':
      return '削除'
    default:
      return status
  }
}

function normalizeMasterItem(item: ItemMasterDetailDto): ItemMasterItem | null {
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

function normalizeStockItem(item: ItemStockDetailDto): ItemStockItem | null {
  if (item.id == null || item.inventory_item_id == null) return null

  return {
    id: item.id,
    inventory_item_id: item.inventory_item_id,
    storage_location: item.storage_location ?? '',
    quantity: item.quantity ?? 0,
    replenish_quantity: item.replenish_quantity,
    inventory_status: item.inventory_status ?? 'available',
    expiration_date: item.expiration_date ?? '',
    memo: item.memo ?? '',
    createdAt: formatJapanDateTime(item.createdAt),
    updatedAt: formatJapanDateTime(item.updatedAt),
  }
}

export async function fetchStockList(signal?: AbortSignal): Promise<StockListItem[]> {
  const [masterResponse, stockResponse] = await Promise.all([
    getItemMasterList({ signal }),
    getItemStockList({ signal }),
  ])

  if (masterResponse.error) throw masterResponse.error
  if (stockResponse.error) throw stockResponse.error

  const masters = (masterResponse.data ?? [])
    .map(normalizeMasterItem)
    .filter((item): item is ItemMasterItem => item !== null)
  const masterById = new Map(masters.map(item => [item.id, item]))

  return (stockResponse.data ?? [])
    .map(normalizeStockItem)
    .filter((item): item is ItemStockItem => item !== null)
    .map(item => ({
      ...item,
      master: masterById.get(item.inventory_item_id),
    }))
}

export async function fetchItemMasterDetail(itemId: number): Promise<ItemMasterItem | null> {
  const response = await getItemMasterDetail({
    query: { id: itemId },
  })
  if (response.error) throw response.error

  const data = response.data as ItemMasterDetailDto | undefined
  return data ? normalizeMasterItem(data) : null
}

export async function saveItemMaster(input: SaveItemMasterInput): Promise<ItemMasterItem> {
  const response = await registerItemMaster({
    body: input as ItemMasterRegistRequest,
  })
  if (response.error) throw response.error

  const data = response.data as ItemMasterDetailDto | undefined
  const item = data ? normalizeMasterItem(data) : null
  if (!item) throw new Error('Invalid item master response.')
  return item
}

export function useStockListService() {
  const { data: items, loading, error } = useAsyncResource<StockListItem[]>({
    load: fetchStockList, initialData: fallbackItems, errorMessage: '在庫一覧の取得に失敗しました。',
  })
  return { items, loading, error }
}
