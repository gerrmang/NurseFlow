/**
 * Minimal fetch wrapper shared by every API call.
 *
 * - Only same-origin relative paths under /api/v1 are used. There is deliberately no configurable
 *   API base URL: the web app and the API are served from one origin (see web/README.md).
 * - Errors carry only the HTTP status and the server's stable `code`. Response bodies, tokens and
 *   request URLs are never copied into errors or logs.
 */
export const API_BASE = '/api/v1'

/** Client-side codes used next to the server's ErrorCode values. */
export const ClientErrorCode = {
  NETWORK: 'NETWORK',
  ABORTED: 'ABORTED',
  SESSION_EXPIRED: 'SESSION_EXPIRED',
  INVALID_RESPONSE: 'INVALID_RESPONSE',
} as const

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string) {
    super(code)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export type FetchLike = (input: string, init?: RequestInit) => Promise<Response>

export const browserFetch: FetchLike = (input, init) => globalThis.fetch(input, init)

export function asApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (isAbortError(error)) return new ApiError(0, ClientErrorCode.ABORTED)
  return new ApiError(0, ClientErrorCode.NETWORK)
}

function isAbortError(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { name?: unknown }).name === 'AbortError'
}

/** Sends a request and maps transport failures. HTTP error statuses are returned, not thrown. */
export async function send(fetchImpl: FetchLike, path: string, init: RequestInit = {}): Promise<Response> {
  try {
    return await fetchImpl(API_BASE + path, { ...init, credentials: 'same-origin', cache: 'no-store' })
  } catch (error) {
    throw asApiError(error)
  }
}

/** Parses a successful JSON body, or throws the response as an [ApiError]. */
export async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) throw await toApiError(response)
  try {
    return (await response.json()) as T
  } catch {
    throw new ApiError(response.status, ClientErrorCode.INVALID_RESPONSE)
  }
}

export async function expectNoContent(response: Response): Promise<void> {
  if (!response.ok) throw await toApiError(response)
}

export async function toApiError(response: Response): Promise<ApiError> {
  let code = `HTTP_${response.status}`
  try {
    const body: unknown = await response.json()
    if (typeof body === 'object' && body !== null && typeof (body as { code?: unknown }).code === 'string') {
      code = (body as { code: string }).code
    }
  } catch {
    // Non-JSON error body: keep the status-based code. The body itself is never surfaced.
  }
  return new ApiError(response.status, code)
}
