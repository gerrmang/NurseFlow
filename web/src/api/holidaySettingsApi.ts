import type { components } from './schema.gen'
import type { AuthorizedFetch } from './nurseflowApi'
import { ApiError, ClientErrorCode, readJson } from './http'
import { monthDates } from '../roster/calendar'

type S = components['schemas']
export type HolidayCalendar = S['HolidayCalendar']
export type HolidayDay = S['HolidayCalendarDay']
export type TargetPolicy = S['TargetHourPolicyInput']
export type Policy = S['TargetHourPolicy']
export interface Tagged<T> { value: T; etag: string }
export const policyFields = [
  ['dailyStandardMinutes', '하루 기준시간', 1, 1440],
  ['leaveCreditMinutes', '연차 환산시간', 0, 1440],
  ['halfDayCreditMinutes', '반차 환산시간', 0, 1440],
  ['sofCreditMinutes', 'SOF 환산시간', 0, 1440],
  ['nightDedicatedTargetMinutes', 'Night 전담 월 목표', 0, 44640],
  ['nightDedicatedNightCreditMinutes', 'Night 전담 1회 환산시간', 0, 1440],
] as const
export const dayLabels: Record<HolidayDay['kind'], string> = { PUBLIC_HOLIDAY: '공휴일', SUBSTITUTE_HOLIDAY: '대체공휴일', WARD_REST_DAY: '병동 휴무', WARD_WORKDAY: '근무일 지정' }
const object = (v: unknown): v is Record<string, unknown> => v !== null && typeof v === 'object'
const integer = (v: unknown, min = 0, max = Number.MAX_SAFE_INTEGER) => Number.isSafeInteger(v) && Number(v) >= min && Number(v) <= max
export function validPolicy(v: unknown): v is TargetPolicy {
  return object(v) && policyFields.every(([key, , min, max], i) => i >= 4 && v[key] === null || integer(v[key], min, max))
}
export function policyInput(v: Policy): TargetPolicy {
  return Object.fromEntries(policyFields.map(([key]) => [key, v[key]])) as TargetPolicy
}
function validCalendar(v: unknown, month: string): v is HolidayCalendar {
  if (!object(v) || v.month !== month || !integer(v.policyRevision) || !object(v.target) || v.target.month !== month || !object(v.holidaySource)) return false
  const dates = monthDates(month).filter(Boolean)
  const days = (list: unknown, national: boolean) => Array.isArray(list) && new Set(list.map(d => object(d) ? d.date : null)).size === list.length && list.every(d => object(d) && dates.includes(String(d.date)) && Object.hasOwn(dayLabels, String(d.kind)) && (!national || ['PUBLIC_HOLIDAY', 'SUBSTITUTE_HOLIDAY'].includes(String(d.kind))) && (d.name == null || typeof d.name === 'string' && d.name.length <= 50))
  return days(v.days, false) && days(v.publicHolidays, true) && typeof v.holidaySource.configured === 'boolean' && (v.holidaySource.fetchedAt === null || typeof v.holidaySource.fetchedAt === 'string' && Number.isFinite(Date.parse(v.holidaySource.fetchedAt))) && ['daysInMonth', 'weekendDays', 'holidayDays', 'wardRestDays', 'wardWorkdays', 'restDays', 'targetDays', 'dailyStandardMinutes', 'generalTargetMinutes'].every(k => integer((v.target as Record<string, unknown>)[k]))
}
export function createHolidaySettingsApi(fetch: AuthorizedFetch, wardId: string) {
  const base = `/wards/${encodeURIComponent(wardId)}`
  async function tagged<T>(path: string, valid: (v: unknown) => boolean, init?: RequestInit): Promise<Tagged<T>> {
    const response = await fetch(path, init)
    const value = await readJson<unknown>(response)
    const etag = response.headers.get('ETag')
    if (!valid(value) || !etag || !/^"[^"\r\n]+"$/.test(etag)) throw new ApiError(0, ClientErrorCode.INVALID_RESPONSE)
    return { value: value as T, etag }
  }
  const json = (body: unknown, etag: string): RequestInit => ({ method: 'PUT', headers: { 'Content-Type': 'application/json', 'If-Match': etag }, body: JSON.stringify(body) })
  const calendarPath = (month: string) => `${base}/holiday-calendar?month=${encodeURIComponent(month)}`
  const policy = (v: unknown) => object(v) && integer(v.policyRevision) && validPolicy(v)
  return {
    calendar: (month: string) => tagged<HolidayCalendar>(calendarPath(month), v => validCalendar(v, month)),
    policy: () => tagged<Policy>(`${base}/target-hour-policy`, policy),
    saveCalendar: (month: string, days: HolidayDay[], etag: string) => tagged<HolidayCalendar>(calendarPath(month), v => validCalendar(v, month), json({ days }, etag)),
    savePolicy: (input: TargetPolicy, etag: string) => tagged<Policy>(`${base}/target-hour-policy`, policy, json(input, etag)),
    refresh: (month: string) => tagged<HolidayCalendar>(`${base}/holiday-calendar/refresh?month=${encodeURIComponent(month)}`, v => validCalendar(v, month), { method: 'POST' }),
  }
}
export type HolidaySettingsApi = ReturnType<typeof createHolidaySettingsApi>
