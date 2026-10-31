import { DELETE_MODAL_ID } from './DeleteModal.constants'
export { DELETE_MODAL_ID } from './DeleteModal.constants'
import { Modal } from '@/components/ui/modal'
import { useModalState } from '@/contexts/ModalContext'
import { SendButton }     from '@/components/ui/send-button'

/**
 * 削除確認モーダル
 *
 * 呼び出し側:
 * ```tsx
 * const { openModal } = useModalManager()
 * openModal(DELETE_MODAL_ID, { name: 'サーバー1', onConfirm: handleDelete })
 * ```
 */
export function DeleteModal() {
  const { open, params, close } = useModalState(DELETE_MODAL_ID)

  const name      = params.name      as string      | undefined
  const onConfirm = params.onConfirm as (() => void) | undefined

  const handleConfirm = () => {
    onConfirm?.()
    close()
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title="削除の確認"
      description="この操作は取り消せません"
      variant="danger"
      size="sm"
      footer={
        <>
          <SendButton
            onClick={close}
            variant="default"
          >
            キャンセル
          </SendButton>
          <SendButton
            onClick={handleConfirm}
            variant="delete"
          >
            削除する
          </SendButton>
        </>
      }
    >
      <p>
        <span className="font-medium text-zinc-900 dark:text-zinc-100">{name ?? 'このアイテム'}</span>
        を削除してもよいですか？
      </p>
    </Modal>
  )
}
