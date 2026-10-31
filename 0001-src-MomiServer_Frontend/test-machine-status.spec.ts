import { test, expect, login } from './tests/frontend-fixtures'

test.beforeEach(async ({ page }) => {
  await login(page)
})

test('Machine Status page layout verification', async ({ page }, testInfo) => {
  await page.goto('/machine/status')
  await expect(page.getByRole('heading', { name: 'マシンステータス', exact: true })).toBeVisible()
  for (const label of ['全体ヘルススコア', 'リソース使用率', 'CPU温度', 'GPU温度', 'リアルタイム更新', 'Storage']) {
    await expect(page.getByText(label, { exact: true })).toBeVisible()
  }
  for (const title of ['Details', '使用率', 'ログ関連', 'ジョブ履歴', 'バックアップ履歴']) {
    await expect(page.getByRole('heading', { name: title, exact: true }).first()).toBeVisible()
  }
  await expect(page.getByRole('combobox')).toContainText('メインサーバ')
  await expect(page.getByText('CPU温度', { exact: true }).locator('..')).toContainText('未取得')
  await expect(page.getByText('GPU温度', { exact: true }).locator('..')).toContainText('未取得')
  await page.screenshot({ path: testInfo.outputPath('machine-status-full.png'), fullPage: true })
})

test('Machine Status with serverId param', async ({ page }, testInfo) => {
  await page.goto('/machine/status/1')
  await expect(page.getByRole('heading', { name: 'メインサーバ', exact: true })).toBeVisible()
  await expect(page.getByRole('combobox')).toBeDisabled()
  await page.screenshot({ path: testInfo.outputPath('machine-status-id1.png'), fullPage: true })
})

test('Machine Status treats omitted temperature metrics as unavailable', async ({ page }) => {
  await page.route('**/api/status/machines', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{
        id: 1, name: 'メインサーバ', status: 'online', cpu: 20, mem: 30,
        healthScore: 70, storage: [], worktime: '1 day', version: '1.0.0 / Linux',
        ipAddress: '192.168.0.2', hostname: 'server01', location: '自宅',
        metricsAvailable: true, observedAt: '2026-10-10T10:00:05+09:00',
      }]),
    })
  })

  await page.goto('/machine/status/1')

  await expect(page.getByText('CPU温度', { exact: true }).locator('..')).toContainText('未取得')
  await expect(page.getByText('GPU温度', { exact: true }).locator('..')).toContainText('未取得')
})

test('Machine Status reflects a WebSocket snapshot', async ({ page }) => {
  await page.routeWebSocket('**/ws/machines?ticket=*', socket => {
    socket.send(JSON.stringify({
      type: 'machine_update',
      generated_at: '2026-10-10T10:00:05+09:00',
      machines: [{
        id: 1, name: 'メインサーバ更新', status: 'maintenance', cpu: 20, mem: 40,
        healthScore: 70, cpuTemp: 50, gpuTemp: 45, storage: [], worktime: '1 day',
        version: '1.0.1 / Linux', ipAddress: '192.168.0.20', hostname: 'server01',
        location: '自宅', metricsAvailable: true, observedAt: '2026-10-10T10:00:05+09:00',
      }],
    }))
  })
  await page.goto('/machine/status')
  await page.getByRole('checkbox').press('Space')

  await expect(page.getByRole('heading', { name: 'メインサーバ更新', exact: true })).toBeVisible()
  await expect(page.getByText('Maintenance', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('30%', { exact: true })).toBeVisible()

  await page.getByRole('checkbox').press('Space')
  await expect(page.getByRole('heading', { name: 'メインサーバ', exact: true })).toBeVisible()
})
