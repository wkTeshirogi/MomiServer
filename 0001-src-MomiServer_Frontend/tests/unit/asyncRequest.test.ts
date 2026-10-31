import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createAsyncRequestScope } from '../../src/lib/asyncRequest.ts'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (cause: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

test('only the latest response commits, including when the old client ignores cancellation', async () => {
  const scope = createAsyncRequestScope()
  const old = deferred<string>()
  const values: string[] = []
  let signal: AbortSignal | undefined
  let settled = 0
  const first = scope.run(input => { signal = input; return old.promise }, {
    onSuccess: value => values.push(value), onError: () => assert.fail('stale failure'), onSettled: () => settled++,
  })
  await scope.run(async () => 'latest', { onSuccess: value => values.push(value), onError: () => assert.fail('failure') })
  assert.equal(signal?.aborted, true)
  old.resolve('old')
  assert.equal(await first, undefined)
  assert.deepEqual(values, ['latest'])
  assert.equal(settled, 0)
})

test('an old failure cannot replace a later success or stop its loading state', async () => {
  const scope = createAsyncRequestScope()
  const old = deferred<string>()
  const current = deferred<string>()
  let loading = false
  let error: unknown = null
  let value = ''
  const handlers = { onStart: () => { loading = true }, onSuccess: (next: string) => { value = next },
    onError: (cause: unknown) => { error = cause }, onSettled: () => { loading = false } }
  const first = scope.run(() => old.promise, handlers)
  const second = scope.run(() => current.promise, handlers)
  old.reject(new Error('old failure'))
  await first
  assert.equal(loading, true)
  assert.equal(error, null)
  current.resolve('latest')
  await second
  assert.equal(loading, false)
  assert.equal(value, 'latest')
})

test('unmount cancellation suppresses success, error and final-state callbacks', async () => {
  for (const failed of [false, true]) {
    const scope = createAsyncRequestScope()
    const pending = deferred<string>()
    const task = scope.run(() => pending.promise, {
      onSuccess: () => assert.fail('unmounted success'), onError: () => assert.fail('unmounted error'),
      onSettled: () => assert.fail('unmounted settled'),
    })
    scope.cancel(); scope.cancel()
    if (failed) pending.reject(new Error('late'))
    else pending.resolve('late')
    await task
  }
})

test('current failures and synchronous throws use the failure and settled handlers once', async () => {
  const scope = createAsyncRequestScope()
  const events: string[] = []
  const failure = new Error('test')
  await scope.run(() => { throw failure }, {
    onStart: () => events.push('start'), onSuccess: () => assert.fail('success'),
    onError: cause => { assert.equal(cause, failure); events.push('error') }, onSettled: () => events.push('settled'),
  })
  assert.deepEqual(events, ['start', 'error', 'settled'])
})

test('independent resources do not cancel one another and a cancelled lane can be reused', async () => {
  const first = createAsyncRequestScope()
  const second = createAsyncRequestScope()
  const pending = deferred<string>()
  const task = first.run(() => pending.promise, { onSuccess: value => assert.equal(value, 'first'), onError: () => assert.fail() })
  second.cancel()
  await second.run(async () => 'second', { onSuccess: value => assert.equal(value, 'second'), onError: () => assert.fail() })
  pending.resolve('first')
  assert.equal(await task, 'first')
})
