import { render } from '@testing-library/react'
import { StrictMode, useEffect } from 'react'
import { MemoryRouter, useLocation, useNavigate, type NavigateFunction } from 'react-router'
import { AppRoutes } from '../App'
import { InviteProvider } from '../invite/InviteContext'
import { PendingInviteStore } from '../invite/pendingInvite'
import { SessionProvider } from '../session/SessionContext'
import { SessionManager } from '../session/SessionManager'
import { createInTabLock } from '../session/sessionLock'
import { FakeAuthServer } from './fakeAuthServer'
import { FakeInvitationBackend } from './fakeInvitationBackend'
import { FakeStorage } from './fakeStorage'

function LocationProbe({ onNavigate }: { onNavigate: (navigate: NavigateFunction) => void }) {
  const navigate = useNavigate()
  useEffect(() => onNavigate(navigate), [navigate, onNavigate])
  const location = useLocation()
  return <><output data-testid="location">{location.pathname + location.search + location.hash}</output><output data-testid="route-state">{JSON.stringify(location.state)}</output></>
}

export interface AppOptions {
  /** Reuse these to simulate another page load of the same browser (same cookies, storage and server). */
  server?: FakeAuthServer
  backend?: FakeInvitationBackend
  storage?: FakeStorage
  strict?: boolean
  clock?: { t: number }
  configure?: (ctx: { server: FakeAuthServer; backend: FakeInvitationBackend; storage: FakeStorage }) => void
}

/** Mounts the real app routes with the real SessionManager and invitation store over fakes. */
export function setupApp(path: string, options: AppOptions = {}) {
  const server = options.server ?? new FakeAuthServer()
  const backend = options.backend ?? new FakeInvitationBackend(server)
  const storage = options.storage ?? new FakeStorage()
  const clock = options.clock ?? { t: Date.now() }
  backend.now = () => clock.t
  options.configure?.({ server, backend, storage })

  const redirect = vi.fn()
  const store = new PendingInviteStore({ storage: () => storage, now: () => clock.t })
  const manager = new SessionManager({
    auth: server.auth,
    lock: createInTabLock(),
    // Call through the property so tests can spy on these after the manager is created.
    fetch: (input, init) => backend.fetch(input, init),
    origin: 'http://localhost:5173',
    redirect,
  })
  const [withoutHash] = path.split('#')
  const [pathname, search = ''] = withoutHash!.split('?')
  manager.start(pathname!, search ? `?${search}` : '')

  let navigate!: NavigateFunction
  const onNavigate = (value: NavigateFunction) => { navigate = value }
  const tree = (
    <SessionProvider manager={manager}>
      <InviteProvider store={store}>
        <MemoryRouter initialEntries={[path]}>
          <AppRoutes />
          <LocationProbe onNavigate={onNavigate} />
        </MemoryRouter>
      </InviteProvider>
    </SessionProvider>
  )
  const view = render(options.strict === false ? tree : <StrictMode>{tree}</StrictMode>)
  return { server, backend, storage, store, manager, redirect, clock, navigate: (path: string) => navigate(path), ...view }
}
