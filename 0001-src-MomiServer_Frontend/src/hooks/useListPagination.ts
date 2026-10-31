import { DEFAULT_LIST_PAGE_SIZE, type ListPageSize } from '@/constants/pagination'
export { LIST_PAGE_SIZES } from '@/constants/pagination'
export type { ListPageSize } from '@/constants/pagination'
import { useMemo, useState } from 'react'
export function useListPagination<T>(items: T[], resetKey = '') {
  const [pageSize, setPageSizeState] = useState<ListPageSize>(DEFAULT_LIST_PAGE_SIZE)
  const [requestedPageIndex, setRequestedPageIndex] = useState(0)
  const [previousResetKey, setPreviousResetKey] = useState(resetKey)
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize))

  // 検索・更新で件数が減った直後も、存在しないページを参照して空表示にならないよう表示位置を補正する。
  const pageIndex = previousResetKey === resetKey ? Math.min(requestedPageIndex, totalPages - 1) : 0
  const start = pageIndex * pageSize

  // これは取得済み配列の画面内ページング。items.lengthをサーバー全件数として扱うAPIページングとは分ける。
  const visibleItems = useMemo(
    () => items.slice(start, start + pageSize),
    [items, pageSize, start],
  )

  if (requestedPageIndex !== pageIndex) setRequestedPageIndex(pageIndex)
  if (previousResetKey !== resetKey) setPreviousResetKey(resetKey)

  function setPageSize(value: ListPageSize) {
    setPageSizeState(value)
    setRequestedPageIndex(0)
  }

  return {
    visibleItems,
    paginationProps: {
      pageSize,
      pageIndex,
      itemCount: visibleItems.length,
      totalItems: items.length,
      hasNext: pageIndex + 1 < totalPages,
      onPageSizeChange: setPageSize,
      onPrevious: () => setRequestedPageIndex(Math.max(0, pageIndex - 1)),
      onNext: () => setRequestedPageIndex(Math.min(totalPages - 1, pageIndex + 1)),
    },
  }
}
