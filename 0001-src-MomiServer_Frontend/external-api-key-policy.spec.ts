import { type Page } from '@playwright/test'
import { test, expect, login } from './tests/frontend-fixtures'
import { defaultExpiry } from './src/pages/Master/ApiKeys/page.utils'
import { type ExternalApiKey } from './src/api/externalApiKeyApi'

test.use({ timezoneId: 'Asia/Tokyo' })

const existingKey: ExternalApiKey = {
  id: 'existing-key', name: 'Warehouse production', keyPrefix: 'momi_example',
  scopes: ['inventory:read'], status: 'ACTIVE', expiresAt: null,
  lastUsedAt: '2026-10-20T00:00:00Z', createdAt: '2026-10-01T00:00:00Z',
  createdBy: 'admin@example.com', revokedAt: null, revokedBy: null,
}
const fakeSecret = 'momi_TEST_ONLY_NOT_A_REAL_KEY'

test('key issuance disables background page size and navigation while busy', async ({ page }) => {
  let release!: () => void
  const issueGate = new Promise<void>(resolve => { release = resolve })
  await openKeys(page, { issueGate })
  await page.route('**/api/external-api-keys', route => route.request().method() !== 'GET' ? route.fallback() : route.fulfill({
    json: Array.from({ length: 25 }, (_, i) => ({ ...existingKey, id: `key-${i}`, name: `Key ${i}` })),
  }))
  await page.getByRole('button', { name: '再読込', exact: true }).click()
  await expect(page.getByText('1-20件 / 検索結果 25件', { exact: true })).toBeVisible()
  await openIssue(page)
  await page.getByLabel('連携先名', { exact: true }).fill('Busy issuance')
  await page.getByRole('button', { name: '発行', exact: true }).click()
  try {
    await expect(page.getByLabel('表示件数')).toBeDisabled()
    await expect(page.getByRole('button', { name: '次のページへ' })).toBeDisabled()
    await expect(page.getByLabel('連携先名で検索')).toBeDisabled()
  } finally {
    release()
  }
  await expect(page.getByText(fakeSecret, { exact: true })).toBeVisible()
  await expect(page.getByLabel('表示件数')).toBeEnabled()
})

test('issuing a key invalidates an older pending list reload', async ({ page }) => {
  await openKeys(page)
  let release: () => void = () => {}
  let started = false
  let oldRequestAborted = false
  page.on('requestfailed', request => {
    if (new URL(request.url()).pathname === '/api/external-api-keys' && request.method() === 'GET') oldRequestAborted = true
  })
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/external-api-keys', async route => {
    if (route.request().method() !== 'GET') return route.fallback()
    started = true
    await gate
    // The obsolete HTTP request may already have been aborted by the client.
    await route.fulfill({ json: [existingKey] }).catch(() => {})
  })
  await page.getByRole('button', { name: '再読込', exact: true }).click()
  await expect.poll(() => started).toBe(true)
  await openIssue(page)
  await page.getByLabel('連携先名', { exact: true }).fill('Latest issued key')
  await page.getByRole('button', { name: '発行', exact: true }).click()
  await expect(page.getByText('Latest issued key', { exact: true })).toBeVisible()
  release()
  await expect.poll(() => oldRequestAborted).toBe(true)
  await expect(page.getByText('Latest issued key', { exact: true })).toBeVisible()
  await expect(page.getByText('1-3件 / 検索結果 3件', { exact: true })).toBeVisible()
})

async function openKeys(page: Page, options: { failIssue?: boolean; failRevoke?: boolean; issueGate?: Promise<void> } = {}) {
  const issued: { name: string; scopes: string[]; expiresAt: string | null }[] = []
  const revoked: string[] = []
  let keys = [existingKey, { ...existingKey, id: 'unused-key', name: 'Warehouse test', lastUsedAt: null }]
  await page.route('**/api/external-api-keys', async route => {
    if (route.request().method() === 'GET') return route.fulfill({ json: keys })
    if (options.issueGate) await options.issueGate
    if (options.failIssue) return route.fulfill({ status: 500, json: { message: 'Test issue failure' } })
    const input = route.request().postDataJSON()
    issued.push(input)
    const key = { ...existingKey, ...input, id: `issued-${issued.length}`, keyPrefix: 'momi_newtest', lastUsedAt: null }
    keys = [key, ...keys]
    return route.fulfill({ json: { key, secret: fakeSecret } })
  })
  await page.route('**/api/external-api-keys/*/revoke', async route => {
    if (options.failRevoke) return route.fulfill({ status: 500, json: { message: 'Test revoke failure' } })
    const id = new URL(route.request().url()).pathname.split('/').at(-2)!
    revoked.push(id)
    keys = keys.map(key => key.id === id ? { ...key, status: 'REVOKED' } : key)
    return route.fulfill({ json: keys.find(key => key.id === id) })
  })
  await login(page)
  await page.clock.setFixedTime(new Date())
  await page.goto('/master/api')
  await expect(page.getByText(existingKey.name, { exact: true })).toBeVisible()
  return { issued, revoked }
}

async function openIssue(page: Page) {
  await page.getByRole('button', { name: 'APIキー発行', exact: true }).click()
  await expect(page.getByRole('dialog', { name: 'APIキー発行', exact: true })).toBeVisible()
}

test('default expiry uses a local calendar year and clamps leap day', () => {
  expect(defaultExpiry(new Date(2026, 9, 24, 9, 30))).toBe('2027-10-24T09:30')
  expect(defaultExpiry(new Date(2027, 11, 31, 23, 59))).toBe('2028-12-31T23:59')
  expect(defaultExpiry(new Date(2028, 1, 29, 0, 5))).toBe('2029-02-28T00:05')
  expect(defaultExpiry(new Date(2027, 1, 28, 12, 0))).toBe('2028-02-28T12:00')
})

test('defaults to one year, sends a timed key and resets after issuance', async ({ page }) => {
  const { issued } = await openKeys(page)
  await openIssue(page)
  const expiry = page.getByLabel('有効期限', { exact: true })
  const expected = await page.evaluate(() => {
    const date = new Date()
    const year = date.getFullYear() + 1
    const month = date.getMonth() + 1
    return { year, month, day: Math.min(date.getDate(), new Date(year, month, 0).getDate()) }
  })
  const initial = await expiry.inputValue()
  expect(initial.slice(0, 10)).toBe(`${expected.year}-${String(expected.month).padStart(2, '0')}-${String(expected.day).padStart(2, '0')}`)
  await expect(page.getByLabel('無期限', { exact: true })).not.toBeChecked()
  await page.getByLabel('連携先名', { exact: true }).fill('Inventory integration')
  await page.getByLabel('在庫数量更新', { exact: true }).check()
  await expiry.fill('2099-01-15T10:20')
  await page.getByRole('button', { name: '発行', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '発行したAPIキー' })
  await expect(dialog.getByText(fakeSecret, { exact: true })).toBeVisible()
  await expect(dialog.getByRole('status')).toContainText('APIキーを発行しました。')
  await expect(page.getByRole('status').filter({ hasText: 'APIキーを発行しました。' })).toHaveCount(1)
  await dialog.getByRole('button', { name: '通知を閉じる', exact: true }).click()
  await expect(dialog.getByRole('status')).toHaveCount(0)
  await expect(dialog.getByText(fakeSecret, { exact: true })).toBeVisible()
  expect(issued).toEqual([{ name: 'Inventory integration', scopes: ['inventory:read', 'inventory:write'], expiresAt: '2099-01-15T01:20:00.000Z' }])
  await dialog.getByRole('button', { name: '閉じる', exact: true }).first().click()
  await expect(page.getByText(fakeSecret, { exact: true })).toHaveCount(0)
  await expect(page.getByText('APIキーを発行しました。', { exact: true })).toHaveCount(0)
  expect(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).not.toContain(fakeSecret)
  await openIssue(page)
  await expect(expiry).toHaveValue(initial)
  await expect(page.getByLabel('連携先名', { exact: true })).toHaveValue('')
  await expect(page.getByLabel('在庫参照', { exact: true })).toBeChecked()
  await expect(page.getByLabel('在庫数量更新', { exact: true })).not.toBeChecked()
})

test('unlimited expiry is explicit and resets for the next key', async ({ page }) => {
  const { issued } = await openKeys(page)
  await openIssue(page)
  const expiry = page.getByLabel('有効期限', { exact: true })
  const unlimited = page.getByLabel('無期限', { exact: true })
  await page.getByLabel('連携先名', { exact: true }).fill('Write-only integration')
  await page.getByLabel('在庫参照', { exact: true }).uncheck()
  await page.getByLabel('在庫数量更新', { exact: true }).check()
  await expiry.fill('2000-01-01T00:00')
  await unlimited.check()
  await expect(expiry).toBeDisabled()
  await unlimited.uncheck()
  await expect(expiry).toBeEnabled()
  await expect(expiry).toHaveValue('2000-01-01T00:00')
  await unlimited.check()
  await page.getByRole('button', { name: '発行', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '発行したAPIキー' })
  await expect(dialog).toBeVisible()
  expect(issued).toEqual([{ name: 'Write-only integration', scopes: ['inventory:write'], expiresAt: null }])
  await dialog.getByRole('button', { name: '閉じる', exact: true }).first().click()
  await openIssue(page)
  await expect(unlimited).not.toBeChecked()
  await expect(expiry).toBeEnabled()
  expect(new Date(await expiry.inputValue()).getTime()).toBeGreaterThan(Date.now())
  await page.getByRole('button', { name: 'キャンセル', exact: true }).click()
  await expect(page.getByRole('row').filter({ hasText: existingKey.name }).getByRole('cell', { name: '無期限', exact: true })).toBeVisible()
})

for (const invalid of ['', '2000-01-01T00:00']) {
  test(`rejects ${invalid ? 'past' : 'empty'} expiry inside the modal without posting`, async ({ page }) => {
    const { issued } = await openKeys(page)
    await openIssue(page)
    await page.getByLabel('連携先名', { exact: true }).fill('Invalid expiry')
    await page.getByLabel('有効期限', { exact: true }).fill(invalid)
    await page.getByRole('button', { name: '発行', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: 'APIキー発行', exact: true })
    const alert = dialog.getByRole('alert').filter({ hasText: '連携先名、権限、有効期限を確認してください。' })
    await expect(alert).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(1)
    expect(issued).toEqual([])
    expect((await alert.boundingBox())!.y).toBeLessThan((await dialog.getByLabel('連携先名', { exact: true }).boundingBox())!.y)
    await dialog.getByRole('button', { name: 'キャンセル', exact: true }).click()
    await expect(page.getByRole('alert')).toHaveCount(0)
  })
}

test('rejects missing scope and retains input after issue failure', async ({ page }) => {
  const { issued } = await openKeys(page, { failIssue: true })
  await openIssue(page)
  const dialog = page.getByRole('dialog', { name: 'APIキー発行', exact: true })
  await page.getByLabel('連携先名', { exact: true }).fill('Retry integration')
  await page.getByLabel('在庫参照', { exact: true }).uncheck()
  await page.getByRole('button', { name: '発行', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('連携先名、権限、有効期限を確認してください。')
  expect(issued).toEqual([])
  await page.getByLabel('在庫参照', { exact: true }).check()
  await page.getByLabel('無期限', { exact: true }).check()
  await page.getByRole('button', { name: '発行', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('Test issue failure')
  await expect(page.getByRole('alert')).toHaveCount(1)
  await expect(page.getByLabel('連携先名', { exact: true })).toHaveValue('Retry integration')
  await expect(page.getByLabel('無期限', { exact: true })).toBeChecked()
  await expect(page.getByLabel('有効期限', { exact: true })).toBeDisabled()
  await expect(dialog).toBeVisible()
  await expect(page.getByRole('dialog')).toHaveCount(1)
})

for (const failRevoke of [false, true]) {
  test(`revocation ${failRevoke ? 'failure' : 'success'} stays outside modals and does not reuse issuance notices`, async ({ page }) => {
    await openKeys(page, { failIssue: true, failRevoke })
    await openIssue(page)
    await page.getByLabel('連携先名', { exact: true }).fill('Failed integration')
    await page.getByRole('button', { name: '発行', exact: true }).click()
    const issueDialog = page.getByRole('dialog', { name: 'APIキー発行', exact: true })
    await expect(issueDialog.getByRole('alert')).toContainText('Test issue failure')
    await issueDialog.getByRole('button', { name: 'キャンセル', exact: true }).click()
    await page.getByRole('row').filter({ hasText: existingKey.name }).getByRole('button', { name: '無効化', exact: true }).click()
    const revokeDialog = page.getByRole('dialog', { name: 'APIキーの無効化', exact: true })
    await expect(revokeDialog.getByRole('alert')).toHaveCount(0)
    await expect(revokeDialog.getByRole('status')).toHaveCount(0)
    await expect(revokeDialog).not.toContainText('Test issue failure')
    await revokeDialog.getByRole('button', { name: '無効化する', exact: true }).click()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    const message = failRevoke ? 'Test revoke failure' : 'APIキーを無効化しました。'
    const notice = page.getByRole(failRevoke ? 'alert' : 'status').filter({ hasText: message })
    await expect(notice).toBeVisible()
    expect((await notice.boundingBox())!.y).toBeLessThan((await page.getByRole('heading', { name: '発行済みキー', exact: true }).boundingBox())!.y)
    await openIssue(page)
    await expect(issueDialog.getByRole('alert')).toHaveCount(0)
    await expect(issueDialog.getByRole('status')).toHaveCount(0)
    await expect(issueDialog).not.toContainText(message)
  })
}

test('issuance opens on demand and cancelling does not send a request', async ({ page }) => {
  const { issued } = await openKeys(page)
  await expect(page.getByLabel('連携先名', { exact: true })).toHaveCount(0)
  await openIssue(page)
  await page.getByLabel('連携先名', { exact: true }).fill('Draft integration')
  await page.getByRole('button', { name: 'キャンセル', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(issued).toEqual([])
  await openIssue(page)
  await expect(page.getByLabel('連携先名', { exact: true })).toHaveValue('Draft integration')
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
})

test('pending issuance cannot be closed or submitted again', async ({ page }) => {
  let release = () => {}
  const issueGate = new Promise<void>(resolve => { release = resolve })
  try {
    const { issued } = await openKeys(page, { issueGate })
    await openIssue(page)
    await page.getByLabel('連携先名', { exact: true }).fill('Pending integration')
    const request = page.waitForRequest(request => new URL(request.url()).pathname === '/api/external-api-keys' && request.method() === 'POST')
    await page.getByRole('button', { name: '発行', exact: true }).click()
    await request
    const dialog = page.getByRole('dialog', { name: 'APIキー発行', exact: true })
    await expect(dialog.getByRole('button', { name: '発行', exact: true })).toBeDisabled()
    await expect(dialog.getByRole('button', { name: 'キャンセル', exact: true })).toBeDisabled()
    await expect(dialog.getByLabel('連携先名', { exact: true })).toBeDisabled()
    await expect(dialog.getByLabel('無期限', { exact: true })).toBeDisabled()
    await page.keyboard.press('Escape')
    await dialog.getByRole('button', { name: '閉じる', exact: true }).click()
    await expect(dialog).toBeVisible()
    release()
    await expect(page.getByRole('dialog', { name: '発行したAPIキー', exact: true })).toBeVisible()
    expect(issued).toHaveLength(1)
  } finally {
    release()
  }
})

test('catalog lists external endpoints only and filters by the key scopes', async ({ page }) => {
  const { issued } = await openKeys(page)
  await page.getByRole('button', { name: '利用可能なAPI', exact: true }).click()
  const catalog = page.getByRole('dialog', { name: '利用可能なAPI', exact: true })
  await expect(catalog.getByRole('heading', { level: 3 })).toHaveCount(3)
  await expect(catalog).toContainText('/api/v1/external/inventory/stocks/{id}/operations')
  await expect(catalog).toContainText('X-API-Key')
  await expect(catalog).toContainText('inventory:read')
  await expect(catalog).toContainText('inventory:write')
  await expect(catalog).not.toContainText('/api/external-api-keys')
  await expect(catalog).not.toContainText(fakeSecret)
  await catalog.getByRole('button', { name: '閉じる', exact: true }).first().click()
  await page.getByRole('button', { name: `${existingKey.name}の利用可能なAPI`, exact: true }).click()
  await expect(catalog.getByRole('heading', { level: 3 })).toHaveCount(2)
  await expect(catalog).not.toContainText('inventory:write')
  await page.keyboard.press('Escape')
  await openIssue(page)
  await page.getByLabel('連携先名', { exact: true }).fill('Write-only catalog')
  await page.getByLabel('在庫参照', { exact: true }).uncheck()
  await page.getByLabel('在庫数量更新', { exact: true }).check()
  await page.getByRole('button', { name: '発行', exact: true }).click()
  const secret = page.getByRole('dialog', { name: '発行したAPIキー', exact: true })
  await expect(secret).toBeVisible()
  await expect(page.getByRole('dialog')).toHaveCount(1)
  await secret.getByRole('button', { name: '閉じる', exact: true }).first().click()
  await page.getByRole('button', { name: 'Write-only catalogの利用可能なAPI', exact: true }).click()
  await expect(catalog.getByRole('heading', { level: 3 })).toHaveCount(1)
  await expect(catalog).toContainText('入庫・出庫・棚卸')
  await expect(catalog).not.toContainText('inventory:read')
  expect(issued).toHaveLength(1)
  await catalog.getByRole('button', { name: '閉じる', exact: true }).first().click()
  await page.getByRole('row').filter({ hasText: 'Write-only catalog' }).getByRole('button', { name: '無効化', exact: true }).click()
  await page.getByRole('button', { name: '無効化する', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('button', { name: 'Write-only catalogの利用可能なAPI', exact: true }).click()
  await expect(catalog.getByRole('status')).toContainText('利用できません')
})

test('request body is expandable only for APIs that accept JSON', async ({ page }) => {
  await openKeys(page)
  await page.getByRole('button', { name: '利用可能なAPI', exact: true }).click()
  const catalog = page.getByRole('dialog', { name: '利用可能なAPI', exact: true })
  const body = catalog.locator('details')
  await expect(body).toHaveCount(1)
  await expect(body).not.toHaveAttribute('open')
  await expect(body.getByText('operationId', { exact: true })).not.toBeVisible()
  const summary = body.locator('summary')
  await summary.click()
  await expect(body).toHaveAttribute('open')
  await expect(body.getByText('application/json', { exact: true })).toBeVisible()
  const fields = body.locator('dl > div')
  await expect(fields).toHaveCount(4)
  for (const name of ['operationId', 'changeType', 'quantity']) {
    await expect(fields.filter({ has: page.getByText(name, { exact: true }) })).toContainText('必須')
  }
  await expect(fields.filter({ has: page.getByText('reason', { exact: true }) })).toContainText('任意')
  await expect(body).toContainText('255文字以下')
  await expect(body).toContainText('0〜2147483647')
  await expect(body).toContainText('inbound（入庫）、outbound（出庫）、stocktake（棚卸）')
  const example = JSON.parse(await body.locator('pre').innerText())
  expect(example).toEqual({ operationId: '550e8400-e29b-41d4-a716-446655440000', changeType: 'outbound', quantity: 1, reason: '外部連携による出庫' })
  await summary.focus()
  await page.keyboard.press('Enter')
  await expect(body).not.toHaveAttribute('open')
  await expect(body.getByText('operationId', { exact: true })).not.toBeVisible()
  await page.keyboard.press('Space')
  await expect(body.getByText('operationId', { exact: true })).toBeVisible()
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '利用可能なAPI', exact: true }).click()
  await expect(catalog.locator('details')).not.toHaveAttribute('open')
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: `${existingKey.name}の利用可能なAPI`, exact: true }).click()
  await expect(catalog.locator('details')).toHaveCount(0)
})

test('revoke confirmation identifies used and unused keys and supports cancellation', async ({ page }) => {
  const { revoked } = await openKeys(page)
  const row = page.getByRole('row').filter({ hasText: existingKey.name })
  await row.getByRole('button', { name: '無効化', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'APIキーの無効化', exact: true })
  await expect(dialog).toContainText(existingKey.name)
  await expect(dialog).toContainText(existingKey.keyPrefix)
  await expect(dialog).toContainText('2026/10/20 09:00:00')
  await dialog.getByRole('button', { name: 'キャンセル' }).click()
  expect(revoked).toEqual([])
  await page.getByRole('row').filter({ hasText: 'Warehouse test' }).getByRole('button', { name: '無効化', exact: true }).click()
  await expect(dialog).toContainText('未利用')
  await dialog.getByRole('button', { name: '無効化する', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: 'APIキーを無効化しました。' })).toBeVisible()
  expect(revoked).toEqual(['unused-key'])
  await expect(page.getByRole('row').filter({ hasText: 'Warehouse test' }).getByRole('button', { name: '無効化', exact: true })).toBeDisabled()
  await expect(row.getByRole('button', { name: '無効化', exact: true })).toBeEnabled()
})

test('failed revocation closes confirmation and shows the error above the form', async ({ page }) => {
  await openKeys(page, { failRevoke: true })
  await page.getByRole('row').filter({ hasText: existingKey.name }).getByRole('button', { name: '無効化', exact: true }).click()
  await page.getByRole('button', { name: '無効化する', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.getByRole('alert')).toContainText('Test revoke failure')
  await expect(page.getByRole('row').filter({ hasText: existingKey.name }).getByRole('button', { name: '無効化', exact: true })).toBeEnabled()
})

for (const role of ['USER', 'OPERATOR'] as const) {
  test.describe(`${role} boundary`, () => {
    test.use({ authRole: role })
    test('does not load the key management API', async ({ page }) => {
      await login(page)
      await page.goto('/master/api')
      await expect(page).toHaveURL(/\/index$/)
      // The fixture fails any unmocked API request, including key management.
    })
  })
}
