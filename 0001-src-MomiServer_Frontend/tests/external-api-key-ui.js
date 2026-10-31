// Run with playwright-cli run-code --filename tests/external-api-key-ui.js on a local Vite session.
async page => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  const base = new URL(page.url()).origin
  const fakeSecret = 'momi_DEMONSTRATION_ONLY_NOT_A_REAL_KEY'
  let failList = false
  let failRevoke = false
  let listRequests = 0
  let keys = Array.from({ length: 25 }, (_, index) => ({
    id: String(index + 1), name: `Warehouse ${index + 1}`, keyPrefix: `momi_demo${index}`,
    scopes: ['inventory:read'], status: 'ACTIVE', expiresAt: null, lastUsedAt: null,
    createdAt: '2026-10-20T00:00:00Z', createdBy: 'admin@example.com', revokedAt: null, revokedBy: null,
  }))

  await page.unrouteAll({ behavior: 'wait' })
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path === '/api/external-api-keys' && request.method() === 'GET') {
      listRequests++
      return route.fulfill(failList ? { status: 500, json: { message: 'Test list failure' } } : { json: keys })
    }
    if (path === '/api/external-api-keys' && request.method() === 'POST') {
      const key = { ...keys[0], ...request.postDataJSON(), id: '26', keyPrefix: 'momi_newdemo' }
      keys = [key, ...keys]
      return route.fulfill({ json: { key, secret: fakeSecret } })
    }
    if (path.endsWith('/revoke')) {
      if (failRevoke) return route.fulfill({ status: 500, json: { message: 'Test revoke failure' } })
      keys = keys.map(key => key.id === '26' ? { ...key, status: 'REVOKED' } : key)
      return route.fulfill({ json: keys[0] })
    }
    if (path.endsWith('/unread/count')) return route.fulfill({ json: { unread_count: 0 } })
    return route.fulfill({ json: [] })
  })

  async function setRole(role) {
    await page.evaluate(role => {
      const token = btoa(JSON.stringify({ alg: 'none' })) + '.' +
        btoa(JSON.stringify({ role, exp: Math.floor(Date.now() / 1000) + 3600 })) + '.'
      localStorage.setItem('access_token', token)
      localStorage.setItem('refresh_token', token)
      localStorage.setItem('user_email', 'api-key-ui@example.invalid')
    }, role)
  }

  await page.goto(`${base}/login`)
  await setRole('ADMIN')
  await page.goto(`${base}/master/api`)
  await page.getByRole('heading', { name: '外部APIキー管理' }).waitFor()
  await page.getByText('Warehouse 20', { exact: true }).waitFor()
  if (await page.getByText('Warehouse 21', { exact: true }).count()) throw Error('Initial page size must be 20')
  await page.getByLabel('表示件数', { exact: true }).selectOption('50')
  await page.getByText('Warehouse 25', { exact: true }).waitFor()
  await page.getByLabel('連携先名で検索').fill('Warehouse 25')
  if (await page.getByRole('button', { name: '無効化', exact: true }).count() !== 1) throw Error('Search failed')
  await page.getByLabel('連携先名で検索').fill('')

  await page.getByRole('button', { name: '利用可能なAPI', exact: true }).click()
  const catalog = page.getByRole('dialog', { name: '利用可能なAPI', exact: true })
  await catalog.getByText('X-API-Key', { exact: true }).waitFor()
  await page.screenshot({ path: 'output/playwright/api-keys-catalog-desktop.png', fullPage: true })
  await catalog.getByRole('button', { name: '閉じる', exact: true }).first().click()
  await page.getByRole('button', { name: 'APIキー発行', exact: true }).click()
  await page.getByLabel('連携先名', { exact: true }).fill('Inventory integration')
  await page.screenshot({ path: 'output/playwright/api-keys-issue-desktop.png', fullPage: true })
  await page.getByLabel('在庫数量更新', { exact: true }).check()
  await page.getByRole('button', { name: '発行', exact: true }).click()
  await page.getByText(fakeSecret, { exact: true }).waitFor()
  const issuedDialog = page.getByRole('dialog', { name: '発行したAPIキー', exact: true })
  await issuedDialog.getByRole('status').filter({ hasText: 'APIキーを発行しました。' }).waitFor()
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.screenshot({ path: 'output/playwright/api-keys-issued-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: 'output/playwright/api-keys-issued-mobile.png', fullPage: true, animations: 'disabled' })
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.getByRole('button', { name: '閉じる', exact: true }).first().click()
  if (await page.getByText(fakeSecret, { exact: true }).count()) throw Error('Secret remained visible after closing')
  const storage = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))
  if (storage.includes(fakeSecret)) throw Error('Secret must not be persisted in browser storage')

  failRevoke = true
  await page.getByRole('button', { name: '無効化', exact: true }).first().click()
  await page.getByRole('button', { name: '無効化する', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: 'Test revoke failure' }).waitFor()
  failRevoke = false
  await page.getByRole('button', { name: '無効化', exact: true }).first().click()
  await page.getByRole('button', { name: '無効化する', exact: true }).click()
  await page.getByText('APIキーを無効化しました。', { exact: true }).waitFor()
  if (!await page.getByRole('button', { name: '無効化', exact: true }).first().isDisabled()) {
    throw Error('Revoked key must not be enabled')
  }

  failList = true
  await page.getByRole('button', { name: '再読込', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: 'Test list failure' }).waitFor()
  const headingTop = (await page.getByRole('heading', { name: '発行済みキー', exact: true }).boundingBox()).y
  const noticeTop = (await page.getByRole('alert').filter({ hasText: 'Test list failure' }).boundingBox()).y
  if (noticeTop >= headingTop) throw Error('Notifications must precede the form')
  failList = false
  await page.getByRole('button', { name: '再読込', exact: true }).click()
  await page.getByText('Inventory integration', { exact: true }).waitFor()
  await page.getByRole('button', { name: '通知を閉じる' }).click()
  await page.evaluate(() => document.documentElement.classList.remove('dark'))
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: 'output/playwright/api-keys-mobile.png', fullPage: true })
  if (await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)) {
    throw Error('Mobile page must not overflow horizontally')
  }
  await page.getByRole('button', { name: '利用可能なAPI', exact: true }).click()
  await catalog.getByText('X-API-Key', { exact: true }).waitFor()
  await catalog.locator('summary').filter({ hasText: 'Request body' }).click()
  await catalog.getByText('operationId', { exact: true }).waitFor()
  await catalog.getByText('operationId', { exact: true }).scrollIntoViewIfNeeded()
  await page.screenshot({ path: 'output/playwright/api-keys-catalog-mobile.png', fullPage: true, animations: 'disabled' })
  if (await catalog.evaluate(element => element.scrollWidth > element.clientWidth)) throw Error('Catalog overflows on mobile')
  await page.evaluate(() => document.documentElement.classList.add('dark'))
  await catalog.getByRole('heading', { name: 'JSON例', exact: true }).scrollIntoViewIfNeeded()
  await page.screenshot({ path: 'output/playwright/api-keys-catalog-body-mobile-dark.png', fullPage: true, animations: 'disabled' })
  await page.evaluate(() => document.documentElement.classList.remove('dark'))
  await catalog.getByRole('button', { name: '閉じる', exact: true }).first().click()
  await page.getByRole('button', { name: 'APIキー発行', exact: true }).click()
  await page.getByRole('dialog', { name: 'APIキー発行', exact: true }).waitFor()
  await page.screenshot({ path: 'output/playwright/api-keys-issue-mobile.png', fullPage: true })
  await page.getByRole('button', { name: 'キャンセル', exact: true }).click()
  await page.getByRole('button', { name: '無効化', exact: true }).nth(1).click()
  const dialog = page.getByRole('dialog', { name: 'APIキーの無効化', exact: true })
  await dialog.getByText('momi_demo0', { exact: true }).waitFor()
  await dialog.getByText('未利用', { exact: true }).waitFor()
  await page.screenshot({ path: 'output/playwright/api-keys-revoke-mobile.png', fullPage: true })
  if (await dialog.evaluate(element => element.scrollWidth > element.clientWidth)) {
    throw Error('Revoke confirmation must fit the mobile dialog')
  }
  await page.evaluate(() => document.documentElement.classList.add('dark'))
  await page.screenshot({ path: 'output/playwright/api-keys-revoke-mobile-dark.png', fullPage: true })
  await dialog.getByRole('button', { name: 'キャンセル', exact: true }).click()
  await page.evaluate(() => document.documentElement.classList.remove('dark'))

  for (const role of ['OPERATOR', 'USER']) {
    const requestsBefore = listRequests
    await setRole(role)
    await page.goto(`${base}/master/api`)
    await page.waitForURL(`${base}/index`)
    if (requestsBefore !== listRequests) throw Error('Non-admins must not request API key data')
  }
  if (errors.length) throw Error(errors.join('\n'))
  await page.evaluate(() => localStorage.clear())
  await page.goto(`${base}/login`)
  console.log('PASS: issue, one-time display, revoke, errors, pagination, desktop/mobile, ADMIN-only route')
}
