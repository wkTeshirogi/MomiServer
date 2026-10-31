import { test, expect, login } from './tests/frontend-fixtures'

test.use({ timezoneId: 'Asia/Tokyo' })

test('schedule counts use definitions and log pages recover after the dataset shrinks', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-10-31T10:00:00+09:00'))
  await page.route('**/api/master/batch/schedule/list', route => route.fulfill({ json: [{
    id: 1, batch_definition_id: 1, target_agent_id: 1, cron_expression: '0 1 * * *', enabled: true,
  }] }))
  await page.route('**/api/master/device/list', route => route.fulfill({ json: [{ id: 1, devicename: 'Log machine' }] }))
  await page.route('**/api/master/batch/schedule/executions?*', route => route.fulfill({ json: [{
    schedule_id: 1, scheduled_date: '2026-10-31', scheduled_hour: 1, execution_status: 'success', execution_count: 25,
  }] }))
  let count = 25
  const offsets: number[] = []
  await page.route('**/api/master/batch/schedule/executions/logs?*', route => {
    const url = new URL(route.request().url())
    const offset = Number(url.searchParams.get('offset'))
    const limit = Number(url.searchParams.get('limit'))
    offsets.push(offset)
    const logs = Array.from({ length: count }, (_, i) => ({ execution_id: `log-${i}`, execution_status: 'success',
      scheduled_fire_time: '2026-10-31T01:00:00', started_at: '2026-10-31T01:00:00', finished_at: '2026-10-31T01:00:01',
      exit_code: 0, stdout: `Output ${i}`, stderr: null, message: null, output_truncated: false }))
    return route.fulfill({ json: { logs: logs.slice(offset, offset + limit), has_more: offset + limit < count, total_count: count } })
  })
  await login(page)
  await page.goto('/master/batch/schedule')
  await expect(page.getByText('1-1定義 / 検索結果 1定義', { exact: true })).toBeVisible()
  await page.locator('button:not([disabled])').filter({ hasText: '実行ログ' }).first().click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('1-20件 / 検索結果 25件', { exact: true })).toBeVisible()
  count = 5
  await dialog.getByRole('button', { name: '次のページへ' }).click()
  await expect(dialog.getByText('1-5件 / 検索結果 5件', { exact: true })).toBeVisible()
  await expect(dialog.getByRole('button', { name: '前のページへ' })).toBeDisabled()
  expect(offsets).toContain(20)
  expect(offsets.at(-1)).toBe(0)
})
