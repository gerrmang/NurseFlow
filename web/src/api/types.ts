import type { components } from './schema.gen'

type Schemas = components['schemas']

export type WebSession = Schemas['WebSession']
export type WebKakaoAuthorization = Schemas['WebKakaoAuthorization']
export type CurrentUser = Schemas['CurrentUser']
export type WardMembership = Schemas['WardMembership']
export type WardRole = WardMembership['roles'][number]
export type ApiProblem = Schemas['ApiProblem']
export interface WardList {
  items: WardMembership[]
}

export type Invitation = Schemas['Invitation']
export type InvitationRole = Invitation['role']
export type InvitationStatus = Invitation['status']
export type CreatedInvitation = Schemas['CreatedInvitation']
export type InvitationPreview = Schemas['InvitationPreview']
export type CreateInvitationRequest = Schemas['CreateInvitationRequest']
export type RedeemInvitationRequest = Schemas['RedeemInvitationRequest']
export type StaffMember = Schemas['StaffMember']
export interface InvitationList {
  items: Invitation[]
}
