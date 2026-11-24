import { FakeStorage } from '../test/fakeStorage'
import type { SessionState } from '../session/SessionManager'
import { LOGIN_RETURN_KEY, LOGIN_RETURN_TTL_MS, PendingInviteStore } from './pendingInvite'

const T1 = 'A'.repeat(43)
const T2 = 'B'.repeat(43)

function make() {
  const storage = new FakeStorage()
  let now = 1_000_000
  const store = new PendingInviteStore({ storage: () => storage, now: () => now })
  return { storage, store, advance: (ms: number) => (now += ms), clock: () => now }
}

const signedIn = (userId: string, epoch = 0): SessionState => ({
  status: 'signedIn',
  user: { userId, displayName: '가상 사용자' },
  epoch,
})
const signedOut: SessionState = { status: 'signedOut' }

describe('offer', () => {
  it('registers once for the same token and replaces it for a different token', () => {
    const { store } = make()
    expect(store.offer(T1)).toBe('accepted')
    const first = store.getInvite()
    expect(store.offer(T1)).toBe('same')
    expect(store.getInvite()).toBe(first)
    expect(store.offer(T2)).toBe('accepted')
    expect(store.getInvite()?.token).toBe(T2)
    expect(store.getInvite()!.seq).toBeGreaterThan(first!.seq)
  })

  it('refuses to replace an invitation while its join request is running', () => {
    const { store } = make()
    store.offer(T1)
    store.setRedeeming(store.getInvite()!.seq, true)
    expect(store.offer(T2)).toBe('busy')
    expect(store.getInvite()?.token).toBe(T1)
    store.setRedeeming(store.getInvite()!.seq, false)
    expect(store.offer(T2)).toBe('accepted')
  })
})

describe('saveForLogin', () => {
  it('stores only version, token and expiry, valid for 10 minutes', () => {
    const { store, storage, clock } = make()
    store.offer(T1)
    expect(store.saveForLogin()).toBe(true)
    expect(storage.items.size).toBe(1)
    expect(JSON.parse(storage.items.get(LOGIN_RETURN_KEY)!)).toEqual({ v: 1, token: T1, expiresAt: clock() + LOGIN_RETURN_TTL_MS })
  })

  it('reports a storage failure instead of pretending it saved', () => {
    const { store, storage } = make()
    store.offer(T1)
    storage.failSet = true
    expect(store.saveForLogin()).toBe(false)
  })

  it('saves nothing without an invitation', () => {
    const { store, storage } = make()
    expect(store.saveForLogin()).toBe(false)
    expect(storage.items.size).toBe(0)
  })
})

describe('consumeLoginReturn', () => {
  function saved() {
    const ctx = make()
    ctx.store.offer(T1)
    ctx.store.saveForLogin()
    // A new page load after the Kakao round trip: a fresh store reading the same storage.
    const next = new PendingInviteStore({ storage: () => ctx.storage, now: ctx.clock })
    return { ...ctx, next }
  }

  it('restores the invitation and removes the entry before returning', () => {
    const { next, storage } = saved()
    expect(next.consumeLoginReturn()).toEqual({ kind: 'restored' })
    expect(next.getInvite()?.token).toBe(T1)
    expect(storage.items.size).toBe(0)
  })

  it('reads storage once: a second call returns the first result without touching storage again', () => {
    const { next, storage, clock } = saved()
    const first = next.consumeLoginReturn()
    storage.items.set(LOGIN_RETURN_KEY, JSON.stringify({ v: 1, token: T2, expiresAt: clock() + 1000 }))
    storage.removed.length = 0
    expect(next.consumeLoginReturn()).toBe(first)
    expect(next.getInvite()?.token).toBe(T1)
    expect(storage.removed).toHaveLength(0)
  })

  it('reports none when nothing was saved', () => {
    const { store } = make()
    expect(store.consumeLoginReturn()).toEqual({ kind: 'none' })
  })

  it('does not restore after 10 minutes (logical expiry) and still removes the entry', () => {
    const { next, storage, advance } = saved()
    advance(LOGIN_RETURN_TTL_MS)
    expect(next.consumeLoginReturn()).toEqual({ kind: 'failed', reason: 'expired' })
    expect(next.getInvite()).toBeNull()
    expect(storage.items.size).toBe(0)
    expect(next.getRestoreNotice()).toBe('expired')
  })

  it.each([
    ['not JSON', '{oops'],
    ['wrong version', JSON.stringify({ v: 2, token: T1, expiresAt: 1_000_000 + 1000 })],
    ['bad token', JSON.stringify({ v: 1, token: 'short', expiresAt: 1_000_000 + 1000 })],
    ['missing expiry', JSON.stringify({ v: 1, token: T1 })],
    ['expiry further than the 10 minute limit', JSON.stringify({ v: 1, token: T1, expiresAt: 1_000_000 + LOGIN_RETURN_TTL_MS + 5000 })],
    ['not an object', '"text"'],
  ])('rejects a damaged entry: %s', (_name, raw) => {
    const { store, storage } = make()
    storage.items.set(LOGIN_RETURN_KEY, raw)
    expect(store.consumeLoginReturn()).toEqual({ kind: 'failed', reason: 'invalid' })
    expect(store.getInvite()).toBeNull()
    expect(storage.items.size).toBe(0)
  })

  it('read failure: nothing restored, recovery notice set', () => {
    const { next, storage } = saved()
    storage.failGet = true
    expect(next.consumeLoginReturn()).toEqual({ kind: 'failed', reason: 'readFailed' })
    expect(next.getInvite()).toBeNull()
    expect(next.getRestoreNotice()).toBe('readFailed')
  })

  it('remove failure: the entry is NOT treated as consumed, so nothing is restored automatically', () => {
    const { next, storage } = saved()
    storage.failRemove = true
    expect(next.consumeLoginReturn()).toEqual({ kind: 'failed', reason: 'removeFailed' })
    expect(next.getInvite()).toBeNull()
    expect(storage.items.size).toBe(1)
    expect(next.getRestoreNotice()).toBe('removeFailed')
  })

  it('a whole-storage access failure (getter throws) is a read failure', () => {
    const store = new PendingInviteStore({
      storage: () => {
        throw new DOMException('blocked', 'SecurityError')
      },
    })
    expect(store.consumeLoginReturn()).toEqual({ kind: 'failed', reason: 'readFailed' })
  })

  it('a new offered link clears the recovery notice', () => {
    const { store, storage } = make()
    storage.items.set(LOGIN_RETURN_KEY, '{oops')
    store.consumeLoginReturn()
    expect(store.getRestoreNotice()).toBe('invalid')
    store.offer(T1)
    expect(store.getRestoreNotice()).toBeNull()
  })
})

describe('sweepStaleEntry', () => {
  it('removes a leftover entry and tolerates a failing removeItem', () => {
    const { store, storage } = make()
    storage.items.set(LOGIN_RETURN_KEY, 'x')
    store.sweepStaleEntry()
    expect(storage.items.size).toBe(0)
    storage.items.set(LOGIN_RETURN_KEY, 'x')
    storage.failRemove = true
    expect(() => store.sweepStaleEntry()).not.toThrow()
  })
})

describe('session following', () => {
  it('lets the first sign-in adopt an invitation received while signed out (normal return)', () => {
    const { store } = make()
    store.observeSession(signedOut)
    store.offer(T1)
    expect(store.getInvite()?.owner).toBeNull()
    store.observeSession(signedIn('user-a'))
    expect(store.getInvite()?.owner).toEqual({ userId: 'user-a', epoch: 0 })
  })

  it('binds an invitation opened while signed in to that account', () => {
    const { store } = make()
    store.observeSession(signedIn('user-a'))
    store.offer(T1)
    expect(store.getInvite()?.owner).toEqual({ userId: 'user-a', epoch: 0 })
  })

  it('drops an owned invitation on logout, session end and another tab logging out', () => {
    for (const ended of [signedOut, { status: 'signingOut' } as SessionState, { status: 'logoutFailed' } as SessionState]) {
      const { store, storage } = make()
      store.observeSession(signedIn('user-a'))
      store.offer(T1)
      storage.items.set(LOGIN_RETURN_KEY, 'leftover')
      store.observeSession(ended)
      expect(store.getInvite()).toBeNull()
      expect(storage.items.size).toBe(0)
    }
  })

  it('drops the invitation when a different account (or a new epoch) signs in', () => {
    const { store } = make()
    store.observeSession(signedIn('user-a', 0))
    store.offer(T1)
    store.observeSession(signedIn('user-b', 1))
    expect(store.getInvite()).toBeNull()

    store.offer(T2)
    store.observeSession(signedIn('user-b', 2))
    expect(store.getInvite()).toBeNull()
  })

  it('keeps an invitation across exchanging/restoring states and the same identity', () => {
    const { store } = make()
    store.observeSession(signedIn('user-a'))
    store.offer(T1)
    store.observeSession({ status: 'restoring' })
    store.observeSession({ status: 'exchanging' })
    store.observeSession(signedIn('user-a'))
    expect(store.getInvite()?.token).toBe(T1)
  })

  it('keeps an ownerless invitation when the signed-out state is simply observed (login page)', () => {
    const { store } = make()
    store.observeSession(signedOut)
    store.offer(T1)
    store.observeSession(signedOut)
    expect(store.getInvite()?.token).toBe(T1)
  })
})

describe('discard', () => {
  it('clears memory and storage', () => {
    const { store, storage } = make()
    store.offer(T1)
    store.saveForLogin()
    store.discard()
    expect(store.getInvite()).toBeNull()
    expect(storage.items.size).toBe(0)
  })

  it('still discards memory when removeItem fails', () => {
    const { store, storage } = make()
    store.offer(T1)
    storage.failRemove = true
    store.discard()
    expect(store.getInvite()).toBeNull()
  })
})
