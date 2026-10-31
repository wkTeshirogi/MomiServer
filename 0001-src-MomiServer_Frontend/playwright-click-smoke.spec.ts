import { test, expect, login } from './tests/frontend-fixtures'

test.beforeEach(async ({ page }) => {
  await login(page)
})

test('top page controls are clickable', async ({ page }) => {
  await page.goto('/index')

  await page.getByRole('button', { name: '詳細', exact: true }).first().click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await expect(dialog).toContainText('Test notification message')
  await dialog.getByRole('button', { name: '閉じる', exact: true }).first().click()
  await expect(dialog).toBeHidden()
  await expect(page.getByText('Test notification', { exact: true })).toBeVisible()
  await expect(page.getByRole('complementary').getByRole('link', { name: /^アラート(?:\s+\d+)?$/ })).not.toContainText('1')

  await page.getByRole('link', { name: '詳細', exact: true }).first().click()
  await expect(page).toHaveURL(/\/service\/status\/1$/)
  await expect(page.getByRole('heading', { name: '7Days To Die', exact: true })).toBeVisible()
})

test('status select changes selected server', async ({ page }) => {
  await page.goto('/machine/status')

  const select = page.getByRole('combobox')
  await expect(select).toContainText('メインサーバ')
  await select.click()
  await page.getByRole('option', { name: 'ユーザ端末1', exact: true }).click()
  await expect(select).toContainText('ユーザ端末1')
  await expect(page.getByRole('heading', { name: 'ユーザ端末1', exact: true })).toBeVisible()
})
