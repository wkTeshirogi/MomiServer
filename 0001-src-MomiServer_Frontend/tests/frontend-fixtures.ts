import { test as base, expect, type Page } from '@playwright/test'

type TestOptions = { authRole: 'ADMIN' | 'OPERATOR' | 'USER' | 'BAN_USER'; sessionLifetimeSeconds: number }

function makeToken(role: TestOptions['authRole'], lifetime: number) {
  const encode = (value: Record<string, unknown>) => Buffer.from(JSON.stringify(value)).toString('base64url')
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({
    exp: Math.floor(Date.now() / 1000) + lifetime,
    role,
  })}.`
}

export const test = base.extend<TestOptions>({
  authRole: ['ADMIN', { option: true }],
  sessionLifetimeSeconds: [3600, { option: true }],
  page: async ({ page, authRole, sessionLifetimeSeconds }, runTest) => {
    const unexpectedRequests: string[] = []
    const pageErrors: string[] = []
    let unread = true
    let dismissed = false
    page.on('pageerror', error => pageErrors.push(error.message))
    // All API requests are intercepted so tests cannot modify the running backend.
    await page.route(url => url.pathname.startsWith('/api/'), async route => {
      const request = route.request()
      const path = new URL(request.url()).pathname
      if (path === '/api/users/me/settings' && request.method() === 'GET') {
        await route.fulfill({ json: { username: null, theme: 'dark' } })
      } else if (path === '/api/auth/login' && request.method() === 'POST') {
        const token = makeToken(authRole, sessionLifetimeSeconds)
        await route.fulfill({ json: { accessToken: token, refreshToken: token, expiresIn: sessionLifetimeSeconds } })
      } else if (path === '/api/auth/logout' && request.method() === 'POST') {
        await route.fulfill({ status: 204 })
      } else if (path === '/api/notifications/unread/count' && request.method() === 'GET') {
        await route.fulfill({ json: { unread_count: unread ? 1 : 0 } })
      } else if ((path === '/api/notifications/unread' || path === '/api/notifications' || path === '/api/notifications/page') && request.method() === 'GET') {
        const items = !dismissed && (unread || path !== '/api/notifications/unread') ? [{
          id: 1, notification_id: 'test-notice', read_status: unread ? 'unread' : 'read',
          notification_type: 'system_notice', level: 'info', title: 'Test notification',
          message: 'Test notification message', source_type: 'system',
          createdAt: '2026-10-03T10:00:00+09:00',
        }] : []
        await route.fulfill({ json: path === '/api/notifications/page' ? { items, next_cursor: null } : items })
      } else if (path === '/api/notifications/test-notice/read' && request.method() === 'POST') {
        unread = false
        await route.fulfill({ status: 204 })
      } else if (path === '/api/notifications/test-notice/dismiss' && request.method() === 'POST') {
        unread = false
        dismissed = true
        await route.fulfill({ status: 204 })
      } else if (path === '/api/status/machines' && request.method() === 'GET') {
        await route.fulfill({ json: [{
          id: 1, name: 'メインサーバ', status: 'online', cpu: 0, mem: 0,
          healthScore: 0, cpuTemp: null, gpuTemp: null, storage: [], worktime: '',
          version: '1.0.0 / Linux', ipAddress: '192.168.0.20',
          hostname: 'server01', location: '自宅', metricsAvailable: false,
          observedAt: '2026-10-10T10:00:00+09:00',
        }, {
          id: 2, name: 'ユーザ端末1', status: 'offline', cpu: 0, mem: 0,
          healthScore: 0, cpuTemp: null, gpuTemp: null, storage: [], worktime: '',
          version: '', ipAddress: '192.168.0.21', hostname: '', location: '',
          metricsAvailable: false,
        }] })
      } else if (path === '/api/status/services' && request.method() === 'GET') {
        await route.fulfill({ json: [{
          id: 1, name: '7Days To Die', status: 'unknown', cpu: 0, mem: 0,
          worktime: '', version: '1.0.0', players: '', address: '192.168.0.20',
          custom_now: 0, custom_max: 0, custom_name: 'none', pid: '',
          metricsAvailable: false, observedAt: '2026-10-10T10:00:00+09:00',
        }] })
      } else if (path === '/api/status/websocket-ticket' && request.method() === 'POST') {
        const channel = request.postDataJSON().channel as 'machines' | 'services'
        await route.fulfill({ json: {
          ticket: `test-${channel}-ticket`,
          websocket_path: `/ws/${channel}`,
          expires_at: '2026-10-10T10:00:30+09:00',
        } })
      } else {
        unexpectedRequests.push(`${request.method()} ${path}`)
        await route.fulfill({ status: 501, json: { message: 'Unmocked test request' } })
      }
    })
    await runTest(page)
    expect(unexpectedRequests, 'Unexpected API calls must be explicitly mocked').toEqual([])
    expect(pageErrors, 'Uncaught browser errors').toEqual([])
  },
})

export { expect }

export async function login(page: Page) {
  await page.goto('/login')
  await page.getByLabel('メールアドレス', { exact: true }).fill('admin@example.com')
  await page.getByLabel('パスワード', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'ログイン', exact: true }).click()
  await expect(page).toHaveURL(/\/index$/)
  await expect(page.getByRole('heading', { name: '通知一覧', exact: true })).toBeVisible()
}
