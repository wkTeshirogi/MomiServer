import { test, expect, login } from './tests/frontend-fixtures'

for (const target of [
  { api: 'user', url: '/master/users', row: { id: 1, email: 'retained@example.com', role: 'ADMIN' }, text: 'retained@example.com' },
  { api: 'device', url: '/master/machine', row: { id: 1, devicename: 'Retained device', address: '127.0.0.1' }, text: 'Retained device' },
]) {
  test(`${target.api} list distinguishes initial failure and preserves rows on retry failure`, async ({ page }) => {
    let fail = true
    await page.route(`**/api/master/${target.api}/list`, route => route.fulfill(fail
      ? { status: 503, json: { message: 'Unavailable' } } : { json: [target.row] }))
    await login(page)
    await page.goto(target.url)
    await expect(page.getByRole('alert')).toContainText('取得に失敗しました。')
    fail = false
    await page.getByRole('button', { name: '再読込', exact: true }).click()
    await expect(page.getByText(target.text, { exact: true })).toBeVisible()
    fail = true
    await page.getByRole('button', { name: '再読込', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('取得に失敗しました。')
    await expect(page.getByText(target.text, { exact: true })).toBeVisible()
    fail = false
    await page.getByRole('button', { name: '再読込', exact: true }).click()
    await expect(page.getByRole('alert')).toHaveCount(0)
  })
}

test('client-side search resets the page even when the new result still has multiple pages', async ({ page }) => {
  await page.route('**/api/master/user/list', route => route.fulfill({ json: Array.from({ length: 125 }, (_, i) => ({
    id: i + 1, email: `${i < 80 ? 'matching' : 'other'}-${i}@example.com`, role: 'USER',
  })) }))
  await login(page)
  await page.goto('/master/users')
  await expect(page.getByText('1-20件 / 検索結果 125件', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '次のページへ' }).click()
  await expect(page.getByText('21-40件 / 検索結果 125件', { exact: true })).toBeVisible()
  await page.getByPlaceholder('メールアドレス・権限で検索...').fill('matching')
  await expect(page.getByText('1-20件 / 検索結果 80件 / 全 125件', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '前のページへ' })).toBeDisabled()
  await page.getByLabel('表示件数').selectOption('50')
  await expect(page.getByText('1-50件 / 検索結果 80件 / 全 125件', { exact: true })).toBeVisible()
})

test('security settings are read-only, reloadable, and responsive', async ({ page }, testInfo) => {
  let fail = false
  await page.route('**/api/master/common-settings/security', route => route.fulfill(fail
    ? { status: 503, json: { message: 'Unavailable' } }
    : { json: { accessTokenSeconds: 86400, refreshTokenSeconds: 86400, iotRateLimitEnabled: false,
      iotPerMinute: 60, externalInventoryHourlyLimit: 1234, loginFailureTolerance: 5, loginLockMinutes: 15 } }))
  await login(page)
  await page.goto('/master/common-settings')
  await page.getByRole('button', { name: /セキュリティ設定/ }).click()
  await expect(page.getByText('15 分', { exact: true })).toBeVisible()
  await expect(page.getByText('1,234 回 / キー / 時', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /保存/ })).toHaveCount(0)
  await expect(page.locator('main input, main select')).toHaveCount(0)
  await page.screenshot({ path: testInfo.outputPath('security-desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByText('15 分', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('security-mobile.png'), fullPage: true })
  fail = true
  await page.getByRole('button', { name: '再読込', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('セキュリティ設定の取得に失敗しました。')
  await expect(page.getByText('15 分', { exact: true })).toBeVisible()
  fail = false
  await page.getByRole('button', { name: '再読込', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveCount(0)
})

for (const role of ['USER', 'OPERATOR'] as const) {
  test.describe(`security settings ${role}`, () => {
    test.use({ authRole: role })
    test('cannot open the administrative settings page', async ({ page }) => {
      await login(page)
      await page.goto('/master/common-settings/security')
      await expect(page).toHaveURL(/\/index$/)
    })
  })
}

for (const failedApi of ['service', 'device']) {
  test(`service master preserves rows and reports ${failedApi} retrieval errors`, async ({ page }) => {
    let fail = false
    for (const api of ['service', 'device']) {
      await page.route(`**/api/master/${api}/list`, route => route.fulfill(fail && api === failedApi
        ? { status: 503, json: { message: 'Unavailable' } }
        : { json: api === 'service'
          ? [{ id: 1, view_name: 'Retained service', target_machine: 1, status: 'watch' }]
          : [{ id: 1, devicename: 'Retained machine', address: '127.0.0.1' }] }))
    }
    await login(page)
    await page.goto('/master/services')
    await expect(page.getByText('Retained service', { exact: true })).toBeVisible()
    fail = true
    await page.getByRole('button', { name: '再読込', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('取得に失敗しました。')
    await expect(page.getByText('Retained service', { exact: true })).toBeVisible()
    await expect(page.getByText('表示できるサービスはありません。')).toHaveCount(0)
    fail = false
    await page.getByRole('button', { name: '再読込', exact: true }).click()
    await expect(page.getByRole('alert')).toHaveCount(0)
  })
}

test('API log reload returns to the first page when the result shrinks', async ({ page }) => {
  let count = 25
  await page.route('**/api/logs/access?*', route => {
    const url = new URL(route.request().url())
    const offset = Number(url.searchParams.get('offset'))
    const limit = Number(url.searchParams.get('limit'))
    const rows = Array.from({ length: count }, (_, i) => ({ id: i + 1, request_id: `request-${i}`,
      occurred_at: '2026-10-26T10:00:00', http_method: 'GET', request_path: '/test',
      endpoint_pattern: null, status_code: 200, duration_ms: 1, source_type: 'web',
      principal: null, ip_address: null }))
    return route.fulfill({ json: { logs: rows.slice(offset, offset + limit),
      has_more: offset + limit < count, total_count: count } })
  })
  await login(page)
  await page.goto('/logs/api')
  await expect(page.getByText('1-20件 / 検索結果 25件', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '次のページへ' }).click()
  await expect(page.getByText('21-25件 / 検索結果 25件', { exact: true })).toBeVisible()
  count = 5
  await page.getByRole('button', { name: '再読込', exact: true }).click()
  await expect(page.getByText('1-5件 / 検索結果 5件', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '前のページへ' })).toBeDisabled()
})

test('notifications follow the cursor instead of changing offsets when a new row arrives', async ({ page }) => {
  let traversals = 0
  const rows = Array.from({ length: 125 }, (_, i) => ({ id: 125 - i, notification_id: `stable-${125 - i}`,
    title: `Stable ${125 - i}`, read_status: 'read', notification_type: 'system_notice',
    level: 'info', message: 'Stable message', source_type: 'system', createdAt: '2026-10-26T10:00:00' }))
  await page.route('**/api/notifications/page?*', route => {
    const url = new URL(route.request().url())
    expect(url.searchParams.has('offset')).toBe(false)
    if (!url.searchParams.has('cursor')) {
      traversals++
      return route.fulfill({ json: { items: rows.slice(0, 100), next_cursor: 'stable-boundary' } })
    }
    expect(url.searchParams.get('cursor')).toBe('stable-boundary')
    return route.fulfill({ json: { items: rows.slice(100), next_cursor: null } })
  })
  await login(page)
  await page.goto('/alerts')
  await expect(page.getByText('1-20件 / 検索結果 125件', { exact: true })).toBeVisible()
  await page.getByLabel('表示件数').selectOption('100')
  await expect(page.getByRole('row').filter({ hasText: 'Stable message' })).toHaveCount(100)
  await page.getByRole('button', { name: '次のページへ' }).click()
  await expect(page.getByRole('row').filter({ hasText: 'Stable message' })).toHaveCount(25)
  expect(traversals).toBe(1)
})
