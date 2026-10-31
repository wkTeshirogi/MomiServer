import { NotificationBanner } from '@/components/ui/notification-banner'
import { useMemo, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Save } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { validateCronExpression, type CronValidationResult } from '@/lib/cronSchedule'
import {
  toSaveInput,
  useBatchScheduleDetailService,
  validateBatchSchedule,
  type FieldErrors,
  type SaveBatchScheduleInput,
} from './service'

function FieldErrorMessage({ message }: { message?: string }) {
  if (!message) return null

  return (
    <p className="text-xs text-red-600 dark:text-red-300">
      {message}
    </p>
  )
}


function DetailRow({ label, value }: { label: string; value: string | number | boolean | null | undefined }) {
  return (
    <div className="grid grid-cols-[120px_1fr] gap-2 text-sm">
      <span className="text-zinc-500 dark:text-zinc-400">{label}</span>
      <span className="text-zinc-900 dark:text-zinc-100">{value === '' || value == null ? '-' : String(value)}</span>
    </div>
  )
}

function NumberInput({
  id,
  value,
  onChange,
  min,
  max,
  required = false,
}: {
  id: string
  value: number
  onChange: (value: number) => void
  min?: number
  max?: number
  required?: boolean
}) {
  return (
    <Input
      id={id}
      type="number"
      min={min}
      max={max}
      required={required}
      value={value}
      onChange={event => onChange(Number(event.target.value))}
      className="w-full"
    />
  )
}

function CronPreview({ preview }: { preview: CronValidationResult }) {
  if (!preview.valid) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700 dark:border-red-900 dark:bg-red-950/50 dark:text-red-300">
        {preview.error}
      </div>
    )
  }

  return (
    <div className="space-y-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-200">
      <div className="font-medium">{preview.description}</div>
      {preview.normalizedExpression && (
        <div className="text-emerald-700 dark:text-emerald-300">
          実行時の形式: {preview.normalizedExpression}
        </div>
      )}
      {preview.nextRuns.length > 0 && (
        <div>
          <div className="mb-1 text-emerald-700 dark:text-emerald-300">次回候補</div>
          <div className="flex flex-wrap gap-1.5">
            {preview.nextRuns.map(run => (
              <span
                key={run}
                className="rounded-full bg-white px-2 py-1 text-emerald-800 ring-1 ring-emerald-200 dark:bg-emerald-950 dark:text-emerald-100 dark:ring-emerald-800"
              >
                {run}
              </span>
            ))}
          </div>
        </div>
      )}
      {preview.nextRuns.length === 0 && (
        <div className="text-emerald-700 dark:text-emerald-300">
          1年以内の次回候補は見つかりませんでした。
        </div>
      )}
    </div>
  )
}

export function BatchScheduleDetail() {
  const { batchScheduleId } = useParams()
  const [searchParams] = useSearchParams()
  const detailId = batchScheduleId ?? searchParams.get('id') ?? undefined
  const navigate = useNavigate()
  const {
    batchSchedule,
    batchDefinitions,
    machines,
    loading,
    error,
    isNew,
    reload,
    save,
  } = useBatchScheduleDetailService(detailId)

  const initialForm = () => {
    const nextForm = toSaveInput(batchSchedule)
    if (!batchSchedule && batchDefinitions.length > 0) nextForm.batch_definition_id = batchDefinitions[0].id
    if (!batchSchedule && machines.length > 0) nextForm.target_agent_id = machines[0].id
    return nextForm
  }
  const [formSource, setFormSource] = useState({ batchSchedule, batchDefinitions, machines })
  const [form, setForm] = useState<SaveBatchScheduleInput>(initialForm)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const cronPreview = useMemo(
    () => validateCronExpression(form.cron_expression),
    [form.cron_expression]
  )

  if (formSource.batchSchedule !== batchSchedule || formSource.batchDefinitions !== batchDefinitions
    || formSource.machines !== machines) {
    setFormSource({ batchSchedule, batchDefinitions, machines })
    setForm(initialForm())
  }

  const patchForm = (patch: Partial<SaveBatchScheduleInput>) => {
    setForm(current => ({ ...current, ...patch }))
    setSaved(false)
    setFormError(null)
  }

  const handleSave = async () => {
    const errors = validateBatchSchedule(form)
    setFieldErrors(errors)

    if (Object.keys(errors).length > 0) {
      setFormError('入力内容を確認してください。')
      scrollToPageTop()
      return
    }

    setSaving(true)
    setFormError(null)

    try {
      const savedItem = await save(form)
      setForm(toSaveInput(savedItem))
      setSaved(true)
      scrollToPageTop()

      if (isNew) {
        navigate(`/master/batch/schedule/${savedItem.id}`, { replace: true })
      } else {
        await reload()
      }
    } catch {
      setFormError('バッチスケジュールの保存に失敗しました。')
      scrollToPageTop()
    } finally {
      setSaving(false)
    }
  }

  const handleBack = () => navigate('/master/batch/schedule')

  if (loading) {
    return (
      <div className="space-y-6 text-left">
        <PageHeader isNew={isNew} onBack={handleBack} />
        <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
          <div className="py-8 text-sm text-zinc-500 dark:text-zinc-400">読み込み中...</div>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6 text-left">
      <PageHeader isNew={isNew} onBack={handleBack} />

      {error && (
        <NotificationBanner type="error">
          {error}
        </NotificationBanner>
      )}
      {formError && (
        <NotificationBanner type="error" onClose={() => setFormError(null)}>
          {formError}
        </NotificationBanner>
      )}
      {saved && (
        <NotificationBanner type="success" onClose={() => setSaved(false)}>
          保存しました。
        </NotificationBanner>
      )}

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
          <h2 className="mb-4 text-lg font-semibold text-zinc-900 dark:text-white">基本設定</h2>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Field label="バッチ定義" id="batch_definition_id" error={fieldErrors.batch_definition_id} required>
              <select
                id="batch_definition_id"
                value={form.batch_definition_id}
                onChange={event => patchForm({ batch_definition_id: Number(event.target.value) })}
                required
                className="h-9 w-full rounded-lg border border-zinc-300 bg-white px-3 text-sm outline-none transition-colors focus:border-purple-500 focus:ring-1 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-900"
              >
                <option value={0}>選択してください</option>
                {batchDefinitions.map(definition => (
                  <option key={definition.id} value={definition.id}>
                    {definition.view_name || definition.batch_name} ({definition.id})
                  </option>
                ))}
              </select>
            </Field>
            <Field label="対象マシン" id="target_agent_id" error={fieldErrors.target_agent_id} required>
              <select
                id="target_agent_id"
                value={form.target_agent_id}
                onChange={event => patchForm({ target_agent_id: Number(event.target.value) })}
                required
                className="h-9 w-full rounded-lg border border-zinc-300 bg-white px-3 text-sm outline-none transition-colors focus:border-purple-500 focus:ring-1 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-900"
              >
                <option value={0}>選択してください</option>
                {machines.map(machine => (
                  <option key={machine.id} value={machine.id}>
                    {machine.name || `Machine ${machine.id}`} ({machine.id}){machine.address ? ` - ${machine.address}` : ''}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Cron式" id="cron_expression" error={fieldErrors.cron_expression} required>
              <Input
                id="cron_expression"
                value={form.cron_expression}
                onChange={event => patchForm({ cron_expression: event.target.value })}
                maxLength={255}
                required
                className="w-full"
              />
              <CronPreview preview={cronPreview} />
            </Field>
            <Field label="優先度" id="priority" error={fieldErrors.priority} required>
              <NumberInput
                id="priority"
                value={form.priority}
                min={1}
                max={9}
                required
                onChange={value => patchForm({ priority: value })}
              />
            </Field>
            <Field label="有効" id="enabled">
              <label className="flex h-9 items-center gap-2 text-sm text-zinc-700 dark:text-zinc-300">
                <input
                  id="enabled"
                  type="checkbox"
                  checked={form.enabled}
                  onChange={event => patchForm({ enabled: event.target.checked })}
                  className="rounded border-zinc-300 text-purple-600 focus:ring-purple-500"
                />
                <span>{form.enabled ? '有効' : '無効'}</span>
              </label>
            </Field>
            <Field label="休日実行" id="allow_holiday_execution">
              <label className="flex h-9 items-center gap-2 text-sm text-zinc-700 dark:text-zinc-300">
                <input
                  id="allow_holiday_execution"
                  type="checkbox"
                  checked={form.allow_holiday_execution}
                  onChange={event => patchForm({ allow_holiday_execution: event.target.checked })}
                  className="rounded border-zinc-300 text-purple-600 focus:ring-purple-500"
                />
                <span>{form.allow_holiday_execution ? '許可' : '不可'}</span>
              </label>
            </Field>
          </div>

          <h2 className="mb-4 mt-8 text-lg font-semibold text-zinc-900 dark:text-white">実行期間</h2>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Field label="開始日" id="start_date" error={fieldErrors.date_range}>
              <Input
                id="start_date"
                type="date"
                value={form.start_date}
                onChange={event => patchForm({ start_date: event.target.value })}
                className="w-full"
              />
            </Field>
            <Field label="終了日" id="end_date" error={fieldErrors.date_range}>
              <Input
                id="end_date"
                type="date"
                value={form.end_date}
                onChange={event => patchForm({ end_date: event.target.value })}
                className="w-full"
              />
            </Field>
          </div>

          <div className="mt-6 flex justify-end gap-3 border-t border-zinc-200 pt-4 dark:border-zinc-800">
            <Button variant="ghost" onClick={handleBack}>キャンセル</Button>
            <Button onClick={() => void handleSave()} disabled={saving}>
              <Save className="size-4" />
              {saving ? '保存中...' : '保存'}
            </Button>
          </div>

        </Card>

        <Card className="h-fit rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
          <h2 className="mb-4 text-lg font-semibold text-zinc-900 dark:text-white">読み取り情報</h2>
          <div className="space-y-3">
            <DetailRow label="ID" value={batchSchedule?.id} />
            <DetailRow label="作成日時" value={batchSchedule?.createdAt} />
            <DetailRow label="更新日時" value={batchSchedule?.updatedAt} />
            <DetailRow label="状態" value={form.enabled ? '有効' : '無効'} />
            <DetailRow label="対象マシン" value={machines.find(machine => machine.id === form.target_agent_id)?.name ?? form.target_agent_id} />
            <DetailRow label="Cron式" value={form.cron_expression} />
          </div>
        </Card>
      </div>
    </div>
  )
}

function scrollToPageTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function PageHeader({ isNew, onBack }: { isNew: boolean; onBack: () => void }) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
          {isNew ? 'バッチスケジュール新規作成' : 'バッチスケジュール詳細'}
        </h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          バッチスケジュール情報の確認・編集を行います。
        </p>
      </div>
      <Button variant="ghost" onClick={onBack}>一覧に戻る</Button>
    </div>
  )
}

function Field({
  label,
  id,
  error,
  required,
  children,
}: {
  label: string
  id: string
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
      <FieldErrorMessage message={error} />
    </div>
  )
}
