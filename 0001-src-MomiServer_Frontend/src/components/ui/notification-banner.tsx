import { styles } from './notification-banner.constants'
import type { NotificationType } from './notification-banner.constants'
export type { NotificationType } from './notification-banner.constants'
import { useEffect, useRef, useState } from 'react'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

type NotificationBannerProps = {
  type: NotificationType
  children: string
  onClose?: () => void
  autoScroll?: boolean
  className?: string
}

/** A changed message starts a fresh notification; ordinary parent renders do not reopen it. */
export function NotificationBanner(props: NotificationBannerProps) {
  return <DismissibleNotification key={`${props.type}:${props.children}`} {...props} />
}

function DismissibleNotification({ type, children, onClose, autoScroll = false, className }: NotificationBannerProps) {
  const [dismissed, setDismissed] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (autoScroll) ref.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [autoScroll])

  if (dismissed) return null

  return (
    <div ref={ref} role={type === 'error' || type === 'warning' ? 'alert' : 'status'} aria-atomic="true"
      className={cn('flex scroll-mt-20 items-start gap-3 rounded-lg border px-4 py-3 text-sm', styles[type], className)}>
      <div className="min-w-0 flex-1 whitespace-pre-wrap break-words">{children}</div>
      <button type="button" aria-label="通知を閉じる" title="閉じる"
        className="-my-1 -mr-1 flex size-7 shrink-0 items-center justify-center rounded hover:bg-black/5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-current dark:hover:bg-white/10"
        onClick={() => { setDismissed(true); onClose?.() }}>
        <X className="size-4" aria-hidden="true" />
      </button>
    </div>
  )
}
