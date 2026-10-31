import { NotificationBanner } from '@/components/ui/notification-banner'
import { Attachments } from './Attachments'
import { useCallback, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Save, Trash2 } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  toSaveInput,
  useBatchDefinitionDetailService,
  validateBatchDefinition,
  type FieldErrors,
  type SaveBatchDefinitionInput,
} from './service'
import { BatchDefinitionConflictError } from '../List/service'

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
  value: number | null
  onChange: (value: number | null) => void
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
      value={value ?? ''}
      onChange={event => onChange(event.target.value === '' ? null : Number(event.target.value))}
      className="w-full"
    />
  )
}

export function BatchDefinitionDetail() {
  const { batchDefinitionId } = useParams()
  const [searchParams] = useSearchParams()
  const detailId = batchDefinitionId ?? searchParams.get('id') ?? undefined
  const navigate = useNavigate()
  const {
    batchDefinition,
    loading,
    error,
    isNew,
    reload,
    save,
  } = useBatchDefinitionDetailService(detailId)

  const [formSource, setFormSource] = useState(batchDefinition)
  const [form, setForm] = useState<SaveBatchDefinitionInput>(() => toSaveInput(batchDefinition))
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [attachmentNotice, setAttachmentNotice] = useState<{type: 'error' | 'success'; text: string} | null>(null)
  const reportAttachment = useCallback((type: 'error' | 'success', text: string) => { setAttachmentNotice({type, text}); scrollToPageTop() }, [])
  const [formError, setFormError] = useState<string | null>(null)
	const [conflicted, setConflicted] = useState(false)

  // Synchronize only when fetched data changes; ordinary renders preserve edits.
  if (formSource !== batchDefinition) {
    setFormSource(batchDefinition)
    setForm(toSaveInput(batchDefinition))
  }

  const patchForm = (patch: Partial<SaveBatchDefinitionInput>) => {
    setForm(current => ({ ...current, ...patch }))
    setSaved(false)
    setFormError(null)
		setConflicted(false)
  }

  const handleSave = async () => {
    const errors = validateBatchDefinition(form)
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
        navigate(`/master/batch/difinition/${savedItem.id}`, { replace: true })
      } else {
        await reload()
      }
    } catch (saveError) {
		if (saveError instanceof BatchDefinitionConflictError) {
			setConflicted(true)
			setFormError('他の操作で更新されています。入力内容を保持しています。最新データを再読込してください。')
		} else {
			setFormError('バッチ定義の保存に失敗しました。')
		}
      scrollToPageTop()
    } finally {
      setSaving(false)
    }
  }

	const handleReloadLatest = async () => {
		setConflicted(false)
		setFormError(null)
		await reload()
	}

	const handleDelete = async () => {
		if (isNew || !batchDefinition) return
		if (form.enabled) {
			setFormError('論理削除する前に定義を無効化して保存してください。')
			scrollToPageTop()
			return
		}
		if (!window.confirm('このバッチ定義を論理削除しますか？')) return

		setSaving(true)
		setFormError(null)
		try {
			await save({ ...form, enabled: false, status: 'deleted' })
			navigate('/master/batch/difinition', {
				replace: true,
				state: { notice: 'バッチ定義を削除しました。' },
			})
		} catch (deleteError) {
			if (deleteError instanceof BatchDefinitionConflictError) {
				setConflicted(true)
				setFormError('他の操作で更新されています。最新データを再読込してから削除してください。')
			} else {
				setFormError('未完了の実行要求が存在するか、バッチ定義の削除に失敗しました。')
			}
			scrollToPageTop()
		} finally {
			setSaving(false)
		}
	}

  const handleBack = () => navigate('/master/batch/difinition')

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

      {attachmentNotice && <NotificationBanner type={attachmentNotice.type} onClose={() => setAttachmentNotice(null)}>{attachmentNotice.text}</NotificationBanner>}
      {error && (
        <NotificationBanner type="error">
          {error}
        </NotificationBanner>
      )}
      {formError && (
		<div className="space-y-2">
			<NotificationBanner type="error" onClose={() => setFormError(null)}>
				{formError}
			</NotificationBanner>
			{conflicted && (
				<div className="flex justify-end">
					<Button variant="outline" size="sm" onClick={() => void handleReloadLatest()}>
						最新を再読込
					</Button>
				</div>
			)}
		</div>
      )}
      {saved && (
        <NotificationBanner type="success" onClose={() => setSaved(false)}>
          保存しました。
        </NotificationBanner>
      )}

      <Attachments definitionId={isNew ? undefined : Number(detailId)} notice={reportAttachment} />
      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
          <h2 className="mb-4 text-lg font-semibold text-zinc-900 dark:text-white">基本設定</h2>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Field label="バッチ名" id="batch_name" error={fieldErrors.batch_name} required>
              <Input
                id="batch_name"
                value={form.batch_name}
                onChange={event => patchForm({ batch_name: event.target.value })}
                maxLength={255}
                required
                className="w-full"
              />
            </Field>
            <Field label="表示名" id="view_name" error={fieldErrors.view_name} required>
              <Input
                id="view_name"
                value={form.view_name}
                onChange={event => patchForm({ view_name: event.target.value })}
                maxLength={255}
                required
                className="w-full"
              />
            </Field>
            <Field label="カテゴリ" id="category" error={fieldErrors.category} required>
              <Input
                id="category"
                value={form.category}
                onChange={event => patchForm({ category: event.target.value })}
                maxLength={50}
                required
                className="w-full"
              />
            </Field>
            <Field label="実行種別" id="execution_type" error={fieldErrors.execution_type} required>
              <Input
                id="execution_type"
                value={form.execution_type}
                onChange={event => patchForm({ execution_type: event.target.value })}
                maxLength={50}
                required
                className="w-full"
              />
            </Field>
            <Field label="実行コマンド" id="execution_command" error={fieldErrors.execution_command} required>
              <Input
                id="execution_command"
                value={form.execution_command}
                onChange={event => patchForm({ execution_command: event.target.value })}
                maxLength={1000}
                required
                className="w-full"
              />
            </Field>
            <Field label="タグ" id="tags">
              <Input
                id="tags"
                value={form.tags}
                onChange={event => patchForm({ tags: event.target.value })}
                maxLength={500}
                className="w-full"
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
          </div>

          <h2 className="mb-4 mt-8 text-lg font-semibold text-zinc-900 dark:text-white">実行設定</h2>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
            <Field label="タイムアウト秒" id="timeout_seconds" error={fieldErrors.timeout_seconds} required>
              <NumberInput
                id="timeout_seconds"
                value={form.timeout_seconds}
                min={0}
                required
                onChange={value => patchForm({ timeout_seconds: value ?? 0 })}
              />
            </Field>
            <Field label="最大リトライ回数" id="max_retry_count" error={fieldErrors.max_retry_count} required>
              <NumberInput
                id="max_retry_count"
                value={form.max_retry_count}
                min={0}
                max={9}
                required
                onChange={value => patchForm({ max_retry_count: value ?? 0 })}
              />
            </Field>
            <Field label="リトライ間隔秒" id="retry_interval_seconds">
              <NumberInput
                id="retry_interval_seconds"
                value={form.retry_interval_seconds}
                min={0}
                onChange={value => patchForm({ retry_interval_seconds: value })}
              />
            </Field>
            <Field label="優先度" id="priority" error={fieldErrors.priority} required>
              <NumberInput
                id="priority"
                value={form.priority}
                min={1}
                max={9}
                required
                onChange={value => patchForm({ priority: value ?? 5 })}
              />
            </Field>
            <Field label="実行ユーザー" id="execution_user">
              <Input
                id="execution_user"
                value={form.execution_user}
                onChange={event => patchForm({ execution_user: event.target.value })}
                maxLength={255}
                className="w-full"
              />
            </Field>
            <Field label="必要ロール" id="required_role">
              <Input
                id="required_role"
                value={form.required_role}
                onChange={event => patchForm({ required_role: event.target.value })}
                maxLength={50}
                className="w-full"
              />
            </Field>
            <Field label="作業ディレクトリ" id="working_directory">
              <p className="text-xs text-zinc-500">Agentの新規実行は、この値にかかわらず実行IDごとの専用ディレクトリを使用します。</p>
              <Input
                id="working_directory"
                value={form.working_directory}
                onChange={event => patchForm({ working_directory: event.target.value })}
                maxLength={500}
                className="w-full"
              />
            </Field>
            <Field label="並列実行" id="allow_parallel_execution">
              <label className="flex h-9 items-center gap-2 text-sm text-zinc-700 dark:text-zinc-300">
                <input
                  id="allow_parallel_execution"
                  type="checkbox"
                  checked={form.allow_parallel_execution}
                  onChange={event => patchForm({ allow_parallel_execution: event.target.checked })}
                  className="rounded border-zinc-300 text-purple-600 focus:ring-purple-500"
                />
                <span>{form.allow_parallel_execution ? '許可' : '不可'}</span>
              </label>
            </Field>
          </div>

          <h2 className="mb-4 mt-8 text-lg font-semibold text-zinc-900 dark:text-white">ログ・通知設定</h2>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Field label="ログ保存期限" id="log_retention_date" error={fieldErrors.log_retention_date} required>
              <Input
                id="log_retention_date"
                type="date"
                value={form.log_retention_date}
                onChange={event => patchForm({ log_retention_date: event.target.value })}
                required
                className="w-full"
              />
            </Field>
            <Field label="ログパス" id="log_path" error={fieldErrors.log_path} required>
              <Input
                id="log_path"
                value={form.log_path}
                onChange={event => patchForm({ log_path: event.target.value })}
                maxLength={500}
                required
                className="w-full"
              />
            </Field>
            <Field label="成功通知" id="success_notification" error={fieldErrors.success_notification} required>
              <Input
                id="success_notification"
                value={form.success_notification}
                onChange={event => patchForm({ success_notification: event.target.value })}
                maxLength={50}
                required
                className="w-full"
              />
            </Field>
            <Field label="失敗通知" id="failure_notification" error={fieldErrors.failure_notification} required>
              <Input
                id="failure_notification"
                value={form.failure_notification}
                onChange={event => patchForm({ failure_notification: event.target.value })}
                maxLength={50}
                required
                className="w-full"
              />
            </Field>
          </div>

          <div className="mt-4 space-y-1.5">
            <label htmlFor="description" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
              説明
            </label>
            <textarea
              id="description"
              value={form.description}
              onChange={event => patchForm({ description: event.target.value })}
              className="min-h-[96px] w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm outline-none transition-colors focus:border-purple-500 focus:ring-1 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-900"
            />
          </div>

          <div className="mt-6 flex justify-end gap-3 border-t border-zinc-200 pt-4 dark:border-zinc-800">
			{!isNew && (
				<Button variant="outline" onClick={() => void handleDelete()} disabled={saving || form.enabled}>
					<Trash2 className="size-4" />
					削除
				</Button>
			)}
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
            <DetailRow label="ID" value={batchDefinition?.id} />
            <DetailRow label="作成日時" value={batchDefinition?.createdAt} />
            <DetailRow label="更新日時" value={batchDefinition?.updatedAt} />
			<DetailRow label="更新番号" value={batchDefinition?.version} />
            <DetailRow label="状態" value={form.enabled ? '有効' : '無効'} />
            <DetailRow label="カテゴリ" value={form.category} />
            <DetailRow label="実行種別" value={form.execution_type} />
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
          {isNew ? 'バッチ定義新規作成' : 'バッチ定義詳細'}
        </h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          バッチ定義情報の確認・編集を行います。
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
