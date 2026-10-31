import {
  createContext, useContext, useState, useCallback,
  type ReactNode,
} from 'react'

// ────────────────────────────────────────────────
// 型定義
// ────────────────────────────────────────────────
export type ModalParams = Record<string, unknown>

type ModalState = {
  params: ModalParams
}

type ModalContextValue = {
  openModal:  (id: string, params?: ModalParams) => void
  closeModal: (id: string) => void
  closeAll:   () => void
  isOpen:     (id: string) => boolean
  getParams:  (id: string) => ModalParams
}

// ────────────────────────────────────────────────
// Context
// ────────────────────────────────────────────────
const ModalContext = createContext<ModalContextValue | null>(null)

// ────────────────────────────────────────────────
// Provider
// ────────────────────────────────────────────────
export function ModalProvider({ children }: { children: ReactNode }) {
  const [states, setStates] = useState<Map<string, ModalState>>(new Map())

  const openModal = useCallback((id: string, params: ModalParams = {}) => {
    setStates(prev => new Map(prev).set(id, { params }))
  }, [])

  const closeModal = useCallback((id: string) => {
    setStates(prev => {
      const next = new Map(prev)
      next.delete(id)
      return next
    })
  }, [])

  const closeAll  = useCallback(() => setStates(new Map()), [])
  const isOpen    = useCallback((id: string) => states.has(id), [states])
  const getParams = useCallback((id: string) => states.get(id)?.params ?? {}, [states])

  return (
    <ModalContext.Provider value={{ openModal, closeModal, closeAll, isOpen, getParams }}>
      {children}
    </ModalContext.Provider>
  )
}

// ────────────────────────────────────────────────
// Hooks
// ────────────────────────────────────────────────

/** モーダルを開閉するだけのシンプルなHook */
export function useModalManager() {
  const ctx = useContext(ModalContext)
  if (!ctx) throw new Error('useModalManager は ModalProvider の中で使用してください')
  return ctx
}

/**
 * モーダルコンポーネント側で使うHook
 * open状態とparamsをIDで取得する
 *
 * @example
 * ```tsx
 * export function DeleteModal() {
 *   const { open, params, close } = useModalState('delete')
 *   const name = params.name as string
 *   return (
 *     <Modal open={open} onClose={close} title="削除確認" variant="danger">
 *       {name} を削除しますか？
 *     </Modal>
 *   )
 * }
 * ```
 */
export function useModalState(id: string) {
  const { isOpen, getParams, closeModal } = useModalManager()
  return {
    open:   isOpen(id),
    params: getParams(id),
    close:  () => closeModal(id),
  }
}
