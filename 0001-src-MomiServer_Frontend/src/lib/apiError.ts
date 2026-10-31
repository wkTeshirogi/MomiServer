import { FIELD_ERROR_KEYS, HTML_TAG_PATTERN } from './apiError.constants.ts'

function record(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown> : null
}

function body(error: unknown): unknown {

  // Axiosの応答と生成SDKが直接返すエラー本文の違いはここで吸収し、各画面へ通信形式の分岐を持ち込まない。
  const response = record(record(error)?.response)
  if (response) return response.data
  // Never expose a transport Error's message, request, config, credentials or stack.
  return error instanceof Error ? null : error
}

function text(value: unknown): string | null {
  if (typeof value !== 'string') return null

  // プロキシ等のHTMLエラーページを、利用者向けのAPIメッセージとしてそのまま採用しない。
  const result = value.trim()
  return result && !HTML_TAG_PATTERN.test(result) ? result : null
}

function fieldMessage(value: unknown): string | null {
  const item = record(value)
  return text(value) ?? text(item?.message) ?? text(item?.defaultMessage) ?? text(item?.reason)
}

export function getApiErrorMessage(error: unknown): string | null
export function getApiErrorMessage(error: unknown, fallback: string): string
export function getApiErrorMessage(error: unknown, fallback?: string): string | null {
  const data = body(error)
  const item = record(data)
  return text(data) ?? text(item?.message) ?? text(item?.error) ?? fallback ?? null
}

export function getApiErrorStatus(error: unknown): number | null {
  const item = record(error)
  const status = record(item?.response)?.status ?? record(body(error))?.status
  return typeof status === 'number' && Number.isInteger(status) && status >= 100 && status <= 599 ? status : null
}

/** Extract validation entries without deciding which fields belong to an individual form. */
export function getApiFieldErrors(error: unknown): Record<string, string> {
  const data = record(body(error))
  const fields = new Map<string, string>()
  const assign = (field: unknown, value: unknown) => {
    const name = text(field)
    const message = Array.isArray(value) ? value.map(fieldMessage).find(Boolean) : fieldMessage(value)

    // 同じ項目に複数の検証結果があっても、優先順で最初に得た文言を後続形式の値で上書きしない。
    if (name && message && !fields.has(name)) fields.set(name, message)
  }
  for (const key of FIELD_ERROR_KEYS) {
    const entries = data?.[key]
    if (Array.isArray(entries)) {
      for (const entry of entries) {
        const item = record(entry)
        if (item) assign(item.field ?? item.name ?? item.property ?? item.path, item)
      }
    } else {
      const map = record(entries)
      if (map) Object.entries(map).forEach(([name, value]) => assign(name, value))
    }
  }
  return Object.fromEntries(fields)
}
