import { DATE_ONLY_PATTERN, DATE_TIME_PATTERN, JAPAN_TIME_OFFSET, JAPAN_TIME_ZONE } from './dateTime.constants.ts'

const japanDateTimeFormatter = new Intl.DateTimeFormat('ja-JP', {
  timeZone: JAPAN_TIME_ZONE,
  year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
})

type DateTimeDisplayOptions = { milliseconds?: boolean; emptyValue?: string }

function isCalendarDate(value: string): boolean {
  const date = new Date(`${value}T00:00:00Z`)

  // Dateが2月30日等を翌月へ自動補正しても、元の日付と一致しなければ不正値として扱う。
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value
}

function parseApiDateTime(value: string): Date | null {
  const match = DATE_TIME_PATTERN.exec(value)
  if (!match || !isCalendarDate(match[1])) return null
  const [, date, time, seconds = '00', fraction = '', offset = JAPAN_TIME_OFFSET] = match
  if (Number(time.slice(0, 2)) > 23 || Number(time.slice(3)) > 59 || Number(seconds) > 59) return null
  // LocalDateTime returned by the backend represents Japan time, not the browser's zone.
  const parsed = new Date(`${date}T${time}:${seconds}${fraction.slice(0, 4)}${offset}`)
  return Number.isFinite(parsed.getTime()) ? parsed : null
}

/** Format API timestamps in Japan time; calendar dates are preserved without timezone conversion. */
export function formatJapanDateTime(value?: string | null, options: DateTimeDisplayOptions = {}): string {
  const empty = options.emptyValue ?? '-'
  if (!value) return empty
  if (DATE_ONLY_PATTERN.test(value)) return isCalendarDate(value) ? value : empty
  const date = parseApiDateTime(value)
  if (!date) return empty
  const parts = Object.fromEntries(japanDateTimeFormatter.formatToParts(date).map(part => [part.type, part.value]))
  const milliseconds = options.milliseconds === false ? '' : `.${date.getUTCMilliseconds().toString().padStart(3, '0')}`
  return `${parts.year}/${parts.month}/${parts.day} ${parts.hour}:${parts.minute}:${parts.second}${milliseconds}`
}
