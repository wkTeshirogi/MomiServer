import { emptyForm, usageOptions, purposeOptions, renewalYearOptions, defaultRenewalYears } from './page.constants'
import { renewalExpiry } from './page.utils'
import { useEffect, useMemo, useState } from 'react'
import { Archive, ArrowLeft, CalendarCheck, Save } from 'lucide-react'
import { getApiErrorStatus } from '@/lib/apiError'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import {
  archiveVehicle,
  createVehicle,
  getVehicle,
  getVehicleHistory,
  updateVehicle,
  renewVehicleInspection,
  type Vehicle,
  type VehicleInspectionHistory,
  type VehicleSaveInput,
} from '@/api/vehicleApi'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { Modal } from '@/components/ui/modal'
import { useAuth } from '@/contexts/AuthContext'
import { hasAnyRole } from '@/lib/authz'
import { formatJapanDateTime } from '@/lib/utils'
import { remainingDaysLabel, vehicleStatusClass, vehicleStatusLabel } from '../vehicleDisplay'

type FormErrors = Partial<Record<keyof VehicleSaveInput, string>>

function toForm(vehicle: Vehicle): VehicleSaveInput {
  return {
    display_name: vehicle.display_name,
    registration_number: vehicle.registration_number,
    chassis_number: vehicle.chassis_number,
    vehicle_identification_code: vehicle.vehicle_identification_code ?? '',
    vehicle_name: vehicle.vehicle_name,
    model_code: vehicle.model_code ?? '',
    first_registration_month: vehicle.first_registration_month ?? '',
    inspection_expiry_date: vehicle.inspection_expiry_date,
    issued_date: vehicle.issued_date ?? '',
    usage_category: vehicle.usage_category ?? '',
    purpose: vehicle.purpose ?? '',
    notes: vehicle.notes ?? '',
  }
}

function normalizeForm(form: VehicleSaveInput): VehicleSaveInput {
  const optional = (value?: string) => value?.trim() || undefined
  return {
    display_name: form.display_name.trim(),
    registration_number: form.registration_number.trim(),
    chassis_number: form.chassis_number.trim(),
    vehicle_name: form.vehicle_name.trim(),
    inspection_expiry_date: form.inspection_expiry_date,
    vehicle_identification_code: optional(form.vehicle_identification_code),
    model_code: optional(form.model_code),
    first_registration_month: optional(form.first_registration_month),
    issued_date: optional(form.issued_date),
    usage_category: optional(form.usage_category),
    purpose: optional(form.purpose),
    notes: optional(form.notes),
  }
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

function Field({ label, id, required = false, error, children }: {
  label: string
  id: string
  required?: boolean
  error?: string
  children: React.ReactNode
}) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
        {label}{required && <span className="ml-1 text-red-500">*</span>}
      </label>
      {children}
      {error && <p className="text-xs text-red-500">{error}</p>}
    </div>
  )
}

function historyLabel(changeType: VehicleInspectionHistory['change_type']) {
  switch (changeType) {
    case 'CREATED': return '登録'
    case 'EXPIRY_UPDATED': return '期限更新'
    case 'ARCHIVED': return '管理終了'
  }
}

export function VehicleDetailPage() {
  const { vehicleId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const { user } = useAuth()
  const isNew = !vehicleId || vehicleId === 'new'
  const numericId = Number(vehicleId)
  const canEdit = hasAnyRole(user, ['ADMIN', 'OPERATOR'])
  const isAdmin = hasAnyRole(user, ['ADMIN'])
  const [vehicle, setVehicle] = useState<Vehicle | null>(null)
  const [history, setHistory] = useState<VehicleInspectionHistory[]>([])
  const [form, setForm] = useState<VehicleSaveInput>(emptyForm)
  const [errors, setErrors] = useState<FormErrors>({})
  const invalidId = !isNew && !Number.isFinite(numericId)
  const [loading, setLoading] = useState(!isNew && !invalidId)
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState<string | null>((location.state as { notice?: string } | null)?.notice ?? null)
  const [error, setError] = useState<string | null>(null)
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [renewalOpen, setRenewalOpen] = useState(false)
  const [renewalYears, setRenewalYears] = useState<number>(defaultRenewalYears)
  const [renewalDate, setRenewalDate] = useState('')
  const [renewalIssuedDate, setRenewalIssuedDate] = useState('')
  const [renewalError, setRenewalError] = useState<string | null>(null)
  const archived = vehicle?.status === 'ARCHIVED'
  const editable = canEdit && !archived

  useEffect(() => {
    if (isNew || invalidId) return

    let ignore = false
    const load = async () => {
      try {
        setLoading(true)
        setError(null)
        setHistoryError(null)
        const vehicleResult = await getVehicle(numericId)
        if (!ignore) {
          setVehicle(vehicleResult)
          setForm(toForm(vehicleResult))
        }
        try {
          const historyResult = await getVehicleHistory(numericId)
          if (!ignore) setHistory(historyResult)
        } catch {
          if (!ignore) setHistoryError('車検期限更新履歴の取得に失敗しました。')
        }
      } catch {
        if (!ignore) setError('車両情報の取得に失敗しました。')
      } finally {
        if (!ignore) setLoading(false)
      }
    }
    void load()
    return () => { ignore = true }
  }, [invalidId, isNew, numericId])

  const title = useMemo(() => isNew ? '車両新規登録' : '車両管理詳細', [isNew])

  const patch = (next: Partial<VehicleSaveInput>) => {
    setForm(current => ({ ...current, ...next }))
    setNotice(null)
    setError(null)
  }

  const handleSave = async () => {
    const validationErrors = validate(form)
    setErrors(validationErrors)
    if (Object.keys(validationErrors).length > 0) {
      setError('未入力の必須項目があります。')
      window.scrollTo({ top: 0, behavior: 'smooth' })
      return
    }

    try {
      setSaving(true)
      setError(null)
      const input = normalizeForm(form)
      const saved = isNew ? await createVehicle(input) : await updateVehicle(numericId, input)
      if (isNew) {
        navigate(`/vehicles/${saved.id}`, { replace: true, state: { notice: '車両を登録しました。' } })
      } else {
        setVehicle(saved)
        setForm(toForm(saved))
        setNotice('車両情報を保存しました。')
        setHistoryError(null)
        try {
          setHistory(await getVehicleHistory(saved.id))
        } catch {
          setHistoryError('保存は完了しましたが、車検期限更新履歴を再取得できませんでした。')
        }
        window.scrollTo({ top: 0, behavior: 'smooth' })
      }
    } catch {
      setError('車両情報の保存に失敗しました。入力内容と重複候補を確認してください。')
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } finally {
      setSaving(false)
    }
  }

  const handleArchive = async () => {
    if (!vehicle || !window.confirm('この車両を管理対象外にしますか？')) return
    try {
      setSaving(true)
      const archivedVehicle = await archiveVehicle(vehicle.id)
      setVehicle(archivedVehicle)
      setNotice('車両を管理対象外にしました。')
      setHistoryError(null)
      try {
        setHistory(await getVehicleHistory(vehicle.id))
      } catch {
        setHistoryError('管理終了は完了しましたが、車検期限更新履歴を再取得できませんでした。')
      }
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch {
      setError('車両の管理終了に失敗しました。')
    } finally {
      setSaving(false)
    }
  }

  const openRenewal = () => {
    if (!vehicle || !editable || saving) return
    setRenewalYears(defaultRenewalYears)
    setRenewalDate(renewalExpiry(vehicle.inspection_expiry_date, defaultRenewalYears))
    setRenewalIssuedDate(vehicle.issued_date ?? '')
    setRenewalError(null)
    setRenewalOpen(true)
  }

  const handleRenewal = async () => {
    if (!vehicle || saving) return
    if (!renewalDate || renewalDate <= vehicle.inspection_expiry_date) {
      setRenewalError('新しい満了日は現在の満了日より後の日付を指定してください。')
      return
    }
    setSaving(true)
    setRenewalError(null)
    try {
      const saved = await renewVehicleInspection(vehicle.id, {
        previous_expiry_date: vehicle.inspection_expiry_date,
        inspection_expiry_date: renewalDate,
        issued_date: renewalIssuedDate || undefined,
      })
      setVehicle(saved)
      setForm(current => ({ ...current, inspection_expiry_date: saved.inspection_expiry_date, issued_date: saved.issued_date ?? '' }))
      setErrors(current => ({ ...current, inspection_expiry_date: undefined, issued_date: undefined }))
      setRenewalOpen(false)
      setError(null)
      setNotice('車検情報を更新しました。')
      setHistoryError(null)
      try {
        setHistory(await getVehicleHistory(saved.id))
      } catch {
        setHistoryError('車検更新は完了しましたが、車検期限更新履歴を再取得できませんでした。')
      }
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (cause) {
      setRenewalError(getApiErrorStatus(cause) === 409
        ? '車検満了日が変更されています。画面を再読み込みしてください。'
        : '車検情報の更新に失敗しました。入力内容を確認してください。')
    } finally {
      setSaving(false)
    }
  }

  const inputProps = (field: keyof VehicleSaveInput) => ({
    id: field,
    value: form[field] ?? '',
    disabled: !editable || saving,
    onChange: (event: React.ChangeEvent<HTMLInputElement>) => patch({ [field]: event.target.value }),
  })

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">{title}</h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            車両識別情報と車検有効期間満了日を管理します。
          </p>
        </div>
        <Button variant="ghost" href="/vehicles"><ArrowLeft className="size-4" />一覧に戻る</Button>
      </div>

      {(error || invalidId) && (
        <NotificationBanner type="error" autoScroll>
          {error ?? '車両IDが不正です。'}
        </NotificationBanner>
      )}
      {historyError && (
        <NotificationBanner type="error" onClose={() => setHistoryError(null)}>
          {historyError}
        </NotificationBanner>
      )}
      {notice && <NotificationBanner type="success" onClose={() => setNotice(null)}>{notice}</NotificationBanner>}

      {loading && <p className="py-10 text-center text-sm text-zinc-500">読み込み中...</p>}

      {!loading && (
        <>
          {vehicle && (
            <div className="flex flex-wrap items-center gap-3 border-y border-zinc-200 py-3 dark:border-zinc-800">
              <span className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${vehicleStatusClass(vehicle.status)}`}>
                {vehicleStatusLabel(vehicle.status)}
              </span>
              <span className="text-sm text-zinc-600 dark:text-zinc-300">{remainingDaysLabel(vehicle.days_remaining)}</span>
              <span className="text-sm text-zinc-500">満了日 {vehicle.inspection_expiry_date}</span>
              {editable && (
                <Button onClick={openRenewal} disabled={saving}>
                  <CalendarCheck className="size-4" />車検更新
                </Button>
              )}
            </div>
          )}

          <section aria-labelledby="vehicle-form-heading">
            <h2 id="vehicle-form-heading" className="mb-4 text-base font-semibold text-zinc-900 dark:text-zinc-100">車両情報</h2>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
              <Field label="表示名" id="display_name" required error={errors.display_name}>
                <Input {...inputProps('display_name')} placeholder="自家用車" />
              </Field>
              <Field label="登録番号" id="registration_number" required error={errors.registration_number}>
                <Input {...inputProps('registration_number')} placeholder="品川 300 あ 12-34" />
              </Field>
              <Field label="車台番号" id="chassis_number" required error={errors.chassis_number}>
                <Input {...inputProps('chassis_number')} />
              </Field>
              <Field label="車両識別符号" id="vehicle_identification_code">
                <Input {...inputProps('vehicle_identification_code')} />
              </Field>
              <Field label="車名" id="vehicle_name" required error={errors.vehicle_name}>
                <Input {...inputProps('vehicle_name')} />
              </Field>
              <Field label="型式" id="model_code"><Input {...inputProps('model_code')} /></Field>
              <Field label="初度登録年月" id="first_registration_month">
                <Input {...inputProps('first_registration_month')} type="month" />
              </Field>
              <Field label="車検有効期間満了日" id="inspection_expiry_date" required error={errors.inspection_expiry_date}>
                <Input {...inputProps('inspection_expiry_date')} type="date" />
              </Field>
              <Field label="交付年月日" id="issued_date"><Input {...inputProps('issued_date')} type="date" /></Field>
              <Field label="用途区分" id="usage_category">
                <select
                  id="usage_category"
                  value={form.usage_category ?? ''}
                  disabled={!editable || saving}
                  onChange={event => patch({ usage_category: event.target.value })}
                  className="h-9 w-full rounded-lg border border-zinc-300 bg-white px-3 text-sm text-zinc-900 disabled:opacity-60 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                >
                  {usageOptions.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
                </select>
              </Field>
              <Field label="用途" id="purpose">
                <select
                  id="purpose"
                  value={form.purpose ?? ''}
                  disabled={!editable || saving}
                  onChange={event => patch({ purpose: event.target.value })}
                  className="h-9 w-full rounded-lg border border-zinc-300 bg-white px-3 text-sm text-zinc-900 disabled:opacity-60 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                >
                  {purposeOptions.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
                </select>
              </Field>
            </div>

            <div className="mt-4">
              <Field label="備考" id="notes">
                <textarea
                  id="notes"
                  rows={4}
                  value={form.notes ?? ''}
                  disabled={!editable || saving}
                  onChange={event => patch({ notes: event.target.value })}
                  className="w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 outline-none focus:border-purple-500 focus:ring-1 focus:ring-purple-500 disabled:opacity-60 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
                />
              </Field>
            </div>

            {editable && (
              <div className="mt-5 flex flex-wrap justify-end gap-3 border-t border-zinc-200 pt-4 dark:border-zinc-800">
                {isAdmin && vehicle && !archived && (
                  <Button variant="destructive" onClick={() => void handleArchive()} disabled={saving}>
                    <Archive className="size-4" />管理終了
                  </Button>
                )}
                <Button onClick={() => void handleSave()} disabled={saving}>
                  <Save className="size-4" />{saving ? '保存中...' : '保存'}
                </Button>
              </div>
            )}
          </section>

          {vehicle && (
            <section aria-labelledby="vehicle-history-heading" className="border-t border-zinc-200 pt-6 dark:border-zinc-800">
              <h2 id="vehicle-history-heading" className="mb-4 text-base font-semibold text-zinc-900 dark:text-zinc-100">車検期限更新履歴</h2>
              <Card className="overflow-hidden rounded-lg border border-zinc-200 bg-white py-0 shadow-none dark:border-zinc-800 dark:bg-zinc-900">
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[720px]">
                    <thead className="bg-zinc-50 dark:bg-zinc-800/50">
                      <tr>
                        {['操作', '変更前', '変更後', '更新者', '更新日時'].map(label => (
                          <th key={label} className="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400">{label}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
                      {history.length === 0 && <tr><td colSpan={5} className="px-4 py-8 text-center text-sm text-zinc-500">履歴はありません。</td></tr>}
                      {history.map(item => (
                        <tr key={item.id}>
                          <td className="px-4 py-3 text-sm font-medium text-zinc-900 dark:text-zinc-100">{historyLabel(item.change_type)}</td>
                          <td className="px-4 py-3 text-sm text-zinc-600 dark:text-zinc-300">{item.previous_expiry_date || '-'}</td>
                          <td className="px-4 py-3 text-sm text-zinc-600 dark:text-zinc-300">{item.new_expiry_date || '-'}</td>
                          <td className="px-4 py-3 text-sm text-zinc-600 dark:text-zinc-300">{item.changed_by}</td>
                          <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">{formatJapanDateTime(item.changedAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Card>
            </section>
          )}
        </>
      )}
      <Modal
        open={renewalOpen}
        title="車検更新"
        onClose={() => { if (!saving) setRenewalOpen(false) }}
        closeOnEsc={!saving}
        closeOnOverlay={!saving}
        hideCloseButton={saving}
        footer={<>
          <Button variant="ghost" disabled={saving} onClick={() => setRenewalOpen(false)}>キャンセル</Button>
          <Button disabled={saving} onClick={() => void handleRenewal()}>
            <Save className="size-4" />{saving ? '更新中...' : '更新する'}
          </Button>
        </>}
      >
        <div className="max-h-[65dvh] space-y-4 overflow-y-auto">
          {renewalError && <NotificationBanner type="error">{renewalError}</NotificationBanner>}
          <Field label="現在の満了日" id="renewal-base">
            <Input id="renewal-base" type="date" value={vehicle?.inspection_expiry_date ?? ''} readOnly />
          </Field>
          <Field label="更新年数" id="renewal-years">
            <select id="renewal-years" value={renewalYears} disabled={saving}
              className="h-9 w-full rounded-lg border border-zinc-300 bg-white px-3 text-sm text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
              onChange={event => {
                const years = Number(event.target.value)
                setRenewalYears(years)
                setRenewalDate(renewalExpiry(vehicle?.inspection_expiry_date ?? '', years))
                setRenewalError(null)
              }}>
              {renewalYearOptions.map(years => <option key={years} value={years}>{years}年</option>)}
            </select>
          </Field>
          <Field label="新しい満了日" id="renewal-expiry" required>
            <Input id="renewal-expiry" type="date" value={renewalDate} disabled={saving} required
              onChange={event => { setRenewalDate(event.target.value); setRenewalError(null) }} />
          </Field>
          <Field label="交付年月日" id="renewal-issued">
            <Input id="renewal-issued" type="date" value={renewalIssuedDate} disabled={saving}
              onChange={event => setRenewalIssuedDate(event.target.value)} />
          </Field>
        </div>
      </Modal>
    </div>
  )
}
