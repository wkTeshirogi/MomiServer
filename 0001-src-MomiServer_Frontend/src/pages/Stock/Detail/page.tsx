import { NotificationBanner } from '@/components/ui/notification-banner'
import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { message, MessageCode } from '@/messages/messageCatalog'
import {
  deriveInventoryStatus,
  getInventoryStatusLabel,
  saveItemStockDetail,
  toSaveInput,
  useItemStockDetailService,
  validateItemStock,
  type ItemMasterOption,
  type ItemStockDetail,
  type SaveItemStockInput,
} from './service'

function DetailRow({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <div className="grid grid-cols-[7rem_minmax(0,1fr)] gap-3 text-sm">
      <span className="text-zinc-500 dark:text-zinc-400">{label}</span>
      <span className="break-words text-zinc-900 dark:text-zinc-100">{value || '-'}</span>
    </div>
  )
}

function scrollToPageTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function Field({
  label,
  id,
  error,
  required = false,
  children,
}: {
  label: string
  id?: string
  error?: string
  required?: boolean
  children: React.ReactNode
}) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
        {label}
        {required && <span className="ml-1 text-red-500">*</span>}
      </label>
      {children}
      {error && <p className="text-xs text-red-500">{error}</p>}
    </div>
  )
}

function toOptionalNumber(value: string) {
  if (value.trim() === '') return undefined
  return Math.max(0, Number(value) || 0)
}

type StockDetailFormProps = {
  item: ItemStockDetail | null
  masters: ItemMasterOption[]
  isNew: boolean
  reload: () => Promise<void>
}

function StockDetailForm({ item, masters, isNew, reload }: StockDetailFormProps) {
  const navigate = useNavigate()
  const [form, setForm] = useState<SaveItemStockInput>(() => toSaveInput(item))
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof SaveItemStockInput, string>>>({})
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  const itemOptions = useMemo(
    () => masters.map(master => ({
      value: String(master.id),
      label: master.label,
    })),
    [masters]
  )

  const selectedMaster = masters.find(master => master.id === form.inventory_item_id)
  const masterMinimumQuantity = selectedMaster?.min_quantity
  const derivedInventoryStatus = deriveInventoryStatus(
    form.quantity,
    masterMinimumQuantity,
    form.inventory_status
  )

  const patchForm = (patch: Partial<SaveItemStockInput>) => {
    setForm(current => ({ ...current, ...patch }))
    setSaveError(null)
    setSaved(false)
  }

  const handleBack = () => navigate('/stock')

  const handleSave = async () => {
    const errors = validateItemStock(form)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      setSaved(false)
      setSaveError(message(MessageCode.ValidationFormCheckInput))
      scrollToPageTop()
      return
    }

    try {
      setSaving(true)
      setSaved(false)
      setSaveError(null)
      const savedItem = await saveItemStockDetail({
        ...form,
        id: isNew ? undefined : item?.id,
        inventory_status: derivedInventoryStatus,
        replenish_quantity: form.replenish_quantity,
        expiration_date: form.expiration_date || undefined,
        memo: form.memo || undefined,
      })

      setSaved(true)
      scrollToPageTop()
      if (isNew && savedItem.id) {
        navigate(`/stock/${savedItem.id}`, { replace: true })
      } else {
        await reload()
      }
    } catch {
      setSaveError('在庫情報の保存に失敗しました。')
      scrollToPageTop()
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      {saveError && (
        <NotificationBanner type="error" onClose={() => setSaveError(null)}>
          {saveError}
        </NotificationBanner>
      )}
      {saved && (
        <NotificationBanner type="success" onClose={() => setSaved(false)}>
          保存しました。
        </NotificationBanner>
      )}

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_22rem]">
        <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
          <div className="max-w-3xl space-y-4">
            <Field label="品目" error={fieldErrors.inventory_item_id} required>
              <Select
                value={itemOptions.find(option => option.value === String(form.inventory_item_id)) ?? null}
                options={itemOptions}
                onChange={option => {
                  const nextItemId = Number(option?.value ?? 0)
                  patchForm({
                    inventory_item_id: nextItemId,
                  })
                }}
                placeholder="品目を選択"
                className="max-w-full"
              />
            </Field>

            <Field label="保管場所" id="storage_location" error={fieldErrors.storage_location} required>
              <Input
                id="storage_location"
                value={form.storage_location}
                onChange={event => patchForm({ storage_location: event.target.value })}
                placeholder="食品棚 上段"
              />
            </Field>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Field label="現在数量" id="quantity" error={fieldErrors.quantity} required>
                <Input
                  id="quantity"
                  type="number"
                  min={0}
                  value={form.quantity}
                  onChange={event => patchForm({ quantity: Math.max(0, Number(event.target.value) || 0) })}
                  placeholder="0"
                />
              </Field>

              <div className="space-y-1.5">
                <span className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                  最低数量
                </span>
                <div className="flex h-10 items-center rounded-lg border border-zinc-200 bg-zinc-50 px-3 text-sm text-zinc-700 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-300">
                  {masterMinimumQuantity ?? '-'} {selectedMaster?.unit || ''}
                </div>
              </div>

              <Field label="補充推奨数量" id="replenish_quantity" error={fieldErrors.replenish_quantity}>
                <Input
                  id="replenish_quantity"
                  type="number"
                  min={0}
                  value={form.replenish_quantity ?? ''}
                  onChange={event => patchForm({ replenish_quantity: toOptionalNumber(event.target.value) })}
                  placeholder="未設定"
                />
              </Field>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label="在庫ステータス">
                <div className="flex h-10 items-center rounded-lg border border-zinc-200 bg-zinc-50 px-3 text-sm text-zinc-700 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-300">
                  {getInventoryStatusLabel(derivedInventoryStatus)}
                </div>
              </Field>

              <Field label="期限日" id="expiration_date">
                <Input
                  id="expiration_date"
                  type="date"
                  value={form.expiration_date ?? ''}
                  onChange={event => patchForm({ expiration_date: event.target.value || undefined })}
                />
              </Field>
            </div>

            <Field label="メモ" id="memo">
              <textarea
                id="memo"
                value={form.memo ?? ''}
                onChange={event => patchForm({ memo: event.target.value })}
                rows={4}
                className="w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 outline-none transition-colors placeholder:text-zinc-400 focus:border-purple-500 focus:ring-1 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
                placeholder="在庫に関するメモ"
              />
            </Field>

            <div className="flex justify-end gap-3 border-t border-zinc-200 pt-4 dark:border-zinc-800">
              <Button variant="ghost" onClick={handleBack}>
                キャンセル
              </Button>
              <Button onClick={() => void handleSave()} disabled={saving}>
                {saving ? '保存中...' : '保存'}
              </Button>
            </div>
          </div>
        </Card>

        <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
          <h2 className="mb-4 text-sm font-semibold text-zinc-900 dark:text-zinc-100">登録情報</h2>
          <div className="space-y-3">
            <DetailRow label="在庫ID" value={item?.id} />
            <DetailRow label="品目ID" value={form.inventory_item_id || null} />
            <DetailRow label="メーカー" value={selectedMaster?.manufacturer} />
            <DetailRow label="品目名" value={selectedMaster?.item_name} />
            <DetailRow label="型番" value={selectedMaster?.model_number} />
            <DetailRow label="カテゴリ" value={selectedMaster?.category} />
            <DetailRow label="単位" value={selectedMaster?.unit} />
            <DetailRow label="最低数量" value={masterMinimumQuantity == null ? null : `${masterMinimumQuantity} ${selectedMaster?.unit || ''}`.trim()} />
            <DetailRow label="作成日時" value={item?.createdAt} />
            <DetailRow label="更新日時" value={item?.updatedAt} />
          </div>
        </Card>
      </div>
    </>
  )
}

export function StockDetail() {
  const { itemId } = useParams()
  const navigate = useNavigate()
  const { item, masters, loading, error, isNew, reload } = useItemStockDetailService(itemId)

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            在庫管理詳細
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            品目ごとの実在庫、保管場所、数量を編集し、在庫ステータスを確認します。
          </p>
        </div>
        <Button variant="ghost" onClick={() => navigate('/stock')}>
          一覧に戻る
        </Button>
      </div>

      {error && (
        <NotificationBanner type="error">
          {error}
        </NotificationBanner>
      )}

      {loading && (
        <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
          <p className="text-sm text-zinc-500 dark:text-zinc-400">読み込み中...</p>
        </Card>
      )}

      {!loading && (
        <StockDetailForm
          key={isNew ? 'new' : item?.id ?? 'missing'}
          item={item}
          masters={masters}
          isNew={isNew}
          reload={reload}
        />
      )}
    </div>
  )
}
