import type { AuthApi } from '../api/authApi'
import { ApiError, ClientErrorCode, asApiError, send, type FetchLike } from '../api/http'
import { createNurseFlowApi, type NurseFlowApi } from '../api/nurseflowApi'
import type { WebSession } from '../api/types'
import type { SessionLock } from './sessionLock'

export const CALLBACK_PATH = '/auth/kakao/callback'

/** Refresh this long before the access token's stated expiry to avoid a guaranteed 401. */
const EXPIRY_SKEW_MS = 30_000

export interface SessionUser {
  userId: string
  displayName: string
}

/** Notice keys shown on the login page after the session ends (see messages.ts). */
export type SignedOutNotice = 'expired' | 'loggedOut' | 'cancelled' | 'loginFailed' | (string & {})

export type SessionState =
  | { status: 'restoring' }
  | { status: 'exchanging' }
  | { status: 'signedIn'; user: SessionUser; epoch: number }
  | { status: 'signingOut' }
  | { status: 'logoutFailed' }
  | { status: 'restoreFailed'; code: string }
  | { status: 'signedOut'; notice?: SignedOutNotice }

export interface SessionManagerDeps {
  auth: AuthApi
  lock: SessionLock
  fetch: FetchLike
  /** Origin used to build the exact Kakao redirect URI. */
  origin: string
  /** Leaves the SPA for the Kakao authorization page. */
  redirect: (url: string) => void
  now?: () => number
}

type RefreshOutcome = { ok: true; token: string } | { ok: false; error: ApiError }

const aborted = () => new ApiError(0, ClientErrorCode.ABORTED)
const sessionExpired = () => new ApiError(401, ClientErrorCode.SESSION_EXPIRED)

/**
 * Owns the browser session. The access token lives only in this object's memory; the refresh
 * token only in the server's HttpOnly cookie. Nothing is written to Web Storage.
 *
 * Invariants:
 * - `epoch` increases on every identity boundary (login, logout, expiry). Any result that was
 *   requested under an older epoch is discarded, so late responses cannot leak into a new session.
 * - refresh, code exchange and logout run only inside the shared session lock and are never
 *   aborted, because the browser may still apply their Set-Cookie (see sessionLock.ts).
 * - Concurrent 401s share one refresh, and each request is retried at most once.
 */
export class SessionManager {
  readonly api: NurseFlowApi

  private state: SessionState = { status: 'restoring' }
  private readonly listeners = new Set<() => void>()
  private epoch = 0
  private accessToken: string | null = null
  private accessExpiresAt = 0
  private refreshing: { epoch: number; promise: Promise<RefreshOutcome> } | null = null
  private readonly exchanges = new Map<string, Promise<void>>()
  private loggingOut: Promise<void> | null = null
  private loginStart: Promise<void> | null = null
  private started = false
  private readonly apiControllers = new Set<AbortController>()
  private readonly deps: SessionManagerDeps
  private readonly now: () => number

  constructor(deps: SessionManagerDeps) {
    this.deps = deps
    this.now = deps.now ?? Date.now
    this.api = createNurseFlowApi((path, init) => this.authorizedFetch(path, init))
  }

  getState = (): SessionState => this.state

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  /**
   * Called once at app start. On the Kakao callback with a code, no restore refresh is sent:
   * the callback page performs the code exchange instead, so the two cannot race.
   */
  start(pathname: string, search: string): void {
    if (this.started) return
    this.started = true
    const params = new URLSearchParams(search)
    if (pathname === CALLBACK_PATH && params.get('code') && params.get('state')) {
      this.setState({ status: 'exchanging' })
      return
    }
    void this.restore()
  }

  /** Restores the session from the refresh cookie. Also used by "다시 시도". */
  async restore(): Promise<void> {
    if (this.state.status === 'signingOut' || this.state.status === 'exchanging') return
    const epoch = this.epoch
    this.setState({ status: 'restoring' })
    const outcome = await this.refreshSession()
    if (epoch !== this.epoch || outcome.ok) return
    if (outcome.error.status === 401) {
      this.setState({ status: 'signedOut' })
    } else {
      this.setState({ status: 'restoreFailed', code: outcome.error.code })
    }
  }

  /** Asks the server for a Kakao authorization URL and leaves the page. Repeated clicks send one request. */
  beginKakaoLogin(): Promise<void> {
    if (this.loginStart) return this.loginStart
    this.loginStart = (async () => {
      try {
        const { authorizationUrl } = await this.deps.auth.startKakaoLogin(this.deps.origin + CALLBACK_PATH)
        this.deps.redirect(authorizationUrl)
      } catch (error) {
        this.loginStart = null
        throw asApiError(error)
      }
    })()
    return this.loginStart
  }

  /** Exchanges the Kakao code once per code value (React StrictMode runs effects twice). */
  completeLogin(code: string, state: string): Promise<void> {
    const existing = this.exchanges.get(code)
    if (existing) return existing
    const exchange = this.exchange(code, state)
    this.exchanges.set(code, exchange)
    return exchange
  }

  /** Ends the session on the server. A second call while one is running joins it. */
  logout(): Promise<void> {
    if (this.loggingOut) return this.loggingOut
    this.loggingOut = this.performLogout().finally(() => {
      this.loggingOut = null
    })
    return this.loggingOut
  }

  /** Sends an API request with the in-memory access token, refreshing at most once on 401. */
  async authorizedFetch(path: string, init: RequestInit = {}): Promise<Response> {
    const epoch = this.epoch
    if (this.state.status !== 'signedIn' || this.accessToken === null) throw sessionExpired()
    const controller = new AbortController()
    this.apiControllers.add(controller)
    try {
      let token: string = this.accessToken
      if (this.now() >= this.accessExpiresAt - EXPIRY_SKEW_MS) token = await this.renewAfter(token, epoch)
      let response = await this.sendWithToken(path, init, token, controller)
      if (response.status === 401) {
        token = await this.renewAfter(token, epoch)
        response = await this.sendWithToken(path, init, token, controller)
        if (response.status === 401) {
          if (epoch === this.epoch) this.expire()
          throw sessionExpired()
        }
      }
      if (epoch !== this.epoch) throw aborted()
      return response
    } finally {
      this.apiControllers.delete(controller)
    }
  }

  private async exchange(code: string, state: string): Promise<void> {
    this.started = true
    this.beginEpoch()
    const epoch = this.epoch
    this.setState({ status: 'exchanging' })
    try {
      const session = await this.deps.lock.run(() => this.deps.auth.exchangeCode(code, state))
      if (epoch !== this.epoch) return
      this.applySession(session)
    } catch (error) {
      if (epoch !== this.epoch) return
      this.setState({ status: 'signedOut', notice: asApiError(error).code })
    }
  }

  private async performLogout(): Promise<void> {
    // Drop the access token and invalidate pending JS results right away. A refresh that is
    // already running is NOT aborted: the lock below waits for it, so its Set-Cookie lands first
    // and the server logout then revokes the newest cookie of the family.
    this.beginEpoch()
    const epoch = this.epoch
    this.setState({ status: 'signingOut' })
    try {
      await this.deps.lock.run(() => this.deps.auth.logout())
      if (epoch === this.epoch) this.setState({ status: 'signedOut', notice: 'loggedOut' })
    } catch {
      // The server may still hold a valid refresh cookie, so a reload could restore the session.
      // The token is not brought back; the user is asked to retry.
      if (epoch === this.epoch) this.setState({ status: 'logoutFailed' })
    }
  }

  /** Single-flight refresh inside the session lock. Joins only a refresh from the same epoch. */
  private refreshSession(): Promise<RefreshOutcome> {
    if (this.refreshing && this.refreshing.epoch === this.epoch) return this.refreshing.promise
    const epoch = this.epoch
    const promise: Promise<RefreshOutcome> = this.deps.lock
      .run(() => this.deps.auth.refresh())
      .then(
        (session): RefreshOutcome => {
          if (epoch !== this.epoch) return { ok: false, error: aborted() }
          this.applySession(session)
          return { ok: true, token: session.accessToken }
        },
        (error: unknown): RefreshOutcome => ({ ok: false, error: asApiError(error) }),
      )
      .finally(() => {
        if (this.refreshing?.promise === promise) this.refreshing = null
      })
    this.refreshing = { epoch, promise }
    return promise
  }

  /** Returns a token newer than `usedToken`, refreshing if no other caller already did. */
  private async renewAfter(usedToken: string, epoch: number): Promise<string> {
    if (epoch !== this.epoch) throw aborted()
    if (this.accessToken !== null && this.accessToken !== usedToken) return this.accessToken
    const outcome = await this.refreshSession()
    if (epoch !== this.epoch) throw aborted()
    if (outcome.ok) return outcome.token
    if (outcome.error.status === 401) {
      this.expire()
      throw sessionExpired()
    }
    throw outcome.error
  }

  private sendWithToken(path: string, init: RequestInit, token: string, controller: AbortController): Promise<Response> {
    const headers = new Headers(init.headers)
    headers.set('Authorization', `Bearer ${token}`)
    return send(this.deps.fetch, path, { ...init, headers, signal: controller.signal })
  }

  private applySession(session: WebSession): void {
    this.accessToken = session.accessToken
    const expiresAt = Date.parse(session.accessTokenExpiresAt)
    this.accessExpiresAt = Number.isNaN(expiresAt) ? 0 : expiresAt
    this.setState({
      status: 'signedIn',
      user: { userId: session.user.userId, displayName: session.user.displayName },
      epoch: this.epoch,
    })
  }

  private expire(): void {
    this.beginEpoch()
    this.setState({ status: 'signedOut', notice: 'expired' })
  }

  /** Starts a new identity epoch: forget the access token and cancel ordinary API requests. */
  private beginEpoch(): void {
    this.epoch += 1
    this.accessToken = null
    this.accessExpiresAt = 0
    for (const controller of this.apiControllers) controller.abort()
    this.apiControllers.clear()
  }

  private setState(next: SessionState): void {
    this.state = next
    for (const listener of this.listeners) listener()
  }
}
