import { emptyForm } from '../vehicle.constants'
import { useEffect, useMemo, useState } from 'react'
import { ArrowLeft, Check, FileText, X } from 'lucide-react'
import { getApiErrorMessage } from '@/lib/apiError'
import { useNavigate, useParams } from 'react-router-dom'
import {
  cancelVehicleImport,
  confirmVehicleImport,
  getVehicleImport,
  getVehicleImportSource,
  type VehicleImport,
  type VehicleSaveInput,
} from '@/api/vehicleApi'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { NotificationBanner } from '@/components/ui/notification-banner'
type FormErrors = Partial<Record<keyof VehicleSaveInput, string>>

function toForm(item: VehicleImport): VehicleSaveInput {
  return { ...emptyForm, ...item.candidate, vehicle_identification_code: '', usage_category: '', purpose: '', notes: '' }
}

function validate(form: VehicleSaveInput): FormErrors {
  const errors: FormErrors = {}
  if (!form.display_name.trim()) errors.display_name = '表示名は必須です。'
  if (!form.registration_number.trim()) errors.registration_number = '登録番号は必須です。'
  if (!form.chassis_number.trim()) errors.chassis_number = '車台番号は必須です。'
  if (!form.vehicle_name.trim()) errors.vehicle_name = '車名は必須です。'
  if (!form.inspection_expiry_date) errors.inspection_expiry_date = '車検有効期間満了日は必須です。'
  return errors
}

function Field({ label, id, required, error, children }: { label: string; id: string; required?: boolean; error?: string; children: React.ReactNode }) {
  return <div className="space-y-1.5"><label htmlFor={id} className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">{label}{required && <span className="ml-1 text-red-500">*</span>}</label>{children}{error && <p className="text-xs text-red-500">{error}</p>}</div>
}

export function VehicleImportConfirmPage() {
  const { importId } = useParams()
  const navigate = useNavigate()
  const [item, setItem] = useState<VehicleImport | null>(null)
  const [form, setForm] = useState<VehicleSaveInput>(emptyForm)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [previewError, setPreviewError] = useState<string | null>(null)
  const [errors, setErrors] = useState<FormErrors>({})
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let active = true
    let objectUrl: string | null = null
    const load = async () => {
      if (!importId) { if (active) { setError('取込IDが不正です。'); setLoading(false) }; return }
      try {
        const detail = await getVehicleImport(importId)
        if (active) { setItem(detail); setForm(toForm(detail)) }
        try {
          const source = await getVehicleImportSource(importId)
          const nextObjectUrl = URL.createObjectURL(source)
          if (active) {
            objectUrl = nextObjectUrl
            setPreviewUrl(nextObjectUrl)
          } else {
            URL.revokeObjectURL(nextObjectUrl)
          }
        } catch {
          if (active) setPreviewError('原本を表示できませんでした。入力内容を確認して登録するか、取込をキャンセルしてください。')
        }
      } catch (requestError: unknown) {
        if (active) setError(getApiErrorMessage(requestError, '取込情報を取得できませんでした。'))
      } finally { if (active) setLoading(false) }
    }
    void load()
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [importId])

  const isPdf = item?.detected_media_type === 'application/pdf'
  const method = useMemo(() => item?.extraction_method === 'PDF_TEXT' ? 'PDFテキスト抽出' : 'OCR', [item])
  const patch = (next: Partial<VehicleSaveInput>) => { setForm(current => ({ ...current, ...next })); setError(null) }

  const confirm = async () => {
    if (!importId) return
    const nextErrors = validate(form)
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length) { setError('必須項目を入力してください。'); return }
    try {
      setSaving(true); setError(null)
      const vehicle = await confirmVehicleImport(importId, { ...form, display_name: form.display_name.trim(), registration_number: form.registration_number.trim(), chassis_number: form.chassis_number.trim(), vehicle_name: form.vehicle_name.trim() })
      navigate(`/vehicles/${vehicle.id}`, { state: { notice: '車両を登録しました。' } })
    } catch (requestError: unknown) { setError(getApiErrorMessage(requestError, '車両情報の登録に失敗しました。')) }
    finally { setSaving(false) }
  }

  const cancel = async () => {
    if (!importId) return
    try { setSaving(true); await cancelVehicleImport(importId); navigate('/vehicles', { state: { notice: 'ファイル取込をキャンセルしました。' } }) }
    catch (requestError: unknown) { setError(getApiErrorMessage(requestError, '取込のキャンセルに失敗しました。')) }
    finally { setSaving(false) }
  }

  return <div className="space-y-6 text-left">
    <div className="flex items-start justify-between gap-4"><div><h1 className="flex items-center gap-2 text-xl font-semibold text-zinc-900 dark:text-white"><FileText className="size-5 text-purple-500" />解析結果の確認</h1><p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">原本と照合し、必要な内容を修正してから登録します。</p></div><Button variant="ghost" href="/vehicles"><ArrowLeft className="size-4" />一覧に戻る</Button></div>
    {error && <NotificationBanner type="error" autoScroll onClose={() => setError(null)}>{error}</NotificationBanner>}
    {loading && <p className="py-10 text-center text-sm text-zinc-500">読み込み中...</p>}
    {!loading && item && <>
      {item.warnings.length > 0 && <NotificationBanner type="warning">{item.warnings.join(' ')}</NotificationBanner>}
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <Card className="rounded-lg border border-zinc-200 bg-white shadow-none dark:border-zinc-800 dark:bg-zinc-900"><p className="mb-1 text-sm font-medium text-zinc-900 dark:text-zinc-100">原本プレビュー</p><p className="mb-3 break-all text-xs text-zinc-500 dark:text-zinc-400">{item.display_filename ?? 'アップロード文書'}</p>{previewError && <p className="text-sm text-amber-700 dark:text-amber-300">{previewError}</p>}{previewUrl && (isPdf ? <iframe title="車検証原本" src={previewUrl} className="h-[640px] w-full border border-zinc-200 dark:border-zinc-700" /> : <img src={previewUrl} alt="アップロードした車検証原本" className="max-h-[640px] w-full object-contain" />)}</Card>
        <Card className="rounded-lg border border-zinc-200 bg-white shadow-none dark:border-zinc-800 dark:bg-zinc-900"><div className="mb-4 text-sm text-zinc-500 dark:text-zinc-400">抽出方式: {method}</div>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Field label="表示名" id="display_name" required error={errors.display_name}><Input id="display_name" value={form.display_name} onChange={event => patch({ display_name: event.target.value })} /></Field>
            <Field label="登録番号" id="registration_number" required error={errors.registration_number}><Input id="registration_number" value={form.registration_number} onChange={event => patch({ registration_number: event.target.value })} /></Field>
            <Field label="車台番号" id="chassis_number" required error={errors.chassis_number}><Input id="chassis_number" value={form.chassis_number} onChange={event => patch({ chassis_number: event.target.value })} /></Field>
            <Field label="車名" id="vehicle_name" required error={errors.vehicle_name}><Input id="vehicle_name" value={form.vehicle_name} onChange={event => patch({ vehicle_name: event.target.value })} /></Field>
            <Field label="型式" id="model_code"><Input id="model_code" value={form.model_code ?? ''} onChange={event => patch({ model_code: event.target.value })} /></Field>
            <Field label="初度登録年月" id="first_registration_month"><Input id="first_registration_month" type="month" value={form.first_registration_month ?? ''} onChange={event => patch({ first_registration_month: event.target.value })} /></Field>
            <Field label="車検有効期間満了日" id="inspection_expiry_date" required error={errors.inspection_expiry_date}><Input id="inspection_expiry_date" type="date" value={form.inspection_expiry_date} onChange={event => patch({ inspection_expiry_date: event.target.value })} /></Field>
            <Field label="交付年月日" id="issued_date"><Input id="issued_date" type="date" value={form.issued_date ?? ''} onChange={event => patch({ issued_date: event.target.value })} /></Field>
          </div>
          <div className="mt-5 flex flex-wrap justify-end gap-3 border-t border-zinc-200 pt-4 dark:border-zinc-800"><Button variant="outline" onClick={() => void cancel()} disabled={saving}><X className="size-4" />キャンセル</Button><Button onClick={() => void confirm()} disabled={saving}><Check className="size-4" />{saving ? '登録中...' : '確認して登録'}</Button></div>
        </Card>
      </div>
    </>}
  </div>
}
