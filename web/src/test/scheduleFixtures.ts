import type { Roster, Shift } from '../api/scheduleSettingsApi'
import { ADMIN_WARD, NURSE_A, TEAM_A } from './staffFixtures'
export { ADMIN_WARD, NURSE_A, TEAM_A }
export const SHIFT: Shift = { shiftTypeId: '40000000-0000-4000-8000-000000000001', code: 'D', displayName: '가상 Day', kind: 'DAY', startTime: '07:00', endTime: '15:00', spansMidnight: false, breakMinutes: 0, recognizedMinutes: 480, targetCreditMinutes: 480, durationMinutes: 480, active: true }
export const ROSTER: Roster = { rosterId: '50000000-0000-4000-8000-000000000001', wardId: ADMIN_WARD.wardId, month: '2026-11', status: 'DRAFT', revision: 1, timeZone: 'Asia/Seoul', staffing: { required: 0, assigned: 0, unfilled: 0, hardRuleViolations: 0, seniorUnfilled: 0 }, slots: [] }
export const taggedJson = (value: unknown, etag = '"policy-1"', status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json', ETag: etag } })
