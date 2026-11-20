import { asApiError, ClientErrorCode } from '../api/http'
import type { StaffAdminApi } from '../api/staffAdminApi'
import type { CareerPolicy, RosterEligibility, StaffMember, Team, WardList, WardMember } from '../api/types'

export interface StaffData {
  wardName: string
  teams: Team[]
  staff: StaffMember[]
  members: WardMember[]
  policy: CareerPolicy
  eligibility: RosterEligibility
}
interface State {
  data: StaffData | null
  loading: boolean
  busy: boolean
  forbidden: boolean
  stale: boolean
  uncertain: boolean
  notice: string | null
  noticeTone: 'info' | 'error'
  version: number
}
const errors: Record<string, string> = {
  TEAM_NAME_DUPLICATE: '같은 이름의 팀이 있어요. 다른 이름을 입력해 주세요.',
  TEAM_NOT_EMPTY: '활성 간호사가 있는 팀은 삭제할 수 없어요. 먼저 팀을 옮겨 주세요.',
  TEAM_ORDER_STALE: '다른 화면에서 팀 구성이 바뀌었어요. 최신 목록을 확인하고 다시 정렬해 주세요.',
  LAST_WARD_ADMIN: '마지막 활성 관리자는 해제하거나 비활성화할 수 없어요. 다른 관리자를 먼저 지정해 주세요.',
  MEMBER_ROLE_REQUIRED: '관리자 전용 계정은 관리자 역할만 해제할 수 없어요. 접근을 막으려면 비활성화를 사용해 주세요.',
  ADMIN_NURSE_RECORD_CONFLICT: '관리자와 활성 간호사 기록을 함께 사용할 수 없어요. 관리자 지정 전 간호사 기록을 먼저 비활성화하거나, 기록 활성화 전 관리자 역할을 해제해 주세요.',
  MEMBER_INACTIVE: '구성원 접근을 먼저 재활성화한 뒤 간호사 기록을 활성화해 주세요.',
  INVALID_CAREER_START: '실제 경력 시작 연월을 확인해 주세요. 미래 연월은 사용할 수 없어요.',
  RANK_NOT_IN_POLICY: '현재 병동 직급 목록에서 다시 선택해 주세요.',
  PROFILE_NOT_SUBMITTED: '확인할 실제 경력 정보가 없어요. 먼저 실제 경력 정보를 입력해 주세요.',
  INVALID_REQUEST: '입력값을 확인해 주세요.',
  TEAM_NOT_FOUND: '팀이 삭제되었거나 변경됐어요. 최신 목록을 확인해 주세요.',
  NURSE_NOT_FOUND: '간호사 기록을 찾을 수 없어요. 최신 목록을 확인해 주세요.',
  MEMBER_NOT_FOUND: '구성원을 찾을 수 없어요. 최신 목록을 확인해 주세요.',
}

/** Session/ward-scoped. UI consumes server snapshots; writes are serialized and never auto-retried. */
export class StaffAdminStore {
  private state: State = { data: null, loading: true, busy: false, forbidden: false, stale: true, uncertain: false, notice: null, noticeTone: 'info', version: 0 }
  private listeners = new Set<() => void>()
  private ticket = 0
  private disposed = false
  constructor(readonly api: StaffAdminApi, private wards: () => Promise<WardList>, readonly wardId: string) {}
  getSnapshot = () => this.state
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener) } }
  private patch(patch: Partial<State>) {
    if (this.disposed) return
    this.state = { ...this.state, ...patch }
    this.listeners.forEach((listener) => listener())
  }
  activate() { this.disposed = false }
  dispose() { this.disposed = true; ++this.ticket }
  acknowledge() { if (!this.state.stale && !this.state.loading && !this.state.busy) this.patch({ uncertain: false, notice: '결과를 확인했어요. 필요한 작업을 계속할 수 있어요.', noticeTone: 'info' }) }
  async reload(fromWrite = false): Promise<boolean> {
    if (this.disposed || (this.state.busy && !fromWrite)) return false
    const mine = ++this.ticket
    this.patch({ loading: true, stale: true })
    try {
      const wards = await this.wards()
      if (this.disposed || mine !== this.ticket) return false
      const ward = wards.items.find((item) => item.wardId === this.wardId && item.roles.includes('WARD_ADMIN'))
      if (!ward) { this.patch({ forbidden: true, data: null, loading: false }); return false }
      const [teams, staff, members, policy, eligibility] = await Promise.all([this.api.teams(), this.api.staff(), this.api.members(), this.api.policy(), this.api.eligibility()])
      if (this.disposed || mine !== this.ticket) return false
      this.patch({ data: { wardName: ward.wardName, teams, staff, members, policy, eligibility }, loading: false, stale: false, forbidden: false, version: this.state.version + 1,
        ...(!fromWrite && !this.state.uncertain ? { notice: null, noticeTone: 'info' as const } : {}),
      })
      return true
    } catch (error) {
      if (this.disposed || mine !== this.ticket) return false
      const failure = asApiError(error)
      if (failure.code === ClientErrorCode.ABORTED || failure.code === ClientErrorCode.SESSION_EXPIRED) return false
      const forbidden = failure.status === 403 || failure.code === 'WARD_NOT_FOUND'
      this.patch({ loading: false, stale: true, forbidden, noticeTone: 'error', ...(forbidden ? { data: null } : {}), notice: forbidden ? '병동 관리 권한이 없거나 소속이 변경됐어요.' : '최신 목록을 불러오지 못했어요. 다시 불러온 뒤 작업해 주세요.' })
      return false
    }
  }
  async run(label: string, operation: () => Promise<unknown>): Promise<boolean> {
    if (this.disposed || this.state.busy || this.state.loading || this.state.stale || this.state.forbidden || this.state.uncertain) return false
    this.patch({ busy: true, notice: null, noticeTone: 'info' })
    let success = false
    let message: string
    try {
      await operation()
      success = true
      message = `${label} 완료. 최신 목록을 확인해 주세요.`
    } catch (error) {
      if (this.disposed) return false
      const failure = asApiError(error)
      if (failure.code === ClientErrorCode.ABORTED || failure.code === ClientErrorCode.SESSION_EXPIRED) return false
      const uncertain = failure.status === 0 || failure.status >= 500 || failure.code === ClientErrorCode.INVALID_RESPONSE
      this.patch({ uncertain })
      message = uncertain ? `${label} 결과를 확인하지 못했어요. 이미 처리됐을 수 있어요. 최신 목록을 확인하기 전에는 다시 보내지 마세요.` : errors[failure.code] ?? '요청을 처리하지 못했어요. 최신 상태와 권한을 확인해 주세요.'
    }
    if (this.disposed) return false
    const loaded = await this.reload(true)
    this.patch({ busy: false, noticeTone: success && loaded ? 'info' : 'error', notice: this.state.forbidden ? '병동 관리 권한이 없거나 소속이 변경됐어요. 내 병동 목록에서 확인해 주세요.' : message + (loaded ? '' : ' 최신 목록도 불러오지 못했어요. 다시 불러와 주세요.') })
    return success
  }
}
