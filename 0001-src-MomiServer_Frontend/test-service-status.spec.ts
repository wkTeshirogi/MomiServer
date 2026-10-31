import { test, expect, login } from './tests/frontend-fixtures'

test.beforeEach(async ({ page }) => {
  await login(page)
})

test('Service Status reflects a WebSocket snapshot', async ({ page }) => {
  await page.routeWebSocket('**/ws/services?ticket=*', socket => {
    socket.send(JSON.stringify({
      type: 'service_update',
      generated_at: '2026-10-10T10:00:05+09:00',
      services: [{
        id: 1, name: '7Days To Die Updated', status: 'online', cpu: 25, mem: 35,
        worktime: '1 day', version: '1.0.1', players: '3 / 16',
        address: '192.168.0.20:26900', custom_now: 3, custom_max: 16,
        custom_name: 'Players', pid: '1234', metricsAvailable: true,
        observedAt: '2026-10-10T10:00:05+09:00',
      }],
    }))
  })
  await page.goto('/service/status')
  await page.getByRole('checkbox').press('Space')

  await expect(page.getByRole('heading', { name: '7Days To Die Updated', exact: true })).toBeVisible()
  await expect(page.getByText('Online', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('25%', { exact: true }).first()).toBeVisible()
})
