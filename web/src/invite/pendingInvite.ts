import type { SessionState } from '../session/SessionManager'
import { isInviteToken } from './inviteLink'

/**
 * Holds the invitation the user is working on (B3b).
 *
 * The token lives in memory. The ONLY Web Storage use in the web app is a deliberate, narrow
 * exception: when the user presses "로그인" on the invitation screen, `{v, token, expiresAt}` is
 * written to sessionStorage so the invitation survives the full-page round trip to Kakao. It is
 * read and removed once when the callback page asks for it. Auth tokens are never stored.
 * The 10-minute limit is a logical expiry checked on read; it does not promise that the entry is
 * physically deleted at that moment (a closed tab or a failed removeItem can leave it behind).
 */
export const LOGIN_RETURN_KEY = 'nurseflow.inviteReturn.v1'
export const LOGIN_RETURN_TTL_MS = 10 * 60_000

/** The subset of Storage used here. Every call may throw (blocked storage, quota, private mode). */
export interface StorageLike {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

export type LoginReturnFailure = 'readFailed' | 'removeFailed' | 'invalid' | 'expired'
export type LoginReturn = { kind: 'none' } | { kind: 'restored' } | { kind: 'failed'; reason: LoginReturnFailure }
export type OfferResult = 'accepted' | 'same' | 'busy'

export interface SessionIdentity {
  userId: string
  epoch: number
}

export interface PendingInvite {
  /** Identifies this invitation flow; late results for an older seq are discarded. */
  seq: number
  token: string
  /** null until a signed-in session adopts it (not logged in yet, or first login after the link). */
  owner: SessionIdentity | null
}

export interface PendingInviteDeps {
  /** May throw (accessing window.sessionStorage can throw when storage is blocked). */
  storage: () => StorageLike
  now?: () => number
}

const sameIdentity = (a: SessionIdentity | null, b: SessionIdentity) => a !== null && a.userId === b.userId && a.epoch === b.epoch

export class PendingInviteStore {
  private invite: PendingInvite | null = null
  private seqCounter = 0
  private session: SessionIdentity | null = null
  private redeemingSeq: number | null = null
  private loginReturn: LoginReturn | null = null
  private restoreNotice: LoginReturnFailure | null = null
  private readonly listeners = new Set<() => void>()
  private readonly deps: PendingInviteDeps
  private readonly now: () => number

  constructor(deps: PendingInviteDeps) {
    this.deps = deps
    this.now = deps.now ?? Date.now
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  getInvite = (): PendingInvite | null => this.invite
  getLoginReturn = (): LoginReturn | null => this.loginReturn
  getRestoreNotice = (): LoginReturnFailure | null => this.restoreNotice

  /**
   * Registers an invitation. Idempotent for the same token (StrictMode re-runs effects), replaces the
   * invitation for a different token (last link wins), and refuses to replace while a join request is
   * in flight.
   */
  offer(token: string): OfferResult {
    if (this.invite && this.invite.token === token) return 'same'
    if (this.invite && this.redeemingSeq === this.invite.seq) return 'busy'
    this.invite = { seq: ++this.seqCounter, token, owner: this.session }
    this.restoreNotice = null
    this.notify()
    return 'accepted'
  }

  /** The join request for `seq` started (true) or ended (false). Blocks replacement while it runs. */
  setRedeeming(seq: number, active: boolean): void {
    if (active) this.redeemingSeq = seq
    else if (this.redeemingSeq === seq) this.redeemingSeq = null
  }

  /** Cancel, join finished, or a terminal error was dismissed. */
  discard(): void {
    this.removeStoredEntry()
    if (this.invite === null) return
    this.invite = null
    this.redeemingSeq = null
    this.notify()
  }

  dismissRestoreNotice(): void {
    if (this.restoreNotice === null) return
    this.restoreNotice = null
    this.notify()
  }

  /**
   * Saves the invitation for the login round trip. Returns false when it could not be saved (the user
   * is told to reopen or paste the link after logging in).
   */
  saveForLogin(): boolean {
    const invite = this.invite
    if (!invite) return false
    try {
      this.deps
        .storage()
        .setItem(LOGIN_RETURN_KEY, JSON.stringify({ v: 1, token: invite.token, expiresAt: this.now() + LOGIN_RETURN_TTL_MS }))
      return true
    } catch {
      return false
    }
  }

  /**
   * Called by the Kakao callback page. The first result is remembered, so a re-render or a second
   * effect run (StrictMode) never reads the storage again or overwrites the first outcome.
   *
   * Order: read, then remove, and only then validate and restore. If removal fails the entry is NOT
   * treated as consumed: nothing is restored automatically and the user is asked to reopen the link.
   */
  consumeLoginReturn(): LoginReturn {
    if (this.loginReturn) return this.loginReturn
    const result = this.readAndRemove()
    this.loginReturn = result
    if (result.kind === 'failed') this.restoreNotice = result.reason
    this.notify()
    return result
  }

  /** At app start on any path other than the Kakao callback: drop a stale entry (best effort). */
  sweepStaleEntry(): void {
    this.removeStoredEntry()
  }

  /**
   * Follows the session. An invitation without an owner is adopted by the first signed-in identity
   * (not logged in -> first login). An owned invitation is dropped when the session ends or the
   * identity changes.
   */
  observeSession(state: SessionState): void {
    if (state.status === 'signedIn') {
      const identity: SessionIdentity = { userId: state.user.userId, epoch: state.epoch }
      if (this.invite?.owner && !sameIdentity(this.invite.owner, identity)) this.discard()
      this.session = identity
      if (this.invite && this.invite.owner === null) {
        this.invite = { ...this.invite, owner: identity }
        this.notify()
      }
      return
    }
    if (state.status === 'signedOut' || state.status === 'signingOut' || state.status === 'logoutFailed') {
      const wasSignedIn = this.session !== null
      this.session = null
      if (wasSignedIn) {
        // Logout, expiry or a logout from another tab: nothing from the old account may continue.
        if (this.invite?.owner) this.discard()
        else this.removeStoredEntry()
      }
    }
  }

  private readAndRemove(): LoginReturn {
    let storage: StorageLike
    let raw: string | null
    try {
      storage = this.deps.storage()
      raw = storage.getItem(LOGIN_RETURN_KEY)
    } catch {
      return { kind: 'failed', reason: 'readFailed' }
    }
    if (raw === null) return { kind: 'none' }
    try {
      storage.removeItem(LOGIN_RETURN_KEY)
    } catch {
      return { kind: 'failed', reason: 'removeFailed' }
    }
    const token = this.validate(raw)
    if (token === 'expired' || token === 'invalid') return { kind: 'failed', reason: token }
    this.invite = { seq: ++this.seqCounter, token, owner: this.session }
    return { kind: 'restored' }
  }

  private validate(raw: string): string {
    let value: unknown
    try {
      value = JSON.parse(raw)
    } catch {
      return 'invalid'
    }
    if (typeof value !== 'object' || value === null) return 'invalid'
    const { v, token, expiresAt } = value as Record<string, unknown>
    if (v !== 1 || typeof token !== 'string' || !isInviteToken(token)) return 'invalid'
    if (typeof expiresAt !== 'number' || !Number.isFinite(expiresAt)) return 'invalid'
    const now = this.now()
    if (now >= expiresAt) return 'expired'
    if (expiresAt - now > LOGIN_RETURN_TTL_MS) return 'invalid'
    return token
  }

  private removeStoredEntry(): void {
    try {
      this.deps.storage().removeItem(LOGIN_RETURN_KEY)
    } catch {
      // A leftover entry is rejected on read (expiry and format checks) and swept at the next start.
    }
  }

  private notify(): void {
    for (const listener of this.listeners) listener()
  }
}
