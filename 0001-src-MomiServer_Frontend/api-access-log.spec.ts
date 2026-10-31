import { test, expect, login } from './tests/frontend-fixtures'

const log = {
  id: 1,
  request_id: '9dd5862b-3fcb-4e0d-a3d7-90f31074a9cb',
  occurred_at: '2026-10-12T10:15:30+09:00',
  http_method: 'GET',
  request_path: '/api/status/services/42',
  endpoint_pattern: '/api/status/services/{serverId}',
  status_code: 503,
  duration_ms: 1250,
  source_type: 'web',
  principal: 'operator@example.com',
  ip_address: '192.168.0.10',
  user_agent: 'Playwright',
  error_code: 'SERVICE_UNAVAILABLE',
} as const

const logs = Array.from({ length: 25 }, (_, index) => ({
  ...log,
  id: index + 1,
  request_id: index === 0 ? log.request_id : `9dd5862b-3fcb-4e0d-a3d7-${String(index).padStart(12, '0')}`,
}))

test.describe('API access logs', () => {
  test.use({ authRole: 'OPERATOR' })

  test('operator can search, page, and open log details', async ({ page }, testInfo) => {
    const searches: URL[] = []
    let holdNextPage = false
    let releaseNextPage: (() => void) | undefined
    await page.route('**/api/logs/access?*', async route => {
      const url = new URL(route.request().url())
      const offset = Number(url.searchParams.get('offset'))
      const limit = Number(url.searchParams.get('limit'))
      searches.push(url)
      if (holdNextPage && offset === 20) {
        await new Promise<void>(resolve => { releaseNextPage = resolve })
      }
      await route.fulfill({
        json: {
          logs: logs.slice(offset, offset + limit),
          has_more: offset + limit < logs.length,
          total_count: logs.length,
        },
      })
    })
    await page.route(`**/api/logs/access/${log.request_id}`, route => route.fulfill({ json: log }))

    await login(page)
    await page.goto('/logs/api')

    await expect(page.getByRole('heading', { name: 'APIログ', exact: true })).toBeVisible()
    await expect(page.getByText('/api/status/services/{serverId}', { exact: true }).first()).toBeVisible()
    await expect(page.getByText('503', { exact: true }).first()).toBeVisible()
    await expect(page.getByRole('button', { name: '詳細', exact: true })).toHaveCount(20)
    await expect(page.getByText('1-20件 / 検索結果 25件', { exact: true })).toBeVisible()
    expect(searches.at(-1)?.searchParams.get('offset')).toBe('0')
    expect(searches.at(-1)?.searchParams.get('limit')).toBe('20')

    const tableBounds = await page.getByRole('table').boundingBox()
    const summaryBounds = await page.getByText('1-20件 / 検索結果 25件', { exact: true }).boundingBox()
    const nextButtonBounds = await page.getByRole('button', { name: '次のページへ', exact: true }).boundingBox()
    expect(tableBounds).not.toBeNull()
    expect(summaryBounds).not.toBeNull()
    expect(nextButtonBounds).not.toBeNull()
    expect(summaryBounds!.y + summaryBounds!.height).toBeLessThanOrEqual(tableBounds!.y)
    expect(nextButtonBounds!.y).toBeGreaterThanOrEqual(tableBounds!.y + tableBounds!.height)

    await page.getByLabel('表示件数').selectOption('50')
    await expect(page.getByRole('button', { name: '詳細', exact: true })).toHaveCount(25)
    await expect(page.getByText('1-25件 / 検索結果 25件', { exact: true })).toBeVisible()
    expect(searches.at(-1)?.searchParams.get('offset')).toBe('0')
    expect(searches.at(-1)?.searchParams.get('limit')).toBe('50')

    await page.getByLabel('表示件数').selectOption('20')
    holdNextPage = true
    await page.getByRole('button', { name: '次のページへ', exact: true }).click()
    await expect.poll(() => releaseNextPage).toBeDefined()
    await expect(page.getByText('0-0件 / 検索結果 0件', { exact: true })).toBeVisible()
    await expect(page.getByText('21-40件 / 検索結果 25件', { exact: true })).toHaveCount(0)
    releaseNextPage?.()
    holdNextPage = false
    await expect(page.getByRole('button', { name: '詳細', exact: true })).toHaveCount(5)
    await expect(page.getByText('21-25件 / 検索結果 25件', { exact: true })).toBeVisible()
    expect(searches.at(-1)?.searchParams.get('offset')).toBe('20')
    expect(searches.at(-1)?.searchParams.get('limit')).toBe('20')
    await page.getByRole('button', { name: '前のページへ', exact: true }).click()
    await expect(page.getByRole('button', { name: '詳細', exact: true })).toHaveCount(20)
    await page.screenshot({ path: testInfo.outputPath('api-access-log-desktop.png'), fullPage: true })
    await page.getByLabel('パス・エンドポイント').fill('/api/status')
    await page.getByLabel('ステータス区分').selectOption('server_error')
    const requestCountBeforeSearch = searches.length
    await page.getByRole('button', { name: '検索', exact: true }).click()
    await expect.poll(() => searches.length).toBeGreaterThan(requestCountBeforeSearch)
    expect(searches.at(-1)?.searchParams.get('request_path')).toBe('/api/status')
    expect(searches.at(-1)?.searchParams.get('status_category')).toBe('server_error')

    await page.getByRole('button', { name: '詳細', exact: true }).first().click()
    await expect(page.getByRole('dialog')).toContainText(log.request_id)
    await expect(page.getByRole('dialog')).toContainText('SERVICE_UNAVAILABLE')
    await expect(page.getByRole('dialog')).not.toContainText('Authorization')
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({ path: testInfo.outputPath('api-access-log-mobile-detail.png'), fullPage: true })
  })
})
