import { renewalYearOptions } from './page.constants'

export function renewalExpiry(base: string, years: number): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(base) || !renewalYearOptions.some(value => value === years)) return ''
  const date = new Date(`${base}T00:00:00Z`)
  if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== base) return ''
  const targetYear = date.getUTCFullYear() + years
  if (targetYear > 9999) return ''
  const month = date.getUTCMonth()
  date.setUTCFullYear(targetYear)
  // Calendar-year renewal keeps leap-day expiry in February.
  if (date.getUTCMonth() !== month) date.setUTCDate(0)
  return date.toISOString().slice(0, 10)
}
