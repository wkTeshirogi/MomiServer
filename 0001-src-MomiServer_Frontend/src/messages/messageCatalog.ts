export const MessageCode = {
  InventoryLowTitle: 'inventory.low.title',
  InventoryLowMessage: 'inventory.low.message',
  InventoryOutOfStockTitle: 'inventory.outOfStock.title',
  InventoryOutOfStockMessage: 'inventory.outOfStock.message',
  ValidationFormCheckInput: 'validation.form.checkInput',
  ValidationInventoryItemRequired: 'validation.inventory.itemRequired',
  ValidationInventoryStorageLocationRequired: 'validation.inventory.storageLocationRequired',
  ValidationInventoryQuantityMin: 'validation.inventory.quantityMin',
  ValidationInventoryReplenishQuantityMin: 'validation.inventory.replenishQuantityMin',
  ValidationInventoryStatusRequired: 'validation.inventory.statusRequired',
  ErrorInventoryInvalidId: 'error.inventory.invalidId',
  ErrorInventoryNotFound: 'error.inventory.notFound',
  ErrorInventoryFetchDetailFailed: 'error.inventory.fetchDetailFailed',
} as const

export type MessageCode = typeof MessageCode[keyof typeof MessageCode]

type MessageValues = Record<string, string | number | boolean | null | undefined>

const messages: Record<MessageCode, string> = {
  [MessageCode.InventoryLowTitle]: '在庫が少なくなっています',
  [MessageCode.InventoryLowMessage]: '在庫品目ID {itemId} の現在数量は {quantity} です。',
  [MessageCode.InventoryOutOfStockTitle]: '在庫切れが発生しました',
  [MessageCode.InventoryOutOfStockMessage]: '在庫品目ID {itemId} の現在数量は {quantity} です。',
  [MessageCode.ValidationFormCheckInput]: '入力内容を確認してください。',
  [MessageCode.ValidationInventoryItemRequired]: '品目は必須です。',
  [MessageCode.ValidationInventoryStorageLocationRequired]: '保管場所は必須です。',
  [MessageCode.ValidationInventoryQuantityMin]: '現在数量は0以上で入力してください。',
  [MessageCode.ValidationInventoryReplenishQuantityMin]: '補充時の推奨数量は0以上で入力してください。',
  [MessageCode.ValidationInventoryStatusRequired]: '在庫ステータスは必須です。',
  [MessageCode.ErrorInventoryInvalidId]: '在庫IDが正しくありません。',
  [MessageCode.ErrorInventoryNotFound]: '在庫情報が見つかりません。',
  [MessageCode.ErrorInventoryFetchDetailFailed]: '在庫詳細の取得に失敗しました。',
}

export function message(code: MessageCode, values: MessageValues = {}): string {
  let template = messages[code] ?? code

  Object.entries(values).forEach(([key, value]) => {
    template = template.replaceAll(`{${key}}`, String(value ?? ''))
  })

  return template
}
