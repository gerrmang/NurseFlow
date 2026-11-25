import { ApiError, asApiError } from '../api/http'
import type { NurseFlowApi } from '../api/nurseflowApi'
import { invalid, validDate, type Decision, type OffEligibility, type OffPolicy, type OffPolicyInput, type OffRequest, type OffRequestApi, type OffRequestList } from '../api/offRequestApi'
import type { WardMembership } from '../api/types'
import { validMonth } from '../roster/PublishedRosterStore'

export interface OffData { ward: WardMembership; list: OffRequestList; policy: OffPolicy | null; eligibility: OffEligibility | null }
interface State {
  month: string; data: OffData | null; loading: boolean; busy: boolean; uncertain: boolean; stale: boolean
  notice: string | null; error: boolean; version: number
}
const messages: Record<string, string> = {
  OFF_REQUEST_DUPLICATE: '같은 날짜에 신청되거나 확정된 요청이 있어요.',
  OFF_REQUEST_LIMIT_REACHED: '월 신청 한도에 도달했어요. 관리자에게 문의해 주세요.',
  OFF_REQUEST_STATE_CONFLICT: '다른 화면에서 요청 상태가 바뀌었어요. 최신 상태를 확인해 주세요.',
  OFF_REQUEST_DEADLINE_PASSED: '마감 후에는 직접 철회할 수 없어요. 관리자에게 요청해 주세요.',
  STALE_POLICY_REVISION: '정책이 변경됐어요. 최신 값을 확인하고 다시 입력해 주세요.',
  NURSE_IDENTITY_REQUIRED: '연결된 활성 간호사 기록이 없어 이용할 수 없어요. 관리자에게 확인해 주세요.',
  WARD_NOT_FOUND: '소속 병동을 확인할 수 없어요. 내 병동 목록을 확인해 주세요.',
  OFF_REQUEST_NOT_FOUND: '요청을 찾을 수 없어요. 최신 목록을 확인해 주세요.',
  NURSE_NOT_FOUND: '선택한 간호사는 현재 편성 대상이 아니에요. 최신 목록을 확인해 주세요.',
  INVALID_REQUEST: '신청 날짜와 입력값을 확인해 주세요. 지난 날짜의 본인 신청은 할 수 없어요.',
}
const accessError = (e: ApiError) => e.status === 401 || e.status === 403 || ['WARD_NOT_FOUND', 'USER_NOT_FOUND', 'SESSION_EXPIRED', 'ABORTED'].includes(e.code)
export const isAdmin = (data: OffData | null) => data?.ward.roles.includes('WARD_ADMIN') === true
export const openRequest = (item: OffRequest) => item.status === 'SUBMITTED' || item.status === 'CONFIRMED'
export const ownCount = (items: OffRequest[]) => items.filter(item => item.source === 'NURSE' && openRequest(item)).length
export const deadlinePassed = (list: OffRequestList, now = Date.now()) => now >= Date.parse(list.deadline)

/** Session/ward scoped. No durable cache, automatic mutation retry, or client authority over hard rules. */
export class OffRequestStore {
  private state: State
  private ticket = 0
  private disposed = false
  private listeners = new Set<() => void>()
  constructor(readonly wardId: string, private api: OffRequestApi, private memberships: Pick<NurseFlowApi, 'getWards'>, now = new Date()) {
    this.state = { month: `${now.getFullYear().toString().padStart(4, '0')}-${String(now.getMonth() + 1).padStart(2, '0')}`, data: null, loading: true, busy: false, uncertain: false, stale: true, notice: null, error: false, version: 0 }
  }
  getSnapshot = () => this.state
  subscribe = (fn: () => void) => { this.listeners.add(fn); return () => { this.listeners.delete(fn) } }
  private patch(patch: Partial<State>) { if (!this.disposed) { this.state = { ...this.state, ...patch }; this.listeners.forEach(fn => fn()) } }
  activate() { this.disposed = false }
  dispose() {
    this.disposed = true; ++this.ticket
    this.state = { ...this.state, data: null, busy: false, loading: true, stale: true, notice: null, uncertain: this.state.uncertain || this.state.busy }
  }
  private current(ticket: number) { return !this.disposed && ticket === this.ticket }
  acknowledge() { if (!this.state.stale && !this.state.loading && !this.state.busy) this.patch({ uncertain: false, notice: '결과를 확인했어요. 필요한 작업을 계속해 주세요.', error: false }) }
  selectMonth(month: string) {
    if (!validMonth(month) || this.state.busy || this.state.uncertain) return
    this.patch({ month }); void this.reload()
  }
  async reload(fromWrite = false): Promise<boolean> {
    if (this.disposed || (this.state.busy && !fromWrite)) return false
    const ticket = ++this.ticket
    const month = this.state.month
    this.patch({ loading: true, stale: true, data: null, ...(!fromWrite ? { notice: null, error: false } : {}) })
    try {
      const { items } = await this.memberships.getWards()
      if (!this.current(ticket)) return false
      const ward = items.find(item => item.wardId === this.wardId)
      if (!ward || !ward.roles.some(role => role === 'WARD_ADMIN' || role === 'WARD_MEMBER')) throw new ApiError(404, 'WARD_NOT_FOUND')
      const admin = ward.roles.includes('WARD_ADMIN')
      const [list, policy, eligibility] = await Promise.all([this.api.list(month), admin ? this.api.policy() : null, admin ? this.api.eligibility() : null])
      if (!this.current(ticket)) return false
      if (policy && policy.value.monthlyLimit !== list.monthlyLimit) throw invalid()
      this.patch({ data: { ward, list, policy, eligibility }, loading: false, stale: false, version: this.state.version + 1 })
      return true
    } catch (error) {
      if (!this.current(ticket)) return false
      const e = asApiError(error)
      this.patch({ loading: false, stale: true, data: null, error: true, notice: messages[e.code] ?? (accessError(e) ? '접근 권한이나 로그인 상태가 변경됐어요. 내 병동 목록을 확인해 주세요.' : '최신 목록을 불러오지 못했어요. 다시 불러와 주세요.') })
      return false
    }
  }
  submit(date: string, nurseId?: string) {
    const data = this.state.data
    if (!data || !validDate(date) || !date.startsWith(`${this.state.month}-`)) return Promise.resolve(false)
    const admin = isAdmin(data)
    if (admin ? !data.eligibility?.items.some(item => item.nurseId === nurseId && item.eligible) : nurseId !== undefined || ownCount(data.list.items) >= data.list.monthlyLimit) return Promise.resolve(false)
    return this.run('신청', () => this.api.submit(date, admin ? nurseId : undefined))
  }
  decide(requestId: string, action: Decision) {
    const data = this.state.data, item = data?.list.items.find(item => item.requestId === requestId)
    if (!data || !item || !openRequest(item)) return Promise.resolve(false)
    if (isAdmin(data) ? action !== 'withdraw' && item.status !== 'SUBMITTED' : action !== 'withdraw' || item.status !== 'SUBMITTED' || deadlinePassed(data.list)) return Promise.resolve(false)
    return this.run({ confirm: '확정', reject: '반려', withdraw: '철회' }[action], () => this.api.decide(requestId, action))
  }
  savePolicy(input: OffPolicyInput) {
    const data = this.state.data
    if (!isAdmin(data) || !data?.policy || !Number.isInteger(input.monthlyLimit) || input.monthlyLimit < 0 || input.monthlyLimit > 31 || !Number.isInteger(input.deadlineDay) || input.deadlineDay < 1 || input.deadlineDay > 28) return Promise.resolve(false)
    const etag = data.policy.etag
    return this.run('정책 저장', () => this.api.savePolicy(input, etag))
  }
  private async run(label: string, operation: () => Promise<unknown>): Promise<boolean> {
    const data = this.state.data
    if (this.disposed || !data || this.state.busy || this.state.loading || this.state.stale || this.state.uncertain) return false
    const ticket = ++this.ticket, admin = isAdmin(data)
    this.patch({ busy: true, notice: null, error: false })
    let sent = false, success = false, notice = `${label} 완료. 최신 목록을 확인해 주세요.`
    try {
      // Recheck before sending: a role change must not turn an admin's proxy entry into an own entry.
      const { items } = await this.memberships.getWards()
      if (!this.current(ticket)) return false
      const ward = items.find(item => item.wardId === this.wardId)
      if (!ward || ward.roles.includes('WARD_ADMIN') !== admin || !ward.roles.some(role => role === 'WARD_MEMBER' || role === 'WARD_ADMIN')) throw new ApiError(403, 'FORBIDDEN')
      sent = true
      await operation()
      if (!this.current(ticket)) return false
      success = true
    } catch (error) {
      if (!this.current(ticket)) return false
      const e = asApiError(error)
      if (accessError(e)) {
        this.patch({ busy: false, stale: true, data: null, error: true, notice: '접근 권한이나 로그인 상태가 변경됐어요. 내 병동 목록을 확인해 주세요.' })
        return false
      }
      const uncertain = sent && (e.status === 0 || e.status >= 500 || e.code === 'INVALID_RESPONSE')
      this.patch({ uncertain })
      notice = uncertain ? `${label} 결과가 불명확해요. 이미 처리됐을 수 있으니 다시 보내지 말고 최신 목록에서 결과를 확인해 주세요.` : messages[e.code] ?? '요청을 처리하지 못했어요. 최신 상태를 확인해 주세요.'
    }
    if (!this.current(ticket)) return false
    const refresh = this.reload(true)
    const refreshTicket = this.ticket
    const loaded = await refresh
    if (!this.current(refreshTicket)) return false
    this.patch({ busy: false, notice: loaded ? notice : `${notice} 최신 목록도 불러오지 못했어요. 다시 불러와 주세요.`, error: !success || !loaded })
    return success && loaded
  }
}
