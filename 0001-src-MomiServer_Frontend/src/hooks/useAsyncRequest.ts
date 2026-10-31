import { useEffect, useState } from 'react'
import { createAsyncRequestScope } from '../lib/asyncRequest'

/** Own one read-request lane. Mutations, authentication and socket lifecycles stay separate. */
export function useAsyncRequest() {
  const [scope] = useState(createAsyncRequestScope)
  useEffect(() => scope.cancel, [scope])
  return scope
}
