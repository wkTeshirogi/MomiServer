import { type InventoryScope } from '@/api/externalApiKeyApi'

// Fixed definitions scoped to pages/Master/ApiKeys.
export const scopeLabels: Record<InventoryScope, string> = { 'inventory:read': '在庫参照', 'inventory:write': '在庫数量更新' }
export const defaultExpiryYears = 1

type ExternalInventoryApi = {
  name: string
  method: 'GET' | 'POST'
  path: string
  scope: InventoryScope
  requestBody?: {
    contentType: string
    fields: { name: string; type: string; required: boolean; description: string }[]
    example: Record<string, string | number>
  }
}

export const externalInventoryApis: ExternalInventoryApi[] = [
  { name: '在庫一覧', method: 'GET', path: '/api/v1/external/inventory/stocks', scope: 'inventory:read' },
  { name: '在庫詳細', method: 'GET', path: '/api/v1/external/inventory/stocks/{id}', scope: 'inventory:read' },
  {
    name: '入庫・出庫・棚卸', method: 'POST', path: '/api/v1/external/inventory/stocks/{id}/operations', scope: 'inventory:write',
    requestBody: {
      contentType: 'application/json',
      fields: [
        { name: 'operationId', type: 'string (UUID)', required: true, description: '操作ごとに異なるUUID。同一キーで同じ操作を再送する場合は、同じUUIDと内容を使用します。' },
        { name: 'changeType', type: 'string', required: true, description: 'inbound（入庫）、outbound（出庫）、stocktake（棚卸）のいずれか。' },
        { name: 'quantity', type: 'integer', required: true, description: '0〜2147483647。入出庫は増減量、棚卸は実測数量を指定します。' },
        { name: 'reason', type: 'string', required: false, description: '変更理由。省略可能、255文字以下。' },
      ],
      example: { operationId: '550e8400-e29b-41d4-a716-446655440000', changeType: 'outbound', quantity: 1, reason: '外部連携による出庫' },
    },
  },
]
