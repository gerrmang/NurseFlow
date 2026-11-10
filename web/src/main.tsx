import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { createAuthApi } from './api/authApi'
import { browserFetch } from './api/http'
import { AppRoutes } from './App'
import { SessionProvider } from './session/SessionContext'
import { SessionManager } from './session/SessionManager'
import { createSessionLock } from './session/sessionLock'
import './styles.css'

const manager = new SessionManager({
  auth: createAuthApi(browserFetch),
  lock: createSessionLock(typeof navigator !== 'undefined' ? navigator.locks : undefined),
  fetch: browserFetch,
  origin: window.location.origin,
  redirect: (url) => window.location.assign(url),
})
// Decided before the first render so the callback page never races a restore refresh.
manager.start(window.location.pathname, window.location.search)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <SessionProvider manager={manager}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </SessionProvider>
  </StrictMode>,
)
