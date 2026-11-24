import { asApiError, ApiError, ClientErrorCode } from '../api/http'
import type { ScheduleSettingsApi, Tagged, Roster, Absence, Shift, Preview, ShiftInput } from '../api/scheduleSettingsApi'
import type { StaffAdminApi } from '../api/staffAdminApi'
import type { StaffMember, Team, WardList } from '../api/types'

export interface SettingsData {
  wardName: string
  shifts: Tagged<{ items: Shift[]; policyRevision: number }>
  teams: Team[]
  staff: StaffMember[]
  draft: Tagged<Roster> | null
  absences: Absence[]
}
interface State {
  data: SettingsData | null
  month: string
  loading: boolean
  busy: boolean
  stale: boolean
  forbidden: boolean
  uncertain: boolean
  notice: string | null
  version: number
}
const messages: Record<string, string> = {
  STALE_POLICY_REVISION: '근무 유형이 다른 화면에서 변경됐어요. 최신 값을 확인하고 다시 입력해 주세요.',
  STALE_ROSTER_REVISION: '초안이 다른 화면에서 변경됐어요. 최신 값을 확인하고 다시 입력해 주세요.',
  ASSIGNMENTS_EXIST: '이미 배치된 초안의 수요는 바꿀 수 없어요. 배치 편집 화면에서 먼저 해제해야 해요.',
  DRAFT_EXISTS: '이 달의 초안이 이미 있어요. 다시 조회해 주세요.',
  NOT_DRAFT: '수정 가능한 초안이 아니에요. 최신 초안을 확인해 주세요.',
  SHIFT_TYPE_CODE_DUPLICATE: '비활성 유형을 포함해 같은 근무 코드가 있어요.',
  INVALID_SHIFT_MINUTES: '휴게·인정·환산 시간을 확인해 주세요.',
  INVALID_SHIFT_INTERVAL: '근무 시작·종료 시각을 확인해 주세요.',
  SHIFT_SPANS_MIDNIGHT_MISMATCH: '자정 경과 여부와 시작·종료 시각이 일치하지 않아요.',
  INVALID_ABSENCE: '부재 시작·종료와 구분을 확인해 주세요.',
  ABSENCE_OUTSIDE_ROSTER: '부재 구간이 선택한 초안의 월과 겹쳐야 해요.',
  ABSENCE_ASSIGNMENT_OVERLAP: '기존 배치와 승인 부재가 겹쳐요. 배치 편집에서 먼저 해제해 주세요.',
  ABSENCE_NOT_FOUND: '부재 일정이 변경되거나 삭제됐어요. 최신 목록을 확인해 주세요.',
  NURSE_NOT_FOUND: '간호사 기록을 찾을 수 없어요. 최신 목록을 확인해 주세요.',
  DATE_OUTSIDE_ROSTER: '선택한 초안의 월 안에서 입력해 주세요.',
  TEAM_REQUIRED_FOR_DEMAND: '필요 인원에 팀을 지정해 주세요.',
  SHIFT_TYPE_NOT_FOUND: '비활성화되거나 삭제된 근무 유형이에요. 최신 목록을 확인해 주세요.',
  TEAM_NOT_FOUND: '팀 구성이 변경됐어요. 최신 목록을 확인해 주세요.',
  INVALID_REQUEST: '입력값을 확인해 주세요.',
  INVALID_REQUIREMENTS: '팀·날짜·근무 유형과 필요 인원을 확인해 주세요.',
  DUPLICATE_REQUIREMENT: '같은 팀·날짜·근무 유형이 중복됐어요.',
  TOO_MANY_SLOTS: '전체 필요 인원이 서버 상한을 초과했어요.',
}
const ended = (code: string) => code === ClientErrorCode.ABORTED || code === ClientErrorCode.SESSION_EXPIRED
const forbidden = (status: number, code: string) => status === 401 || status === 403 || code === 'WARD_NOT_FOUND' || code === 'USER_NOT_FOUND'

/** One ward/session instance. All writes are serialized, then server snapshots replace local data. */
export class ScheduleSettingsStore {
  private state: State
  private listeners = new Set<() => void>()
  private ticket = 0
  private disposed = false
  constructor(readonly api: ScheduleSettingsApi, private staffApi: Pick<StaffAdminApi, 'teams' | 'staff'>, private wards: () => Promise<WardList>, readonly wardId: string, month: string) {
    this.state = { data: null, month, loading: true, busy: false, stale: true, forbidden: false, uncertain: false, notice: null, version: 0 }
  }
  getSnapshot = () => this.state
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener) } }
  private patch(patch: Partial<State>) { if (!this.disposed) { this.state = { ...this.state, ...patch }; this.listeners.forEach(f => f()) } }
  activate() { this.disposed = false }
  dispose() { this.disposed = true; ++this.ticket }
  canWrite() { return !this.disposed && !this.state.loading && !this.state.busy && !this.state.stale && !this.state.forbidden && !this.state.uncertain }
  acknowledge() { if (!this.state.loading && !this.state.stale && !this.state.forbidden) this.patch({ uncertain: false, notice: '서버의 최신 결과를 확인했어요.' }) }
  async setMonth(month: string) {
    if (this.state.busy || this.state.uncertain || !/^\d{4}-(0[1-9]|1[0-2])$/.test(month) || month.startsWith('0000')) return
    this.patch({ month, data: null, notice: null })
    await this.reload()
  }
  async reload(fromWrite = false): Promise<boolean> {
    if (this.disposed || (this.state.busy && !fromWrite)) return false
    const mine = ++this.ticket, month = this.state.month
    this.patch({ loading: true, stale: true })
    try {
      const ward = (await this.wards()).items.find(w => w.wardId === this.wardId && w.roles.includes('WARD_ADMIN'))
      if (this.disposed || mine !== this.ticket) return false
      if (!ward) { this.patch({ data: null, loading: false, forbidden: true }); return false }
      const [shifts, teams, staff, rosters] = await Promise.all([this.api.shifts(), this.staffApi.teams(), this.staffApi.staff(), this.api.rosters(month)])
      const summaries = rosters.items.filter(r => r.status === 'DRAFT')
      if (summaries.length > 1 || rosters.items.some(r => r.wardId !== this.wardId || r.month !== month)) throw new ApiError(0, ClientErrorCode.INVALID_RESPONSE)
      let draft: Tagged<Roster> | null = null
      let absences: Absence[] = []
      if (summaries[0]) {
        const before = await this.api.roster(summaries[0].rosterId)
        absences = await this.api.absences(before.value.rosterId)
        draft = await this.api.roster(before.value.rosterId)
        if (before.etag !== draft.etag || draft.value.status !== 'DRAFT' || draft.value.wardId !== this.wardId || draft.value.month !== month) throw new ApiError(412, 'STALE_ROSTER_REVISION')
      }
      if (this.disposed || mine !== this.ticket) return false
      this.patch({ data: { wardName: ward.wardName, shifts, teams, staff, draft, absences }, loading: false, stale: false, forbidden: false, version: this.state.version + 1 })
      return true
    } catch (error) {
      if (this.disposed || mine !== this.ticket) return false
      const failure = asApiError(error)
      if (ended(failure.code)) { this.patch({ data: null, loading: false, stale: true }); return false }
      const denied = forbidden(failure.status, failure.code)
      this.patch({ data: denied ? null : this.state.data, loading: false, stale: true, forbidden: denied, notice: denied ? '병동 관리 권한이 없거나 소속이 변경됐어요.' : '최신 설정을 불러오지 못했어요. 다시 조회해 주세요.' })
      return false
    }
  }
  async run(label: string, operation: () => Promise<unknown>): Promise<boolean> {
    if (!this.canWrite()) return false
    const mine = this.ticket
    this.patch({ busy: true, notice: null })
    let success = false
    let notice: string
    try { await operation(); success = true; notice = `${label} 완료. 서버에서 다시 조회한 값이에요.` }
    catch (error) {
      if (this.disposed || mine !== this.ticket) return false
      const failure = asApiError(error)
      if (ended(failure.code) || forbidden(failure.status, failure.code)) {
        this.patch({ data: null, busy: false, stale: true, forbidden: true, notice: '세션 또는 관리 권한이 변경됐어요. 내 병동에서 다시 확인해 주세요.' }); return false
      }
      const uncertain = failure.status === 0 || failure.status >= 500 || failure.code === ClientErrorCode.INVALID_RESPONSE
      this.patch({ uncertain })
      notice = uncertain ? '저장 결과를 확인하지 못했어요. 이미 처리됐을 수 있어요. 최신 결과를 확인하기 전에는 다시 보내지 마세요.' : messages[failure.code] ?? '서버가 변경을 거절했어요. 최신 값과 입력을 확인해 주세요.'
    }
    if (this.disposed || mine !== this.ticket) return false
    const loaded = await this.reload(true)
    this.patch({ busy: false, notice: this.state.forbidden ? '관리 권한이 변경됐어요. 내 병동에서 다시 확인해 주세요.' : notice + (loaded ? '' : ' 최신 값 재조회도 실패했어요. 다시 조회해 주세요.') })
    return success && loaded
  }
  async preview(id: string, input: ShiftInput): Promise<Preview | null> {
    if (!this.canWrite()) return null
    const mine = this.ticket
    this.patch({ busy: true })
    try {
      const result = await this.api.preview(id, input)
      if (this.disposed || mine !== this.ticket) return null
      if (result.policyRevision !== this.state.data?.shifts.value.policyRevision) throw new ApiError(412, 'STALE_POLICY_REVISION')
      this.patch({ busy: false }); return result
    } catch (error) {
      if (this.disposed || mine !== this.ticket) return null
      const failure = asApiError(error)
      if (ended(failure.code) || forbidden(failure.status, failure.code)) { this.patch({ data: null, busy: false, stale: true, forbidden: true }); return null }
      await this.reload(true)
      this.patch({ busy: false, notice: messages[failure.code] ?? '변경 영향을 불러오지 못했어요. 입력을 확인하고 다시 시도해 주세요.' })
      return null
    }
  }
  readDenied(error: unknown) {
    const e = asApiError(error)
    if (forbidden(e.status, e.code) || ended(e.code)) { ++this.ticket; this.patch({ data: null, stale: true, busy: false, loading: false, forbidden: true }) }
  }
}
