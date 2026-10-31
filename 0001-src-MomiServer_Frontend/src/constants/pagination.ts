// Pagination contract shared by list screens, hooks, and summary controls.
export const LIST_PAGE_SIZES = [20, 50, 100] as const
export const DEFAULT_LIST_PAGE_SIZE = LIST_PAGE_SIZES[0]
export type ListPageSize = typeof LIST_PAGE_SIZES[number]
