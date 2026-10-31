export { weekDayAliases as weekdayAliases, weekDayLabels as weekDays } from '@/lib/cronSchedule.constants'
import { CalendarDays, CalendarRange, Clock3 } from 'lucide-react'

// Fixed definitions scoped to pages/Master/Batch/Schedule/List.
export type CalendarView = 'daily' | 'weekly' | 'monthly'

export const viewOptions: Array<{ value: CalendarView; label: string; icon: typeof Clock3 }> = [
  { value: 'daily', label: '日次', icon: Clock3 },
  { value: 'weekly', label: '週次', icon: CalendarRange },
  { value: 'monthly', label: '月次', icon: CalendarDays },
]

export const monthDays = Array.from({ length: 31 }, (_, index) => index + 1)

export const dayHours = Array.from({ length: 24 }, (_, index) => index)
