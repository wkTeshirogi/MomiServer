import { maxBytes, accepted } from './page.constants'
import { useRef, useState } from 'react'
import { ArrowLeft, FileUp, Upload } from 'lucide-react'
import { getApiErrorMessage } from '@/lib/apiError'
import { useNavigate } from 'react-router-dom'
import { uploadVehicleImport } from '@/api/vehicleApi'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { NotificationBanner } from '@/components/ui/notification-banner'

function validateFile(file: File | undefined): string | null {
  if (!file) return 'ファイルを選択してください。'
  if (file.size === 0) return '空のファイルは取り込めません。'
  if (file.size > maxBytes) return 'ファイルサイズは10 MiB以下にしてください。'
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!['pdf', 'png', 'jpg', 'jpeg'].includes(extension ?? '') || (file.type && !accepted.includes(file.type))) {
    return 'PDF、PNG、JPEG形式のファイルを選択してください。'
  }
  return null
}

export function VehicleImportPage() {
  const navigate = useNavigate()
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  const select = (next?: File) => {
    const validation = validateFile(next)
    setError(validation)
    setFile(validation ? null : next ?? null)
  }

  const upload = async () => {
    const validation = validateFile(file ?? undefined)
    if (validation || !file) { setError(validation); return }
    try {
      setUploading(true); setError(null)
      const result = await uploadVehicleImport(file)
      navigate(`/vehicles/import/${result.id}/confirm`)
    } catch (requestError: unknown) {
      setError(getApiErrorMessage(requestError, '解析に失敗しました。Tesseractの設定を確認するか、手動登録を利用してください。'))
    } finally { setUploading(false) }
  }

  return <div className="space-y-6 text-left">
    <div className="flex items-start justify-between gap-4">
      <div><h1 className="flex items-center gap-2 text-xl font-semibold text-zinc-900 dark:text-white"><FileUp className="size-5 text-purple-500" />PDF・画像から登録</h1>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">車検証を読み取り、確認・修正してから登録します。</p></div>
      <Button variant="ghost" href="/vehicles"><ArrowLeft className="size-4" />一覧に戻る</Button>
    </div>
    {error && <NotificationBanner type="error" autoScroll onClose={() => setError(null)}>{error}</NotificationBanner>}
    <Card className="rounded-lg border border-zinc-200 bg-white shadow-none dark:border-zinc-800 dark:bg-zinc-900">
      <input ref={inputRef} className="sr-only" type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" onChange={event => select(event.target.files?.[0])} />
      <div className="flex min-h-56 flex-col items-center justify-center gap-4 border-2 border-dashed border-zinc-300 p-6 text-center dark:border-zinc-700"
        onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); select(event.dataTransfer.files[0]) }}>
        <Upload className="size-8 text-zinc-400" />
        <p className="text-sm text-zinc-600 dark:text-zinc-300">PDF、PNG、JPEGをここへドロップ</p>
        <Button variant="outline" onClick={() => inputRef.current?.click()}>ファイルを選択</Button>
        {file && <p className="text-sm font-medium text-zinc-900 dark:text-zinc-100">{file.name} ({(file.size / 1024 / 1024).toFixed(2)} MiB)</p>}
      </div>
      <div className="mt-4 flex justify-end"><Button onClick={() => void upload()} disabled={uploading}><Upload className="size-4" />{uploading ? '解析中...' : 'アップロードして確認'}</Button></div>
    </Card>
  </div>
}
