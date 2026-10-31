const { spawn } = require('node:child_process')
const { execFileSync } = require('node:child_process')
const { chromium } = require('playwright')

const baseUrl = 'http://127.0.0.1:5173'

function makeJwt(payload) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url')
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode(payload)}.`
}

async function waitForServer() {
  const deadline = Date.now() + 30_000
  while (Date.now() < deadline) {
    try {
      const response = await fetch(baseUrl)
      if (response.ok) return
    } catch {
      // wait
    }
    await new Promise(resolve => setTimeout(resolve, 250))
  }
  throw new Error('Vite server did not become ready')
}

async function main() {
  const server = spawn('cmd.exe', ['/c', 'npm.cmd', 'run', 'dev', '--', '--host', '127.0.0.1'], {
    cwd: process.cwd(),
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: false,
  })

  server.stdout.on('data', chunk => process.stdout.write(chunk))
  server.stderr.on('data', chunk => process.stderr.write(chunk))

  try {
    await waitForServer()

    const browser = await chromium.launch({ headless: true })
    const page = await browser.newPage({ viewport: { width: 1280, height: 800 } })
    const requests = []
    page.on('request', request => requests.push(request.url()))
    const token = makeJwt({
      exp: Math.floor(Date.now() / 1000) + 3600,
      role: 'ADMIN_ROLE',
    })

    await page.addInitScript(authToken => {
      localStorage.setItem('access_token', authToken)
      localStorage.setItem('refresh_token', authToken)
      localStorage.setItem('user_email', 'admin@example.com')
    }, token)

    await page.route('**/api/master/device/list**', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: 1,
          devicename: 'main-machine',
          address: '192.168.0.10',
          location: 'server-room',
          mqtt_client_id: 'machine-main',
          note: 'primary machine',
          status: '10',
          createdAt: '2026-07-20T00:00:00',
          updatedAt: '2026-07-20T01:00:00',
        },
      ]),
    }))

    await page.route('**/api/master/device/detail**', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 1,
        devicename: 'main-machine',
        address: '192.168.0.10',
        location: 'server-room',
        mqtt_client_id: 'machine-main',
        note: 'primary machine',
        status: '10',
        createdAt: '2026-07-20T00:00:00',
        updatedAt: '2026-07-20T01:00:00',
      }),
    }))

    await page.goto(`${baseUrl}/master/machine`)
    await page.waitForLoadState('networkidle')
    await page.getByText('稼働中').waitFor()
    await page.getByRole('heading', { name: '端末マスタ一覧' }).waitFor()
    await page.getByRole('link', { name: '詳細' }).click()
    await page.waitForURL('**/master/machine/1')
    await page.getByRole('heading', { name: '端末マスタ詳細' }).waitFor()
    try {
      await page.getByLabel('端末名').fill('main-machine-updated')
    } catch (error) {
      console.log('\nREQUESTS')
      console.log(requests.join('\n'))
      console.log('\nBODY')
      console.log(await page.locator('body').innerText())
      throw error
    }
    await page.locator('[data-slot="select-trigger"]').click()
    await page.getByText('メンテナンス').click()
    await page.getByRole('button', { name: '保存' }).click()
    await page.getByText('更新APIが未定義のため').waitFor()

    await Promise.race([
      browser.close(),
      new Promise(resolve => setTimeout(resolve, 2000)),
    ])
    console.log('\nPLAYWRIGHT_MACHINE_MASTER_SMOKE_OK')
  } finally {
    if (server.pid) {
      try {
        execFileSync('taskkill.exe', ['/PID', String(server.pid), '/T', '/F'], { stdio: 'ignore' })
      } catch {
        server.kill()
      }
    }
  }
}

main().then(() => {
  process.exit(0)
}).catch(error => {
  console.error(error)
  process.exit(1)
})
