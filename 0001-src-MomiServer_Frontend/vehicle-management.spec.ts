import { test, expect, login } from './tests/frontend-fixtures'
import type { Page } from '@playwright/test'

type VehicleFixture = {
  id: number
  display_name: string
  registration_number: string
  chassis_number: string
  vehicle_name: string
  model_code: string
  inspection_expiry_date: string
  status: string
  days_remaining: number
  created_by: string
  updated_by: string
  createdAt: string
  updatedAt: string
}

function vehicleFixture(): VehicleFixture {
  return {
    id: 1,
    display_name: '自家用車',
    registration_number: '品川 300 あ 12-34',
    chassis_number: 'TEST-CHASSIS-001',
    vehicle_name: 'テスト車',
    model_code: 'TEST-MODEL',
    inspection_expiry_date: '2026-11-01',
    status: 'DUE_SOON',
    days_remaining: 15,
    created_by: 'operator@example.com',
    updated_by: 'operator@example.com',
    createdAt: '2026-10-01T10:00:00+09:00',
    updatedAt: '2026-10-10T10:00:00+09:00',
  }
}

async function mockVehicleApi(page: Page, options: { historyFailureFromRequest?: number } = {}) {
  let vehicle = vehicleFixture()
  let historyRequestCount = 0
  const histories = [{
    id: 1,
    vehicle_id: 1,
    change_type: 'CREATED',
    previous_expiry_date: null,
    new_expiry_date: vehicle.inspection_expiry_date,
    changed_by: 'operator@example.com',
    changedAt: '2026-10-01T10:00:00+09:00',
  }]

  await page.route('**/api/vehicles**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (path === '/api/vehicles' && request.method() === 'GET') {
      await route.fulfill({ json: [vehicle] })
      return
    }
    if (path === '/api/vehicles' && request.method() === 'POST') {
      vehicle = { ...vehicle, ...request.postDataJSON(), id: 2 }
      await route.fulfill({ json: vehicle })
      return
    }
    if (/\/api\/vehicles\/\d+\/history$/.test(path) && request.method() === 'GET') {
      historyRequestCount += 1
      if (options.historyFailureFromRequest && historyRequestCount >= options.historyFailureFromRequest) {
        await route.fulfill({ status: 500, json: { message: 'History unavailable' } })
        return
      }
      await route.fulfill({ json: histories })
      return
    }
    if (/\/api\/vehicles\/\d+$/.test(path) && request.method() === 'GET') {
      await route.fulfill({ json: vehicle })
      return
    }
    if (/\/api\/vehicles\/\d+$/.test(path) && request.method() === 'PUT') {
      vehicle = { ...vehicle, ...request.postDataJSON() }
      await route.fulfill({ json: vehicle })
      return
    }
    if (/\/api\/vehicles\/\d+\/archive$/.test(path) && request.method() === 'POST') {
      vehicle = { ...vehicle, status: 'ARCHIVED' }
      await route.fulfill({ json: vehicle })
      return
    }

    await route.fulfill({ status: 501, json: { message: 'Unmocked vehicle request' } })
  })
}

async function mockVehicleImportApi(page: Page) {
  const imported = {
    id: 'd2719ac9-6d21-4ceb-a091-aedc6f8e66d1', status: 'PENDING_REVIEW', display_filename: 'uploaded-document.pdf', detected_media_type: 'application/pdf',
    extraction_method: 'PDF_TEXT', candidate: { display_name: 'テスト車', registration_number: '品川 300 あ 12-34', chassis_number: 'TEST-CHASSIS-001', vehicle_name: 'テスト車', model_code: 'TEST-MODEL', inspection_expiry_date: '2027-10-01' },
    warnings: ['車台番号を原本と照合してください。'], expires_at: '2026-10-18T10:00:00',
  }
  await page.route('**/api/vehicle-imports**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path === '/api/vehicle-imports' && request.method() === 'POST') { await route.fulfill({ json: imported }); return }
    if (path === `/api/vehicle-imports/${imported.id}` && request.method() === 'GET') { await route.fulfill({ json: imported }); return }
    if (path === `/api/vehicle-imports/${imported.id}/source` && request.method() === 'GET') {
      await route.fulfill({ contentType: 'application/pdf', body: '%PDF-1.4\nmock' }); return
    }
    if (path === `/api/vehicle-imports/${imported.id}/confirm` && request.method() === 'POST') {
      await route.fulfill({ json: { ...vehicleFixture(), ...request.postDataJSON(), id: 2 } }); return
    }
    if (path === `/api/vehicle-imports/${imported.id}/cancel` && request.method() === 'POST') { await route.fulfill({ status: 204 }); return }
    await route.fulfill({ status: 501, json: { message: 'Unmocked vehicle import request' } })
  })
}

test.describe('vehicle management for USER', () => {
  test.use({ authRole: 'USER' })

  test('can read vehicles but cannot register or edit', async ({ page }) => {
    await mockVehicleApi(page, { historyFailureFromRequest: 1 })
    await login(page)

    await expect(page.getByRole('link', { name: '車両管理' })).toBeVisible()
    await page.goto('/vehicles')
    await expect(page.getByRole('heading', { name: '車両管理一覧' })).toBeVisible()
    await expect(page.getByText('自家用車', { exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: '新規登録' })).toHaveCount(0)

    await page.getByRole('link', { name: '詳細' }).click()
    await expect(page.getByLabel('表示名')).toBeDisabled()
    await expect(page.getByLabel('表示名')).toHaveValue('自家用車')
    await expect(page.getByRole('button', { name: '保存' })).toHaveCount(0)
    await expect(page.getByRole('heading', { name: '車検期限更新履歴' })).toBeVisible()
    await expect(page.getByText('車検期限更新履歴の取得に失敗しました。')).toBeVisible()
    await expect(page.getByText('車両情報の取得に失敗しました。')).toHaveCount(0)

    await page.goto('/vehicles/new')
    await expect(page).toHaveURL(/\/index$/)
    await page.goto('/vehicles/import')
    await expect(page).toHaveURL(/\/index$/)
  })
})

test.describe('vehicle document import for OPERATOR', () => {
  test.use({ authRole: 'OPERATOR' })

  test('validates upload, reviews extracted fields, and cancels with a list notice', async ({ page }) => {
    await mockVehicleApi(page)
    await mockVehicleImportApi(page)
    await login(page)
    await page.goto('/vehicles')
    await expect(page.getByRole('link', { name: 'PDF・画像から登録' })).toBeVisible()
    await page.getByRole('link', { name: 'PDF・画像から登録' }).click()
    await page.getByRole('button', { name: 'アップロードして確認' }).click()
    await expect(page.getByText('ファイルを選択してください。')).toBeVisible()
    await page.locator('input[type=file]').setInputFiles({ name: 'inspection.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\nmock') })
    await page.getByRole('button', { name: 'アップロードして確認' }).click()
    await expect(page.getByRole('heading', { name: '解析結果の確認' })).toBeVisible()
    await expect(page.getByText('uploaded-document.pdf')).toBeVisible()
    await expect(page.getByText('抽出方式: PDFテキスト抽出')).toBeVisible()
    await page.getByRole('button', { name: 'キャンセル' }).click()
    await expect(page).toHaveURL(/\/vehicles$/)
    await expect(page.getByText('ファイル取込をキャンセルしました。')).toBeVisible()
  })

  test('confirms corrected fields and shows a detail success notice', async ({ page }) => {
    await mockVehicleApi(page)
    await mockVehicleImportApi(page)
    await login(page)
    await page.goto('/vehicles/import')
    await page.locator('input[type=file]').setInputFiles({ name: 'inspection.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\nmock') })
    await page.getByRole('button', { name: 'アップロードして確認' }).click()
    await page.getByLabel('表示名').fill('業務車両')
    await page.getByRole('button', { name: '確認して登録' }).click()
    await expect(page).toHaveURL(/\/vehicles\/2$/)
    await expect(page.getByText('車両を登録しました。')).toBeVisible()
  })
})

test.describe('vehicle management for OPERATOR', () => {
  test.use({ authRole: 'OPERATOR' })

  test('can register and update but cannot archive vehicles', async ({ page }) => {
    await mockVehicleApi(page, { historyFailureFromRequest: 2 })
    await login(page)
    await page.goto('/vehicles/new')

    await page.getByLabel('表示名').fill('業務車両')
    await page.getByLabel('登録番号').fill('品川 500 さ 56-78')
    await page.getByLabel('車台番号').fill('TEST-CHASSIS-002')
    await page.getByLabel('車名').fill('業務用テスト車')
    await page.getByLabel('車検有効期間満了日').fill('2027-10-01')
    await page.getByRole('button', { name: '保存' }).click()

    await expect(page).toHaveURL(/\/vehicles\/2$/)
    await expect(page.getByText('車両を登録しました。')).toBeVisible()
    await expect(page.getByRole('button', { name: '管理終了' })).toHaveCount(0)

    await page.getByLabel('表示名').fill('業務車両 更新')
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('車両情報を保存しました。')).toBeVisible()
    await expect(page.getByText('保存は完了しましたが、車検期限更新履歴を再取得できませんでした。')).toBeVisible()
    await expect(page.getByText('車両情報の保存に失敗しました。入力内容と重複候補を確認してください。')).toHaveCount(0)
  })
})

test.describe('vehicle management for ADMIN', () => {
  test.use({ authRole: 'ADMIN' })

  test('can archive a vehicle', async ({ page }) => {
    await mockVehicleApi(page, { historyFailureFromRequest: 2 })
    await login(page)
    await page.goto('/vehicles/1')

    page.once('dialog', dialog => dialog.accept())
    await page.getByRole('button', { name: '管理終了' }).click()
    await expect(page.getByText('車両を管理対象外にしました。')).toBeVisible()
    await expect(page.getByText('管理終了は完了しましたが、車検期限更新履歴を再取得できませんでした。')).toBeVisible()
    await expect(page.getByText('車両の管理終了に失敗しました。')).toHaveCount(0)
    await expect(page.getByText('管理終了', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '保存' })).toHaveCount(0)
  })
})
