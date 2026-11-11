import type { AuthApi } from '../api/authApi'
import { ApiError, type FetchLike } from '../api/http'
import type { WebSession } from '../api/types'

/**
 * In-memory model of the A2 web session server plus one browser cookie jar shared by all "tabs".
 * It reproduces what matters for the client's races: refresh rotation, reuse detection revoking
 * the whole family, logout revoking the family, and Set-Cookie being applied when a response
 * arrives. Calls can be held to control ordering. All identities are fictional.
 */
type Op = 'authorize' | 'exchange' | 'refresh' | 'logout'

interface RefreshRow {
  family: number
  userId: string
  status: 'active' | 'replaced' | 'revoked'
}

interface Gate {
  promise: Promise<void>
  release: () => void
}

export const USER_A = { userId: '00000000-0000-4000-8000-00000000000a', displayName: '가상 간호사 가' }
export const USER_B = { userId: '00000000-0000-4000-8000-00000000000b', displayName: '가상 간호사 나' }

export class FakeAuthServer {
  /** The browser's nf_refresh cookie (shared by every tab). */
  cookie: string | null = null
  readonly calls: Op[] = []
  readonly completed: Op[] = []
  /** Access tokens the API currently accepts. */
  readonly validAccessTokens = new Set<string>()
  private readonly refreshRows = new Map<string, RefreshRow>()
  private readonly codes = new Map<string, typeof USER_A>()
  private readonly gates = new Map<Op, Gate[]>()
  private readonly failures = new Map<Op, ApiError>()
  private seq = 0
  private families = 0
  accessTtlMs = 10 * 60_000
  now = () => Date.now()

  /** Pretend the browser already holds a refresh cookie for `user`. */
  signInAs(user: typeof USER_A): void {
    this.cookie = this.issueRefresh(++this.families, user.userId)
  }

  registerCode(code: string, user: typeof USER_A): void {
    this.codes.set(code, user)
  }

  /** The next call of `op` waits until the returned release() is called. */
  hold(op: Op): () => void {
    let release!: () => void
    const promise = new Promise<void>((resolve) => (release = resolve))
    const list = this.gates.get(op) ?? []
    list.push({ promise, release })
    this.gates.set(op, list)
    return release
  }

  /** The next call of `op` fails with this error (e.g. network) without touching server state. */
  failNext(op: Op, error: ApiError): void {
    this.failures.set(op, error)
  }

  expireAllAccessTokens(): void {
    this.validAccessTokens.clear()
  }

  isFamilyActive(userId: string): boolean {
    return [...this.refreshRows.values()].some((row) => row.userId === userId && row.status === 'active')
  }

  readonly auth: AuthApi = {
    startKakaoLogin: async (redirectUri) =>
      this.handle('authorize', () => ({
        authorizationUrl: `https://kauth.example.test/oauth/authorize?redirect_uri=${encodeURIComponent(redirectUri)}`,
        expiresAt: new Date(this.now() + 600_000).toISOString(),
      })),
    exchangeCode: async (code) =>
      this.handle('exchange', () => {
        const user = this.codes.get(code)
        if (!user) throw new ApiError(401, 'INVALID_LOGIN_STATE')
        this.codes.delete(code)
        this.cookie = this.issueRefresh(++this.families, user.userId)
        return this.session(user)
      }),
    refresh: async () =>
      this.handle('refresh', (presented) => {
        const row = presented ? this.refreshRows.get(presented) : undefined
        if (!presented || !row) throw new ApiError(401, 'INVALID_REFRESH_TOKEN')
        if (row.status !== 'active') {
          if (row.status === 'replaced') this.revokeFamily(row.family)
          throw new ApiError(401, 'INVALID_REFRESH_TOKEN')
        }
        row.status = 'replaced'
        this.cookie = this.issueRefresh(row.family, row.userId)
        const user = [USER_A, USER_B].find((candidate) => candidate.userId === row.userId)!
        return this.session(user)
      }),
    logout: async () =>
      this.handle('logout', (presented) => {
        const row = presented ? this.refreshRows.get(presented) : undefined
        if (row) this.revokeFamily(row.family)
        this.cookie = null
      }),
  }

  /** fetch for ordinary API calls: 401 unless the Bearer token is currently valid. */
  readonly fetch: FetchLike = async (input, init) => {
    if (init?.signal?.aborted) throw new DOMException('aborted', 'AbortError')
    const auth = new Headers(init?.headers).get('Authorization') ?? ''
    const token = auth.replace(/^Bearer /, '')
    if (!this.validAccessTokens.has(token)) return json(401, { code: 'UNAUTHORIZED' })
    if (input.endsWith('/me')) return json(200, { ...this.userForToken(token), memberships: [] })
    if (input.endsWith('/wards')) return json(200, { items: [] })
    return json(404, { code: 'NOT_FOUND' })
  }

  private readonly accessOwners = new Map<string, typeof USER_A>()

  private userForToken(token: string) {
    const user = this.accessOwners.get(token)!
    return { userId: user.userId, displayName: user.displayName }
  }

  /**
   * Like a browser, the request carries the cookie present when it is sent; the server's effect
   * (rotation, revocation, Set-Cookie) lands when the held response is released.
   */
  private async handle<T>(op: Op, effect: (presentedCookie: string | null) => T): Promise<T> {
    this.calls.push(op)
    const presented = this.cookie
    const gate = this.gates.get(op)?.shift()
    if (gate) await gate.promise
    const failure = this.failures.get(op)
    if (failure) {
      this.failures.delete(op)
      this.completed.push(op)
      throw failure
    }
    try {
      return effect(presented)
    } finally {
      this.completed.push(op)
    }
  }

  private issueRefresh(family: number, userId: string): string {
    const token = `refresh-${++this.seq}`
    this.refreshRows.set(token, { family, userId, status: 'active' })
    return token
  }

  private revokeFamily(family: number): void {
    for (const row of this.refreshRows.values()) if (row.family === family) row.status = 'revoked'
  }

  private session(user: typeof USER_A): WebSession {
    const accessToken = `access-${++this.seq}`
    this.validAccessTokens.add(accessToken)
    this.accessOwners.set(accessToken, user)
    return {
      tokenType: 'Bearer',
      accessToken,
      accessTokenExpiresAt: new Date(this.now() + this.accessTtlMs).toISOString(),
      refreshTokenExpiresAt: new Date(this.now() + 30 * 86_400_000).toISOString(),
      user: { ...user, newUser: false },
    }
  }
}

export function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** Lets pending promise callbacks run. */
export async function flush(times = 10): Promise<void> {
  for (let i = 0; i < times; i++) await Promise.resolve()
}
