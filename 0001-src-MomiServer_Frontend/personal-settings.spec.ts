import { test, expect, login } from './tests/frontend-fixtures'
import type { Page } from '@playwright/test'

async function mockSettings(page: Page, options: { themeFailure?: boolean; profileFailure?: boolean; passwordFailure?: boolean; loadFailures?: number } = {}) {
  let settings = { username: 'Test user', theme: 'dark' }
  let loads = 0
  const submissions: { path: string; body: Record<string, string> }[] = []
  await page.route('**/api/users/me/settings**', async route => {
    const path = new URL(route.request().url()).pathname
    const request = route.request()
    if (path === '/api/users/me/settings' && request.method() === 'GET') {
      loads++
      if (loads <= (options.loadFailures ?? 0)) await route.fulfill({ status: 500, json: { message: 'Unavailable' } })
      else await route.fulfill({ json: settings })
      return
    }
    const body = request.postDataJSON()
    submissions.push({ path, body })
    if (path.endsWith('/profile') && request.method() === 'PUT') {
      if (options.profileFailure) await route.fulfill({ status: 500, json: { message: 'Failed' } })
      else { settings = { ...settings, username: body.username }; await route.fulfill({ json: settings }) }
    } else if (path.endsWith('/theme') && request.method() === 'PUT') {
      if (options.themeFailure) await route.fulfill({ status: 500, json: { message: 'Failed' } })
      else { settings = { ...settings, theme: body.theme }; await route.fulfill({ json: settings }) }
    } else if (path.endsWith('/password') && request.method() === 'POST') {
      if (options.passwordFailure) await route.fulfill({ status: 400, json: { message: '現在のパスワードが正しくありません。' } })
      else await route.fulfill({ status: 204 })
    } else await route.fallback()
  })
  return submissions
}

async function fillPassword(page: Page, next = 'new-password-456', confirmation = next) {
  await page.getByLabel('現在のパスワード', { exact: true }).fill('old-password-123')
  await page.getByLabel('新しいパスワード', { exact: true }).fill(next)
  await page.getByLabel('新しいパスワード（確認）', { exact: true }).fill(confirmation)
}

for (const role of ['ADMIN', 'OPERATOR', 'USER'] as const) {
  test.describe(`${role} personal settings`, () => {
    test.use({ authRole: role })
    test('opens from the menu and saves only the own username', async ({ page }) => {
      const submissions = await mockSettings(page)
      await login(page)
      await page.getByRole('button', { name: 'ユーザーメニュー' }).click()
      await page.getByRole('link', { name: '設定', exact: true }).click()
      await expect(page).toHaveURL(/\/settings$/)
      await expect(page.getByRole('heading', { name: '個人設定' })).toBeVisible()
      await expect(page.getByRole('textbox', { name: 'ユーザー名', exact: true })).toHaveValue('Test user')
      await page.getByRole('textbox', { name: 'ユーザー名', exact: true }).fill(' New display name ')
      await page.getByRole('button', { name: 'ユーザー名を保存' }).click()
      await expect(page.getByText('ユーザー名を保存しました。', { exact: true })).toBeVisible()
      expect(submissions).toEqual([{ path: '/api/users/me/settings/profile', body: { username: 'New display name' } }])
      await page.getByRole('button', { name: 'ユーザーメニュー' }).click()
      await expect(page.getByText('New display name', { exact: true })).toBeVisible()
      await page.reload()
      await expect(page.getByRole('textbox', { name: 'ユーザー名', exact: true })).toHaveValue('New display name')
      await expect(page.getByLabel('メールアドレス', { exact: true })).toHaveCount(0)
      await expect(page.getByRole('heading', { name: /通知設定/ })).toHaveCount(0)
    })
  })
}

test('blank name does not submit and a failed name save preserves input', async ({ page }) => {
  const submissions = await mockSettings(page, { profileFailure: true })
  await login(page)
  await page.goto('/settings')
  await page.getByRole('textbox', { name: 'ユーザー名', exact: true }).fill('   ')
  await page.getByRole('button', { name: 'ユーザー名を保存' }).click()
  await expect(page.getByText('ユーザー名は1～100文字で入力してください。')).toBeVisible()
  expect(submissions).toEqual([])
  await page.getByRole('textbox', { name: 'ユーザー名', exact: true }).fill('Retry name')
  await page.getByRole('button', { name: 'ユーザー名を保存' }).click()
  await expect(page.getByText('ユーザー名を保存できませんでした。')).toBeVisible()
  await expect(page.getByRole('textbox', { name: 'ユーザー名', exact: true })).toHaveValue('Retry name')
})

test('theme applies immediately, blocks conflicting changes and persists after reload', async ({ page }) => {
  await mockSettings(page)
  let release: () => void = () => {}
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/users/me/settings/theme', async route => { await gate; await route.fallback() })
  await login(page)
  await page.goto('/settings')
  await page.getByRole('textbox', { name: 'ユーザー名', exact: true }).fill('Unsaved name')
  await page.getByLabel('ライト', { exact: true }).check()
  try {
    await expect(page.locator('html')).not.toHaveClass(/dark/)
    await expect(page.getByLabel('ダーク', { exact: true })).toBeDisabled()
    await expect(page.getByRole('button', { name: 'ダークモードに切り替え' })).toBeDisabled()
    await expect(page.getByRole('button', { name: 'ユーザー名を保存' })).toBeDisabled()
  } finally { release() }
  await expect(page.getByText('テーマを保存しました。')).toBeVisible()
  await expect(page.getByRole('textbox', { name: 'ユーザー名', exact: true })).toHaveValue('Unsaved name')
  await page.reload()
  await expect(page.getByLabel('ライト', { exact: true })).toBeChecked()
  await expect(page.locator('html')).not.toHaveClass(/dark/)
  await page.getByRole('button', { name: 'ダークモードに切り替え' }).click()
  await expect(page.getByLabel('ダーク', { exact: true })).toBeChecked()
  await expect(page.locator('html')).toHaveClass(/dark/)
})

test('failed theme save restores the persisted theme and shows a top error', async ({ page }) => {
  await mockSettings(page, { themeFailure: true })
  await login(page)
  await page.goto('/settings')
  await page.getByLabel('ライト', { exact: true }).check()
  const error = page.getByText('テーマを保存できませんでした。変更前のテーマに戻しました。')
  await expect(error).toBeVisible()
  await expect(page.locator('html')).toHaveClass(/dark/)
  await expect(page.getByLabel('ダーク', { exact: true })).toBeChecked()
  const errorBox = await error.boundingBox()
  const formBox = await page.getByRole('textbox', { name: 'ユーザー名', exact: true }).boundingBox()
  expect(errorBox!.y).toBeLessThan(formBox!.y)
})

test('settings load failure can be retried without enabling edits prematurely', async ({ page }) => {
  const options = { loadFailures: Number.MAX_SAFE_INTEGER }
  await mockSettings(page, options)
  await login(page)
  await page.goto('/settings')
  await expect(page.getByText('個人設定を取得できませんでした。')).toBeVisible()
  await expect(page.getByRole('textbox', { name: 'ユーザー名', exact: true })).toHaveCount(0)
  options.loadFailures = 0
  await page.getByRole('button', { name: '再取得', exact: true }).click()
  await expect(page.getByRole('textbox', { name: 'ユーザー名', exact: true })).toHaveValue('Test user')
})

for (const scenario of ['short', 'mismatch', 'same', 'bytes']) {
  test(`invalid ${scenario} password does not submit`, async ({ page }) => {
    const submissions = await mockSettings(page)
    await login(page)
    await page.goto('/settings')
    const next = scenario === 'short' ? 'short' : scenario === 'same' ? 'old-password-123'
      : scenario === 'bytes' ? '日'.repeat(25) : 'new-password-456'
    await fillPassword(page, next, scenario === 'mismatch' ? 'different-password' : next)
    await page.getByRole('button', { name: 'パスワードを変更', exact: true }).click()
    await expect(page.getByRole('alert')).toBeVisible()
    expect(submissions).toEqual([])
    await expect(page).toHaveURL(/\/settings$/)
  })
}

test('incorrect current password keeps the authenticated screen and input', async ({ page }) => {
  await mockSettings(page, { passwordFailure: true })
  await login(page)
  await page.goto('/settings')
  await fillPassword(page)
  await page.getByRole('button', { name: 'パスワードを変更', exact: true }).click()
  await expect(page.getByText('現在のパスワードが正しくありません。')).toBeVisible()
  await expect(page).toHaveURL(/\/settings$/)
  await expect(page.getByLabel('新しいパスワード', { exact: true })).toHaveValue('new-password-456')
})

test('password success clears local credentials and asks for login without issuing logout or refresh', async ({ page }) => {
  const submissions = await mockSettings(page)
  const authCalls: string[] = []
  await page.route('**/api/auth/{logout,refresh}', async route => { authCalls.push(route.request().url()); await route.fulfill({ status: 500 }) })
  await login(page)
  await page.goto('/settings')
  await fillPassword(page)
  await page.getByRole('button', { name: 'パスワードを変更', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByText('パスワードを変更しました。再ログインしてください。')).toBeVisible()
  expect(submissions).toEqual([{ path: '/api/users/me/settings/password', body: {
    current_password: 'old-password-123', new_password: 'new-password-456', confirm_password: 'new-password-456',
  } }])
  expect(authCalls).toEqual([])
  expect(await page.evaluate(() => ({ ...localStorage }))).not.toHaveProperty('access_token')
  expect(await page.evaluate(() => ({ ...localStorage }))).not.toHaveProperty('refresh_token')
  expect(await page.evaluate(() => JSON.stringify({ ...localStorage }))).not.toContain('new-password-456')
})

test('a settings response from the previous login cannot change the new sessions theme', async ({ page }) => {
  let loads = 0
  let release: () => void = () => {}
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/users/me/settings', async route => {
    loads++
    if (loads === 1) {
      await gate
      await route.fulfill({ json: { username: 'Old session', theme: 'dark' } })
    } else await route.fulfill({ json: { username: 'New session', theme: 'light' } })
  })
  try {
    await login(page)
    await expect.poll(() => loads).toBe(1)
    await page.getByRole('button', { name: 'ユーザーメニュー' }).click()
    await page.getByRole('button', { name: 'ログアウト', exact: true }).click()
    await expect(page).toHaveURL(/\/login$/)
    await page.getByLabel('メールアドレス', { exact: true }).fill('admin@example.com')
    await page.getByLabel('パスワード', { exact: true }).fill('test-only-password')
    await page.getByRole('button', { name: 'ログイン', exact: true }).click()
    await expect(page).toHaveURL(/\/index$/)
    await page.getByRole('button', { name: 'ユーザーメニュー' }).click()
    await expect(page.getByText('New session', { exact: true })).toBeVisible()
    const response = page.waitForResponse(url => url.url().endsWith('/api/users/me/settings'))
    release()
    await response
    await expect(page.locator('html')).not.toHaveClass(/dark/)
    await expect(page.getByText('Old session', { exact: true })).toHaveCount(0)
  } finally { release() }
})

test.describe('same-account token rotation', () => {
  test.use({ sessionLifetimeSeconds: 5 })
  test('reschedules automatic logout after token expiry has been extended', async ({ page }) => {
    await page.clock.install()
    await login(page)
    await page.evaluate(() => {
      const payload = { role: 'ADMIN', exp: Math.floor(Date.now() / 1000) + 20 }
      const token = `e30.${btoa(JSON.stringify(payload))}.`
      localStorage.setItem('access_token', token)
      localStorage.setItem('refresh_token', token)
    })
    await page.getByRole('button', { name: 'ユーザーメニュー' }).click()
    await page.getByRole('link', { name: '設定', exact: true }).click()
    await page.clock.fastForward(6000)
    await expect(page).toHaveURL(/\/settings$/)
    await page.clock.fastForward(15000)
    await expect(page).toHaveURL(/\/login$/)
  })
})
