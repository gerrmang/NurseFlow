import { ApiError, readJson } from './http'
import type { AuthorizedFetch } from './nurseflowApi'
import type { components } from './schema.gen'

type S = components['schemas']
export type OffRequest = S['OffRequest']
export type OffRequestList = S['OffRequestList']
export type OffPolicyInput = S['OffRequestPolicyInput']
export type OffPolicy = { value: S['OffRequestPolicy']; etag: string }
export type OffEligibility = S['RosterEligibility']
export type Decision = 'confirm' | 'reject' | 'withdraw'
export const invalid = () => new ApiError(0, 'INVALID_RESPONSE')
export function validDate(value: string): boolean {
  return /^(?!0000)\d{4}-(0[1-9]|1[0-2])-\d{2}$/.test(value) &&
    Number.isFinite(Date.parse(`${value}T00:00:00Z`)) && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value
}
const integer = (v: number, min: number, max: number) => Number.isInteger(v) && v >= min && v <= max
const instant = (v: unknown): v is string => typeof v === 'string' && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?(?:Z|[+-]\d\d:\d\d)$/.test(v) && validDate(v.slice(0, 10)) && Number.isFinite(Date.parse(v))
const id = (v: unknown): v is string => typeof v === 'string' && v.length > 0
export function checkRequest(v: OffRequest): OffRequest {
  if (!v || !id(v.requestId) || !id(v.nurseId) || typeof v.date !== 'string' || !validDate(v.date) ||
    !['SUBMITTED', 'CONFIRMED', 'REJECTED', 'WITHDRAWN'].includes(v.status) || !['NURSE', 'ADMIN'].includes(v.source) ||
    !instant(v.submittedAt) || (v.decidedAt != null && !instant(v.decidedAt)) || typeof v.afterDeadline !== 'boolean') throw invalid()
  return v
}
const json = (method: string, body?: unknown): RequestInit => ({ method, ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }) })

export function createOffRequestApi(fetch: AuthorizedFetch, wardId: string) {
  const ward = `/wards/${encodeURIComponent(wardId)}`
  const base = `${ward}/off-requests`
  const policyResponse = async (response: Response): Promise<OffPolicy> => {
    const value = await readJson<S['OffRequestPolicy']>(response)
    const etag = response.headers.get('ETag')
    if (!value || !integer(value.monthlyLimit, 0, 31) || !integer(value.deadlineDay, 1, 28) ||
      !integer(value.policyRevision, 0, Number.MAX_SAFE_INTEGER) || etag !== `"policy-${value.policyRevision}"`) throw invalid()
    return { value, etag }
  }
  return {
    list: async (month: string): Promise<OffRequestList> => {
      const value = await readJson<OffRequestList>(await fetch(`${base}?${new URLSearchParams({ month })}`))
      if (!value || !Array.isArray(value.items) || !instant(value.deadline) || !integer(value.monthlyLimit, 0, 31)) throw invalid()
      value.items.forEach(item => { checkRequest(item); if (!item.date.startsWith(`${month}-`)) throw invalid() })
      if (new Set(value.items.map(item => item.requestId)).size !== value.items.length) throw invalid()
      return value
    },
    submit: async (date: string, nurseId?: string) => {
      const value = checkRequest(await readJson<OffRequest>(await fetch(base, json('POST', { date, ...(nurseId ? { nurseId } : {}) }))))
      if (value.date !== date || value.status !== 'SUBMITTED' || value.source !== (nurseId ? 'ADMIN' : 'NURSE') || (nurseId && value.nurseId !== nurseId)) throw invalid()
      return value
    },
    decide: async (requestId: string, action: Decision) => {
      const value = checkRequest(await readJson<OffRequest>(await fetch(`${base}/${encodeURIComponent(requestId)}/${action}`, json('POST'))))
      const expected = { confirm: 'CONFIRMED', reject: 'REJECTED', withdraw: 'WITHDRAWN' }[action]
      if (value.requestId !== requestId || value.status !== expected) throw invalid()
      return value
    },
    policy: async () => policyResponse(await fetch(`${base}/policy`)),
    savePolicy: async (input: OffPolicyInput, etag: string) => policyResponse(await fetch(`${base}/policy`, {
      ...json('PUT', input), headers: { 'Content-Type': 'application/json', 'If-Match': etag },
    })),
    eligibility: async (): Promise<OffEligibility> => {
      const value = await readJson<OffEligibility>(await fetch(`${ward}/roster-eligibility`))
      if (!value || typeof value.demoMode !== 'boolean' || !integer(value.policyRevision, 0, Number.MAX_SAFE_INTEGER) || !Array.isArray(value.items) ||
        value.items.some(item => !item || !id(item.nurseId) || typeof item.displayName !== 'string' || typeof item.eligible !== 'boolean' || typeof item.reason !== 'string') ||
        new Set(value.items.map(item => item.nurseId)).size !== value.items.length) throw invalid()
      return value
    },
  }
}
export type OffRequestApi = ReturnType<typeof createOffRequestApi>
