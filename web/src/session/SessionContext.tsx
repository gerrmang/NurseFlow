import { createContext, useContext, useSyncExternalStore, type ReactNode } from 'react'
import type { SessionManager, SessionState } from './SessionManager'

const SessionContext = createContext<SessionManager | null>(null)

export function SessionProvider({ manager, children }: { manager: SessionManager; children: ReactNode }) {
  return <SessionContext.Provider value={manager}>{children}</SessionContext.Provider>
}

export function useSessionManager(): SessionManager {
  const manager = useContext(SessionContext)
  if (!manager) throw new Error('SessionProvider is missing')
  return manager
}

export function useSessionState(): SessionState {
  const manager = useSessionManager()
  return useSyncExternalStore(manager.subscribe, manager.getState)
}
