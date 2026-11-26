import type { OffEligibility, OffPolicy, OffRequest, OffRequestList } from '../api/offRequestApi'
import { NURSE_A, ADMIN_WARD } from './staffFixtures'
import { WARD_1 } from './fakeInvitationBackend'
import type { WardMembership } from '../api/types'

export const OFF_MONTH = '2031-03'
export const OFF_ITEM: OffRequest = { requestId: '40000000-0000-4000-8000-000000000001', nurseId: NURSE_A.nurseId, date: '2031-03-01', status: 'SUBMITTED', source: 'NURSE', submittedAt: '2031-02-01T00:00:00Z', afterDeadline: false, decidedAt: null }
export const OFF_LIST: OffRequestList = { items: [OFF_ITEM], deadline: '2031-02-15T15:00:00Z', monthlyLimit: 4 }
export const OFF_POLICY: OffPolicy = { value: { monthlyLimit: 4, deadlineDay: 15, policyRevision: 2 }, etag: '"policy-2"' }
export const OFF_ELIGIBILITY: OffEligibility = { demoMode: false, policyRevision: 2, items: [{ nurseId: NURSE_A.nurseId, displayName: NURSE_A.displayName, eligible: true, reason: 'ELIGIBLE' }] }
export const OFF_ADMIN = ADMIN_WARD
export const OFF_MEMBER: WardMembership = { ...WARD_1, roles: ['WARD_MEMBER'], canViewWardCalendar: true }
