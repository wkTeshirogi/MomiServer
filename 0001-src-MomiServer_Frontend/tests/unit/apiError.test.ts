import { test } from 'node:test'
import assert from 'node:assert/strict'
import { AxiosError } from 'axios'
import { getApiErrorMessage, getApiErrorStatus, getApiFieldErrors } from '../../src/lib/apiError.ts'

const responseError = (data: unknown, status = 400) => ({ response: { status, data }, request: { token: 'secret' } })

test('Axios response and generated SDK body have the same public message', () => {
  const data = { status: 400, message: '  入力内容を確認してください。  ', error: 'Bad Request' }
  const error = new AxiosError('transport-secret')
  Object.assign(error, responseError(data))
  assert.equal(getApiErrorMessage(error), '入力内容を確認してください。')
  assert.equal(getApiErrorMessage(data), '入力内容を確認してください。')
  assert.equal(getApiErrorStatus(error), 400)
  assert.equal(getApiErrorStatus(data), 400)
})

test('legacy text and error-property responses remain supported', () => {
  assert.equal(getApiErrorMessage(responseError(' 保存できませんでした。 ')), '保存できませんでした。')
  assert.equal(getApiErrorMessage(responseError({ message: '', error: '入力エラー' })), '入力エラー')
  assert.equal(getApiErrorMessage({ message: 42, error: '入力エラー' }), '入力エラー')
})

test('network errors never expose request, config, credentials or stack', () => {
  const error = new AxiosError('Bearer secret-and-request-details')
  Object.assign(error, { config: { headers: { Authorization: 'secret' } }, request: { password: 'secret' } })
  assert.equal(getApiErrorMessage(error, '通信に失敗しました。'), '通信に失敗しました。')
  assert.equal(getApiErrorMessage(new Error('password=secret')), null)
  assert.deepEqual(getApiFieldErrors(error), {})
  assert.equal(getApiErrorStatus(error), null)
})

for (const data of [null, undefined, 42, false, [], {}, '', '   ', '<html><body>Proxy failure</body></html>', '<!DOCTYPE html>', { message: '<script>secret</script>' }]) {
  test(`unexpected response uses the screen fallback: ${JSON.stringify(data)}`, () => {
    assert.equal(getApiErrorMessage(responseError(data), '処理に失敗しました。'), '処理に失敗しました。')
  })
}

test('field errors accept the API array form and do not expose rejected values', () => {
  const data = { message: 'Validation failed', fieldErrors: [
    { field: 'email', message: '形式が不正です。', rejectedValue: 'secret' },
    { field: 'email', message: '後続の文言' },
    { field: 'password', rejectedValue: 'secret' },
    { field: 'role', message: { secret: true } },
    null,
  ] }
  assert.deepEqual(getApiFieldErrors(responseError(data)), { email: '形式が不正です。' })
  assert.deepEqual(getApiFieldErrors(data), { email: '形式が不正です。' })
})

test('legacy field arrays and maps are normalized without mapping form-specific names', () => {
  assert.deepEqual(getApiFieldErrors({
    errors: { email: ['', 'メール形式エラー'], password: { defaultMessage: 'パスワード形式エラー' } },
    validationErrors: [{ name: 'user.role', reason: '権限エラー' }],
    details: [{ property: 'quantity', message: '数量エラー' }],
    violations: [{ path: 'storage_location', message: '場所エラー' }],
  }), { email: 'メール形式エラー', password: 'パスワード形式エラー', 'user.role': '権限エラー', quantity: '数量エラー', storage_location: '場所エラー' })
})

test('field names cannot change the result prototype', () => {
  const fields = getApiFieldErrors(JSON.parse('{"errors":{"__proto__":"Invalid","constructor":"Invalid"}}'))
  assert.equal(Object.getPrototypeOf(fields), Object.prototype)
  assert.equal(Object.hasOwn(fields, '__proto__'), true)
  assert.equal(fields.__proto__, 'Invalid')
})

test('malformed validation entries are ignored', () => {
  assert.deepEqual(getApiFieldErrors({ errors: [null, 42, 'secret', { message: 'No field' }, { field: ' ', message: 'No name' }, { field: 'email', message: '<html>Failure</html>' }] }), {})
})

test('HTTP status checks remain available independently from message extraction', () => {
  assert.equal(getApiErrorStatus(responseError({}, 409)), 409)
  assert.equal(getApiErrorStatus({ response: { status: '409' } }), null)
  assert.equal(getApiErrorStatus({ status: 0 }), null)
  assert.equal(getApiErrorStatus({ status: 600 }), null)
})
