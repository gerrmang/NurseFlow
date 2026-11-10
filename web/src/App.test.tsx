import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode } from 'react'
import { MemoryRouter, useLocation } from 'react-router'
import { ApiError } from './api/http'
import { AppRoutes } from './App'
import { SessionProvider } from './session/SessionContext'
import { SessionManager } from './session/SessionManager'
import { createInTabLock } from './session/sessionLock'
import { FakeAuthServer, json, USER_A, USER_B } from './test/fakeAuthServer'

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname + location.search}</output>
}

function setup(path: string, configure?: (server: FakeAuthServer) => void) {
  const server = new FakeAuthServer()
  configure?.(server)
  const redirect = vi.fn()
  const manager = new SessionManager({
    auth: server.auth,
    lock: createInTabLock(),
    // Call through the property so tests can spy on server.fetch after the manager is created.
    fetch: (input, init) => server.fetch(input, init),
    origin: 'http://localhost:5173',
    redirect,
  })
  const [pathname, search = ''] = path.split('?')
  manager.start(pathname!, search ? `?${search}` : '')
  render(
    <StrictMode>
      <SessionProvider manager={manager}>
        <MemoryRouter initialEntries={[path]}>
          <AppRoutes />
          <LocationProbe />
        </MemoryRouter>
      </SessionProvider>
    </StrictMode>,
  )
  return { server, manager, redirect }
}

const location = () => screen.getByTestId('location').textContent

describe('routing and session guard', () => {
  it('sends a visitor without a session to the login page', async () => {
    setup('/wards')
    expect(await screen.findByRole('button', { name: '카카오로 로그인' })).toBeInTheDocument()
    expect(location()).toBe('/login')
  })

  it('shows the ward list with role labels after restoring the session', async () => {
    setup('/', (server) => {
      server.signInAs(USER_A)
      const base = server.fetch
      vi.spyOn(server, 'fetch').mockImplementation(async (input, init) => {
        const response = await base(input, init)
        if (response.ok && input.endsWith('/wards')) {
          return json(200, {
            items: [
              { wardId: '10000000-0000-4000-8000-000000000001', wardName: '가상 내과 병동', roles: ['WARD_ADMIN'], canViewWardCalendar: true },
              { wardId: '10000000-0000-4000-8000-000000000002', wardName: '가상 외과 병동', roles: ['WARD_MEMBER'], canViewWardCalendar: true },
            ],
          })
        }
        return response
      })
    })
    expect(await screen.findByText('가상 내과 병동')).toBeInTheDocument()
    expect(screen.getByText('관리자')).toBeInTheDocument()
    expect(screen.getByText('간호사')).toBeInTheDocument()
    expect(screen.getByText(`${USER_A.displayName}님, 안녕하세요.`)).toBeInTheDocument()
    expect(location()).toBe('/wards')
  })

  it('explains what to do when the user has no ward yet', async () => {
    setup('/wards', (server) => server.signInAs(USER_A))
    expect(await screen.findByText('아직 소속된 병동이 없어요.')).toBeInTheDocument()
  })

  it('shows a ward loading error with a working retry', async () => {
    const { server } = setup('/wards', (server) => server.signInAs(USER_A))
    await screen.findByText('아직 소속된 병동이 없어요.')
    const base = server.fetch
    const spy = vi.spyOn(server, 'fetch').mockImplementation(async () => json(500, { code: 'INTERNAL_ERROR' }))
    await userEvent.click(screen.getByRole('button', { name: '새로고침' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('서버에 일시적인 문제가 있어요')
    spy.mockImplementation(base)
    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByText('아직 소속된 병동이 없어요.')).toBeInTheDocument()
  })

  it('returns to the login page with an expiry notice when the session cannot be renewed', async () => {
    const { server } = setup('/wards', (server) => server.signInAs(USER_A))
    await screen.findByText('아직 소속된 병동이 없어요.')
    server.expireAllAccessTokens()
    server.cookie = null
    await userEvent.click(screen.getByRole('button', { name: '새로고침' }))
    expect(await screen.findByText('로그인이 만료됐어요. 다시 로그인해 주세요.')).toBeInTheDocument()
    expect(location()).toBe('/login')
  })

  it('offers retry and sign-out when restoring fails for network reasons', async () => {
    setup('/wards', (server) => {
      server.signInAs(USER_A)
      server.failNext('refresh', new ApiError(0, 'NETWORK'))
    })
    expect(await screen.findByRole('alert')).toHaveTextContent('서버에 연결할 수 없어요')
    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByText('아직 소속된 병동이 없어요.')).toBeInTheDocument()
  })

  it('shows a not-found page for unknown paths', async () => {
    setup('/nope')
    expect(await screen.findByText('페이지를 찾을 수 없어요.')).toBeInTheDocument()
  })
})

describe('login page', () => {
  it('starts Kakao login once with the exact callback URI', async () => {
    const { server, redirect } = setup('/login')
    const startSpy = vi.spyOn(server.auth, 'startKakaoLogin')
    const button = await screen.findByRole('button', { name: '카카오로 로그인' })
    await userEvent.click(button)
    await userEvent.click(button)
    expect(startSpy).toHaveBeenCalledTimes(1)
    expect(startSpy).toHaveBeenCalledWith('http://localhost:5173/auth/kakao/callback')
    expect(redirect).toHaveBeenCalledTimes(1)
  })

  it('explains a server configuration problem and allows another try', async () => {
    setup('/login', (server) => server.failNext('authorize', new ApiError(503, 'IDENTITY_PROVIDER_NOT_CONFIGURED')))
    await userEvent.click(await screen.findByRole('button', { name: '카카오로 로그인' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('웹 로그인 설정이 아직 완료되지 않았어요')
    expect(screen.getByRole('button', { name: '카카오로 로그인' })).toBeEnabled()
  })
})

describe('Kakao callback page', () => {
  it('exchanges the code once under StrictMode, strips it from the URL and opens the ward list', async () => {
    const { server } = setup('/auth/kakao/callback?code=code-b&state=state-b', (server) => server.registerCode('code-b', USER_B))
    expect(await screen.findByText(`${USER_B.displayName}님, 안녕하세요.`)).toBeInTheDocument()
    expect(server.calls.filter((op) => op === 'exchange')).toHaveLength(1)
    expect(server.calls).not.toContain('refresh')
    expect(location()).toBe('/wards')
  })

  it('removes the code from the address bar before the exchange finishes', async () => {
    const { server } = setup('/auth/kakao/callback?code=code-b&state=state-b', (server) => {
      server.registerCode('code-b', USER_B)
      server.hold('exchange')
    })
    await waitFor(() => expect(location()).toBe('/auth/kakao/callback'))
    expect(screen.getByText('카카오 로그인을 마무리하고 있어요…')).toBeInTheDocument()
    expect(server.calls).toEqual(['exchange'])
  })

  it('treats a cancelled Kakao consent as a notice, not an error', async () => {
    setup('/auth/kakao/callback?error=access_denied&error_description=User%20denied')
    expect(await screen.findByText('카카오 로그인을 취소했어요.')).toBeInTheDocument()
    expect(location()).toBe('/login')
  })

  it('shows the login-state error when the exchange is rejected', async () => {
    setup('/auth/kakao/callback?code=stale&state=stale')
    expect(await screen.findByText(/로그인 시간이 지났거나 다른 창에서 시작된 로그인이에요/)).toBeInTheDocument()
    expect(location()).toBe('/login')
  })

  it('redirects a direct visit without parameters', async () => {
    setup('/auth/kakao/callback', (server) => server.signInAs(USER_A))
    expect(await screen.findByText('아직 소속된 병동이 없어요.')).toBeInTheDocument()
    expect(location()).toBe('/wards')
  })
})

describe('logout', () => {
  it('shows progress, then the login page, and a reload does not restore the session', async () => {
    const { server } = setup('/wards', (server) => server.signInAs(USER_A))
    await screen.findByText('아직 소속된 병동이 없어요.')
    const release = server.hold('logout')
    await userEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    expect(await screen.findByText('로그아웃하고 있어요…')).toBeInTheDocument()
    await act(async () => release())
    expect(await screen.findByText('로그아웃했어요.')).toBeInTheDocument()
    expect(server.cookie).toBeNull()

    const reloaded = new SessionManager({
      auth: server.auth,
      lock: createInTabLock(),
      fetch: server.fetch,
      origin: 'http://localhost:5173',
      redirect: vi.fn(),
    })
    reloaded.start('/wards', '')
    await waitFor(() => expect(reloaded.getState()).toEqual({ status: 'signedOut' }))
  })

  it('offers a logout retry when the server call fails', async () => {
    const { server } = setup('/wards', (server) => server.signInAs(USER_A))
    await screen.findByText('아직 소속된 병동이 없어요.')
    server.failNext('logout', new ApiError(0, 'NETWORK'))
    await userEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('로그아웃을 완료하지 못했어요')
    await userEvent.click(screen.getByRole('button', { name: '로그아웃 재시도' }))
    expect(await screen.findByText('로그아웃했어요.')).toBeInTheDocument()
    expect(server.cookie).toBeNull()
  })

  it('does not show the previous account after logging in with another one', async () => {
    const { server, manager } = setup('/wards', (server) => {
      server.signInAs(USER_A)
      server.registerCode('code-b', USER_B)
    })
    await screen.findByText(`${USER_A.displayName}님, 안녕하세요.`)
    await userEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    await screen.findByText('로그아웃했어요.')
    await act(async () => manager.completeLogin('code-b', 'state-b'))
    expect(await screen.findByText(`${USER_B.displayName}님, 안녕하세요.`)).toBeInTheDocument()
    expect(screen.queryByText(`${USER_A.displayName}님, 안녕하세요.`)).not.toBeInTheDocument()
    expect(server.isFamilyActive(USER_A.userId)).toBe(false)
  })
})
