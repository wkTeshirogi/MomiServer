import { useCallback, useEffect, useState } from 'react'
import { getApiErrorMessage } from '../lib/apiError'
import { useAsyncRequest } from './useAsyncRequest'

type Options<T> = {
  load: (signal: AbortSignal) => Promise<T>
  initialData: T
  errorMessage: string
  apiErrorMessage?: boolean
  clearOnReload?: boolean
  clearOnError?: boolean
  refreshKey?: unknown
}

/** Shared loading/error/data handling for ordinary HTTP reads; the latest request alone can commit. */
export function useAsyncResource<T>({ load, initialData, errorMessage, apiErrorMessage = false,
  clearOnReload = false, clearOnError = false, refreshKey }: Options<T>) {

  // 呼出元が毎回空配列等を生成しても、リセット用データの参照変化でreloadを繰り返さないよう初期値を保持する。
  const [empty] = useState(() => initialData)
  const [state, setState] = useState({ data: initialData, loading: true, error: null as string | null })
  const { run, cancel } = useAsyncRequest()

  // 再取得中に既存データを残すか、ページ切替時に消すかは画面側が選ぶ。共通化のために表示方針を変えない。
  const reload = useCallback(() => run(load, {
    onStart: () => setState(current => ({ data: clearOnReload ? empty : current.data, loading: true, error: null })),
    onSuccess: data => setState({ data, loading: false, error: null }),
    onError: cause => setState(current => ({ ...current, data: clearOnError ? empty : current.data, loading: false,
      error: apiErrorMessage ? getApiErrorMessage(cause, errorMessage) : errorMessage })),
  }), [run, load, clearOnReload, clearOnError, empty, apiErrorMessage, errorMessage])

  useEffect(() => {

    // loadは呼出元で安定化する。条件変更・アンマウントでは開始前タイマーと開始済み取得の両方を無効化する。
    const timer = window.setTimeout(() => { void reload() }, 0)
    return () => { window.clearTimeout(timer); cancel() }
  }, [reload, cancel, refreshKey])

  return { ...state, reload }
}
