import { ApiError, asApiError } from '../api/http'
import type { NurseFlowApi } from '../api/nurseflowApi'
import type { PersonalCalendar, PublishedRosterApi, WardCalendar } from '../api/publishedRosterApi'
import type { WardMembership } from '../api/types'

export type RosterMode = 'mine' | 'ward'
export type RosterResult = { mode: 'mine'; calendar: PersonalCalendar } | { mode: 'ward'; calendar: WardCalendar }
export interface RosterState {
  month: string
  mode: RosterMode | null
  ward: WardMembership | null
  status: 'loading' | 'ready' | 'error'
  result: RosterResult | null
  error: { code: string; status: number } | null
}

export const validMonth = (month: string) => /^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(month)
export function moveMonth(month: string, delta: number): string {
  const [year, number] = month.split('-').map(Number) as [number, number]
  const index = year * 12 + number - 1 + delta
  return `${Math.floor(index / 12).toString().padStart(4, '0')}-${(index % 12 + 1).toString().padStart(2, '0')}`
}

/** One instance per session epoch and ward. Every load rechecks membership; data stays in memory. */
export class PublishedRosterStore {
  private state: RosterState
  private ticket = 0
  private listeners = new Set<() => void>()
  constructor(private wardId: string, private api: PublishedRosterApi, private memberships: Pick<NurseFlowApi, 'getWards'>, now = new Date()) {
    this.state = { month: `${now.getFullYear().toString().padStart(4, '0')}-${(now.getMonth() + 1).toString().padStart(2, '0')}`, mode: null, ward: null, status: 'loading', result: null, error: null }
  }
  getState = () => this.state
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener) } }
  private set(patch: Partial<RosterState>) {
    this.state = { ...this.state, ...patch }
    this.listeners.forEach(listener => listener())
  }
  stop = () => { ++this.ticket }
  selectMonth = (month: string) => { if (validMonth(month)) { this.set({ month }); void this.load() } }
  selectMode = (mode: RosterMode) => {
    if (mode === 'ward' && !this.state.ward?.canViewWardCalendar) return
    this.set({ mode }); void this.load()
  }
  load = async () => {
    const ticket = ++this.ticket
    const month = this.state.month
    this.set({ status: 'loading', result: null, error: null })
    try {
      const { items } = await this.memberships.getWards()
      if (ticket !== this.ticket) return
      const ward = items.find(item => item.wardId === this.wardId)
      if (!ward) throw new ApiError(404, 'WARD_NOT_FOUND')
      const mode = this.state.mode === 'ward' && !ward.canViewWardCalendar ? 'mine'
        : this.state.mode ?? (ward.roles.includes('WARD_ADMIN') && ward.canViewWardCalendar ? 'ward' : 'mine')
      this.set({ ward, mode })
      const result: RosterResult = mode === 'mine'
        ? { mode, calendar: await this.api.mine(this.wardId, month) }
        : { mode, calendar: await this.api.ward(this.wardId, month) }
      if (ticket !== this.ticket) return
      if (result.calendar.wardId !== this.wardId || result.calendar.month !== month) throw new ApiError(0, 'INVALID_RESPONSE')
      this.set({ status: 'ready', result })
    } catch (error) {
      if (ticket !== this.ticket) return
      const problem = asApiError(error)
      const lostAccess = problem.code === 'WARD_NOT_FOUND' || (problem.status === 403 && problem.code !== 'NURSE_IDENTITY_REQUIRED')
      this.set({ status: 'error', result: null, error: { code: problem.code, status: problem.status }, ...(lostAccess ? { ward: null } : {}) })
    }
  }
}
