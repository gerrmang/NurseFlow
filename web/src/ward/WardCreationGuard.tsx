import { useEffect, useSyncExternalStore } from 'react'
import { useSessionManager, useSessionState } from '../session/SessionContext'
import { getWardCreationFlow } from './WardCreationFlow'

/** Mounted outside the routes so navigation cannot discard the pending-request warning. */
export function WardCreationGuard() {
  const session = useSessionState()
  return session.status === 'signedIn'
    ? <SessionCreationGuard key={session.epoch} epoch={session.epoch} />
    : null
}

function SessionCreationGuard({ epoch }: { epoch: number }) {
  const manager = useSessionManager()
  const flow = getWardCreationFlow(manager, epoch)
  const state = useSyncExternalStore(flow.subscribe, flow.getSnapshot)
  const warnOnUnload = state.busy || state.uncertain
  useEffect(() => {
    if (!warnOnUnload) return
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [warnOnUnload])
  return null
}
