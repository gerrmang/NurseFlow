import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { createAuthApi } from './api/authApi'
import { browserFetch } from './api/http'
import { AppRoutes } from './App'
import { InviteProvider } from './invite/InviteContext'
import { PendingInviteStore } from './invite/pendingInvite'
import { SessionProvider } from './session/SessionContext'
import { CALLBACK_PATH, SessionManager } from './session/SessionManager'
import { createSessionLock } from './session/sessionLock'
import { createLogoutChannel } from './session/logoutChannel'
import './styles.css'

const manager = new SessionManager({
  auth: createAuthApi(browserFetch),
  lock: createSessionLock(typeof navigator !== 'undefined' ? navigator.locks : undefined),
  fetch: browserFetch,
  origin: window.location.origin,
  redirect: (url) => window.location.assign(url),
  logoutChannel: createLogoutChannel(),
})
// Invitation state. sessionStorage is used only for the login round trip (see pendingInvite.ts).
const invites = new PendingInviteStore({ storage: () => window.sessionStorage })
// A saved invitation is only meant for the Kakao callback; anywhere else it is stale.
if (window.location.pathname !== CALLBACK_PATH) invites.sweepStaleEntry()
// Decided before the first render so the callback page never races a restore refresh.
manager.start(window.location.pathname, window.location.search)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <SessionProvider manager={manager}>
      <InviteProvider store={invites}>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </InviteProvider>
    </SessionProvider>
  </StrictMode>,
)
