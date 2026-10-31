import { monthAliases, weekDayAliases, weekDayLabels } from './cronSchedule.constants'
type CronPart = {
  source: string
  values: number[] | null
}

type CronParts = {
  second: CronPart
  minute: CronPart
  hour: CronPart
  dayOfMonth: CronPart
  month: CronPart
  dayOfWeek: CronPart
}

export type CronValidationResult = {
  valid: boolean
  error?: string
  normalizedExpression?: string
  description?: string
  nextRuns: string[]
}

export function validateCronExpression(expression: string): CronValidationResult {
  const cron = expression.trim()
  if (!cron) {
    return {
      valid: false,
      error: 'Cron式は必須です。',
      nextRuns: [],
    }
  }

  const rawParts = cron.split(/\s+/)
  if (rawParts.length !== 5 && rawParts.length !== 6) {
    return {
      valid: false,
      error: 'Cron式は5項目または6項目で入力してください。',
      nextRuns: [],
    }
  }

  const parts = rawParts.length === 5 ? ['0', ...rawParts] : rawParts
  const [second, minute, hour, dayOfMonth, month, dayOfWeek] = parts
  const parsed = {
    second: parseCronPart(second, 0, 59, {}),
    minute: parseCronPart(minute, 0, 59, {}),
    hour: parseCronPart(hour, 0, 23, {}),
    dayOfMonth: parseCronPart(dayOfMonth, 1, 31, {}),
    month: parseCronPart(month, 1, 12, monthAliases),
    dayOfWeek: parseCronPart(dayOfWeek, 0, 7, weekDayAliases),
  }

  const invalidPart = Object.values(parsed).find(part => part.error)
  if (invalidPart?.error) {
    return {
      valid: false,
      error: invalidPart.error,
      nextRuns: [],
    }
  }

  const cronParts: CronParts = {
    second: parsed.second.part,
    minute: parsed.minute.part,
    hour: parsed.hour.part,
    dayOfMonth: parsed.dayOfMonth.part,
    month: parsed.month.part,
    dayOfWeek: normalizeDayOfWeekPart(parsed.dayOfWeek.part),
  }

  if (cronParts.second.values === null || cronParts.second.values.length !== 1 || cronParts.second.values[0] !== 0) {
    return {
      valid: false,
      error: '秒は0を指定してください。例: 0 30 2 * * *',
      nextRuns: [],
    }
  }

  return {
    valid: true,
    normalizedExpression: parts.join(' '),
    description: describeCron(cronParts),
    nextRuns: findNextRuns(cronParts),
  }
}

function parseCronPart(
  source: string,
  min: number,
  max: number,
  aliases: Record<string, number>
): { part: CronPart; error?: string } {
  const value = source.trim().toUpperCase()
  if (!value) {
    return {
      part: { source, values: [] },
      error: 'Cron式に空の項目があります。',
    }
  }

  if (/[LW#]/.test(value)) {
    return {
      part: { source, values: [] },
      error: 'L、W、# を含むCron式は画面では対応していません。',
    }
  }

  if (value === '*' || value === '?') {
    return { part: { source, values: null } }
  }

  const values = new Set<number>()

  for (const segment of value.split(',')) {
    const [rangeText, stepText] = segment.split('/')
    const step = stepText ? Number(stepText) : 1
    if (!Number.isInteger(step) || step <= 0) {
      return {
        part: { source, values: [] },
        error: `Cron式のステップ指定が不正です: ${source}`,
      }
    }

    if (rangeText === '*' || rangeText === '?') {
      for (let index = min; index <= max; index += step) values.add(index)
      continue
    }

    const [startText, endText] = rangeText.split('-')
    const start = parseCronValue(startText, aliases)
    const end = endText ? parseCronValue(endText, aliases) : start
    if (start == null || end == null || start > end || start < min || end > max) {
      return {
        part: { source, values: [] },
        error: `Cron式の範囲指定が不正です: ${source}`,
      }
    }

    for (let index = start; index <= end; index += step) values.add(index)
  }

  return { part: { source, values: Array.from(values).sort((a, b) => a - b) } }
}

function parseCronValue(value: string, aliases: Record<string, number>): number | null {
  if (value in aliases) return aliases[value]

  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : null
}

function normalizeDayOfWeekPart(part: CronPart): CronPart {
  if (part.values === null) return part

  return {
    source: part.source,
    values: Array.from(new Set(part.values.map(value => value === 7 ? 0 : value))).sort((a, b) => a - b),
  }
}

function describeCron(parts: CronParts): string {
  const timeLabel = formatTime(parts.hour, parts.minute)
  const monthLabel = formatNumberPart(parts.month, '毎月', '月')
  const dayOfMonthLabel = formatNumberPart(parts.dayOfMonth, '毎日', '日')
  const dayOfWeekLabel = formatWeekDayPart(parts.dayOfWeek)
  const isEveryMonth = parts.month.values === null
  const isEveryDayOfMonth = parts.dayOfMonth.values === null
  const isEveryDayOfWeek = parts.dayOfWeek.values === null

  if (isEveryMonth && isEveryDayOfMonth && isEveryDayOfWeek) {
    return `毎日 ${timeLabel} に実行します。`
  }

  if (isEveryMonth && isEveryDayOfMonth && !isEveryDayOfWeek) {
    return `毎週 ${dayOfWeekLabel} ${timeLabel} に実行します。`
  }

  if (isEveryMonth && !isEveryDayOfMonth && isEveryDayOfWeek) {
    return `毎月 ${dayOfMonthLabel} ${timeLabel} に実行します。`
  }

  return `${monthLabel}、${dayOfMonthLabel}、${dayOfWeekLabel} の ${timeLabel} に実行します。`
}

function formatTime(hour: CronPart, minute: CronPart): string {
  if (hour.values === null && minute.values === null) return '毎分'
  if (hour.values === null && minute.values?.length === 1) {
    return `毎時 ${minute.values[0].toString().padStart(2, '0')}分`
  }
  if (hour.values?.length === 1 && minute.values === null) {
    return `${hour.values[0].toString().padStart(2, '0')}時台の毎分`
  }
  if (hour.values?.length === 1 && minute.values?.length === 1) {
    return `${hour.values[0].toString().padStart(2, '0')}:${minute.values[0].toString().padStart(2, '0')}`
  }

  return `${formatNumberPart(hour, '毎時', '時')} ${formatNumberPart(minute, '毎分', '分')}`
}

function formatNumberPart(part: CronPart, everyLabel: string, suffix: string): string {
  if (part.values === null) return everyLabel
  if (part.values.length <= 4) return part.values.map(value => `${value}${suffix}`).join('、')

  return `${part.values[0]}${suffix}-${part.values[part.values.length - 1]}${suffix}`
}

function formatWeekDayPart(part: CronPart): string {
  if (part.values === null) return '毎日'

  return part.values.map(value => `${weekDayLabels[value] ?? value}曜日`).join('、')
}

function findNextRuns(parts: CronParts): string[] {
  const result: string[] = []
  const cursor = new Date()
  cursor.setSeconds(0, 0)
  cursor.setMinutes(cursor.getMinutes() + 1)

  const limit = new Date(cursor)
  limit.setFullYear(limit.getFullYear() + 1)

  while (cursor <= limit && result.length < 5) {
    if (matchesCron(parts, cursor)) {
      result.push(formatDateTime(cursor))
    }
    cursor.setMinutes(cursor.getMinutes() + 1)
  }

  return result
}

function matchesCron(parts: CronParts, date: Date): boolean {
  return matchesPart(parts.minute, date.getMinutes())
    && matchesPart(parts.hour, date.getHours())
    && matchesPart(parts.dayOfMonth, date.getDate())
    && matchesPart(parts.month, date.getMonth() + 1)
    && matchesPart(parts.dayOfWeek, date.getDay())
}

function matchesPart(part: CronPart, value: number): boolean {
  return part.values === null || part.values.includes(value)
}

function formatDateTime(date: Date): string {
  const year = date.getFullYear()
  const month = (date.getMonth() + 1).toString().padStart(2, '0')
  const day = date.getDate().toString().padStart(2, '0')
  const hour = date.getHours().toString().padStart(2, '0')
  const minute = date.getMinutes().toString().padStart(2, '0')
  const weekDay = weekDayLabels[date.getDay()]

  return `${year}/${month}/${day} (${weekDay}) ${hour}:${minute}`
}
