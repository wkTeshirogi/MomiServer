import { useId } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { LIST_PAGE_SIZES, type ListPageSize } from '@/constants/pagination'

type ListPaginationSummaryProps = {
  pageSize: number
  pageIndex: number
  itemCount: number
  onPageSizeChange: (pageSize: ListPageSize) => void
  totalItems?: number
  overallItems?: number
  loading?: boolean
  itemUnit?: string
}

type ListPaginationProps = {
  pageIndex: number
  hasNext: boolean
  onPrevious: () => void
  onNext: () => void
  loading?: boolean
}

export function ListPaginationSummary({
  pageSize,
  pageIndex,
  itemCount,
  onPageSizeChange,
  totalItems,
  overallItems,
  loading = false,
  itemUnit = '件',
}: ListPaginationSummaryProps) {
  const selectId = useId()
  const start = itemCount === 0 ? 0 : pageIndex * pageSize + 1
  const end = itemCount === 0 ? 0 : start + itemCount - 1

  return (
    <div className="flex flex-col gap-3 text-sm text-zinc-500 dark:text-zinc-400 sm:flex-row sm:items-center sm:justify-between">
      <div>
        {start}-{end}{itemUnit}
        {totalItems !== undefined && ` / 検索結果 ${totalItems}${itemUnit}`}
        {overallItems !== undefined && overallItems !== totalItems && ` / 全 ${overallItems}${itemUnit}`}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <label htmlFor={selectId} className="font-medium text-zinc-700 dark:text-zinc-300">
          表示件数
        </label>
        <select
          id={selectId}
          value={pageSize}
          disabled={loading}
          onChange={event => onPageSizeChange(Number(event.target.value) as ListPageSize)}
          className="h-9 rounded-md border border-zinc-300 bg-white px-3 text-zinc-900 focus:outline-none focus:ring-2 focus:ring-purple-500 disabled:opacity-50 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
        >
          {LIST_PAGE_SIZES.map(size => <option key={size} value={size}>{size}{itemUnit}</option>)}
        </select>
      </div>
    </div>
  )
}

export function ListPagination({
  pageIndex,
  hasNext,
  onPrevious,
  onNext,
  loading = false,
}: ListPaginationProps) {
  return (
    <div className="flex justify-end gap-2">
      <Button aria-label="前のページへ" variant="outline" size="sm" disabled={loading || pageIndex === 0} onClick={onPrevious}>
        <ChevronLeft className="size-4" />前へ
      </Button>
      <Button aria-label="次のページへ" variant="outline" size="sm" disabled={loading || !hasNext} onClick={onNext}>
        次へ<ChevronRight className="size-4" />
      </Button>
    </div>
  )
}
