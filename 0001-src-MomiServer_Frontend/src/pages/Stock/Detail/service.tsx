import { inventoryStatusOptions } from './service.constants'
export { inventoryStatusOptions } from './service.constants'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAsyncRequest } from '@/hooks/useAsyncRequest'
import { getItemMasterList, getItemStockDetail, saveItemStock } from '@/generated/sdk.gen'
import type { ItemMasterDetailDto, ItemStockDetailDto, ItemStockRegistRequest } from '@/generated/types.gen'
import { formatJapanDateTime } from '@/lib/utils'
import { message, MessageCode } from '@/messages/messageCatalog'

export type ItemMasterOption = {
  id: number
  label: string
  item_name: string
  manufacturer: string
  model_number: string
  category: string
  unit: string
  min_quantity: number
  status: string
}

export type ItemStockDetail = {
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

export type SaveItemStockInput = {
  id?: number
  inventory_item_id: number
  storage_location: string
  quantity: number
  replenish_quantity?: number
  inventory_status: string
  expiration_date?: string
  memo?: string
}

export type ItemStockFieldErrors = Partial<Record<keyof SaveItemStockInput, string>>

export function deriveInventoryStatus(quantity: number, minimumQuantity: number | undefined, currentStatus: string) {
  if (currentStatus === 'deleted') return 'deleted'
  if (quantity === 0) return 'out_of_stock'
  if (minimumQuantity != null && quantity <= minimumQuantity) return 'low'
  return 'available'
}

export function getInventoryStatusLabel(status: string) {
  return inventoryStatusOptions.find(option => option.value === status)?.label ?? status
}

function normalizeMasterOption(item: ItemMasterDetailDto): ItemMasterOption | null {
  if (item.id == null) return null

  const name = item.item_name ?? ''
  const model = item.model_number ? ` / ${item.model_number}` : ''
  const manufacturer = item.manufacturer ? `${item.manufacturer} ` : ''

  return {
    id: item.id,
    label: `${manufacturer}${name}${model}`.trim() || `品目ID ${item.id}`,
    item_name: name,
    manufacturer: item.manufacturer ?? '',
    model_number: item.model_number ?? '',
    category: item.category ?? '',
    unit: item.unit ?? '',
    min_quantity: item.min_quantity ?? 0,
    status: item.status ?? '',
  }
}

function normalizeStock(item: ItemStockDetailDto): ItemStockDetail | null {
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

export function createEmptyItemStock(): SaveItemStockInput {
  return {
    inventory_item_id: 0,
    storage_location: '',
    quantity: 0,
    replenish_quantity: undefined,
    inventory_status: 'available',
    expiration_date: undefined,
    memo: '',
  }
}

export function toSaveInput(item: ItemStockDetail | null): SaveItemStockInput {
  if (!item) return createEmptyItemStock()

  return {
    id: item.id,
    inventory_item_id: item.inventory_item_id,
    storage_location: item.storage_location,
    quantity: item.quantity,
    replenish_quantity: item.replenish_quantity,
    inventory_status: item.inventory_status,
    expiration_date: item.expiration_date || undefined,
    memo: item.memo,
  }
}

export function validateItemStock(input: SaveItemStockInput): ItemStockFieldErrors {
  const errors: ItemStockFieldErrors = {}

  if (!Number.isFinite(input.inventory_item_id) || input.inventory_item_id <= 0) {
    errors.inventory_item_id = message(MessageCode.ValidationInventoryItemRequired)
  }
  if (!input.storage_location.trim()) errors.storage_location = message(MessageCode.ValidationInventoryStorageLocationRequired)
  if (!Number.isFinite(input.quantity) || input.quantity < 0) {
    errors.quantity = message(MessageCode.ValidationInventoryQuantityMin)
  }
  if (input.replenish_quantity != null && (!Number.isFinite(input.replenish_quantity) || input.replenish_quantity < 0)) {
    errors.replenish_quantity = message(MessageCode.ValidationInventoryReplenishQuantityMin)
  }
  if (!input.inventory_status.trim()) errors.inventory_status = message(MessageCode.ValidationInventoryStatusRequired)

  return errors
}

async function fetchItemMasters(): Promise<ItemMasterOption[]> {
  const response = await getItemMasterList()
  if (response.error) throw response.error

  return (response.data ?? [])
    .map(normalizeMasterOption)
    .filter((item): item is ItemMasterOption => item !== null)
}

async function fetchItemStockDetail(itemId: number): Promise<ItemStockDetail | null> {
  const response = await getItemStockDetail({
    query: { id: itemId },
  })
  if (response.error) throw response.error

  const data = response.data as ItemStockDetailDto | undefined
  return data ? normalizeStock(data) : null
}

export async function saveItemStockDetail(input: SaveItemStockInput): Promise<ItemStockDetail> {
  const response = await saveItemStock({
    body: input as ItemStockRegistRequest,
  })
  if (response.error) throw response.error

  const data = response.data as ItemStockDetailDto | undefined
  const item = data ? normalizeStock(data) : null
  if (!item) throw new Error('Invalid item stock response.')
  return item
}

export function useItemStockDetailService(itemId?: string) {
  const { run, cancel } = useAsyncRequest()
  const isNew = !itemId || itemId === 'new'
  const [item, setItem] = useState<ItemStockDetail | null>(null)
  const [masters, setMasters] = useState<ItemMasterOption[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    const numericId = Number(itemId)
    if (!isNew && !Number.isFinite(numericId)) {
      setError(message(MessageCode.ErrorInventoryInvalidId))
      setLoading(false)
      return
    }

    await run(() => Promise.all([
        fetchItemMasters(),
        isNew ? Promise.resolve(null) : fetchItemStockDetail(numericId),
      ]), {
      onStart: () => { setLoading(true); setError(null) },
      onSuccess: ([nextMasters, nextItem]) => {
        setMasters(nextMasters); setItem(nextItem)
        if (!isNew && !nextItem) setError(message(MessageCode.ErrorInventoryNotFound))
      },
      onError: () => setError(message(MessageCode.ErrorInventoryFetchDetailFailed)),
      onSettled: () => setLoading(false),
    })
  }, [itemId, isNew, run])

  useEffect(() => {
    const timer = window.setTimeout(() => { void reload() }, 0)
    return () => { window.clearTimeout(timer); cancel() }
  }, [reload, cancel])

  return useMemo(
    () => ({
      item,
      masters,
      loading,
      error,
      isNew,
      reload,
    }),
    [item, masters, loading, error, isNew, reload]
  )
}
