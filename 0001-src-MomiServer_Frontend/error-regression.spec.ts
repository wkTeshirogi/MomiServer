import { test, expect, login } from './tests/frontend-fixtures'
import type { Page, WebSocketRoute } from '@playwright/test'

function alertNavigation(page: Page) {
  return page.getByRole('complementary').getByRole('link', { name: /^アラート(?:\s+\d+)?$/ })
}

for (const [status, message] of [
  [400, '入力内容に誤りがあります'],
  [401, 'メールアドレスまたはパスワードが正しくありません'],
  [500, 'サーバーエラーが発生しました。しばらくしてから再試行してください'],
] as const) {
  test(`login HTTP ${status} displays the correct error and can retry`, async ({ page }, testInfo) => {
    let fail = true
    await page.route('**/api/auth/login', async route => {
      if (fail) await route.fulfill({ status, json: { message: 'Test failure' } })
      else await route.fallback()
    })
    await page.goto('/login')
    await page.getByLabel('メールアドレス', { exact: true }).fill('admin@example.com')
    await page.getByLabel('パスワード', { exact: true }).fill('test-only-password')
    const submit = page.getByRole('button', { name: 'ログイン', exact: true })
    await submit.click()
    await expect(page.getByRole('alert')).toContainText(message)
    await expect(submit).toBeEnabled()
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByLabel('メールアドレス', { exact: true })).toHaveValue('admin@example.com')
    if (status === 401) {
      await page.screenshot({ path: testInfo.outputPath('login-error-desktop.png'), fullPage: true })
    }
    fail = false
    await submit.click()
    await expect(page).toHaveURL(/\/index$/)
    await expect(page.getByRole('alert')).toHaveCount(0)
  })
}

test('notification retrieval failure is shown without an uncaught error', async ({ page }) => {
  await page.route('**/api/notifications?*', route =>
    route.fulfill({ status: 503, json: { message: 'Test unavailable' } }))
  await login(page)
  await expect(page.getByRole('alert')).toContainText('通知一覧の取得に失敗しました。')
  await expect(page.getByText('通知を読み込み中です。')).toHaveCount(0)
})

test('vehicle expiry notification appears on the top page and alerts with a vehicle link', async ({ page }) => {
  const items = [{
    id: 101, notification_id: 'vehicle-expiry-notice', read_status: 'unread',
    notification_type: 'vehicle_inspection_expiry', level: 'warning',
    title: '車検期限のお知らせ（1か月前）', message: 'テスト車両の車検期限が近づいています。',
    source_type: 'vehicle', source_id: '42', link_path: '/vehicles/42',
    createdAt: '2026-10-17T09:00:00+09:00',
  }]
  await page.route('**/api/notifications?*', route => route.fulfill({ json: items }))
  await page.route('**/api/notifications/page?*', route => route.fulfill({ json: { items, next_cursor: null } }))
  await login(page)
  await expect(page.getByText('車検期限のお知らせ（1か月前）', { exact: true })).toBeVisible()
  await expect(page.getByText('車検期限', { exact: true })).toBeVisible()
  await page.goto('/alerts')
  await expect(page.getByText('車検期限', { exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '関連画面を開く' })).toHaveAttribute('href', '/vehicles/42')
})

for (const role of ['ADMIN', 'OPERATOR', 'USER'] as const) {
  test.describe(`notification workflow ${role}`, () => {
    test.use({ authRole: role })
    test('read and dismiss update the list and sidebar count', async ({ page }) => {
      await login(page)
      await page.goto('/alerts')
      const navigation = alertNavigation(page)
      await expect(navigation).toContainText('1')
      await page.getByRole('button', { name: '既読', exact: true }).click()
      await expect(page.getByRole('button', { name: '既読', exact: true })).toHaveCount(0)
      await expect(navigation).not.toContainText('1')
      await page.reload()
      await expect(page.getByText('Test notification', { exact: true })).toBeVisible()
      await expect(page.getByRole('button', { name: '既読', exact: true })).toHaveCount(0)
      await page.getByRole('button', { name: '非表示', exact: true }).click()
      await expect(page.getByText('表示できる通知はありません。')).toBeVisible()
      await page.reload()
      await expect(page.getByText('Test notification', { exact: true })).toHaveCount(0)
    })
  })
}

for (const [action, label, message] of [
  ['read', '既読', '通知の既読更新に失敗しました。'],
  ['dismiss', '非表示', '通知の非表示更新に失敗しました。'],
] as const) {
  test(`notification ${action} failure preserves the row and count and allows retry`, async ({ page }) => {
    let fail = true
    await page.route(`**/api/notifications/test-notice/${action}`, async route => {
      if (fail) await route.fulfill({ status: 503, json: { message: 'Test unavailable' } })
      else await route.fallback()
    })
    await login(page)
    await page.goto('/alerts')
    const button = page.getByRole('button', { name: label, exact: true })
    await button.click()
    const banner = page.getByRole('alert')
    await expect(banner).toContainText(message)
    const row = page.getByRole('row').filter({ hasText: 'Test notification message' })
    await expect(row).toBeVisible()
    await expect(button).toBeEnabled()
    await expect(alertNavigation(page)).toContainText('1')
    expect((await banner.boundingBox())!.y).toBeLessThan((await row.boundingBox())!.y)
    fail = false
    await button.click()
    await expect(banner).toHaveCount(0)
    await expect(alertNavigation(page)).not.toContainText('1')
    if (action === 'dismiss') await expect(row).toHaveCount(0)
    else await expect(page.getByRole('button', { name: '既読', exact: true })).toHaveCount(0)
  })
}

test('unread count polling failure keeps the last confirmed badge', async ({ page }) => {
  await login(page)
  const navigation = alertNavigation(page)
  await expect(navigation).toContainText('1')
  await page.route('**/api/notifications/unread/count', route =>
    route.fulfill({ status: 503, json: { message: 'Test unavailable' } }))
  const response = page.waitForResponse('**/api/notifications/unread/count')
  await page.evaluate("window.dispatchEvent(new CustomEvent('notification:changed'))")
  await response
  await expect(navigation).toContainText('1')
})

test('notification updates disable conflicting actions until the request completes', async ({ page }) => {
  let finish: () => void = () => {}
  const pending = new Promise<void>(resolve => { finish = resolve })
  await page.route('**/api/notifications/test-notice/read', async route => {
    await pending
    await route.fallback()
  })
  await login(page)
  await page.goto('/alerts')
  await page.getByRole('button', { name: '既読', exact: true }).click()
  await expect(page.getByRole('button', { name: '既読', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '非表示', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '再読込', exact: true })).toBeDisabled()
  finish()
  await expect(page.getByRole('button', { name: '既読', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '非表示', exact: true })).toBeEnabled()
})

test('an older unread count response cannot overwrite the count after marking read', async ({ page }) => {
  await login(page)
  const navigation = alertNavigation(page)
  await expect(navigation).toContainText('1')
  let finish: () => void = () => {}
  const pending = new Promise<void>(resolve => { finish = resolve })
  let first = true
  await page.route('**/api/notifications/unread/count', async route => {
    if (!first) return route.fallback()
    first = false
    await pending
    await route.fulfill({ json: { unread_count: 1 } })
  })
  const request = page.waitForRequest('**/api/notifications/unread/count')
  await page.evaluate("window.dispatchEvent(new CustomEvent('notification:changed'))")
  await request
  await page.getByRole('button', { name: '詳細', exact: true }).first().click()
  await expect(navigation).not.toContainText('1')
  const staleResponse = page.waitForResponse('**/api/notifications/unread/count')
  finish()
  await staleResponse
  await expect(navigation).not.toContainText('1')
})

test('batch definition retrieval failure can be retried', async ({ page }) => {
  let fail = true
  await page.route('**/api/master/batch/list', route => fail
    ? route.fulfill({ status: 500, json: { message: 'Test failure' } })
    : route.fulfill({ json: [] }))
  await login(page)
  await page.goto('/master/batch/difinition')
  await expect(page.getByRole('alert')).toContainText('バッチ定義一覧の取得に失敗しました。')
  fail = false
  await page.getByRole('button', { name: '再読込', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '再読込', exact: true })).toBeEnabled()
})

test('batch definition conflict preserves input until the user reloads', async ({ page }) => {
  let version = 3
  await page.route('**/api/master/batch/detail?*', route => route.fulfill({ json: {
    id: 10, version, status: 'active', batch_name: 'daily-job', view_name: 'Daily job',
    description: '', enabled: true, category: 'system', tags: '', execution_type: 'command',
    execution_command: 'run.cmd', working_directory: '', timeout_seconds: 60, max_retry_count: 0,
    retry_interval_seconds: null, execution_user: '', required_role: '', priority: 5,
    allow_parallel_execution: false, log_retention_date: '2999-12-31', log_path: 'logs/daily.log',
    success_notification: 'none', failure_notification: 'none',
  } }))
  await page.route('**/api/master/batch/register', route =>
    route.fulfill({ status: 409, json: { code: 'OPTIMISTIC_LOCK_CONFLICT' } }))
  await page.route('**/api/master/batch/10/files', route =>
		route.fulfill({ json: { files: [], exceeds_limits: false } }))
  await page.route('**/api/master/common-settings/batch', route => route.fulfill({ json: {
    attachment_max_file_mib: 100, attachment_max_total_mib: 500, attachment_max_count: 20,
  } }))

  await login(page)
  await page.goto('/master/batch/difinition/10')
  const displayName = page.getByLabel('表示名', { exact: false })
  await displayName.fill('Edited name')
  await page.getByRole('button', { name: '保存', exact: true }).click()

  await expect(page.getByRole('alert')).toContainText('入力内容を保持しています')
  await expect(displayName).toHaveValue('Edited name')
  version = 4
  await page.getByRole('button', { name: '最新を再読込', exact: true }).click()
  await expect(displayName).toHaveValue('Daily job')
})

test('disabled batch definition can be logically deleted', async ({ page }) => {
  const detail = {
    id: 10, version: 3, status: 'active', batch_name: 'daily-job', view_name: 'Daily job',
    description: '', enabled: false, category: 'system', tags: '', execution_type: 'command',
    execution_command: 'run.cmd', working_directory: '', timeout_seconds: 60, max_retry_count: 0,
    retry_interval_seconds: null, execution_user: '', required_role: '', priority: 5,
    allow_parallel_execution: false, log_retention_date: '2999-12-31', log_path: 'logs/daily.log',
    success_notification: 'none', failure_notification: 'none',
  }
  await page.route('**/api/master/batch/detail?*', route => route.fulfill({ json: detail }))
  await page.route('**/api/master/batch/10/files', route =>
		route.fulfill({ json: { files: [], exceeds_limits: false } }))
  await page.route('**/api/master/common-settings/batch', route => route.fulfill({ json: {
    attachment_max_file_mib: 100, attachment_max_total_mib: 500, attachment_max_count: 20,
  } }))
  await page.route('**/api/master/batch/register', async route => {
    const body = route.request().postDataJSON()
    expect(body).toMatchObject({ id: 10, version: 3, enabled: false, status: 'deleted' })
    await route.fulfill({ json: { ...detail, version: 4, status: 'deleted' } })
  })
  await page.route('**/api/master/batch/list', route => route.fulfill({ json: [] }))
  page.once('dialog', dialog => dialog.accept())

  await login(page)
  await page.goto('/master/batch/difinition/10')
  await page.getByRole('button', { name: '削除', exact: true }).click()

  await expect(page).toHaveURL(/\/master\/batch\/difinition$/)
  await expect(page.getByRole('status')).toContainText('バッチ定義を削除しました。')
})

test('item detail initializes fetched values and keeps edits on rerender', async ({ page }) => {
  await page.route('**/api/master/item/detail?*', route => route.fulfill({ json: {
    id: 1, item_name: 'Test item', category: 'parts', unit: 'pcs', min_quantity: 2, status: 'active',
  } }))
  await login(page)
  await page.goto('/master/items/1')
  const name = page.getByLabel('品目名', { exact: false })
  await expect(name).toHaveValue('Test item')
  await name.fill('Edited item')
  await page.getByLabel('カテゴリ', { exact: false }).fill('updated')
  await expect(name).toHaveValue('Edited item')
})

test('service registration validates required fields before calling the API', async ({ page }) => {
  await page.route('**/api/master/device/list', route => route.fulfill({ json: [] }))
  await login(page)
  await page.goto('/master/services/new')
  await page.getByRole('button', { name: '保存', exact: true }).click()

  await expect(page.getByRole('alert')).toContainText('入力内容を確認してください。')
  await expect(page.getByText('systemdユニット名は必須です。')).toBeVisible()
  await expect(page.getByText('サービス名は必須です。')).toBeVisible()
  await expect(page.getByText('監視対象端末を選択してください。')).toBeVisible()
  await expect(page.getByText('ログパスは必須です。')).toBeVisible()
  await expect(page.getByText('管理状態を選択してください。')).toBeVisible()
})

test('machine registration validates required fields before calling the API', async ({ page }) => {
  await login(page)
  await page.goto('/master/machine/new')
  await page.getByRole('button', { name: '保存', exact: true }).click()

  await expect(page.getByRole('alert')).toContainText('入力内容を確認してください。')
  await expect(page.getByText('端末名は必須です。')).toBeVisible()
  await expect(page.getByText('アドレスは必須です。')).toBeVisible()
})

test('WebSocket reconnects after remote close but not after explicit disconnect', async ({ page }) => {
  const connections: WebSocketRoute[] = []
  await page.routeWebSocket('**/ws/machines?ticket=*', socket => { connections.push(socket) })
  await login(page)
  await page.goto('/machine/status')
  const live = page.getByRole('checkbox')
  await live.press('Space')
  await expect(live).toBeChecked()
  await expect.poll(() => connections.length).toBe(1)
  await expect(page.getByRole('combobox')).toBeDisabled()
  connections[0].close({ code: 1011, reason: 'Test remote close' })
  await expect.poll(() => connections.length, { timeout: 7000 }).toBe(2)
  await expect(page.getByRole('combobox')).toBeDisabled()
  await live.press('Space')
  await expect(live).not.toBeChecked()
  await expect(page.getByRole('combobox')).toBeEnabled()
  await page.waitForTimeout(3500)
  expect(connections).toHaveLength(2)
})

test('WebSocket ticket failure is shown at the top and retried', async ({ page }) => {
  let fail = true
  await page.route('**/api/status/websocket-ticket', route => fail
    ? route.fulfill({ status: 503, json: { message: 'Test unavailable' } })
    : route.fallback())
  await page.routeWebSocket('**/ws/machines?ticket=*', () => {})
  await login(page)
  await page.goto('/machine/status')
  await page.getByRole('checkbox').press('Space')

  const alert = page.getByRole('alert')
  await expect(alert).toContainText('リアルタイム更新に接続できません。再接続しています。')
  const card = page.getByRole('heading', { name: 'メインサーバ', exact: true })
  expect((await alert.boundingBox())!.y).toBeLessThan((await card.boundingBox())!.y)

  fail = false
  await expect(alert).toHaveCount(0, { timeout: 7000 })
})

test.describe('USER role', () => {
  test.use({ authRole: 'USER' })
  test('machine status is denied while service status remains available', async ({ page }) => {
    await login(page)
    await page.goto('/machine/status')
    await expect(page).toHaveURL(/\/index$/)
    await page.goto('/service/status/1')
    await expect(page.getByRole('heading', { name: '7Days To Die', exact: true })).toBeVisible()
  })
})

test.describe('session expiry', () => {
  test.use({ sessionLifetimeSeconds: 5 })
  test('expired tokens redirect to login without page navigation', async ({ page }) => {
    await login(page)
    await expect(page).toHaveURL(/\/login$/, { timeout: 10000 })
    expect(await page.evaluate(() => localStorage.getItem('access_token'))).toBeNull()
  })
})

test.describe('mobile error display', () => {
  test.use({ viewport: { width: 390, height: 844 } })
  test('login failure fits the viewport and stays above the inputs', async ({ page }, testInfo) => {
    await page.route('**/api/auth/login', route =>
      route.fulfill({ status: 401, json: { message: 'Test failure' } }))
    await page.goto('/login')
    const email = page.getByLabel('メールアドレス', { exact: true })
    await email.fill('admin@example.com')
    await page.getByLabel('パスワード', { exact: true }).fill('test-only-password')
    await page.getByRole('button', { name: 'ログイン', exact: true }).click()
    const alert = page.getByRole('alert')
    await expect(alert).toContainText('メールアドレスまたはパスワードが正しくありません')
    const bannerBounds = await alert.boundingBox()
    const inputBounds = await email.boundingBox()
    expect(bannerBounds).not.toBeNull()
    expect(inputBounds).not.toBeNull()
    expect(bannerBounds!.x).toBeGreaterThanOrEqual(0)
    expect(bannerBounds!.x + bannerBounds!.width).toBeLessThanOrEqual(390)
    expect(bannerBounds!.y + bannerBounds!.height).toBeLessThan(inputBounds!.y)
    await page.screenshot({ path: testInfo.outputPath('login-error-mobile.png'), fullPage: true })
  })
})
