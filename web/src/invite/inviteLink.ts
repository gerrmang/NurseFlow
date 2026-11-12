/**
 * Invitation link helpers (B3b).
 *
 * The token travels only in the URL fragment (`#token=`), which browsers do not send to servers or
 * as a Referer. It is read once, removed from the address bar, and never put into router state,
 * logs or error objects. The server's `inviteUrl` is intentionally not used for web links: it may
 * be an Android development scheme or absent.
 */
export const INVITE_PATH = '/invite'

const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/

export const isInviteToken = (value: string): boolean => TOKEN_PATTERN.test(value)

export type FragmentRead = { kind: 'none' } | { kind: 'invalid' } | { kind: 'token'; token: string }

/** Pure read of `location.hash`. Has no side effects, so it is safe during render and in StrictMode. */
export function readInviteFragment(hash: string): FragmentRead {
  if (hash === '' || hash === '#') return { kind: 'none' }
  const match = /^#token=([^&=#]*)$/.exec(hash)
  if (!match) return { kind: 'invalid' }
  let decoded: string
  try {
    decoded = decodeURIComponent(match[1]!)
  } catch {
    return { kind: 'invalid' }
  }
  return isInviteToken(decoded) ? { kind: 'token', token: decoded } : { kind: 'invalid' }
}

export function buildWebInviteLink(origin: string, token: string): string {
  return `${origin}${INVITE_PATH}#token=${encodeURIComponent(token)}`
}

/** Text for pasting into the Android app (development scheme, see feature-breakdown §7 #6). */
export function buildAppPasteText(token: string): string {
  return `nurseflow://invite#token=${token}`
}

/** True when a link on this origin cannot be opened from another device. */
export function isLocalOrigin(origin: string): boolean {
  try {
    const { hostname } = new URL(origin)
    return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '[::1]'
  } catch {
    return false
  }
}

/**
 * Accepts, after trimming: this web origin's `/invite#token=…`, the app's `nurseflow://invite#token=…`,
 * or the bare 43-character token. Anything else (other origins, extra query/path, extra fragment
 * parameters) is rejected.
 */
export function parseInviteInput(text: string, origin: string): string | null {
  const value = text.trim()
  if (isInviteToken(value)) return value
  let url: URL
  try {
    url = new URL(value)
  } catch {
    return null
  }
  if (url.search !== '' || url.username !== '' || url.password !== '') return null
  if (url.protocol === 'nurseflow:') {
    if (url.hostname !== 'invite' || (url.pathname !== '' && url.pathname !== '/')) return null
  } else if (url.protocol === 'http:' || url.protocol === 'https:') {
    if (url.origin !== origin || url.pathname !== INVITE_PATH) return null
  } else {
    return null
  }
  const read = readInviteFragment(url.hash)
  return read.kind === 'token' ? read.token : null
}
