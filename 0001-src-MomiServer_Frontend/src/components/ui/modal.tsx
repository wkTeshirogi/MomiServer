import { variantConfig, sizeClass } from './modal.constants'
import type { ModalVariant, ModalSize } from './modal.constants'
export type { ModalVariant, ModalSize } from './modal.constants'
import { useEffect, useCallback, useState, type ReactNode } from 'react'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

export type ModalProps = {
  /** 表示フラグ */
  open: boolean
  /** 閉じる処理 */
  onClose: () => void
  /** モーダルのタイトル */
  title?: string
  /** タイトル横のサブテキスト */
  description?: string
  /** モーダルのバリアント */
  variant?: ModalVariant
  /** モーダルのサイズ */
  size?: ModalSize
  /** フッターのコンテンツ（ボタン等） */
  footer?: ReactNode
  /** オーバーレイクリックで閉じるか */
  closeOnOverlay?: boolean
  /** Escキーで閉じるか */
  closeOnEsc?: boolean
  /** × ボタンを非表示にするか */
  hideCloseButton?: boolean
  children: ReactNode
}

// ────────────────────────────────────────────────
// Modal 本体
// ────────────────────────────────────────────────
export function Modal({
  open,
  onClose,
  title,
  description,
  variant = 'default',
  size = 'md',
  footer,
  closeOnOverlay = true,
  closeOnEsc = true,
  hideCloseButton = false,
  children,
}: ModalProps) {
  // Esc キーで閉じる
  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    if (closeOnEsc && e.key === 'Escape') onClose()
  }, [closeOnEsc, onClose])

  useEffect(() => {
    if (!open) return
    document.addEventListener('keydown', handleKeyDown)
    // スクロール固定
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = ''
    }
  }, [open, handleKeyDown])

  if (!open) return null

  const { icon, headerClass, iconClass } = variantConfig[variant]

  return (
    // オーバーレイ
    <div
      className={cn(
        'fixed inset-0 z-50 flex items-center justify-center p-4',
        'backdrop-blur-sm',
        'animate-in fade-in duration-150',
      )}
      onClick={closeOnOverlay ? onClose : undefined}
      aria-modal="true"
      role="dialog"
      aria-labelledby={title ? 'modal-title' : undefined}
    >
      {/* モーダル本体（クリックの伝播を止める） */}
      <div
        className={cn(
          'relative w-full rounded-xl border shadow-xl',
          'bg-white border-zinc-200',
          'dark:bg-zinc-900 dark:border-zinc-800',
          'animate-in zoom-in-95 fade-in duration-150',
          sizeClass[size],
        )}
        onClick={e => e.stopPropagation()}
      >
        {/* ヘッダー */}
        {(title || !hideCloseButton) && (
          <div className={cn(
            'flex items-start gap-3 p-5',
            footer || children ? 'border-b border-zinc-100 dark:border-zinc-800' : '',
          )}>
            {/* バリアントアイコン */}
            {icon && (
              <div className={cn('flex-shrink-0 rounded-lg p-1.5', iconClass)}>
                {icon}
              </div>
            )}

            {/* タイトル・説明 */}
            {title && (
              <div className="flex-1 min-w-0">
                <h2
                  id="modal-title"
                  className={cn(
                    'text-sm font-semibold text-zinc-900 dark:text-zinc-100',
                    headerClass,
                  )}
                >
                  {title}
                </h2>
                {description && (
                  <p className="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">
                    {description}
                  </p>
                )}
              </div>
            )}

            {/* 閉じるボタン */}
            {!hideCloseButton && (
              <button
                onClick={onClose}
                className={cn(
                  'ml-auto flex-shrink-0 rounded-lg p-1 transition-colors',
                  'text-zinc-400 hover:bg-zinc-100 hover:text-zinc-600',
                  'dark:text-zinc-500 dark:hover:bg-zinc-800 dark:hover:text-zinc-300',
                )}
                aria-label="閉じる"
              >
                <X size={16} />
              </button>
            )}
          </div>
        )}

        {/* コンテンツ */}
        <div className="p-5 text-sm text-zinc-700 dark:text-zinc-300">
          {children}
        </div>

        {/* フッター */}
        {footer && (
          <div className={cn(
            'flex items-center justify-end gap-2 p-4',
            'border-t border-zinc-100 dark:border-zinc-800',
          )}>
            {footer}
          </div>
        )}
      </div>
    </div>
  )
}

// ────────────────────────────────────────────────
// useModal — 開閉状態を管理するカスタムフック
// ────────────────────────────────────────────────
export function useModal(initialOpen = false) {
  const [open, setOpen] = useState(initialOpen)
  return {
    open,
    onOpen:  () => setOpen(true),
    onClose: () => setOpen(false),
    toggle:  () => setOpen(o => !o),
  }
}
