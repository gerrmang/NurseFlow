import { json } from '../test/fakeAuthServer'
import { createAuthApi } from './authApi'
import { ApiError, readJson } from './http'

function recordingFetch(response: () => Response | Promise<Response>) {
  return vi.fn(async (_input: string, _init?: RequestInit) => response())
}

const session = {
  tokenType: 'Bearer',
  accessToken: 'access-test',
  accessTokenExpiresAt: '2026-11-10T00:10:00Z',
  refreshTokenExpiresAt: '2026-12-10T00:00:00Z',
  user: { userId: '00000000-0000-4000-8000-00000000000a', displayName: '가상 간호사 가', newUser: false },
}

describe('web auth API', () => {
  it('starts Kakao login with the redirect URI, the client header and same-origin credentials', async () => {
    const fetch = recordingFetch(() => json(200, { authorizationUrl: 'https://kauth.example.test/a', expiresAt: '2026-11-10T00:10:00Z' }))
    const result = await createAuthApi(fetch).startKakaoLogin('http://localhost:5173/auth/kakao/callback')

    expect(result.authorizationUrl).toBe('https://kauth.example.test/a')
    const [url, init] = fetch.mock.calls[0]!
    expect(url).toBe('/api/v1/auth/web/kakao/authorization')
    expect(init).toMatchObject({ method: 'POST', credentials: 'same-origin', cache: 'no-store' })
    expect(new Headers(init?.headers).get('X-NurseFlow-Client')).toBe('web')
    expect(JSON.parse(String(init?.body))).toEqual({ redirectUri: 'http://localhost:5173/auth/kakao/callback' })
  })

  it('posts code and state unchanged for the exchange', async () => {
    const fetch = recordingFetch(() => json(200, session))
    await createAuthApi(fetch).exchangeCode('code-1', 'state-1')
    const [url, init] = fetch.mock.calls[0]!
    expect(url).toBe('/api/v1/auth/web/kakao/code')
    expect(JSON.parse(String(init?.body))).toEqual({ code: 'code-1', state: 'state-1' })
  })

  it('sends refresh and logout without a body, relying on the HttpOnly cookie', async () => {
    const fetch = recordingFetch(() => json(200, session))
    const api = createAuthApi(fetch)
    await api.refresh()
    fetch.mockImplementation(async () => new Response(null, { status: 204 }))
    await api.logout()

    for (const [url, init] of fetch.mock.calls) {
      expect(url).toMatch(/^\/api\/v1\/auth\/web\/(session\/refresh|logout)$/)
      expect(init?.body).toBeUndefined()
      expect(init?.credentials).toBe('same-origin')
      expect(new Headers(init?.headers).get('X-NurseFlow-Client')).toBe('web')
      expect(new Headers(init?.headers).has('Authorization')).toBe(false)
    }
  })

  it('maps problem+json errors to their stable code', async () => {
    const fetch = recordingFetch(() =>
      json(401, { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'INVALID_REFRESH_TOKEN', detail: '안내' }),
    )
    await expect(createAuthApi(fetch).refresh()).rejects.toEqual(new ApiError(401, 'INVALID_REFRESH_TOKEN'))
  })

  it('maps non-JSON errors by status and transport failures to NETWORK', async () => {
    await expect(createAuthApi(recordingFetch(() => new Response('<html>bad gateway</html>', { status: 502 }))).refresh()).rejects.toEqual(
      new ApiError(502, 'HTTP_502'),
    )
    const offline = vi.fn(async () => {
      throw new TypeError('Failed to fetch')
    })
    await expect(createAuthApi(offline).refresh()).rejects.toEqual(new ApiError(0, 'NETWORK'))
  })

  it('rejects an unreadable success body without exposing it', async () => {
    await expect(readJson(new Response('not json', { status: 200 }))).rejects.toEqual(new ApiError(200, 'INVALID_RESPONSE'))
  })
})
