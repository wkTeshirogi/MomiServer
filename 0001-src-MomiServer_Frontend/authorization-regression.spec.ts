import { test, expect, login } from './tests/frontend-fixtures'

test.describe('ADMIN navigation', () => {
  test.use({ authRole: 'ADMIN' })

  test('shows administrative, machine, monitoring, and Swagger links', async ({ page }) => {
    await login(page)

    await expect(page.getByRole('link', { name: 'マシンステータス' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'APIログ' })).toBeVisible()
    await expect(page.getByRole('link', { name: '車両管理' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'マスタ管理' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'GitLab Repository' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'モニタリング (Grafana)' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Prometheus' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Swagger UI' }))
      .toHaveAttribute('href', 'http://localhost:8080/swagger-ui/index.html')

    await page.goto('/service/status')
    await expect(page.getByRole('link', { name: '設定', exact: true })).toBeVisible()
    await page.goto('/machine/status')
    await expect(page.getByRole('link', { name: '設定', exact: true })).toBeVisible()
  })
})

test.describe('OPERATOR navigation', () => {
  test.use({ authRole: 'OPERATOR' })

  test('shows operational links but hides administrative controls', async ({ page }) => {
    await login(page)

    await expect(page.getByRole('link', { name: 'マシンステータス' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'APIログ' })).toBeVisible()
    await expect(page.getByRole('link', { name: '車両管理' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'マスタ管理' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'GitLab Repository' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'モニタリング (Grafana)' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Prometheus' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Swagger UI' })).toHaveCount(0)

    await page.goto('/service/status')
    await expect(page.getByRole('link', { name: '設定', exact: true })).toHaveCount(0)
    await page.goto('/machine/status')
    await expect(page.getByRole('link', { name: '設定', exact: true })).toHaveCount(0)
  })
})

test.describe('USER navigation', () => {
  test.use({ authRole: 'USER' })

  test('shows only general-use routes and hides external operations links', async ({ page }) => {
    await login(page)

    await expect(page.getByRole('link', { name: 'マシンステータス' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'APIログ' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: '車両管理' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'マスタ管理' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'GitLab Repository' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'モニタリング (Grafana)' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'Prometheus' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'Swagger UI' })).toHaveCount(0)

    await page.goto('/service/status')
    await expect(page).toHaveURL(/\/service\/status$/)
    await expect(page.getByRole('link', { name: '設定', exact: true })).toHaveCount(0)
    await page.goto('/machine/status')
    await expect(page).toHaveURL(/\/index$/)
    await page.goto('/logs/api')
    await expect(page).toHaveURL(/\/index$/)
  })
})

test('unimplemented navigation entries are not shown', async ({ page }) => {
  await login(page)

  await expect(page.getByRole('link', { name: 'ダッシュボード' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'ドキュメント' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'API リファレンス' })).toHaveCount(0)
})

test.describe('BAN_USER authentication', () => {
  test.use({ authRole: 'BAN_USER' })

  test('rejects login and does not persist tokens', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('メールアドレス', { exact: true }).fill('banned@example.com')
    await page.getByLabel('パスワード', { exact: true }).fill('test-only-password')
    await page.getByRole('button', { name: 'ログイン', exact: true }).click()

    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('alert')).toContainText(
      'メールアドレスまたはパスワードが正しくありません',
    )
    expect(await page.evaluate(() => localStorage.getItem('access_token'))).toBeNull()
    expect(await page.evaluate(() => localStorage.getItem('refresh_token'))).toBeNull()
  })
})
