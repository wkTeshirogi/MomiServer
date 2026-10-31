import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAsyncRequest } from '@/hooks/useAsyncRequest'
import {
  fetchItemMasterDetail,
  itemStatusOptions,
  saveItemMaster,
  type ItemMasterItem,
  type SaveItemMasterInput,
} from '../List/service'

export type ItemMasterFieldErrors = Partial<Record<keyof SaveItemMasterInput, string>>

export { itemStatusOptions, saveItemMaster }
export type { ItemMasterItem, SaveItemMasterInput }

export function createEmptyItemMaster(): SaveItemMasterInput {
  return {
    item_name: '',
    category: '',
    unit: '',
    min_quantity: 0,
    manufacturer: '',
    model_number: '',
    jan_code: '',
    purchase_url: '',
    status: 'active',
    memo: '',
  }
}

export function toSaveInput(item: ItemMasterItem | null): SaveItemMasterInput {
  if (!item) return createEmptyItemMaster()

  return {
    id: item.id,
    item_name: item.item_name,
    category: item.category,
    unit: item.unit,
    min_quantity: item.min_quantity,
    manufacturer: item.manufacturer,
    model_number: item.model_number,
    jan_code: item.jan_code,
    purchase_url: item.purchase_url,
    status: item.status,
    memo: item.memo,
  }
}

export function validateItemMaster(input: SaveItemMasterInput): ItemMasterFieldErrors {
  const errors: ItemMasterFieldErrors = {}
  if (!input.item_name.trim()) errors.item_name = '品目名は必須です。'
  if (!input.category.trim()) errors.category = 'カテゴリは必須です。'
  if (!input.unit.trim()) errors.unit = '単位は必須です。'
  if (!Number.isFinite(input.min_quantity) || input.min_quantity < 0) {
    errors.min_quantity = '最低数量は0以上で入力してください。'
  }
  if ((input.jan_code ?? '').length > 13) errors.jan_code = 'JANコードは13桁以内で入力してください。'
	if (!input.status.trim()) errors.status = 'ステータスは必須です。'
	return errors
}

export function useItemMasterDetailService(itemId?: string) {
  const { run, cancel } = useAsyncRequest()
  const isNew = !itemId || itemId === 'new'
  const [item, setItem] = useState<ItemMasterItem | null>(null)
  const [loading, setLoading] = useState(!isNew)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    if (isNew) return

    const numericId = Number(itemId)
    if (!Number.isFinite(numericId)) {
      setError('品目IDが正しくありません。')
      setLoading(false)
      return
    }

    await run(() => fetchItemMasterDetail(numericId), {
      onStart: () => { setLoading(true); setError(null) },
      onSuccess: nextItem => { setItem(nextItem); if (!nextItem) setError('在庫品目マスタが見つかりません。') },
      onError: () => setError('在庫品目マスタ詳細の取得に失敗しました。'),
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
      loading,
      error,
      isNew,
      reload,
    }),
    [item, loading, error, isNew, reload]
  )
}
