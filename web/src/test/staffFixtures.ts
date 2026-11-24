import type { CareerPolicy, StaffMember, Team, WardMember } from '../api/types'
import { USER_A, USER_B } from './fakeAuthServer'
import { WARD_1 } from './fakeInvitationBackend'

export { WARD_1 }
export const TEAM_A: Team = { teamId: '30000000-0000-4000-8000-000000000001', name: '가상 A팀', sortOrder: 1 }
export const TEAM_B: Team = { teamId: '30000000-0000-4000-8000-000000000002', name: '가상 B팀', sortOrder: 2 }
export const POLICY: CareerPolicy = { stageThresholdMonths: [12, 36, 72, 120], ranks: ['가상 직급', '신규'], isDefault: false }
export const NURSE_A: StaffMember = {
  nurseId: '20000000-0000-4000-8000-000000000001', displayName: '가상 간호사 가', active: true, qualifications: ['가상 자격'],
  linkedAccount: true, team: TEAM_A, teamOrder: 1, seniorEligible: false, stageOverride: 4, rosterStage: 4,
  profile: { status: 'CONFIRMED', hospitalJoinedOn: '2024-01', careerStartedOn: '2020-03', rank: '가상 직급', confirmedCareerStartedOn: '2020-03', confirmedRank: '가상 직급', scoringStage: 3, experienceMonths: 80 },
}
export const NURSE_B: StaffMember = { ...NURSE_A, nurseId: '20000000-0000-4000-8000-000000000002', displayName: '가상 간호사 나', linkedAccount: false, teamOrder: 2 }
export const ADMIN: WardMember = { ...USER_A, roles: ['WARD_ADMIN'], active: true, canViewWardCalendar: true, nurseId: null }
export const MEMBER: WardMember = { ...USER_B, roles: ['WARD_MEMBER'], active: true, canViewWardCalendar: true, nurseId: NURSE_A.nurseId }
export const ADMIN_WARD = { ...WARD_1, roles: ['WARD_ADMIN'] as ('WARD_ADMIN' | 'WARD_MEMBER')[], canViewWardCalendar: true }
