type Handlers<T> = {
  onStart?: () => void
  onSuccess: (value: T) => void
  onError: (cause: unknown) => void
  onSettled?: () => void
}

/** Latest-result ownership remains effective even if a client ignores AbortSignal. */
export function createAsyncRequestScope() {

  // このスコープごとに最新要求を管理し、独立した一覧・補助マスタの取得を相互に中断させない。
  let current: AbortController | null = null
  const cancel = () => { current?.abort(); current = null }
  const start = () => {

    // 通信中断に対応しないクライアントでも、所有者の交代で古い応答の反映を無効化する。
    cancel()
    const controller = new AbortController()
    current = controller
    return { signal: controller.signal, isCurrent: () => current === controller && !controller.signal.aborted }
  }
  const run = async <T,>(task: (signal: AbortSignal) => Promise<T>, handlers: Handlers<T>) => {
    const request = start()
    handlers.onStart?.()
    try {
      const value = await task(request.signal)
      if (request.isCurrent()) { handlers.onSuccess(value); return value }
    } catch (cause) {
      if (request.isCurrent()) handlers.onError(cause)
    } finally {

      // 古い要求の終了処理で、新しい要求のloading表示まで解除しない。
      if (request.isCurrent()) handlers.onSettled?.()
    }
  }
  return { run, cancel }
}
