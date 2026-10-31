import { statusOptions, emptyMachine } from './page.constants'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { registerDevice } from '@/generated/sdk.gen'
import { useMachineMasterService, type MachineMasterItem } from './service'
import { getMachineStatusLabel } from '../status'
import { AgentRegistration } from './AgentRegistration'

type MachineFieldErrors = Partial<Record<'devicename' | 'address' | 'status', string>>

function MachineMasterForm({
  machine,
  onBack,
  onReload,
  onNotify,
}: {
  machine: MachineMasterItem
  onBack: () => void
  onReload: () => Promise<void>
  onNotify: (notification: PageNotification) => void
}) {
  const navigate = useNavigate()
  const [formSource, setFormSource] = useState(machine)
  const [devicename, setDevicename] = useState(machine.devicename)
  const [address, setAddress] = useState(machine.address)
  const [location, setLocation] = useState(machine.location)
  const [mqttClientId, setMqttClientId] = useState(machine.mqttClientId)
  const [note, setNote] = useState(machine.note)
  const [status, setStatus] = useState(machine.status)
  const [saving, setSaving] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<MachineFieldErrors>({})

  if (formSource !== machine) {
    setFormSource(machine)
    setDevicename(machine.devicename)
    setAddress(machine.address)
    setLocation(machine.location)
    setMqttClientId(machine.mqttClientId)
    setNote(machine.note)
    setStatus(machine.status)
    setFieldErrors({})
  }

  const handleSave = async () => {
    const errors: MachineFieldErrors = {}
    if (!devicename.trim()) errors.devicename = '端末名は必須です。'
    if (!address.trim()) errors.address = 'アドレスは必須です。'
    else if (address.length < 7 || address.length > 15) {
      errors.address = 'アドレスは7文字以上15文字以内で入力してください。'
    }
    if (!status.trim()) errors.status = '状態を選択してください。'

    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      onNotify({ type: 'error', message: '入力内容を確認してください。' })
      scrollToPageTop()
      return
    }

    setSaving(true)
    onNotify(null)

    try {
      const response = await registerDevice({
        body: {
          id: machine.id > 0 ? machine.id : undefined,
          deviceName: devicename,
          address,
          status,
          location: location || undefined,
          mqtt_client_id: mqttClientId || undefined,
          note: note || undefined,
        },
      })

      if (response.error) throw response.error

      onNotify({ type: 'success', message: '保存しました。' })
      scrollToPageTop()
      if (machine.id <= 0 && response.data?.id) {
        navigate(`/master/machine/${response.data.id}`, { replace: true })
      } else {
        await onReload()
      }
    } catch {
      onNotify({ type: 'error', message: '端末マスタの保存に失敗しました。' })
      scrollToPageTop()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_22rem]">
      <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
        <div className="max-w-2xl space-y-4">
          <div className="space-y-1.5">
            <label htmlFor="devicename" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
              端末名
            </label>
            <Input
              id="devicename"
              value={devicename}
              onChange={event => {
                setDevicename(event.target.value)
                setFieldErrors(current => ({ ...current, devicename: undefined }))
              }}
              aria-invalid={Boolean(fieldErrors.devicename)}
              placeholder="main-server"
            />
            {fieldErrors.devicename && <p className="text-xs text-red-500">{fieldErrors.devicename}</p>}
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <label htmlFor="address" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                アドレス
              </label>
              <Input
                id="address"
                value={address}
                onChange={event => {
                  setAddress(event.target.value)
                  setFieldErrors(current => ({ ...current, address: undefined }))
                }}
                aria-invalid={Boolean(fieldErrors.address)}
                placeholder="192.168.0.10"
              />
              {fieldErrors.address && <p className="text-xs text-red-500">{fieldErrors.address}</p>}
            </div>

            <div className="space-y-1.5">
              <label htmlFor="location" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                設置場所
              </label>
              <Input
                id="location"
                value={location}
                onChange={event => setLocation(event.target.value)}
                placeholder="server-room"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <label htmlFor="mqttClientId" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                MQTT Client ID
              </label>
              <Input
                id="mqttClientId"
                value={mqttClientId}
                onChange={event => setMqttClientId(event.target.value)}
                placeholder="machine-main"
              />
            </div>

            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
                状態
              </label>
              <Select
                value={statusOptions.find(option => option.value === status) ?? { value: status, label: getMachineStatusLabel(status) }}
                options={statusOptions}
                onChange={option => {
                  if (!option) return
                  setStatus(option.value)
                  setFieldErrors(current => ({ ...current, status: undefined }))
                }}
                placeholder="状態を選択"
              />
              {fieldErrors.status && <p className="text-xs text-red-500">{fieldErrors.status}</p>}
            </div>
          </div>

          <div className="space-y-1.5">
            <label htmlFor="note" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
              メモ
            </label>
            <textarea
              id="note"
              value={note}
              onChange={event => setNote(event.target.value)}
              rows={4}
              className="w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 outline-none transition-colors placeholder:text-zinc-400 focus:border-purple-500 focus:ring-1 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
              placeholder="端末に関するメモ"
            />
          </div>

          <div className="flex justify-end gap-3 border-t border-zinc-200 pt-4 dark:border-zinc-800">
            <Button variant="ghost" onClick={onBack}>
              キャンセル
            </Button>
            <Button onClick={() => void handleSave()} disabled={saving}>
              {saving ? '保存中...' : '保存'}
            </Button>
          </div>
        </div>
      </Card>
    </div>
  )
}

type PageNotification = { type: 'error' | 'success'; message: string } | null


function scrollToPageTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

export function MachineMasterDetail() {
  const { machineID, machineId } = useParams()
  const navigate = useNavigate()
  const routeId = machineID ?? machineId
  const isNew = routeId === 'new'
  const parsedMachineId = Number(routeId)
  const { machine, loading, error, reload } = useMachineMasterService(!isNew && Number.isFinite(parsedMachineId) ? parsedMachineId : undefined)
  const displayMachine = isNew ? emptyMachine : machine
  const [notification, setNotification] = useState<PageNotification>(null)

  const handleBack = () => navigate('/master/machine')

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            端末マスタ詳細
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            端末の接続情報とメタ情報を確認できます。
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
      {notification && <NotificationBanner type={notification.type} onClose={() => setNotification(null)}>{notification.message}</NotificationBanner>}

      {!isNew && loading && (
        <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
          <p className="text-sm text-zinc-500 dark:text-zinc-400">読み込み中...</p>
        </Card>
      )}

      {(isNew || !loading) && displayMachine && (
        <MachineMasterForm machine={displayMachine} onBack={handleBack} onReload={reload} onNotify={setNotification} />
      )}
      {!isNew && !loading && displayMachine && (
        <AgentRegistration key={displayMachine.id} deviceId={displayMachine.id} onNotify={setNotification} />
      )}
    </div>
  )
}
