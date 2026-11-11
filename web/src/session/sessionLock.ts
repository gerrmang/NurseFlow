/**
 * One lock for every call that changes the auth cookies: refresh, Kakao code exchange and logout.
 *
 * The refresh cookie is shared by all tabs and rotates on every refresh; presenting an already
 * rotated token revokes the whole session family. epoch/AbortController only protect JS state:
 * an aborted fetch can still have its Set-Cookie applied and the server still processes it. So
 * cookie-changing calls are never aborted; they are serialized here instead, across tabs via the
 * Web Locks API and within one tab as a fallback.
 */
export const SESSION_LOCK_NAME = 'nurseflow-web-session'

export interface SessionLock {
  run<T>(task: () => Promise<T>): Promise<T>
}

export type LockManagerLike = Pick<LockManager, 'request'>

export function createSessionLock(locks: LockManagerLike | undefined): SessionLock {
  if (locks && typeof locks.request === 'function') {
    // request() resolves with the callback's own promise result.
    return { run: <T>(task: () => Promise<T>) => locks.request(SESSION_LOCK_NAME, () => task()) as unknown as Promise<T> }
  }
  return createInTabLock()
}

/** Serializes tasks within one tab. Used when the Web Locks API is unavailable. */
export function createInTabLock(): SessionLock {
  let tail: Promise<unknown> = Promise.resolve()
  return {
    run<T>(task: () => Promise<T>): Promise<T> {
      const result = tail.then(() => task())
      tail = result.then(
        () => undefined,
        () => undefined,
      )
      return result
    },
  }
}
