import { readJson } from './http'
import type { CurrentUser, WardList } from './types'

/** Sends an authorized request; provided by the SessionManager (adds Bearer, refreshes once on 401). */
export type AuthorizedFetch = (path: string, init?: RequestInit) => Promise<Response>

export interface NurseFlowApi {
  getMe(): Promise<CurrentUser>
  getWards(): Promise<WardList>
}

export function createNurseFlowApi(authorizedFetch: AuthorizedFetch): NurseFlowApi {
  return {
    getMe: async () => readJson(await authorizedFetch('/me')),
    getWards: async () => readJson(await authorizedFetch('/wards')),
  }
}
