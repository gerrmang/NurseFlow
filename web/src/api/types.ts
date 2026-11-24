import type { components } from './schema.gen'

type Schemas = components['schemas']

export type WebSession = Schemas['WebSession']
export type WebKakaoAuthorization = Schemas['WebKakaoAuthorization']
export type CurrentUser = Schemas['CurrentUser']
export type WardMembership = Schemas['WardMembership']
export type WardRole = WardMembership['roles'][number]
export type ApiProblem = Schemas['ApiProblem']
export type HospitalCandidate = Schemas['HospitalCandidate']
export type HospitalSearchPage = Schemas['HospitalSearchPage']
export type CreateWardRequest = Schemas['CreateWardRequest']
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
export type RosterEligibility = Schemas['RosterEligibility']
export type Team = Schemas['Team']
export type TeamInput = Schemas['TeamInput']
export type StaffInput = Schemas['StaffInput']
export type CareerPolicy = Schemas['CareerPolicy']
export type CareerProfileInput = Schemas['CareerProfileInput']
export type RosterSettingsRequest = Schemas['RosterSettingsRequest']
export type StaffAdjustment = Schemas['StaffAdjustment']
export type WardMember = Schemas['WardMember']
export type UpdateMembershipRequest = Schemas['UpdateMembershipRequest']
export interface InvitationList {
  items: Invitation[]
}
