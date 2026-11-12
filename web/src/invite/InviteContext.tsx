import { createContext, useContext, useEffect, useSyncExternalStore, type ReactNode } from 'react'
import { useSessionManager } from '../session/SessionContext'
import type { LoginReturn, LoginReturnFailure, PendingInvite, PendingInviteStore } from './pendingInvite'

const InviteContext = createContext<PendingInviteStore | null>(null)

/** Must be inside SessionProvider: keeps the store in step with the session (adopt, drop on logout/switch). */
export function InviteProvider({ store, children }: { store: PendingInviteStore; children: ReactNode }) {
  const manager = useSessionManager()
  useEffect(() => {
    store.observeSession(manager.getState())
    return manager.subscribe(() => store.observeSession(manager.getState()))
  }, [manager, store])
  return <InviteContext.Provider value={store}>{children}</InviteContext.Provider>
}

export function useInviteStore(): PendingInviteStore {
  const store = useContext(InviteContext)
  if (!store) throw new Error('InviteProvider is missing')
  return store
}

export function usePendingInvite(): PendingInvite | null {
  const store = useInviteStore()
  return useSyncExternalStore(store.subscribe, store.getInvite)
}

export function useLoginReturn(): LoginReturn | null {
  const store = useInviteStore()
  return useSyncExternalStore(store.subscribe, store.getLoginReturn)
}

export function useRestoreNotice(): LoginReturnFailure | null {
  const store = useInviteStore()
  return useSyncExternalStore(store.subscribe, store.getRestoreNotice)
}
