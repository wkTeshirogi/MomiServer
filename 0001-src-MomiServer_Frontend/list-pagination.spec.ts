import { test, expect, login } from './tests/frontend-fixtures'

const notifications = Array.from({ length: 125 }, (_, index) => ({
  id: index + 1,
  notification_id: `pagination-notice-${index + 1}`,
  read_status: 'read',
  notification_type: 'system_notice',
  level: 'info',
  title: `Pagination notification ${index + 1}`,
  message: `Pagination message ${index + 1}`,
  source_type: 'system',
  createdAt: '2026-10-12T10:00:00+09:00',
}))

test('list pagination starts at 20 and can switch to 50 items', async ({ page }) => {
  let availableNotifications = notifications
  const allPageCursors: (string | null)[] = []
  await page.route('**/api/notifications/page?*', async route => {
    const url = new URL(route.request().url())
    const limit = Number(url.searchParams.get('limit'))
    const cursor = url.searchParams.get('cursor')
    const offset = Number(cursor ?? 0)
    if (limit === 100) allPageCursors.push(cursor)
    await route.fulfill({ json: { items: availableNotifications.slice(offset, offset + limit),
      next_cursor: offset + limit < availableNotifications.length ? String(offset + limit) : null } })
  })

  await login(page)
  await page.goto('/alerts')

  const notificationRows = page.getByRole('row').filter({ hasText: 'Pagination notification' })
  await expect(page.getByLabel('表示件数')).toHaveValue('20')
  await expect(notificationRows).toHaveCount(20)
  await expect(page.getByText('1-20件 / 検索結果 125件', { exact: true })).toBeVisible()
  expect(allPageCursors).toEqual([null, '100'])

  const tableBounds = await page.getByRole('table').boundingBox()
  const pageSizeBounds = await page.getByLabel('表示件数').boundingBox()
  const nextButtonBounds = await page.getByRole('button', { name: '次のページへ', exact: true }).boundingBox()
  expect(tableBounds).not.toBeNull()
  expect(pageSizeBounds).not.toBeNull()
  expect(nextButtonBounds).not.toBeNull()
  expect(pageSizeBounds!.y + pageSizeBounds!.height).toBeLessThanOrEqual(tableBounds!.y)
  expect(nextButtonBounds!.y).toBeGreaterThanOrEqual(tableBounds!.y + tableBounds!.height)

  await page.getByLabel('表示件数').selectOption('50')
  await expect(notificationRows).toHaveCount(50)
  await expect(page.getByText('1-50件 / 検索結果 125件', { exact: true })).toBeVisible()

  await page.getByLabel('表示件数').selectOption('20')
  for (let pageIndex = 0; pageIndex < 6; pageIndex++) {
    await page.getByRole('button', { name: '次のページへ', exact: true }).click()
  }
  await expect(notificationRows).toHaveCount(5)
  await expect(page.getByText('121-125件 / 検索結果 125件', { exact: true })).toBeVisible()

  availableNotifications = notifications.slice(0, 5)
  await page.getByRole('button', { name: '再読込', exact: true }).click()
  await expect(page.getByText('1-5件 / 検索結果 5件', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '前のページへ', exact: true })).toBeDisabled()

  availableNotifications = notifications
  await page.getByRole('button', { name: '再読込', exact: true }).click()
  await expect(page.getByText('1-20件 / 検索結果 125件', { exact: true })).toBeVisible()
})
