import { ApiError } from '../api/http'
import { FakeAuthServer, USER_A, USER_B, flush } from '../test/fakeAuthServer'
import { CALLBACK_PATH, SessionManager } from './SessionManager'
import { createInTabLock, createSessionLock, SESSION_LOCK_NAME, type SessionLock } from './sessionLock'
import { createLogoutChannel } from './logoutChannel'

const ORIGIN = 'http://localhost:5173'

function managerFor(server: FakeAuthServer, lock: SessionLock = createInTabLock(), redirect = vi.fn()) {
  // Call through the property so tests can spy on server.fetch after the manager is created.
  return new SessionManager({ auth: server.auth, lock, fetch: (input, init) => server.fetch(input, init), origin: ORIGIN, redirect })
}

/** A lock that does not serialize anything, to exercise the epoch guard on its own. */
const noLock: SessionLock = { run: (task) => task() }

async function signedIn(server: FakeAuthServer, manager: SessionManager) {
  server.signInAs(USER_A)
  manager.start('/wards', '')
  await vi.waitFor(() => expect(manager.getState().status).toBe('signedIn'))
}

describe('restore', () => {
  it('signs in from the refresh cookie', async () => {
    const server = new FakeAuthServer()
    const manager = managerFor(server)
    await signedIn(server, manager)
    expect(manager.getState()).toMatchObject({ status: 'signedIn', user: { userId: USER_A.userId } })
    expect(server.calls).toEqual(['refresh'])
  })

  it('shows the login page quietly when there is no session cookie', async () => {
    const server = new FakeAuthServer()
    const manager = managerFor(server)
    manager.start('/', '')
    await vi.waitFor(() => expect(manager.getState()).toEqual({ status: 'signedOut' }))
  })

  it('sends a single restore refresh even if start and restore are triggered twice', async () => {
    const server = new FakeAuthServer()
    server.signInAs(USER_A)
    const manager = managerFor(server)
    manager.start('/', '')
    manager.start('/', '')
    void manager.restore()
    await vi.waitFor(() => expect(manager.getState().status).toBe('signedIn'))
    expect(server.calls).toEqual(['refresh'])
    expect(server.isFamilyActive(USER_A.userId)).toBe(true)
  })

  it('reports a restore failure on network errors and keeps the cookie usable', async () => {
    const server = new FakeAuthServer()
    server.signInAs(USER_A)
    server.failNext('refresh', new ApiError(0, 'NETWORK'))
    const manager = managerFor(server)
    manager.start('/', '')
    await vi.waitFor(() => expect(manager.getState()).toEqual({ status: 'restoreFailed', code: 'NETWORK' }))
    await manager.restore()
    expect(manager.getState().status).toBe('signedIn')
  })

  it('reports a misconfigured proxy or origin as a restore failure', async () => {
    const server = new FakeAuthServer()
    server.failNext('refresh', new ApiError(403, 'WEB_CLIENT_REQUIRED'))
    const manager = managerFor(server)
    manager.start('/', '')
    await vi.waitFor(() => expect(manager.getState()).toEqual({ status: 'restoreFailed', code: 'WEB_CLIENT_REQUIRED' }))
  })
})

describe('authorized requests', () => {
  it('shares one refresh among concurrent 401s and retries each request once', async () => {
    const server = new FakeAuthServer()
    const manager = managerFor(server)
    await signedIn(server, manager)
    server.expireAllAccessTokens()
    const fetchSpy = vi.spyOn(server, 'fetch')

    const results = await Promise.all(Array.from({ length: 5 }, () => manager.api.getMe()))

    expect(results.map((me) => me.userId)).toEqual(Array(5).fill(USER_A.userId))
    expect(server.calls.filter((op) => op === 'refresh')).toHaveLength(2) // restore + one shared refresh
    expect(fetchSpy).toHaveBeenCalledTimes(10) // 5 first attempts + 5 single retries
    expect(server.isFamilyActive(USER_A.userId)).toBe(true)
  })

  it('stops after one retry when the refreshed token is still rejected', async () => {
    const server = new FakeAuthServer()
    const manager = managerFor(server)
    await signedIn(server, manager)
    const fetchSpy = vi.spyOn(server, 'fetch').mockImplementation(async () => new Response('{"code":"UNAUTHORIZED"}', { status: 401 }))

    await expect(manager.api.getMe()).rejects.toMatchObject({ code: 'SESSION_EXPIRED' })

    expect(fetchSpy).toHaveBeenCalledTimes(2)
    expect(server.calls.filter((op) => op === 'refresh')).toHaveLength(2) // restore + exactly one renewal
    expect(manager.getState()).toEqual({ status: 'signedOut', notice: 'expired' })
  })

  it('signs out with an expiry notice when the refresh cookie is rejected', async () => {
    const server = new FakeAuthServer()
    const manager = managerFor(server)
    await signedIn(server, manager)
    server.expireAllAccessTokens()
    server.cookie = null

    await expect(manager.api.getWards()).rejects.toMatchObject({ code: 'SESSION_EXPIRED' })
    expect(manager.getState()).toEqual({ status: 'signedOut', notice: 'expired' })
  })

  it('keeps the session when the refresh fails for network reasons', async () => {
    const server = new FakeAuthServer()
    const manager = managerFor(server)
    await signedIn(server, manager)
    server.expireAllAccessTokens()
    server.failNext('refresh', new ApiError(0, 'NETWORK'))

    await expect(manager.api.getMe()).rejects.toMatchObject({ code: 'NETWORK' })
    expect(manager.getState().status).toBe('signedIn')
  })

  it('refreshes ahead of a known expiry instead of waiting for a 401', async () => {
    const server = new FakeAuthServer()
    server.accessTtlMs = 10_000 // inside the 30 s skew
    const manager = managerFor(server)
    await signedIn(server, manager)
    const fetchSpy = vi.spyOn(server, 'fetch')
    await manager.api.getMe()
    expect(server.calls).toEqual(['refresh', 'refresh'])
    expect(fetchSpy).toHaveBeenCalledTimes(1)
  })

  it('adds the bearer token and never stores tokens in web storage', async () => {
    const server = new FakeAuthServer()
    const manager = managerFor(server)
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    await signedIn(server, manager)
    const fetchSpy = vi.spyOn(server, 'fetch')
    await manager.api.getWards()
    const [url, init] = fetchSpy.mock.calls[0]!
    expect(url).toBe('/api/v1/wards')
    expect(new Headers(init?.headers).get('Authorization')).toMatch(/^Bearer access-/)
    expect(init).toMatchObject({ credentials: 'same-origin', cache: 'no-store' })
    expect(setItem).not.toHaveBeenCalled()
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })
})

describe('Kakao callback', () => {
  it('does not send the start-up restore refresh on the callback route', async () => {
    const server = new FakeAuthServer()
    server.signInAs(USER_A)
    server.registerCode('code-b', USER_B)
    const manager = managerFor(server)

    manager.start(CALLBACK_PATH, '?code=code-b&state=s')
    expect(manager.getState().status).toBe('exchanging')
    await manager.completeLogin('code-b', 's')

    expect(server.calls).toEqual(['exchange'])
    expect(manager.getState()).toMatchObject({ status: 'signedIn', user: { userId: USER_B.userId } })
  })

  it('exchanges a code only once (StrictMode runs effects twice)', async () => {
    const server = new FakeAuthServer()
    server.registerCode('code-b', USER_B)
    const manager = managerFor(server)
    manager.start(CALLBACK_PATH, '?code=code-b&state=s')
    await Promise.all([manager.completeLogin('code-b', 's'), manager.completeLogin('code-b', 's')])
    expect(server.calls).toEqual(['exchange'])
  })

  it('waits for a running refresh before exchanging, and discards the old account result', async () => {
    const server = new FakeAuthServer()
    server.signInAs(USER_A)
    server.registerCode('code-b', USER_B)
    const manager = managerFor(server)
    const releaseRefresh = server.hold('refresh')
    manager.start('/wards', '') // restore for account A is now in flight
    await flush()

    const login = manager.completeLogin('code-b', 's')
    await flush()
    expect(server.calls).toEqual(['refresh']) // exchange queued behind the lock

    releaseRefresh()
    await login

    expect(server.calls).toEqual(['refresh', 'exchange'])
    expect(manager.getState()).toMatchObject({ status: 'signedIn', user: { userId: USER_B.userId } })
    const me = await manager.api.getMe()
    expect(me.userId).toBe(USER_B.userId)
  })

  it('keeps the new login when a late restore response arrives after the exchange (epoch guard)', async () => {
    const server = new FakeAuthServer()
    server.signInAs(USER_A)
    server.registerCode('code-b', USER_B)
    const manager = managerFor(server, noLock)
    const releaseRefresh = server.hold('refresh')
    manager.start('/wards', '')
    await flush()

    await manager.completeLogin('code-b', 's')
    expect(manager.getState()).toMatchObject({ status: 'signedIn', user: { userId: USER_B.userId } })

    // Account A's restore, sent with A's cookie, answers late. Without the lock its Set-Cookie can
    // even replace B's cookie in the jar; that is why the real lock exists. Here only the epoch
    // guard is under test: the in-memory session must stay B.
    releaseRefresh()
    await flush(20)

    expect(manager.getState()).toMatchObject({ status: 'signedIn', user: { userId: USER_B.userId } })
    const me = await manager.api.getMe()
    expect(me.userId).toBe(USER_B.userId)
  })

  it('shows the server code when the exchange is rejected', async () => {
    const server = new FakeAuthServer()
    const manager = managerFor(server)
    manager.start(CALLBACK_PATH, '?code=unknown&state=s')
    await manager.completeLogin('unknown', 's')
    expect(manager.getState()).toEqual({ status: 'signedOut', notice: 'INVALID_LOGIN_STATE' })
  })
})

describe('logout', () => {
  it('clears another tab with a still-valid access token before server logout finishes, without echoing logout', async () => {
    const server = new FakeAuthServer()
    const lock = createInTabLock()
    const listeners = new Set<() => void>()
    const publish = vi.fn(() => { for (const listener of listeners) listener() })
    const makeTab = () => new SessionManager({
      auth: server.auth, lock, fetch: server.fetch, origin: ORIGIN, redirect: vi.fn(),
      logoutChannel: { publish, subscribe: (listener) => { listeners.add(listener); return () => listeners.delete(listener) } },
    })
    const tabA = makeTab()
    const tabB = makeTab()
    await signedIn(server, tabA)
    tabB.start('/wards', '')
    await vi.waitFor(() => expect(tabB.getState().status).toBe('signedIn'))
    expect((await tabB.api.getMe()).userId).toBe(USER_A.userId)
    const release = server.hold('logout')

    const logout = tabA.logout()
    await flush()
    expect(tabA.getState().status).toBe('signingOut')
    expect(tabB.getState()).toEqual({ status: 'signedOut', notice: 'loggedOut' })
    await expect(tabB.api.getMe()).rejects.toMatchObject({ code: 'SESSION_EXPIRED' })
    expect(publish).toHaveBeenCalledTimes(1)
    expect(server.calls.filter((op) => op === 'logout')).toHaveLength(1)
    release()
    await logout
  })

  it('discards a late refresh in a tab that receives logout and still serializes server revocation', async () => {
    const server = new FakeAuthServer()
    const lock = createInTabLock()
    const listeners = new Set<() => void>()
    const makeTab = () => new SessionManager({
      auth: server.auth, lock, fetch: server.fetch, origin: ORIGIN, redirect: vi.fn(),
      logoutChannel: {
        publish: () => { for (const listener of listeners) listener() },
        subscribe: (listener) => { listeners.add(listener); return () => listeners.delete(listener) },
      },
    })
    const tabA = makeTab()
    const tabB = makeTab()
    await signedIn(server, tabA)
    const release = server.hold('refresh')
    tabB.start('/wards', '')
    await flush()
    const logout = tabA.logout()
    expect(tabB.getState()).toEqual({ status: 'signedOut', notice: 'loggedOut' })
    release()
    await logout
    expect(tabB.getState()).toEqual({ status: 'signedOut', notice: 'loggedOut' })
    expect(server.cookie).toBeNull()
    expect(server.completed.slice(-2)).toEqual(['refresh', 'logout'])
  })

  it('still calls server logout when cross-tab notification fails', async () => {
    const server = new FakeAuthServer()
    const manager = new SessionManager({
      auth: server.auth, lock: createInTabLock(), fetch: server.fetch, origin: ORIGIN, redirect: vi.fn(),
      logoutChannel: { publish: () => { throw new Error('channel unavailable') }, subscribe: () => () => undefined },
    })
    await signedIn(server, manager)
    await manager.logout()
    expect(server.cookie).toBeNull()
    expect(manager.getState()).toEqual({ status: 'signedOut', notice: 'loggedOut' })
  })

  it('waits for an in-flight refresh, does not abort it, then revokes the newest cookie', async () => {
    const server = new FakeAuthServer()
    const manager = managerFor(server)
    await signedIn(server, manager)
    server.expireAllAccessTokens()
    const releaseRefresh = server.hold('refresh')
    const request = manager.api.getMe().catch((error: unknown) => error)
    await flush()

    const logout = manager.logout()
    expect(manager.getState()).toEqual({ status: 'signingOut' })
    await flush()
    expect(server.calls).toEqual(['refresh', 'refresh']) // logout not sent yet

    releaseRefresh()
    await logout

    expect(server.calls).toEqual(['refresh', 'refresh', 'logout'])
    expect(server.completed).toEqual(['refresh', 'refresh', 'logout']) // refresh finished (not aborted) first
    expect(server.cookie).toBeNull()
    expect(server.isFamilyActive(USER_A.userId)).toBe(false)
    expect(manager.getState()).toEqual({ status: 'signedOut', notice: 'loggedOut' })
    expect(await request).toMatchObject({ code: 'ABORTED' })
  })

  it('does not restore the session after a completed logout and reload', async () => {
    const server = new FakeAuthServer()
    const manager = managerFor(server)
    await signedIn(server, manager)
    await manager.logout()

    const reloaded = managerFor(server)
    reloaded.start('/wards', '')
    await vi.waitFor(() => expect(reloaded.getState()).toEqual({ status: 'signedOut' }))
  })

  it('runs another tab refresh queued during logout after it, so that tab ends signed out', async () => {
    const server = new FakeAuthServer()
    const sharedLock = createInTabLock() // stands in for the cross-tab Web Lock
    const tabA = managerFor(server, sharedLock)
    await signedIn(server, tabA)
    const tabB = managerFor(server, sharedLock)
    tabB.start('/wards', '')
    await vi.waitFor(() => expect(tabB.getState().status).toBe('signedIn'))

    const releaseLogout = server.hold('logout')
    const logout = tabB.logout()
    await flush()
    server.expireAllAccessTokens()
    const tabARequest = tabA.api.getMe().catch((error: unknown) => error)
    await flush()
    expect(server.calls.at(-1)).toBe('logout') // tab A's refresh waits for the lock

    releaseLogout()
    await logout
    expect(await tabARequest).toMatchObject({ code: 'SESSION_EXPIRED' })
    expect(server.calls.slice(-2)).toEqual(['logout', 'refresh'])
    expect(tabA.getState()).toEqual({ status: 'signedOut', notice: 'expired' })
  })

  it('moves to logoutFailed without bringing the token back when the server call fails', async () => {
    const server = new FakeAuthServer()
    const manager = managerFor(server)
    await signedIn(server, manager)
    server.failNext('logout', new ApiError(0, 'NETWORK'))

    await manager.logout()

    expect(manager.getState()).toEqual({ status: 'logoutFailed' })
    await expect(manager.authorizedFetch('/me')).rejects.toMatchObject({ code: 'SESSION_EXPIRED' })
    await manager.logout()
    expect(manager.getState()).toEqual({ status: 'signedOut', notice: 'loggedOut' })
    expect(server.cookie).toBeNull()
  })

  it('joins a second logout click', async () => {
    const server = new FakeAuthServer()
    const manager = managerFor(server)
    await signedIn(server, manager)
    await Promise.all([manager.logout(), manager.logout()])
    expect(server.calls.filter((op) => op === 'logout')).toHaveLength(1)
  })

  it('ignores a restore request while signing out', async () => {
    const server = new FakeAuthServer()
    const manager = managerFor(server)
    await signedIn(server, manager)
    const release = server.hold('logout')
    const logout = manager.logout()
    await manager.restore()
    expect(manager.getState()).toEqual({ status: 'signingOut' })
    release()
    await logout
    expect(server.calls).toEqual(['refresh', 'logout'])
  })
})

describe('logout broadcast transport', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends only a logout marker and ignores unrelated payloads', () => {
    const postMessage = vi.fn()
    let receive!: (event: MessageEvent<unknown>) => void
    const removeEventListener = vi.fn()
    vi.stubGlobal('BroadcastChannel', class {
      postMessage = postMessage
      addEventListener(_type: string, listener: typeof receive) { receive = listener }
      removeEventListener = removeEventListener
    })
    const channel = createLogoutChannel()!
    const listener = vi.fn()
    const unsubscribe = channel.subscribe(listener)
    channel.publish()
    expect(postMessage).toHaveBeenCalledExactlyOnceWith({ type: 'logout' })
    for (const data of [null, 'logout', { type: 'login' }, { type: 'logout', token: 'fictional' }]) {
      receive(new MessageEvent('message', { data }))
    }
    expect(listener).not.toHaveBeenCalled()
    receive(new MessageEvent('message', { data: { type: 'logout' } }))
    expect(listener).toHaveBeenCalledTimes(1)
    unsubscribe()
    expect(removeEventListener).toHaveBeenCalledWith('message', receive)
  })

  it('keeps the existing server-only logout behavior when messaging is unavailable', () => {
    vi.stubGlobal('BroadcastChannel', undefined)
    expect(createLogoutChannel()).toBeUndefined()
  })
})

describe('login start', () => {
  it('sends the exact callback URI once and leaves for Kakao', async () => {
    const server = new FakeAuthServer()
    const redirect = vi.fn()
    const manager = managerFor(server, createInTabLock(), redirect)
    const startSpy = vi.spyOn(server.auth, 'startKakaoLogin')
    await Promise.all([manager.beginKakaoLogin(), manager.beginKakaoLogin()])
    expect(startSpy).toHaveBeenCalledTimes(1)
    expect(startSpy).toHaveBeenCalledWith('http://localhost:5173/auth/kakao/callback')
    expect(redirect).toHaveBeenCalledWith(expect.stringContaining('https://kauth.example.test/oauth/authorize'))
  })

  it('allows another attempt after a failure', async () => {
    const server = new FakeAuthServer()
    server.failNext('authorize', new ApiError(503, 'IDENTITY_PROVIDER_NOT_CONFIGURED'))
    const manager = managerFor(server)
    await expect(manager.beginKakaoLogin()).rejects.toMatchObject({ code: 'IDENTITY_PROVIDER_NOT_CONFIGURED' })
    await expect(manager.beginKakaoLogin()).resolves.toBeUndefined()
  })
})

describe('session lock', () => {
  it('uses the Web Locks API with the shared lock name when available', async () => {
    const request = vi.fn((_name: string, callback: () => Promise<unknown>) => callback())
    const lock = createSessionLock({ request } as unknown as LockManager)
    await expect(lock.run(async () => 42)).resolves.toBe(42)
    expect(request).toHaveBeenCalledWith(SESSION_LOCK_NAME, expect.any(Function))
  })

  it('falls back to an in-tab queue that runs tasks one at a time, even after a failure', async () => {
    const lock = createSessionLock(undefined)
    const order: string[] = []
    let releaseFirst!: () => void
    const first = lock.run(
      () =>
        new Promise<void>((resolve) => {
          order.push('first:start')
          releaseFirst = () => {
            order.push('first:end')
            resolve()
          }
        }),
    )
    const failing = lock.run(async () => {
      order.push('failing')
      throw new Error('x')
    })
    const third = lock.run(async () => {
      order.push('third')
    })
    await flush()
    expect(order).toEqual(['first:start'])
    releaseFirst()
    await first
    await expect(failing).rejects.toThrow('x')
    await third
    expect(order).toEqual(['first:start', 'first:end', 'failing', 'third'])
  })
})

describe('refresh identity boundary for invitation writes', () => {
  it.each(['redeem', 'issue', 'reissue', 'revoke'] as const)('aborts %s before transmission when the refresh cookie belongs to another account', async (op) => {
    const server = new FakeAuthServer()
    server.accessTtlMs = 10_000
    const manager = managerFor(server)
    await signedIn(server, manager)
    const previous = manager.getState()
    server.signInAs(USER_B) // Another tab replaced only the cookie; no logout broadcast.
    const fetchSpy = vi.spyOn(server, 'fetch')
    const requests = {
      redeem: () => manager.api.redeemInvitation({ token: 'fictional-invite' }),
      issue: () => manager.api.createInvitation('ward', { role: 'WARD_ADMIN' }),
      reissue: () => manager.api.reissueInvitation('ward', 'invite'),
      revoke: () => manager.api.revokeInvitation('ward', 'invite'),
    }
    await expect(requests[op]()).rejects.toMatchObject({ code: 'ABORTED' })
    expect(fetchSpy).not.toHaveBeenCalled()
    expect(manager.getState()).toMatchObject({ status: 'signedIn', user: USER_B })
    expect(manager.getState()).not.toMatchObject({ epoch: 'epoch' in previous ? previous.epoch : -1 })
  })

  it('never retries a rejected write with a different account after a 401 refresh', async () => {
    const server = new FakeAuthServer()
    const manager = managerFor(server)
    await signedIn(server, manager)
    server.expireAllAccessTokens()
    server.signInAs(USER_B)
    const fetchSpy = vi.spyOn(server, 'fetch')
    await expect(manager.api.redeemInvitation({ token: 'fictional-invite' })).rejects.toMatchObject({ code: 'ABORTED' })
    expect(fetchSpy).toHaveBeenCalledTimes(1)
    expect(manager.getState()).toMatchObject({ status: 'signedIn', user: USER_B })
  })
})
