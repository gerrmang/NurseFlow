import type { StorageLike } from '../invite/pendingInvite'

/** In-memory sessionStorage stand-in whose operations can be made to throw, like blocked storage. */
export class FakeStorage implements StorageLike {
  readonly items = new Map<string, string>()
  failGet = false
  failSet = false
  failRemove = false
  readonly removed: string[] = []

  getItem(key: string): string | null {
    if (this.failGet) throw new DOMException('blocked', 'SecurityError')
    return this.items.get(key) ?? null
  }

  setItem(key: string, value: string): void {
    if (this.failSet) throw new DOMException('quota', 'QuotaExceededError')
    this.items.set(key, value)
  }

  removeItem(key: string): void {
    if (this.failRemove) throw new DOMException('blocked', 'SecurityError')
    this.removed.push(key)
    this.items.delete(key)
  }
}
