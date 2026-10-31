import { test, expect, login } from './tests/frontend-fixtures'
import type { Page } from '@playwright/test'
import { renewalExpiry } from './src/pages/Vehicles/Detail/page.utils'
import type { Vehicle } from './src/api/vehicleApi'

async function mockRenewal(page: Page, options: { archived?: boolean; failure?: number; historyFailure?: boolean } = {}) {
  let vehicle: Vehicle = {
    id: 1, display_name: 'Renewal vehicle', registration_number: 'TEST-001', chassis_number: 'CHASSIS-001',
    vehicle_name: 'Test car', inspection_expiry_date: '2028-02-29', issued_date: '2026-02-28',
    status: options.archived ? 'ARCHIVED' : 'VALID', days_remaining: 365,
    created_by: 'creator', updated_by: 'creator', createdAt: '2026-01-01T00:00:00', updatedAt: '2026-01-01T00:00:00',
  }
  const submissions: Record<string, string>[] = []
  let historyRequests = 0
  await page.route('**/api/vehicles/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/vehicles/1/inspection' && route.request().method() === 'PUT') {
      const body = route.request().postDataJSON()
      submissions.push(body)
      if (options.failure) {
        await route.fulfill({ status: options.failure, json: { message: 'Renewal failed' } })
      } else {
        vehicle = { ...vehicle, inspection_expiry_date: body.inspection_expiry_date,
          issued_date: body.issued_date ?? vehicle.issued_date, days_remaining: 1095, updated_by: 'operator' }
        await route.fulfill({ json: vehicle })
      }
    } else if (path === '/api/vehicles/1/history') {
      historyRequests++
      if (options.historyFailure && historyRequests > 1) {
        await route.fulfill({ status: 500, json: { message: 'History failed' } })
      } else {
        await route.fulfill({ json: submissions.length ? [{ id: 1, vehicle_id: 1, change_type: 'EXPIRY_UPDATED',
          previous_expiry_date: '2028-02-29', new_expiry_date: vehicle.inspection_expiry_date,
          changed_by: 'operator', changedAt: '2026-10-24T12:00:00' }] : [] })
      }
    } else if (path === '/api/vehicles/1' && route.request().method() === 'GET') {
      await route.fulfill({ json: vehicle })
    } else {
      await route.fallback()
    }
  })
  return submissions
}

test('calendar year calculation clamps leap days and rejects invalid dates', () => {
  expect([1, 2, 3].map(years => renewalExpiry('2028-02-29', years)))
    .toEqual(['2029-02-28', '2030-02-28', '2031-02-28'])
  expect(renewalExpiry('2026-11-01', 2)).toBe('2028-11-01')
  for (const date of ['', 'bad', '2026-02-29', '2026-13-01', '9999-12-31']) {
    expect(renewalExpiry(date, 2)).toBe('')
  }
  expect(renewalExpiry('2026-11-01', 4)).toBe('')
})

test.describe('renewal by OPERATOR', () => {
  test.use({ authRole: 'OPERATOR' })

  test('uses persisted expiry, defaults to two years and preserves unrelated unsaved edits', async ({ page }) => {
    const submissions = await mockRenewal(page)
    await login(page)
    await page.goto('/vehicles/1')
    await page.getByLabel(/^表示名/).fill('Unsaved name')
    await page.getByLabel(/^車検有効期間満了日/).fill('2035-01-01')
    await page.getByRole('button', { name: '車検更新', exact: true }).click()
    const modal = page.getByRole('dialog', { name: '車検更新' })
    await expect(modal.getByLabel('現在の満了日')).toHaveValue('2028-02-29')
    await expect(modal.getByLabel('更新年数')).toHaveValue('2')
    await expect(modal.getByLabel('新しい満了日')).toHaveValue('2030-02-28')
    for (const years of ['1', '3', '2']) {
      await modal.getByLabel('更新年数').selectOption(years)
      await expect(modal.getByLabel('新しい満了日')).toHaveValue(renewalExpiry('2028-02-29', Number(years)))
    }
    await modal.getByLabel('新しい満了日').fill('2030-03-01')
    await modal.getByLabel('交付年月日').fill('2028-02-20')
    await modal.getByRole('button', { name: '更新する' }).click()
    await expect(modal).toHaveCount(0)
    expect(submissions).toEqual([{ previous_expiry_date: '2028-02-29', inspection_expiry_date: '2030-03-01', issued_date: '2028-02-20' }])
    await expect(page.getByText('車検情報を更新しました。', { exact: true })).toBeVisible()
    await expect(page.getByLabel(/^表示名/)).toHaveValue('Unsaved name')
    await expect(page.getByLabel(/^車検有効期間満了日/)).toHaveValue('2030-03-01')
    await expect(page.getByRole('cell', { name: '期限更新', exact: true })).toBeVisible()
  })

  test('validation and cancellation do not send requests; reopening resets the modal', async ({ page }) => {
    const submissions = await mockRenewal(page)
    await login(page)
    await page.goto('/vehicles/1')
    await page.getByRole('button', { name: '車検更新', exact: true }).click()
    const modal = page.getByRole('dialog')
    await modal.getByLabel('新しい満了日').fill('2028-02-29')
    await modal.getByRole('button', { name: '更新する' }).click()
    await expect(modal.getByText('新しい満了日は現在の満了日より後の日付を指定してください。')).toBeVisible()
    expect(submissions).toEqual([])
    await modal.getByRole('button', { name: 'キャンセル' }).click()
    await page.getByRole('button', { name: '車検更新', exact: true }).click()
    await expect(modal.getByLabel('新しい満了日')).toHaveValue('2030-02-28')
    await expect(modal.getByRole('alert')).toHaveCount(0)
    await page.keyboard.press('Escape')
    await expect(modal).toHaveCount(0)
  })

  for (const code of [400, 409, 500]) {
    test(`HTTP ${code} stays in the modal and preserves input`, async ({ page }) => {
      await mockRenewal(page, { failure: code })
      await login(page)
      await page.goto('/vehicles/1')
      await page.getByRole('button', { name: '車検更新', exact: true }).click()
      const modal = page.getByRole('dialog')
      await modal.getByRole('button', { name: '更新する' }).click()
      await expect(modal.getByRole('alert')).toBeVisible()
      await expect(modal.getByLabel('新しい満了日')).toHaveValue('2030-02-28')
      if (code === 409) await expect(modal.getByText(/画面を再読み込み/)).toBeVisible()
      await expect(page.getByText('車検情報を更新しました。', { exact: true })).toHaveCount(0)
    })
  }

  test('history refresh failure does not report a failed renewal', async ({ page }) => {
    await mockRenewal(page, { historyFailure: true })
    await login(page)
    await page.goto('/vehicles/1')
    await page.getByRole('button', { name: '車検更新', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: '更新する' }).click()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await expect(page.getByText('車検情報を更新しました。', { exact: true })).toBeVisible()
    await expect(page.getByText('車検更新は完了しましたが、車検期限更新履歴を再取得できませんでした。')).toBeVisible()
  })

  test('pending renewal blocks closing and duplicate requests; blank issued date is omitted', async ({ page }) => {
    await mockRenewal(page)
    let release: () => void = () => {}
    const responseGate = new Promise<void>(resolve => { release = resolve })
    let requests = 0
    await page.route('**/api/vehicles/1/inspection', async route => {
      requests++
      expect(route.request().postDataJSON()).not.toHaveProperty('issued_date')
      await responseGate
      await route.fallback()
    })
    await login(page)
    await page.goto('/vehicles/1')
    await page.getByRole('button', { name: '車検更新', exact: true }).click()
    const modal = page.getByRole('dialog')
    await modal.getByLabel('交付年月日').fill('')
    await modal.getByRole('button', { name: '更新する' }).click()
    try {
      await expect(modal.getByRole('button', { name: '更新中...' })).toBeDisabled()
      await expect(modal.getByRole('button', { name: 'キャンセル' })).toBeDisabled()
      await expect(modal.getByRole('button', { name: '閉じる', exact: true })).toHaveCount(0)
      await page.keyboard.press('Escape')
      await modal.click({ position: { x: 2, y: 2 } })
      await expect(modal).toBeVisible()
      expect(requests).toBe(1)
    } finally {
      release()
    }
    await expect(modal).toHaveCount(0)
    await expect(page.getByLabel('交付年月日', { exact: true })).toHaveValue('2026-02-28')
  })
})

test('ADMIN can renew but new and archived vehicles have no renewal action', async ({ page }) => {
  await mockRenewal(page)
  await login(page)
  await page.goto('/vehicles/1')
  await expect(page.getByRole('button', { name: '車検更新', exact: true })).toBeVisible()
  await page.goto('/vehicles/new')
  await expect(page.getByRole('button', { name: '車検更新', exact: true })).toHaveCount(0)
  await mockRenewal(page, { archived: true })
  await page.goto('/vehicles/1')
  await expect(page.getByLabel(/^表示名/)).toBeDisabled()
  await expect(page.getByRole('button', { name: '車検更新', exact: true })).toHaveCount(0)
})

test.describe('USER renewal boundary', () => {
  test.use({ authRole: 'USER' })
  test('does not expose a renewal action', async ({ page }) => {
    await mockRenewal(page)
    await login(page)
    await page.goto('/vehicles/1')
    await expect(page.getByLabel(/^表示名/)).toBeDisabled()
    await expect(page.getByRole('button', { name: '車検更新', exact: true })).toHaveCount(0)
  })
})
