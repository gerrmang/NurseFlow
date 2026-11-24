import { readJson } from './http'
import type { AuthorizedFetch } from './nurseflowApi'
import type { components } from './schema.gen'

export type PersonalCalendar = components['schemas']['PublishedPersonalCalendar']
export type WardCalendar = components['schemas']['PublishedWardCalendar']
export interface PublishedRosterApi {
  mine(wardId: string, month: string): Promise<PersonalCalendar>
  ward(wardId: string, month: string): Promise<WardCalendar>
}

export function createPublishedRosterApi(fetch: AuthorizedFetch): PublishedRosterApi {
  const path = (wardId: string, month: string, mine: boolean) =>
    `/wards/${encodeURIComponent(wardId)}/published-rosters${mine ? '/mine' : ''}?month=${encodeURIComponent(month)}`
  return {
    mine: async (wardId, month) => readJson(await fetch(path(wardId, month, true))),
    ward: async (wardId, month) => readJson(await fetch(path(wardId, month, false))),
  }
}
