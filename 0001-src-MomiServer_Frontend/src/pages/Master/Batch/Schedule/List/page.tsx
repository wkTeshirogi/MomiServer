import { viewOptions, weekDays, monthDays, dayHours, weekdayAliases } from './page.constants'
import type { CalendarView } from './page.constants'
import { NotificationBanner } from '@/components/ui/notification-banner'
import { useMemo, useState } from 'react'
import { CheckCircle2, ChevronLeft, ChevronRight, CircleHelp, Clock3, FileText, LoaderCircle, Plus, RefreshCw, Search, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ListPagination, ListPaginationSummary } from '@/components/ui/list-pagination'
import { useListPagination } from '@/hooks/useListPagination'
import { cn } from '@/lib/utils'
import { useBatchScheduleListService, type BatchExecutionStatus, type BatchScheduleExecutionSummary, type BatchScheduleItem } from './service'
import { BatchScheduleExecutionLogModal } from './BatchScheduleExecutionLogModal'

type CronFields = {
  minute: string
  hour: string
  dayOfMonth: string
  month: string
  dayOfWeek: string
  valid: boolean
}

type SchedulePlacement = {
  timeLabel: string
  dailyHours: number[]
  weeklyDays: number[]
  monthlyDays: number[]
  monthLabel: string
  dayOfMonthLabel: string
  dayOfWeekLabel: string
}

type CalendarProps = {
  schedules: Array<{ item: BatchScheduleItem; placement: SchedulePlacement }>
  enabledClasses: Record<string, string>
  enabledLabels: Record<string, string>
  executionLookup: Map<string, ExecutionCounts>
  executionLoading: boolean
  executionUnavailable: boolean
  onOpenLogs: (selection: LogSelection) => void
}

type LogSelection = { scheduleId: number; definitionId: number; date: string; hour?: number }

type ExecutionCounts = Partial<Record<BatchExecutionStatus, number>>

type CalendarDateLabel = {
  day: number
  dayIndex: number
  dateText: string
  displayDate: string
  isBlank?: boolean
  isToday: boolean
}

export function BatchScheduleList() {
  const [searchTerm, setSearchTerm] = useState('')
  const [calendarView, setCalendarView] = useState<CalendarView>('weekly')
  const [currentDate, setCurrentDate] = useState(() => new Date())
  const [logSelection, setLogSelection] = useState<LogSelection | null>(null)
  const weeklyDates = useMemo(() => getWeekDateLabels(currentDate), [currentDate])
  const monthlyDates = useMemo(() => getMonthDateLabels(currentDate), [currentDate])
  const currentDateLabel = useMemo(() => formatDisplayDate(currentDate), [currentDate])
  const currentDateText = useMemo(() => getLocalDateText(currentDate), [currentDate])
  const currentMonthLabel = useMemo(() => formatMonthLabel(currentDate), [currentDate])
  const periodLabel = useMemo(() => formatPeriodLabel(currentDate, calendarView), [calendarView, currentDate])
  const visibleFrom = calendarView === 'weekly' ? weeklyDates[0].dateText
    : calendarView === 'monthly' ? monthlyDates.find(date => !date.isBlank)?.dateText ?? currentDateText
      : currentDateText
  const visibleTo = calendarView === 'weekly' ? weeklyDates[weeklyDates.length - 1].dateText
    : calendarView === 'monthly' ? monthlyDates[monthlyDates.length - 1].dateText : currentDateText
  const {
    batchSchedules,
    executionSummaries,
    loading,
    executionLoading,
    error,
    executionError,
    reload,
    enabledClasses,
    enabledLabels,
  } = useBatchScheduleListService(visibleFrom, visibleTo)

  const filteredBatchSchedules = useMemo(() => {
    const term = searchTerm.trim().toLowerCase()
    if (!term) return batchSchedules

    return batchSchedules.filter(item =>
      [
        String(item.id),
        String(item.batch_definition_id),
        String(item.target_agent_id),
        item.target_machine_name,
        item.target_machine_address,
        item.cron_expression,
        item.start_date,
        item.end_date,
      ].some(value => value.toLowerCase().includes(term))
    )
  }, [batchSchedules, searchTerm])
  const { visibleItems: visibleBatchSchedules, paginationProps } =
    useListPagination(filteredBatchSchedules, searchTerm)

  const scheduledItems = useMemo(
    () => visibleBatchSchedules.map(item => ({
      item,
      placement: createSchedulePlacement(item.cron_expression),
    })),
    [visibleBatchSchedules]
  )
  const executionLookup = useMemo(() => buildExecutionLookup(executionSummaries), [executionSummaries])
  const calendarExecutionProps = {
    executionLookup,
    executionLoading,
    executionUnavailable: Boolean(executionError),
    onOpenLogs: setLogSelection,
  }

  return (
    <div className="space-y-6 text-left">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-1 text-xl font-semibold text-zinc-900 dark:text-white">
            バッチスケジュール一覧
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            登録されているバッチスケジュールをカレンダー形式で表示します。
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative">
            <input
              type="text"
              placeholder="Cron式・定義IDで検索..."
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              className="w-64 rounded-lg border border-zinc-300 bg-white py-2 pl-10 pr-3 text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:ring-2 focus:ring-purple-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
            />
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-zinc-400" />
          </div>
          <Button variant="outline" onClick={() => void reload()} disabled={loading}>
            <RefreshCw className="size-4" />
            再読込
          </Button>
          <Button href="/master/batch/schedule/new">
            <Plus className="size-4" />
            新規作成
          </Button>
        </div>
      </div>

      {error && (
        <NotificationBanner type="error">
          {error}
        </NotificationBanner>
      )}
      {executionError && (
        <NotificationBanner type="error">{executionError}</NotificationBanner>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="inline-flex rounded-full bg-zinc-100 p-1 dark:bg-zinc-800">
          {viewOptions.map(option => {
            const Icon = option.icon
            const active = calendarView === option.value

            return (
              <button
                key={option.value}
                type="button"
                onClick={() => setCalendarView(option.value)}
                className={cn(
                  'inline-flex h-8 items-center gap-2 rounded-full px-3 text-sm font-medium transition-colors',
                  active
                    ? 'bg-white text-zinc-900 shadow-sm dark:bg-zinc-950 dark:text-zinc-100'
                    : 'text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100'
                )}
              >
                <Icon className="size-4" />
                {option.label}
              </button>
            )
          })}
        </div>
        <ListPaginationSummary
          {...paginationProps}
          itemUnit="定義"
          overallItems={batchSchedules.length}
          loading={loading}
        />
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-zinc-200 bg-white px-3 py-2 dark:border-zinc-800 dark:bg-zinc-900">
        <div className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
          {periodLabel}
        </div>
        <div className="flex items-center gap-2">
          <Button
            aria-label="前の期間へ"
            variant="outline"
            size="sm"
            onClick={() => setCurrentDate(date => shiftCalendarDate(date, calendarView, -1))}
          >
            <ChevronLeft className="size-4" />
            前へ
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setCurrentDate(new Date())}
          >
            今日
          </Button>
          <Button
            aria-label="次の期間へ"
            variant="outline"
            size="sm"
            onClick={() => setCurrentDate(date => shiftCalendarDate(date, calendarView, 1))}
          >
            次へ
            <ChevronRight className="size-4" />
          </Button>
        </div>
      </div>

      {loading ? (
        <CalendarMessage message="読み込み中..." />
      ) : filteredBatchSchedules.length === 0 ? (
        <CalendarMessage message="表示できるバッチスケジュールがありません。" />
      ) : (
        <>
          {calendarView === 'daily' && (
            <DailyCalendar
              dateLabel={currentDateLabel}
              dateText={currentDateText}
              schedules={scheduledItems}
              enabledClasses={enabledClasses}
              enabledLabels={enabledLabels}
              {...calendarExecutionProps}
            />
          )}
          {calendarView === 'weekly' && (
            <WeeklyCalendar
              weekDates={weeklyDates}
              schedules={scheduledItems}
              enabledClasses={enabledClasses}
              enabledLabels={enabledLabels}
              {...calendarExecutionProps}
            />
          )}
          {calendarView === 'monthly' && (
            <MonthlyCalendar
              monthLabel={currentMonthLabel}
              monthDates={monthlyDates}
              schedules={scheduledItems}
              enabledClasses={enabledClasses}
              enabledLabels={enabledLabels}
              {...calendarExecutionProps}
            />
          )}
        </>
      )}
      <ListPagination {...paginationProps} loading={loading} />
      {logSelection && (
        <BatchScheduleExecutionLogModal
          key={`${logSelection.scheduleId}:${logSelection.date}:${logSelection.hour ?? 'all'}`}
          selection={logSelection}
          onClose={() => setLogSelection(null)}
        />
      )}
    </div>
  )
}

function DailyCalendar({
  dateLabel,
  dateText,
  schedules,
  enabledClasses,
  enabledLabels,
  executionLookup,
  executionLoading,
  executionUnavailable,
  onOpenLogs,
}: CalendarProps & { dateLabel: string; dateText: string }) {
  return (
    <Card className="rounded-lg border border-zinc-200 bg-white p-0 dark:border-zinc-800 dark:bg-zinc-900">
      <div className="border-b border-zinc-200 px-4 py-3 dark:border-zinc-800">
        <div className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">日次スケジュール</div>
        <div className="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">{dateLabel}</div>
      </div>
      <div className="divide-y divide-zinc-200 dark:divide-zinc-800">
        {dayHours.map(hour => {
          const items = schedules.filter(schedule =>
            schedule.placement.dailyHours.includes(hour)
            && isScheduleActiveOnDate(schedule.item, dateText)
          )

          return (
            <div key={hour} className="grid grid-cols-[72px_minmax(0,1fr)] gap-3 px-4 py-3 sm:grid-cols-[96px_minmax(0,1fr)]">
              <div className="pt-1 text-sm font-medium text-zinc-500 dark:text-zinc-400">
                {hour.toString().padStart(2, '0')}:00
              </div>
              <ScheduleLane emptyLabel="この時間帯の予定はありません。">
                {items.map(({ item, placement }) => (
                  <ScheduleCard
                    key={item.id}
                    item={item}
                    placement={placement}
                    enabledClasses={enabledClasses}
                    enabledLabels={enabledLabels}
                    executionCounts={executionLookup.get(executionKey(item.id, dateText, hour))}
                    executionLoading={executionLoading}
                    executionUnavailable={executionUnavailable}
                    logSelection={{ scheduleId: item.id, definitionId: item.batch_definition_id, date: dateText, hour }}
                    onOpenLogs={onOpenLogs}
                  />
                ))}
              </ScheduleLane>
            </div>
          )
        })}
      </div>
    </Card>
  )
}

function WeeklyCalendar({
  weekDates,
  schedules,
  enabledClasses,
  enabledLabels,
  executionLookup,
  executionLoading,
  executionUnavailable,
  onOpenLogs,
}: CalendarProps & { weekDates: CalendarDateLabel[] }) {
  return (
    <div className="grid grid-cols-1 gap-3 lg:grid-cols-7">
      {weekDates.map(date => {
        const items = schedules.filter(schedule =>
          schedule.placement.weeklyDays.includes(date.dayIndex)
          && isScheduleActiveOnDate(schedule.item, date.dateText)
        )

        return (
          <CalendarColumn
            key={date.displayDate}
            title={`${weekDays[date.dayIndex]}曜日`}
            subtitle={date.displayDate}
            count={items.length}
            highlighted={date.isToday}
          >
            <ScheduleLane emptyLabel="予定なし">
              {items.map(({ item, placement }) => (
                <ScheduleCard
                  key={item.id}
                  item={item}
                  placement={placement}
                  enabledClasses={enabledClasses}
                  enabledLabels={enabledLabels}
                  executionCounts={executionLookup.get(executionKey(item.id, date.dateText))}
                  executionLoading={executionLoading}
                  executionUnavailable={executionUnavailable}
                  logSelection={{ scheduleId: item.id, definitionId: item.batch_definition_id, date: date.dateText }}
                  onOpenLogs={onOpenLogs}
                />
              ))}
            </ScheduleLane>
          </CalendarColumn>
        )
      })}
    </div>
  )
}

function MonthlyCalendar({
  monthLabel,
  monthDates,
  schedules,
  enabledClasses,
  enabledLabels,
  executionLookup,
  executionLoading,
  executionUnavailable,
  onOpenLogs,
}: CalendarProps & { monthLabel: string; monthDates: CalendarDateLabel[] }) {
  return (
    <div className="space-y-3">
      <div className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">{monthLabel}</div>
      <div className="overflow-x-auto">
        <div className="grid min-w-[980px] grid-cols-7 gap-3">
          {weekDays.map(day => (
            <div
              key={day}
              className="rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-center text-xs font-semibold text-zinc-500 dark:border-zinc-800 dark:bg-zinc-900/70 dark:text-zinc-400"
            >
              {day}
            </div>
          ))}
          {monthDates.map((date, index) => {
            if (date.isBlank) {
              return <MonthBlankCell key={`blank-${index}`} />
            }

            const items = schedules.filter(schedule =>
              schedule.placement.monthlyDays.includes(date.day)
              && isScheduleActiveOnDate(schedule.item, date.dateText)
            )

            return (
              <CalendarColumn
                key={date.displayDate}
                title={`${date.day}日`}
                subtitle={`${date.displayDate} ${weekDays[date.dayIndex]}曜日`}
                count={items.length}
                highlighted={date.isToday}
              >
                <ScheduleLane emptyLabel="予定なし">
                  {items.map(({ item, placement }) => (
                    <ScheduleCard
                      key={item.id}
                      item={item}
                      placement={placement}
                      enabledClasses={enabledClasses}
                      enabledLabels={enabledLabels}
                      executionCounts={executionLookup.get(executionKey(item.id, date.dateText))}
                      executionLoading={executionLoading}
                      executionUnavailable={executionUnavailable}
                      logSelection={{ scheduleId: item.id, definitionId: item.batch_definition_id, date: date.dateText }}
                      onOpenLogs={onOpenLogs}
                    />
                  ))}
                </ScheduleLane>
              </CalendarColumn>
            )
          })}
        </div>
      </div>
    </div>
  )
}

function MonthBlankCell() {
  return (
    <div className="min-h-[180px] rounded-lg border border-dashed border-zinc-200 bg-zinc-50/60 dark:border-zinc-800 dark:bg-zinc-900/30" />
  )
}

function CalendarColumn({
  title,
  subtitle,
  count,
  highlighted = false,
  children,
}: {
  title: string
  subtitle?: string
  count: number
  highlighted?: boolean
  children: React.ReactNode
}) {
  return (
    <Card className={cn(
      'min-h-[180px] rounded-lg border bg-white p-0 dark:bg-zinc-900',
      highlighted
        ? 'border-purple-300 ring-2 ring-purple-100 dark:border-purple-800 dark:ring-purple-950'
        : 'border-zinc-200 dark:border-zinc-800'
    )}>
      <div className="flex items-center justify-between border-b border-zinc-200 px-3 py-2 dark:border-zinc-800">
        <div>
          <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">{title}</h2>
          {subtitle && (
            <div className="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">{subtitle}</div>
          )}
        </div>
        <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400">
          {count}
        </span>
      </div>
      <div className="p-3">{children}</div>
    </Card>
  )
}

function ScheduleLane({
  children,
  emptyLabel,
}: {
  children: React.ReactNode
  emptyLabel: string
}) {
  const hasChildren = Boolean(children) && (!Array.isArray(children) || children.length > 0)

  if (!hasChildren) {
    return <div className="rounded-lg border border-dashed border-zinc-200 px-3 py-4 text-center text-xs text-zinc-400 dark:border-zinc-800">{emptyLabel}</div>
  }

  return <div className="space-y-2">{children}</div>
}

function executionKey(scheduleId: number, date: string, hour?: number): string {
  return `${scheduleId}:${date}${hour == null ? '' : `:${hour}`}`
}

function buildExecutionLookup(summaries: BatchScheduleExecutionSummary[]): Map<string, ExecutionCounts> {
  const lookup = new Map<string, ExecutionCounts>()

  for (const summary of summaries) {
    for (const key of [
      executionKey(summary.schedule_id, summary.scheduled_date),
      executionKey(summary.schedule_id, summary.scheduled_date, summary.scheduled_hour),
    ]) {
      const counts = lookup.get(key) ?? {}
      counts[summary.execution_status] = (counts[summary.execution_status] ?? 0) + summary.execution_count
      lookup.set(key, counts)
    }
  }

  return lookup
}

function getExecutionDisplay(counts: ExecutionCounts | undefined, loading: boolean, unavailable: boolean) {
  if (unavailable) return {
    label: '実績未取得', detail: '', icon: CircleHelp,
    badgeClass: 'bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300',
    accentClass: 'border-l-zinc-400 dark:border-l-zinc-400',
  }
  if (loading) return {
    label: '実績確認中', detail: '', icon: LoaderCircle,
    badgeClass: 'bg-sky-100 text-sky-800 dark:bg-sky-950/50 dark:text-sky-300',
    accentClass: 'border-l-sky-500 dark:border-l-sky-500',
  }

  const success = counts?.success ?? 0
  const failed = counts?.failed ?? 0
  const timeout = counts?.timeout ?? 0
  const running = counts?.running ?? 0
  const queued = counts?.queued ?? 0
  const dispatched = counts?.dispatched ?? 0
  const cancelled = counts?.cancelled ?? 0
  const expired = counts?.expired ?? 0
  const detail = [
    success && `成功 ${success}`,
    failed && `失敗 ${failed}`,
    timeout && `タイムアウト ${timeout}`,
    running && `実行中 ${running}`,
    dispatched && `配信済み ${dispatched}`,
    queued && `待機中 ${queued}`,
    cancelled && `取消 ${cancelled}`,
    expired && `期限切れ ${expired}`,
  ].filter(Boolean).join(' / ')

  if (failed + timeout > 0) return {
    label: '実行済・失敗', detail, icon: XCircle,
    badgeClass: 'bg-red-100 text-red-800 dark:bg-red-950/50 dark:text-red-300',
    accentClass: 'border-l-red-500 dark:border-l-red-500',
  }
  if (success > 0) return {
    label: '実行済み', detail, icon: CheckCircle2,
    badgeClass: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300',
    accentClass: 'border-l-emerald-500 dark:border-l-emerald-500',
  }
  if (running > 0) return {
    label: '実行中', detail, icon: LoaderCircle,
    badgeClass: 'bg-sky-100 text-sky-800 dark:bg-sky-950/50 dark:text-sky-300',
    accentClass: 'border-l-sky-500 dark:border-l-sky-500',
  }
  if (queued + dispatched > 0) return {
    label: '実行待ち', detail, icon: Clock3,
    badgeClass: 'bg-amber-100 text-amber-800 dark:bg-amber-950/50 dark:text-amber-300',
    accentClass: 'border-l-amber-500 dark:border-l-amber-500',
  }
  return {
    label: cancelled > 0 ? 'キャンセル' : expired > 0 ? '期限切れ' : '実績なし', detail, icon: CircleHelp,
    badgeClass: 'bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300',
    accentClass: 'border-l-zinc-400 dark:border-l-zinc-400',
  }
}

function ScheduleCard({
  item,
  placement,
  enabledClasses,
  enabledLabels,
  executionCounts,
  executionLoading,
  executionUnavailable,
  logSelection,
  onOpenLogs,
}: {
  item: BatchScheduleItem
  placement: SchedulePlacement
  enabledClasses: Record<string, string>
  enabledLabels: Record<string, string>
  executionCounts?: ExecutionCounts
  executionLoading: boolean
  executionUnavailable: boolean
  logSelection: LogSelection
  onOpenLogs: (selection: LogSelection) => void
}) {
  const enabledKey = String(item.enabled)
  const execution = getExecutionDisplay(executionCounts, executionLoading, executionUnavailable)
  const ExecutionIcon = execution.icon
  const hasCompletedExecution = !executionLoading && !executionUnavailable
    && (executionCounts?.success ?? 0) + (executionCounts?.failed ?? 0) + (executionCounts?.timeout ?? 0) > 0

  return (
    <div
      className={cn(
        'rounded-lg border border-l-4 p-3 text-sm transition-colors',
        item.enabled
          ? 'border-purple-200 bg-purple-50/70 hover:bg-purple-100 dark:border-purple-900/50 dark:bg-purple-950/20 dark:hover:bg-purple-950/40'
          : 'border-zinc-200 bg-zinc-50 hover:bg-zinc-100 dark:border-zinc-800 dark:bg-zinc-900 dark:hover:bg-zinc-800/70',
        execution.accentClass
      )}
    >
      <a href={`/master/batch/schedule/${item.id}`} className="block">
        <div className="mb-2 flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className={cn('inline-flex items-center gap-1 rounded-sm px-2 py-1 text-xs font-semibold', execution.badgeClass)}>
            <ExecutionIcon className="size-3.5" />
            {execution.label}
          </span>
          {execution.detail && (
            <span className="text-xs text-zinc-600 dark:text-zinc-300">{execution.detail}</span>
          )}
        </div>
        <div className="mb-2 flex items-start justify-between gap-2">
          <div className="min-w-0">
            <div className="truncate font-semibold text-zinc-900 dark:text-zinc-100">
              定義ID {item.batch_definition_id}
            </div>
            <div className="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">
              {placement.timeLabel}
            </div>
          </div>
          <span className={`inline-flex shrink-0 rounded-full px-2 py-1 text-xs font-medium ${enabledClasses[enabledKey]}`}>
            {enabledLabels[enabledKey]}
          </span>
        </div>
        <div className="space-y-1 text-xs text-zinc-500 dark:text-zinc-400">
          <div className="truncate">
            {item.target_machine_name || `Machine ${item.target_agent_id}`} ({item.target_agent_id})
          </div>
          <div className="truncate" title={item.cron_expression}>
            Cron: {item.cron_expression}
          </div>
          <div className="flex flex-wrap gap-x-3 gap-y-1">
            <span>優先度 {item.priority}</span>
            <span>{placement.monthLabel}</span>
            <span>{placement.dayOfMonthLabel}</span>
            <span>{placement.dayOfWeekLabel}</span>
          </div>
          <div className="truncate">
            期間 {item.start_date || '-'} - {item.end_date || '-'}
          </div>
        </div>
      </a>
      <button
        type="button"
        disabled={!hasCompletedExecution}
        onClick={() => onOpenLogs(logSelection)}
        title={hasCompletedExecution ? '実行ログを表示' : '完了した実行がありません'}
        className="mt-3 inline-flex min-h-8 items-center gap-1.5 rounded border border-zinc-300 px-2 text-xs font-medium text-zinc-700 hover:bg-white disabled:cursor-not-allowed disabled:opacity-40 dark:border-zinc-700 dark:text-zinc-200 dark:hover:bg-zinc-800"
      >
        <FileText className="size-3.5" />
        実行ログ
      </button>
    </div>
  )
}

function CalendarMessage({ message }: { message: string }) {
  return (
    <Card className="rounded-lg border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
      <div className="py-8 text-center text-sm text-zinc-500 dark:text-zinc-400">
        {message}
      </div>
    </Card>
  )
}

function isScheduleActiveOnDate(item: BatchScheduleItem, dateText: string): boolean {
  const startDate = normalizeDateText(item.start_date)
  const endDate = normalizeDateText(item.end_date)

  if (startDate && dateText < startDate) return false
  if (endDate && dateText > endDate) return false

  return true
}

function normalizeDateText(value: string): string | null {
  const dateText = value.trim()
  if (!dateText || dateText === '-') return null

  const isoDate = dateText.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (isoDate) return isoDate[0]

  const slashDate = dateText.match(/^(\d{4})\/(\d{2})\/(\d{2})/)
  if (slashDate) return `${slashDate[1]}-${slashDate[2]}-${slashDate[3]}`

  return null
}

function getLocalDateText(date: Date): string {
  const year = date.getFullYear()
  const month = (date.getMonth() + 1).toString().padStart(2, '0')
  const day = date.getDate().toString().padStart(2, '0')

  return `${year}-${month}-${day}`
}

function shiftCalendarDate(date: Date, view: CalendarView, amount: number): Date {
  if (view === 'monthly') {
    return shiftMonth(date, amount)
  }

  const nextDate = new Date(date)
  nextDate.setHours(0, 0, 0, 0)
  nextDate.setDate(date.getDate() + (view === 'weekly' ? amount * 7 : amount))

  return nextDate
}

function shiftMonth(date: Date, amount: number): Date {
  const firstDayOfTargetMonth = new Date(date.getFullYear(), date.getMonth() + amount, 1)
  const lastDayOfTargetMonth = new Date(
    firstDayOfTargetMonth.getFullYear(),
    firstDayOfTargetMonth.getMonth() + 1,
    0
  ).getDate()
  firstDayOfTargetMonth.setDate(Math.min(date.getDate(), lastDayOfTargetMonth))

  return firstDayOfTargetMonth
}

function formatPeriodLabel(date: Date, view: CalendarView): string {
  if (view === 'daily') return formatDisplayDate(date)
  if (view === 'monthly') return formatMonthLabel(date)

  const weekDates = getWeekDateLabels(date)
  return `${weekDates[0].displayDate} - ${weekDates[weekDates.length - 1].displayDate}`
}

function getWeekDateLabels(currentDate: Date): CalendarDateLabel[] {
  const startDate = new Date(currentDate)
  startDate.setHours(0, 0, 0, 0)
  startDate.setDate(currentDate.getDate() - currentDate.getDay())
  const todayText = getLocalDateText(currentDate)

  return weekDays.map((_, dayIndex) => {
    const date = new Date(startDate)
    date.setDate(startDate.getDate() + dayIndex)
    const dateText = getLocalDateText(date)

    return {
      day: date.getDate(),
      dayIndex,
      dateText,
      displayDate: formatDisplayDate(date),
      isToday: dateText === todayText,
    }
  })
}

function getMonthDateLabels(currentDate: Date): CalendarDateLabel[] {
  const year = currentDate.getFullYear()
  const month = currentDate.getMonth()
  const todayText = getLocalDateText(currentDate)
  const firstDayIndex = new Date(year, month, 1).getDay()
  const lastDay = new Date(year, month + 1, 0).getDate()
  const blankDates = Array.from({ length: firstDayIndex }, (_, index) => ({
    day: 0,
    dayIndex: index,
    dateText: '',
    displayDate: '',
    isBlank: true,
    isToday: false,
  }))

  const monthDates = Array.from({ length: lastDay }, (_, index) => {
    const date = new Date(year, month, index + 1)
    const dateText = getLocalDateText(date)

    return {
      day: date.getDate(),
      dayIndex: date.getDay(),
      dateText,
      displayDate: formatDisplayDate(date),
      isToday: dateText === todayText,
    }
  })

  return [...blankDates, ...monthDates]
}

function formatDisplayDate(date: Date): string {
  const month = (date.getMonth() + 1).toString().padStart(2, '0')
  const day = date.getDate().toString().padStart(2, '0')

  return `${date.getFullYear()}/${month}/${day}`
}

function formatMonthLabel(date: Date): string {
  const month = (date.getMonth() + 1).toString().padStart(2, '0')

  return `${date.getFullYear()}年${month}月`
}

function createSchedulePlacement(cronExpression: string): SchedulePlacement {
  const fields = parseCronFields(cronExpression)

  if (!fields.valid) {
    return {
      timeLabel: '時刻不明',
      dailyHours: [],
      weeklyDays: [],
      monthlyDays: [],
      monthLabel: '月: 不明',
      dayOfMonthLabel: '日付: 不明',
      dayOfWeekLabel: '曜日: 不明',
    }
  }

  const minutes = expandCronField(fields.minute, 0, 59)
  const hours = expandCronField(fields.hour, 0, 23)
  const dayOfMonths = expandCronField(fields.dayOfMonth, 1, 31)
  const months = expandCronField(fields.month, 1, 12)
  const dayOfWeeks = expandCronField(fields.dayOfWeek, 0, 7, weekdayAliases)
    .map(day => day === 7 ? 0 : day)
  const dailyHours = hours.length > 0 ? hours : dayHours
  const weeklyDays = dayOfWeeks.length > 0 ? Array.from(new Set(dayOfWeeks)) : weekDays.map((_, index) => index)
  const monthlyDays = dayOfMonths.length > 0 ? dayOfMonths : monthDays

  return {
    timeLabel: formatTimeLabel(fields.hour, fields.minute, hours, minutes),
    dailyHours,
    weeklyDays,
    monthlyDays,
    monthLabel: `月: ${formatCronFieldLabel(fields.month, months, '毎月')}`,
    dayOfMonthLabel: `日付: ${formatCronFieldLabel(fields.dayOfMonth, dayOfMonths, '毎日')}`,
    dayOfWeekLabel: `曜日: ${formatWeekdayLabel(fields.dayOfWeek, weeklyDays)}`,
  }
}

function parseCronFields(cronExpression: string): CronFields {
  const parts = cronExpression.trim().split(/\s+/).filter(Boolean)

  if (parts.length === 5) {
    const [minute, hour, dayOfMonth, month, dayOfWeek] = parts
    return { minute, hour, dayOfMonth, month, dayOfWeek, valid: true }
  }

  if (parts.length >= 6) {
    const [, minute, hour, dayOfMonth, month, dayOfWeek] = parts
    return { minute, hour, dayOfMonth, month, dayOfWeek, valid: true }
  }

  return {
    minute: '',
    hour: '',
    dayOfMonth: '',
    month: '',
    dayOfWeek: '',
    valid: false,
  }
}

function expandCronField(
  field: string,
  min: number,
  max: number,
  aliases: Record<string, number> = {}
): number[] {
  if (field === '*' || field === '?') return []

  const values = new Set<number>()

  field.split(',').forEach(part => {
    const normalizedPart = part.trim().toUpperCase()
    if (!normalizedPart) return

    const [rangePart, stepPart] = normalizedPart.split('/')
    const step = stepPart ? Number(stepPart) : 1
    if (!Number.isInteger(step) || step <= 0) return

    if (rangePart === '*' || rangePart === '?') {
      for (let value = min; value <= max; value += step) values.add(value)
      return
    }

    const [startText, endText] = rangePart.split('-')
    const start = parseCronValue(startText, aliases)
    const end = endText ? parseCronValue(endText, aliases) : start
    if (start == null || end == null) return

    const safeStart = Math.max(min, Math.min(max, start))
    const safeEnd = Math.max(min, Math.min(max, end))
    for (let value = safeStart; value <= safeEnd; value += step) values.add(value)
  })

  return Array.from(values).sort((a, b) => a - b)
}

function parseCronValue(value: string, aliases: Record<string, number>): number | null {
  if (value in aliases) return aliases[value]

  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : null
}

function formatTimeLabel(hourField: string, minuteField: string, hours: number[], minutes: number[]): string {
  const everyHour = hourField === '*' || hourField === '?'
  const everyMinute = minuteField === '*' || minuteField === '?'

  if (everyHour && everyMinute) return '毎分'
  if (everyHour && minutes.length === 1) return `毎時 ${minutes[0].toString().padStart(2, '0')}分`
  if (hours.length === 1 && everyMinute) return `${hours[0].toString().padStart(2, '0')}時台 毎分`
  if (hours.length === 1 && minutes.length === 1) {
    return `${hours[0].toString().padStart(2, '0')}:${minutes[0].toString().padStart(2, '0')}`
  }

  return '複数時刻'
}

function formatCronFieldLabel(field: string, values: number[], everyLabel: string): string {
  if (field === '*' || field === '?') return everyLabel
  if (values.length === 0) return field
  if (values.length <= 4) return values.join(', ')

  return `${values[0]}-${values[values.length - 1]}`
}

function formatWeekdayLabel(field: string, values: number[]): string {
  if (field === '*' || field === '?') return '毎日'
  if (values.length === 0) return field

  return values
    .map(value => weekDays[value] ?? String(value))
    .join(', ')
}
