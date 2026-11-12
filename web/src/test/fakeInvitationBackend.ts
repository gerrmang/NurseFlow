import type { FetchLike } from '../api/http'
import type { WardMembership, WardRole } from '../api/types'
import { json, type FakeAuthServer } from './fakeAuthServer'

/**
 * Fictional ward/invitation server for tests, layered over FakeAuthServer's login model. It
 * reproduces the A1/A4 behaviors the web client depends on: 24h invitations, reissue revoking the
 * original, idempotent revoke, preview needing a login, redeem error ordering, and "already joined"
 * conflicts. Calls can be held, failed before processing, or processed with the response lost.
 * No real names, tokens or leave reasons are used.
 */
export type BackendOp = 'wards' | 'list' | 'staff' | 'issue' | 'reissue' | 'revoke' | 'preview' | 'redeem'

interface InvitationRecord {
  id: string
  wardId: string
  role: WardRole
  nurseId?: string
  token: string
  createdAt: number
  expiresAt: number
  redeemedAt?: number
  revokedAt?: number
}

export interface FakeNurse {
  nurseId: string
  displayName: string
  active: boolean
  linkedAccount: boolean
}

interface Fault {
  kind: 'before' | 'lostAfter' | 'status'
  status?: number
  code?: string
}

const DAY = 24 * 60 * 60_000

export const WARD_1 = { wardId: '10000000-0000-4000-8000-000000000001', wardName: '가상 내과 병동', hospitalName: '가상 병원 가' }

export class FakeInvitationBackend {
  readonly calls: { op: BackendOp; method: string; path: string; body: unknown }[] = []
  readonly invitations: InvitationRecord[] = []
  readonly nurses: FakeNurse[] = []
  /** wardId -> userId -> roles */
  private readonly members = new Map<string, Map<string, WardRole[]>>()
  private readonly wardInfo = new Map<string, { wardName: string; hospitalName: string | null }>()
  private readonly faults = new Map<BackendOp, Fault[]>()
  private readonly gates = new Map<BackendOp, { promise: Promise<void>; release: () => void }[]>()
  private seq = 0
  now = () => Date.now()

  constructor(private readonly auth: FakeAuthServer) {
    this.addWard(WARD_1)
  }

  addWard(ward: { wardId: string; wardName: string; hospitalName: string | null }): void {
    this.wardInfo.set(ward.wardId, { wardName: ward.wardName, hospitalName: ward.hospitalName })
    if (!this.members.has(ward.wardId)) this.members.set(ward.wardId, new Map())
  }

  addMember(wardId: string, userId: string, roles: WardRole[]): void {
    this.members.get(wardId)!.set(userId, roles)
  }

  isMember(wardId: string, userId: string): boolean {
    return this.members.get(wardId)?.has(userId) ?? false
  }

  addNurse(partial: Partial<FakeNurse> & { displayName: string }): FakeNurse {
    const nurse: FakeNurse = { nurseId: `20000000-0000-4000-8000-${String(++this.seq).padStart(12, '0')}`, active: true, linkedAccount: false, ...partial }
    this.nurses.push(nurse)
    return nurse
  }

  /** Puts an invitation straight into the store, as if an admin had issued it earlier. Returns its token. */
  seedInvitation(options: { role?: WardRole; nurseId?: string; wardId?: string; expiresInMs?: number; revoked?: boolean; redeemed?: boolean }): string {
    const record = this.create(options.wardId ?? WARD_1.wardId, options.role ?? 'WARD_MEMBER', options.nurseId)
    if (options.expiresInMs !== undefined) record.expiresAt = this.now() + options.expiresInMs
    if (options.revoked) record.revokedAt = this.now()
    if (options.redeemed) record.redeemedAt = this.now()
    return record.token
  }

  hold(op: BackendOp): () => void {
    let release!: () => void
    const promise = new Promise<void>((resolve) => (release = resolve))
    const list = this.gates.get(op) ?? []
    list.push({ promise, release })
    this.gates.set(op, list)
    return release
  }

  /** The next `op` fails before the server does anything (connection dropped). */
  failBefore(op: BackendOp): void {
    this.pushFault(op, { kind: 'before' })
  }

  /** The next `op` is fully processed, but the response never reaches the client. */
  loseResponseAfter(op: BackendOp): void {
    this.pushFault(op, { kind: 'lostAfter' })
  }

  /** The next `op` answers with this error status and code, without changing state. */
  failWith(op: BackendOp, status: number, code: string): void {
    this.pushFault(op, { kind: 'status', status, code })
  }

  count(op: BackendOp): number {
    return this.calls.filter((call) => call.op === op).length
  }

  statusOf(record: InvitationRecord): 'PENDING' | 'REDEEMED' | 'REVOKED' | 'EXPIRED' {
    if (record.redeemedAt !== undefined) return 'REDEEMED'
    if (record.revokedAt !== undefined) return 'REVOKED'
    return this.now() >= record.expiresAt ? 'EXPIRED' : 'PENDING'
  }

  /** Ordinary API fetch: same login rules as FakeAuthServer, plus ward and invitation endpoints. */
  readonly fetch: FetchLike = async (input, init) => {
    if (init?.signal?.aborted) throw new DOMException('aborted', 'AbortError')
    const token = (new Headers(init?.headers).get('Authorization') ?? '').replace(/^Bearer /, '')
    if (!this.auth.validAccessTokens.has(token)) return json(401, { code: 'UNAUTHORIZED' })
    const user = this.auth.userForToken(token)
    const method = (init?.method ?? 'GET').toUpperCase()
    const path = input.replace(/^\/api\/v1/, '').split('?')[0]!
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined

    const route = this.route(method, path)
    if (!route) return this.auth.fetch(input, init)

    this.calls.push({ op: route.op, method, path, body })
    const gate = this.gates.get(route.op)?.shift()
    if (gate) await gate.promise
    const fault = this.faults.get(route.op)?.shift()
    if (fault?.kind === 'before') throw new TypeError('network down')
    if (fault?.kind === 'status') return json(fault.status!, { code: fault.code })
    const response = route.handle(user.userId, body)
    if (fault?.kind === 'lostAfter') throw new TypeError('response lost')
    return response
  }

  private pushFault(op: BackendOp, fault: Fault): void {
    const list = this.faults.get(op) ?? []
    list.push(fault)
    this.faults.set(op, list)
  }

  private route(method: string, path: string): { op: BackendOp; handle: (userId: string, body: unknown) => Response } | null {
    if (method === 'GET' && path === '/wards') return { op: 'wards', handle: (userId) => this.wards(userId) }
    let match = /^\/wards\/([^/]+)\/invitations$/.exec(path)
    if (match && method === 'GET') return { op: 'list', handle: (userId) => this.guardAdmin(match![1]!, userId, () => this.list(match![1]!)) }
    if (match && method === 'POST') return { op: 'issue', handle: (userId, body) => this.guardAdmin(match![1]!, userId, () => this.issue(match![1]!, body)) }
    match = /^\/wards\/([^/]+)\/staff$/.exec(path)
    if (match && method === 'GET') return { op: 'staff', handle: (userId) => this.guardAdmin(match![1]!, userId, () => this.staff()) }
    match = /^\/wards\/([^/]+)\/invitations\/([^/]+)\/reissue$/.exec(path)
    if (match && method === 'POST') return { op: 'reissue', handle: (userId) => this.guardAdmin(match![1]!, userId, () => this.reissue(match![1]!, match![2]!)) }
    match = /^\/wards\/([^/]+)\/invitations\/([^/]+)$/.exec(path)
    if (match && method === 'DELETE') return { op: 'revoke', handle: (userId) => this.guardAdmin(match![1]!, userId, () => this.revoke(match![1]!, match![2]!)) }
    if (method === 'POST' && path === '/invitations/preview') return { op: 'preview', handle: (_userId, body) => this.preview(body) }
    if (method === 'POST' && path === '/invitations/redeem') return { op: 'redeem', handle: (userId, body) => this.redeem(userId, body) }
    return null
  }

  private wards(userId: string): Response {
    const items: WardMembership[] = []
    for (const [wardId, members] of this.members) {
      const roles = members.get(userId)
      const info = this.wardInfo.get(wardId)!
      if (roles) items.push({ wardId, wardName: info.wardName, hospitalName: info.hospitalName, roles, canViewWardCalendar: true } as WardMembership)
    }
    return json(200, { items })
  }

  private guardAdmin(wardId: string, userId: string, action: () => Response): Response {
    const roles = this.members.get(wardId)?.get(userId)
    if (!roles) return json(404, { code: 'WARD_NOT_FOUND' })
    if (!roles.includes('WARD_ADMIN')) return json(403, { code: 'WARD_ADMIN_REQUIRED' })
    return action()
  }

  private view(record: InvitationRecord) {
    return {
      invitationId: record.id,
      wardId: record.wardId,
      role: record.role,
      ...(record.nurseId ? { nurseId: record.nurseId } : {}),
      status: this.statusOf(record),
      createdAt: new Date(record.createdAt).toISOString(),
      expiresAt: new Date(record.expiresAt).toISOString(),
      ...(record.redeemedAt !== undefined ? { redeemedAt: new Date(record.redeemedAt).toISOString() } : {}),
      ...(record.revokedAt !== undefined ? { revokedAt: new Date(record.revokedAt).toISOString() } : {}),
    }
  }

  private create(wardId: string, role: WardRole, nurseId?: string): InvitationRecord {
    const n = ++this.seq
    const record: InvitationRecord = {
      id: `30000000-0000-4000-8000-${String(n).padStart(12, '0')}`,
      wardId,
      role,
      nurseId,
      token: `T${String(n).padStart(42, '0')}`,
      createdAt: this.now() + n, // keeps "newest first" stable inside one millisecond
      expiresAt: this.now() + DAY,
    }
    this.invitations.push(record)
    return record
  }

  private created(record: InvitationRecord): Response {
    // inviteUrl is deliberately an Android development scheme: the web must not use it.
    return json(201, { invitation: this.view(record), token: record.token, inviteUrl: `nurseflow://invite#token=${record.token}` })
  }

  private list(wardId: string): Response {
    const items = this.invitations
      .filter((record) => record.wardId === wardId)
      .sort((a, b) => b.createdAt - a.createdAt)
      .map((record) => this.view(record))
    return json(200, { items })
  }

  private staff(): Response {
    const items = this.nurses.map((nurse) => ({ ...nurse, qualifications: [], profile: {}, teamOrder: null, seniorEligible: false, stageOverride: null, rosterStage: null }))
    return json(200, { items, page: 1, pageSize: items.length, totalItems: items.length })
  }

  private issue(wardId: string, body: unknown): Response {
    const request = body as { role?: WardRole; nurseId?: string }
    if (request.role !== 'WARD_ADMIN' && request.role !== 'WARD_MEMBER') return json(400, { code: 'INVALID_REQUEST' })
    if (request.role === 'WARD_ADMIN' && request.nurseId) return json(400, { code: 'INVALID_INVITATION' })
    const failure = request.nurseId ? this.nurseConflict(request.nurseId, undefined) : null
    if (failure) return failure
    return this.created(this.create(wardId, request.role, request.nurseId))
  }

  private nurseConflict(nurseId: string, ignoring: string | undefined): Response | null {
    const nurse = this.nurses.find((candidate) => candidate.nurseId === nurseId && candidate.active)
    if (!nurse) return json(404, { code: 'NURSE_NOT_FOUND' })
    if (nurse.linkedAccount) return json(409, { code: 'NURSE_ALREADY_LINKED' })
    const pending = this.invitations.some((record) => record.nurseId === nurseId && record.id !== ignoring && this.statusOf(record) === 'PENDING')
    return pending ? json(409, { code: 'INVITATION_ALREADY_PENDING' }) : null
  }

  private reissue(wardId: string, invitationId: string): Response {
    const record = this.invitations.find((candidate) => candidate.id === invitationId && candidate.wardId === wardId)
    if (!record) return json(404, { code: 'INVITATION_NOT_FOUND' })
    const status = this.statusOf(record)
    if (status === 'REDEEMED') return json(409, { code: 'INVITATION_ALREADY_REDEEMED' })
    if (status === 'REVOKED') return json(410, { code: 'INVITATION_REVOKED' })
    const failure = record.nurseId ? this.nurseConflict(record.nurseId, record.id) : null
    if (failure) return failure
    record.revokedAt = this.now()
    return this.created(this.create(wardId, record.role, record.nurseId))
  }

  private revoke(wardId: string, invitationId: string): Response {
    const record = this.invitations.find((candidate) => candidate.id === invitationId && candidate.wardId === wardId)
    if (!record) return json(404, { code: 'INVITATION_NOT_FOUND' })
    if (record.redeemedAt !== undefined) return json(409, { code: 'INVITATION_ALREADY_REDEEMED' })
    record.revokedAt ??= this.now()
    return new Response(null, { status: 204 })
  }

  private lookup(token: unknown): InvitationRecord | Response {
    if (typeof token !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(token)) return json(400, { code: 'INVALID_REQUEST' })
    const record = this.invitations.find((candidate) => candidate.token === token)
    if (!record) return json(404, { code: 'INVITATION_NOT_FOUND' })
    const status = this.statusOf(record)
    if (status === 'REDEEMED') return json(409, { code: 'INVITATION_ALREADY_REDEEMED' })
    if (status === 'REVOKED') return json(410, { code: 'INVITATION_REVOKED' })
    if (status === 'EXPIRED') return json(410, { code: 'INVITATION_EXPIRED' })
    return record
  }

  private preview(body: unknown): Response {
    const record = this.lookup((body as { token?: unknown }).token)
    if (record instanceof Response) return record
    const info = this.wardInfo.get(record.wardId)!
    return json(200, {
      wardId: record.wardId,
      wardName: info.wardName,
      role: record.role,
      expiresAt: new Date(record.expiresAt).toISOString(),
      linksExistingNurse: Boolean(record.nurseId),
      hospitalName: info.hospitalName,
    })
  }

  private redeem(userId: string, body: unknown): Response {
    const request = body as { token?: unknown; hospitalJoinedOn?: unknown }
    const unknownField = Object.keys(request).some((key) => key !== 'token' && key !== 'hospitalJoinedOn')
    if (unknownField) return json(400, { code: 'INVALID_REQUEST' })
    const record = this.lookup(request.token)
    if (record instanceof Response) return record
    const joinedOn = request.hospitalJoinedOn
    if (joinedOn !== undefined && (typeof joinedOn !== 'string' || !/^\d{4}-(0[1-9]|1[0-2])$/.test(joinedOn))) {
      return json(400, { code: 'INVALID_REQUEST' })
    }
    if (this.isMember(record.wardId, userId)) return json(409, { code: 'ALREADY_WARD_MEMBER' })
    if (record.role === 'WARD_MEMBER') {
      if (joinedOn === undefined) return json(400, { code: 'HOSPITAL_JOIN_MONTH_REQUIRED' })
      const [year, month] = (joinedOn as string).split('-').map(Number) as [number, number]
      const today = new Date(this.now())
      if (year < 1950 || year > today.getFullYear() || (year === today.getFullYear() && month > today.getMonth() + 1)) {
        return json(400, { code: 'INVALID_HOSPITAL_JOIN_MONTH' })
      }
      if (record.nurseId) {
        const nurse = this.nurses.find((candidate) => candidate.nurseId === record.nurseId)
        if (nurse?.linkedAccount) return json(409, { code: 'NURSE_ALREADY_LINKED' })
        if (nurse) nurse.linkedAccount = true
      }
    }
    record.redeemedAt = this.now()
    this.addMember(record.wardId, userId, [record.role])
    const info = this.wardInfo.get(record.wardId)!
    return json(201, { wardId: record.wardId, wardName: info.wardName, hospitalName: info.hospitalName, roles: [record.role], canViewWardCalendar: true })
  }
}
