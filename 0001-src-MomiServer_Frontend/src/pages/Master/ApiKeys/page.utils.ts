import { defaultExpiryYears } from './page.constants'

export function defaultExpiry(reference = new Date()): string {
  const expiry = new Date(reference)
  const month = expiry.getMonth()
  expiry.setFullYear(expiry.getFullYear() + defaultExpiryYears)
  // Clamp leap day to February's last day instead of rolling into March.
  if (expiry.getMonth() !== month) expiry.setDate(0)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${expiry.getFullYear()}-${pad(expiry.getMonth() + 1)}-${pad(expiry.getDate())}T${pad(expiry.getHours())}:${pad(expiry.getMinutes())}`
}
