import { expectNoContent, readJson } from './http'
import type {
  CreateInvitationRequest,
  CreatedInvitation,
  CurrentUser,
  InvitationList,
  InvitationPreview,
  RedeemInvitationRequest,
  StaffMember,
  WardList,
  WardMembership,
} from './types'

/** Sends an authorized request; provided by the SessionManager (adds Bearer, refreshes once on 401). */
export type AuthorizedFetch = (path: string, init?: RequestInit) => Promise<Response>

export interface NurseFlowApi {
  getMe(): Promise<CurrentUser>
  getWards(): Promise<WardList>
  /** Admin: invitations of a ward, newest first. The list never contains tokens. */
  listInvitations(wardId: string): Promise<InvitationList>
  /** Admin: issues a 24h single-use invitation. The raw token is only in this response. */
  createInvitation(wardId: string, request: CreateInvitationRequest): Promise<CreatedInvitation>
  /** Admin: revokes the old link and issues a new one for the same role and nurse. */
  reissueInvitation(wardId: string, invitationId: string): Promise<CreatedInvitation>
  /** Admin: revokes an unused invitation. Already revoked is still 204, so repeating it is safe. */
  revokeInvitation(wardId: string, invitationId: string): Promise<void>
  /** Admin: active nurse records, used to pick a record to link. */
  listActiveStaff(wardId: string): Promise<StaffMember[]>
  /** Invitee: does not consume the invitation. The token is sent in the body, never in a URL. */
  previewInvitation(token: string): Promise<InvitationPreview>
  /** Invitee: joins the ward. Not idempotent on the server; never resend automatically. */
  redeemInvitation(request: RedeemInvitationRequest): Promise<WardMembership>
}

const jsonInit = (method: string, body?: unknown): RequestInit =>
  body === undefined
    ? { method }
    : { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }

export function createNurseFlowApi(authorizedFetch: AuthorizedFetch): NurseFlowApi {
  const ward = (wardId: string) => `/wards/${encodeURIComponent(wardId)}`
  return {
    getMe: async () => readJson(await authorizedFetch('/me')),
    getWards: async () => readJson(await authorizedFetch('/wards')),
    listInvitations: async (wardId) => readJson(await authorizedFetch(`${ward(wardId)}/invitations`)),
    createInvitation: async (wardId, request) =>
      readJson(await authorizedFetch(`${ward(wardId)}/invitations`, jsonInit('POST', request))),
    reissueInvitation: async (wardId, invitationId) =>
      readJson(await authorizedFetch(`${ward(wardId)}/invitations/${encodeURIComponent(invitationId)}/reissue`, jsonInit('POST'))),
    revokeInvitation: async (wardId, invitationId) =>
      expectNoContent(await authorizedFetch(`${ward(wardId)}/invitations/${encodeURIComponent(invitationId)}`, jsonInit('DELETE'))),
    listActiveStaff: async (wardId) => {
      const page = await readJson<{ items: StaffMember[] }>(await authorizedFetch(`${ward(wardId)}/staff?includeInactive=false`))
      return page.items
    },
    previewInvitation: async (token) => readJson(await authorizedFetch('/invitations/preview', jsonInit('POST', { token }))),
    redeemInvitation: async (request) => readJson(await authorizedFetch('/invitations/redeem', jsonInit('POST', request))),
  }
}
