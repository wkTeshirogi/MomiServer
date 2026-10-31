import { test } from 'node:test'
import assert from 'node:assert/strict'
import { env } from 'node:process'
import { formatJapanDateTime } from '../../src/lib/dateTime.ts'

for (const timezone of ['UTC', 'Asia/Tokyo', 'America/Los_Angeles']) {
  test(`API datetime interpretation is independent of the host zone (${timezone})`, () => {
    const previous = env.TZ
    env.TZ = timezone
    try {
      assert.equal(formatJapanDateTime('2026-10-24T01:15:30.123Z'), '2026/10/24 10:15:30.123')
      assert.equal(formatJapanDateTime('2026-10-23T18:15:30.123-07:00'), '2026/10/24 10:15:30.123')
      assert.equal(formatJapanDateTime('2026-10-24T10:15:30.123+09:00'), '2026/10/24 10:15:30.123')
      assert.equal(formatJapanDateTime('2026-10-24T10:15:30.123456789'), '2026/10/24 10:15:30.123')
      assert.equal(formatJapanDateTime('2026-10-24 10:15:30'), '2026/10/24 10:15:30.000')
      assert.equal(formatJapanDateTime('2026-10-24T10:15'), '2026/10/24 10:15:00.000')
      assert.equal(formatJapanDateTime('2026-10-24T15:00:00Z'), '2026/10/25 00:00:00.000')
      assert.equal(formatJapanDateTime('2026-10-24'), '2026-10-24')
    } finally {
      if (previous === undefined) delete env.TZ
      else env.TZ = previous
    }
  })
}

test('precision and missing-value labels can match individual screens', () => {
  assert.equal(formatJapanDateTime('2026-10-24T10:15:30.987', { milliseconds: false }), '2026/10/24 10:15:30')
  assert.equal(formatJapanDateTime(null, { emptyValue: '未取得' }), '未取得')
  assert.equal(formatJapanDateTime('invalid', { emptyValue: '—' }), '—')
  assert.equal(formatJapanDateTime('2024-02-29'), '2024-02-29')
})

for (const value of [null, undefined, '', 'invalid', '<html>error</html>', '2026-02-29', '2026-02-30T10:00:00',
  '2026-10-24T24:00:00', '2026-10-24T10:60:00', '2026-10-24T10:00:60', '2026-10-24T10:00:00+99:00', '2026-10-24T10:15.5']) {
  test(`invalid or missing datetime falls back safely: ${String(value)}`, () => {
    assert.equal(formatJapanDateTime(value), '-')
  })
}
