import type { components } from './schema.gen'
import type { AuthorizedFetch } from './nurseflowApi'
import { ApiError, ClientErrorCode, expectNoContent, readJson } from './http'
import { compareInstants } from '../schedule/timeZone'

type S = components['schemas']
export type Shift = S['ShiftType']
export type ShiftInput = S['ShiftTypeInput']
export type Preview = S['ShiftTypeChangePreview']
export type Roster = S['Roster']
export type RosterSummary = S['RosterSummary']
export type Requirement = S['StaffingRequirement']
export type Absence = S['ApprovedAbsence']
export type AbsenceInput = S['ApprovedAbsenceInput']
export type Pattern = S['StaffWorkPattern']
export interface Tagged<T> { value: T; etag: string }
const invalid = () => new ApiError(0, ClientErrorCode.INVALID_RESPONSE)
function object(value: unknown): value is Record<string, unknown> { return value !== null && typeof value === 'object' }
const revision = (value: unknown) => Number.isSafeInteger(value) && Number(value) >= 0
const list = (value: unknown): value is Record<string, unknown> & { items: unknown[] } => object(value) && Array.isArray(value.items)
const shift = (value: unknown) => object(value) && typeof value.shiftTypeId === 'string' && typeof value.code === 'string' && typeof value.displayName === 'string' && ['DAY', 'EVENING', 'NIGHT', 'OTHER'].includes(String(value.kind)) && typeof value.active === 'boolean' && typeof value.spansMidnight === 'boolean' && ['startTime', 'endTime'].every(key => typeof value[key] === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(value[key])) && ['breakMinutes', 'recognizedMinutes', 'targetCreditMinutes', 'durationMinutes'].every(key => revision(value[key]))
function zone(value: unknown): boolean {
  if (typeof value !== 'string') return false
  if (/^(?:UTC|GMT|UT)?[+-](?:0?\d|1[0-8])(?::?[0-5]\d)?(?::?[0-5]\d)?$/.test(value) || value === 'Z') return true
  try { new Intl.DateTimeFormat('en', { timeZone: value }); return true } catch { return false }
}
const roster = (value: unknown) => object(value) && typeof value.rosterId === 'string' && typeof value.wardId === 'string' && typeof value.month === 'string' && /^\d{4}-(0[1-9]|1[0-2])$/.test(value.month) && zone(value.timeZone) && ['DRAFT', 'PUBLISHED'].includes(String(value.status)) && revision(value.revision) && object(value.staffing) && ['required', 'assigned', 'unfilled', 'hardRuleViolations'].every(key => revision((value.staffing as Record<string, unknown>)[key]))
const instant = (value: unknown) => typeof value === 'string' && /T.*(?:Z|[+-]\d{2}:\d{2})$/.test(value) && Number.isFinite(Date.parse(value))
const absence = (value: unknown) => object(value) && ['absenceId', 'nurseId'].every(key => typeof value[key] === 'string') && instant(value.startsAt) && instant(value.endsAt) && compareInstants(String(value.startsAt), String(value.endsAt)) < 0 && ['APPROVED_LEAVE', 'APPROVED_HALF_DAY'].includes(String(value.kind))
async function checked<T>(response: Response, valid: (value: unknown) => boolean): Promise<T> {
  const value = await readJson<unknown>(response)
  if (!valid(value)) throw invalid()
  return value as T
}
async function tagged<T>(response: Response, valid: (value: unknown) => boolean): Promise<Tagged<T>> {
  const value = await checked<T>(response, valid)
  const etag = response.headers.get('ETag')
  if (!etag || !/^"[^"\r\n]+"$/.test(etag)) throw invalid()
  return { value, etag }
}
const json = (method: string, body?: unknown, etag?: string, key?: string): RequestInit => ({ method, headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(etag ? { 'If-Match': etag } : {}), ...(key ? { 'Idempotency-Key': key } : {}) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) })

export function createScheduleSettingsApi(fetch: AuthorizedFetch, wardId: string) {
  const base = `/wards/${encodeURIComponent(wardId)}`
  const shifts = `${base}/shift-types`
  const path = (id: string) => `/rosters/${encodeURIComponent(id)}`
  const patternPath = (id: string) => `${base}/staff/${encodeURIComponent(id)}/work-pattern`
  const validPattern = (v: unknown) => object(v) && ['GENERAL', 'DAY_EVENING_ONLY', 'NIGHT_ONLY'].includes(String(v.workPattern))
  return {
    shifts: async () => tagged<{ items: Shift[]; policyRevision: number }>(await fetch(`${shifts}?includeInactive=true`), v => list(v) && revision(v.policyRevision) && (v.items as unknown[]).every(shift)),
    createShift: async (input: ShiftInput, etag: string, key: string) => tagged<Shift>(await fetch(shifts, json('POST', input, etag, key)), shift),
    updateShift: async (id: string, input: ShiftInput, etag: string) => tagged<Shift>(await fetch(`${shifts}/${encodeURIComponent(id)}`, json('PUT', input, etag)), shift),
    deactivateShift: async (id: string, etag: string) => tagged<Shift>(await fetch(`${shifts}/${encodeURIComponent(id)}`, json('DELETE', undefined, etag)), shift),
    preview: async (id: string, input: ShiftInput) => checked<Preview>(await fetch(`${shifts}/${encodeURIComponent(id)}/preview`, json('POST', input)), v => object(v) && shift(v.shiftType) && revision(v.policyRevision) && typeof v.flagsDrafts === 'boolean' && typeof v.rewritesDraftSlotTimes === 'boolean' && Array.isArray(v.affectedDrafts) && v.affectedDrafts.every(d => object(d) && typeof d.month === 'string' && revision(d.slots) && revision(d.assignedSlots)) && revision(v.publishedRostersUsingType)),
    rosters: async (month: string) => checked<{ items: RosterSummary[] }>(await fetch(`${base}/rosters?month=${encodeURIComponent(month)}`), v => list(v) && (v.items as unknown[]).every(roster)),
    roster: async (id: string) => tagged<Roster>(await fetch(path(id)), v => roster(v) && object(v) && Array.isArray(v.slots) && v.slots.every(s => object(s) && typeof s.slotId === 'string' && typeof s.date === 'string' && typeof s.shiftTypeId === 'string' && typeof s.shiftCode === 'string' && ['FILLED', 'UNFILLED'].includes(String(s.status)))),
    createRoster: async (month: string, key: string) => tagged<Roster>(await fetch(`${base}/rosters`, json('POST', { month }, undefined, key)), v => roster(v) && object(v) && Array.isArray(v.slots)),
    requirements: async (id: string, items: Requirement[], etag: string) => tagged<RosterSummary>(await fetch(`${path(id)}/requirements`, json('PUT', { items }, etag)), roster),
    absences: async (id: string) => (await checked<{ items: Absence[] }>(await fetch(`${path(id)}/absences`), v => list(v) && (v.items as unknown[]).every(absence))).items,
    saveAbsence: async (id: string, input: AbsenceInput, etag: string, absenceId?: string, key?: string) => tagged<Absence>(await fetch(`${path(id)}/absences${absenceId ? `/${encodeURIComponent(absenceId)}` : ''}`, json(absenceId ? 'PUT' : 'POST', input, etag, key)), absence),
    deleteAbsence: async (id: string, absenceId: string, etag: string) => expectNoContent(await fetch(`${path(id)}/absences/${encodeURIComponent(absenceId)}`, json('DELETE', undefined, etag))),
    pattern: async (id: string) => checked<Pattern>(await fetch(patternPath(id)), validPattern),
    savePattern: async (id: string, input: Pattern) => checked<Pattern>(await fetch(patternPath(id), json('PUT', input)), validPattern),
  }
}
export type ScheduleSettingsApi = ReturnType<typeof createScheduleSettingsApi>
