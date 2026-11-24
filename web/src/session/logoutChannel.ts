/** Cross-tab notifications contain no identity, credentials or API response data. */
export const LOGOUT_CHANNEL_NAME = 'nurseflow-web-logout'

export interface LogoutChannel {
  publish(): void
  subscribe(listener: () => void): () => void
}

export function createLogoutChannel(): LogoutChannel | undefined {
  if (typeof BroadcastChannel === 'undefined') return undefined
  let channel: BroadcastChannel
  try {
    channel = new BroadcastChannel(LOGOUT_CHANNEL_NAME)
  } catch {
    // Some browser privacy modes disable cross-tab messaging. Server logout still works.
    return undefined
  }
  return {
    publish: () => channel.postMessage({ type: 'logout' }),
    subscribe(listener) {
      const receive = (event: MessageEvent<unknown>) => {
        if (typeof event.data === 'object' && event.data !== null &&
          Object.keys(event.data).length === 1 && 'type' in event.data && event.data.type === 'logout') listener()
      }
      channel.addEventListener('message', receive)
      return () => channel.removeEventListener('message', receive)
    },
  }
}
