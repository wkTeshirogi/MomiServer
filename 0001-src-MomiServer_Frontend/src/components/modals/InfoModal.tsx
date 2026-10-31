import { INFO_MODAL_ID } from './InfoModal.constants'
export { INFO_MODAL_ID } from './InfoModal.constants'
import { Modal, type ModalSize } from '@/components/ui/modal'
import { useModalState } from '@/contexts/ModalContext'
import { SendButton }     from '@/components/ui/send-button'

/**
 * 情報表示モーダル
 *
 * 呼び出し側:
 * ```tsx
 * const { openModal } = useModalManager()
 * openModal(INFO_MODAL_ID, { title: 'サーバー詳細', message: '...' })
 * ```
 */
export function InfoModal() {
  const { open, params, close } = useModalState(INFO_MODAL_ID)

  const title   = params.title   as string | undefined
  const message = params.message as string | undefined
  const size = params.size as ModalSize | undefined

  return (
    <Modal
      open={open}
      onClose={close}
      title={title ?? 'お知らせ'}
      variant="info"
      size={size ?? 'md'}
      footer={
        <SendButton
          onClick={close}
        >
          閉じる
        </SendButton>
      }
    >
      <p>{message}</p>
    </Modal>
  )
}
