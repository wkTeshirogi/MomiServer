import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const directory = path.dirname(fileURLToPath(import.meta.url))

function generate(catalog) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'momi-message-catalog-'))
  try {
    const backend = path.join(root, 'backend')
    const script = path.join(backend, 'tools/messages/generate-message-catalog.mjs')
    const source = path.join(backend, 'src/main/resources/message-catalog/messages.ja.json')
    fs.mkdirSync(path.dirname(script), { recursive: true })
    fs.mkdirSync(path.dirname(source), { recursive: true })
    fs.copyFileSync(path.join(directory, 'generate-message-catalog.mjs'), script)
    fs.writeFileSync(source, JSON.stringify(catalog), 'utf8')
    const result = spawnSync(process.execPath, [script], { encoding: 'utf8' })
    assert.equal(result.status, 0, result.stderr)
    return fs.readFileSync(path.join(root, '0001-src-smarthomewebserverfrontend/src/messages/messageCatalog.ts'), 'utf8')
  } finally {
    const relative = path.relative(os.tmpdir(), root)
    assert.ok(relative && !relative.startsWith('..') && !path.isAbsolute(relative))
    fs.rmSync(root, { recursive: true, force: true })
  }
}

test('only explicitly public definitions are exported to the frontend', () => {
  const source = generate({ messages: {
    'test.public': { text: '公開文言', visibility: 'public' },
    'test.private': { text: 'BE専用文言', visibility: 'private' },
    'test.unspecified': { text: '未指定文言' },
  } })
  assert.match(source, /公開文言/)
  assert.doesNotMatch(source, /BE専用文言|未指定文言|test\.private|test\.unspecified/)
})

test('backend-only messages and Java bindings do not change the public output', () => {
  const catalog = JSON.parse(fs.readFileSync(path.join(directory, '../../src/main/resources/message-catalog/messages.ja.json'), 'utf8'))
  const publicOnly = { ...catalog, messages: Object.fromEntries(Object.entries(catalog.messages)
    .filter(([, definition]) => definition.visibility === 'public')) }
  assert.equal(generate(catalog), generate(publicOnly))
})

test('private text containing quotes and newlines never becomes frontend source', () => {
  const source = generate({ messages: {
    'test.private': { text: 'private\n"quoted" \\ value', visibility: 'private' },
  } })
  assert.doesNotMatch(source, /test\.private|quoted/)
})
