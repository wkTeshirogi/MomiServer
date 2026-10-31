import { NotificationBanner } from '@/components/ui/notification-banner'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import {
  itemStatusOptions,
  saveItemMaster,
  toSaveInput,
  useItemMasterDetailService,
  validateItemMaster,
  type SaveItemMasterInput,
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

export function ItemMasterDetail() {
  const { itemId } = useParams()
  const navigate = useNavigate()
  const { item, loading, error, isNew, reload } = useItemMasterDetailService(itemId)
  const [formSource, setFormSource] = useState(item)
  const [form, setForm] = useState<SaveItemMasterInput>(() => toSaveInput(item))
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof SaveItemMasterInput, string>>>({})
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  // Synchronize only when fetched data changes; ordinary renders preserve edits.
  if (formSource !== item) {
    setFormSource(item)
    setForm(toSaveInput(item))
  }

  const patchForm = (patch: Partial<SaveItemMasterInput>) => {
    setForm(current => ({ ...current, ...patch }))
    setSaveError(null)
    setSaved(false)
  }

  const handleBack = () => navigate('/master/items')

  const handleSave = async () => {
    const errors = validateItemMaster(form)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      setSaved(false)
      setSaveError('入力内容を確認してください。')
      scrollToPageTop()
      return
    }

    try {
      setSaving(true)
      setSaved(false)
      setSaveError(null)
      const savedItem = await saveItemMaster({
        ...form,
        id: isNew ? undefined : item?.id,
        manufacturer: form.manufacturer || undefined,
        model_number: form.model_number || undefined,
        jan_code: form.jan_code || undefined,
        purchase_url: form.purchase_url || undefined,
        memo: form.memo || undefined,
      })

      setSaved(true)
      scrollToPageTop()
      if (isNew && savedItem.id) {
        navigate(`/master/items/${savedItem.id}`, { replace: true })
      } else {
        await reload()
      }
    } catch {
      setSaveError('在庫品目マスタの保存に失敗しました。')
      scrollToPageTop()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            在庫品目マスタ詳細
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            備品・消耗品の品目情報と管理ステータスを編集します。
          </p>
        </div>
        <Button variant="ghost" onClick={handleBack}>
          一覧に戻る
        </Button>
      </div>

      {!isNew && error && (
        <NotificationBanner type="error">
          {error}
        </NotificationBanner>
      )}

      {!isNew && loading && (
        <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
          <p className="text-sm text-zinc-500 dark:text-zinc-400">読み込み中...</p>
        </Card>
      )}
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

      {(isNew || !loading) && (
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_22rem]">
          <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
            <div className="max-w-3xl space-y-4">
              <Field label="品目名" id="item_name" error={fieldErrors.item_name} required>
                <Input
                  id="item_name"
                  value={form.item_name}
                  onChange={event => patchForm({ item_name: event.target.value })}
                  placeholder="もみじ饅頭"
                />
              </Field>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <Field label="カテゴリ" id="category" error={fieldErrors.category} required>
                  <Input
                    id="category"
                    value={form.category}
                    onChange={event => patchForm({ category: event.target.value })}
                    placeholder="食品"
                  />
                </Field>

                <Field label="単位" id="unit" error={fieldErrors.unit} required>
                  <Input
                    id="unit"
                    value={form.unit}
                    onChange={event => patchForm({ unit: event.target.value })}
                    placeholder="個"
                  />
                </Field>
              </div>

              <Field label="最低数量" id="min_quantity" error={fieldErrors.min_quantity} required>
                <Input
                  id="min_quantity"
                  type="number"
                  min={0}
                  value={form.min_quantity}
                  onChange={event => patchForm({ min_quantity: Math.max(0, Number(event.target.value) || 0) })}
                  placeholder="0"
                />
              </Field>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <Field label="メーカー" id="manufacturer">
                  <Input
                    id="manufacturer"
                    value={form.manufacturer ?? ''}
                    onChange={event => patchForm({ manufacturer: event.target.value })}
                    placeholder="メーカー名"
                  />
                </Field>

                <Field label="型番" id="model_number">
                  <Input
                    id="model_number"
                    value={form.model_number ?? ''}
                    onChange={event => patchForm({ model_number: event.target.value })}
                    placeholder="MOMIJI"
                  />
                </Field>
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <Field label="JANコード" id="jan_code">
                  <Input
                    id="jan_code"
                    maxLength={13}
                    value={form.jan_code ?? ''}
                    onChange={event => patchForm({ jan_code: event.target.value.replace(/\D/g, '').slice(0, 13) })}
                    placeholder="4900000000000"
                  />
                </Field>

                <Field label="ステータス" error={fieldErrors.status} required>
                  <Select
                    value={itemStatusOptions.find(option => option.value === form.status) ?? itemStatusOptions[0]}
                    options={itemStatusOptions}
                    onChange={option => option && patchForm({ status: option.value })}
                    placeholder="ステータスを選択"
                  />
                </Field>
              </div>

              <Field label="購入URL" id="purchase_url">
                <Input
                  id="purchase_url"
                  value={form.purchase_url ?? ''}
                  onChange={event => patchForm({ purchase_url: event.target.value })}
                  placeholder="https://example.com/items/..."
                />
              </Field>

              <Field label="メモ" id="memo">
                <textarea
                  id="memo"
                  value={form.memo ?? ''}
                  onChange={event => patchForm({ memo: event.target.value })}
                  rows={4}
                  className="w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 outline-none transition-colors placeholder:text-zinc-400 focus:border-purple-500 focus:ring-1 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
                  placeholder="品目に関するメモ"
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
              <DetailRow label="ID" value={item?.id} />
              <DetailRow label="作成日時" value={item?.createdAt} />
              <DetailRow label="更新日時" value={item?.updatedAt} />
            </div>
          </Card>
        </div>
      )}
    </div>
  )
}
