import { ApiError, asApiError, ClientErrorCode } from '../api/http'
import { policyInput, type HolidaySettingsApi, type HolidayCalendar, type HolidayDay, type TargetPolicy } from '../api/holidaySettingsApi'
import type { WardList } from '../api/types'

interface State {
  month: string; wardName: string; timeZone: string; calendar: HolidayCalendar | null; etag: string
  days: HolidayDay[]; policy: TargetPolicy | null; calendarDirty: boolean; policyDirty: boolean
  loading: boolean; busy: boolean; stale: boolean; forbidden: boolean; uncertain: boolean; notice: string | null
}
const denied = (e: ApiError) => [401, 403].includes(e.status) || ['WARD_NOT_FOUND', 'USER_NOT_FOUND', ClientErrorCode.SESSION_EXPIRED, ClientErrorCode.ABORTED].includes(e.code)
const messages: Record<string, string> = {
  INVALID_TARGET_HOUR_POLICY: '시간 입력 범위를 확인해 주세요.', INVALID_HOLIDAY_CALENDAR: '날짜·종류·이름을 확인해 주세요.',
  HOLIDAY_SOURCE_NOT_CONFIGURED: '자동 공휴일이 꺼져 있어 직접 입력이 필요해요.', HOLIDAY_SOURCE_UNAVAILABLE: '가져오기에 실패했어요. 기존 달력과 입력을 유지했어요.',
  HOLIDAY_MONTH_OUT_OF_RANGE: '공휴일 가져오기는 작년부터 내년까지만 가능해요.',
}
export class HolidaySettingsStore {
  private state: State
  private listeners = new Set<() => void>()
  private ticket = 0
  private disposed = false
  constructor(readonly api: HolidaySettingsApi, private wards: () => Promise<WardList>, readonly wardId: string, month: string) {
    this.state = { month, wardName: '', timeZone: 'UTC', calendar: null, etag: '', days: [], policy: null, calendarDirty: false, policyDirty: false, loading: true, busy: false, stale: true, forbidden: false, uncertain: false, notice: null }
  }
  getSnapshot = () => this.state
  subscribe = (f: () => void) => { this.listeners.add(f); return () => { this.listeners.delete(f) } }
  private patch(p: Partial<State>) { if (!this.disposed) { this.state = { ...this.state, ...p }; this.listeners.forEach(f => f()) } }
  activate() { this.disposed = false }
  dispose() { this.disposed = true; ++this.ticket }
  editable() { const s = this.state; return !this.disposed && !!s.calendar && !s.loading && !s.busy && !s.stale && !s.forbidden && !s.uncertain }
  editDay(day: HolidayDay, remove = false) { if (this.editable()) this.patch({ days: [...this.state.days.filter(d => d.date !== day.date), ...(remove ? [] : [day])].sort((a, b) => a.date.localeCompare(b.date)), calendarDirty: true }) }
  editPolicy(policy: TargetPolicy) { if (this.editable()) this.patch({ policy, policyDirty: true }) }
  private fail(error: unknown, write: boolean, refresh = false) {
    const e = asApiError(error)
    if (denied(e)) { this.patch({ calendar: null, policy: null, days: [], etag: '', forbidden: true, stale: true, notice: '관리 권한 또는 세션이 변경됐어요. 내 병동에서 다시 확인해 주세요.' }); return }
    const conflict = [409, 412].includes(e.status)
    const uncertain = write && !refresh && (e.status === 0 || e.status >= 500 || e.code === ClientErrorCode.INVALID_RESPONSE)
    this.patch({ stale: this.state.stale || conflict || uncertain, uncertain: this.state.uncertain || uncertain, notice: conflict ? '다른 화면에서 정책이 변경됐어요. 입력을 보관했어요. 최신 설정을 다시 조회한 뒤 확인해 주세요.' : uncertain ? '저장 결과가 불확실해요. 다시 보내지 말고 최신 설정을 조회해 결과를 확인해 주세요.' : messages[e.code] ?? (write ? '요청을 처리하지 못했어요. 입력을 유지했어요.' : '최신 설정을 불러오지 못했어요. 다시 조회해 주세요.') })
  }
  async reload(month = this.state.month, reset: 'all' | 'calendar' | 'policy' = 'all', fromWrite = false, expectedTag?: string) {
    if (this.disposed || this.state.busy && !fromWrite || !/^\d{4}-(0[1-9]|1[0-2])$/.test(month) || month.startsWith('0000')) return false
    const mine = ++this.ticket
    this.patch({ loading: true, stale: true })
    try {
      const ward = (await this.wards()).items.find(w => w.wardId === this.wardId && w.roles.includes('WARD_ADMIN'))
      if (!ward) throw new ApiError(403, 'FORBIDDEN')
      const calendar = await this.api.calendar(month), policy = await this.api.policy()
      if (calendar.etag !== policy.etag || calendar.value.policyRevision !== policy.value.policyRevision || expectedTag && calendar.etag !== expectedTag) throw new ApiError(412, 'STALE_POLICY_REVISION')
      if (this.disposed || mine !== this.ticket) return false
      this.patch({ month, wardName: ward.wardName, calendar: calendar.value, etag: calendar.etag, loading: false, stale: false, forbidden: false,
        ...(reset !== 'policy' ? { days: calendar.value.days, calendarDirty: false } : {}),
        ...(reset !== 'calendar' ? { policy: policyInput(policy.value), policyDirty: false } : {}) })
      return true
    } catch (e) { if (!this.disposed && mine === this.ticket) { this.fail(e, false); this.patch({ loading: false }) } return false }
  }
  acknowledge() { if (!this.state.loading && !this.state.busy && !this.state.stale && !this.state.forbidden) this.patch({ uncertain: false, notice: '최신 저장 결과를 확인했어요.' }) }
  async save(kind: 'calendar' | 'policy' | 'refresh') {
    if (!this.editable()) return false
    const mine = this.ticket, s = this.state
    this.patch({ busy: true, notice: null })
    try {
      if (kind === 'refresh') {
        const calendar = await this.api.refresh(s.month)
        if (this.disposed || mine !== this.ticket) return false
        // Refresh never saves local ward edits. A changed revision needs a full explicit reload.
        if (calendar.etag !== s.etag) throw new ApiError(412, 'STALE_POLICY_REVISION')
        this.patch({ calendar: calendar.value, notice: '국가 공휴일을 갱신했어요. 미저장 입력은 유지했어요.' })
      } else {
        const saved = kind === 'calendar' ? await this.api.saveCalendar(s.month, s.days, s.etag) : await this.api.savePolicy(s.policy!, s.etag)
        if (this.disposed || mine !== this.ticket) return false
        const loaded = await this.reload(s.month, kind, true, saved.etag)
        this.patch({ notice: loaded ? '저장 후 서버에서 다시 조회했어요.' : this.state.notice })
        return loaded
      }
      return true
    } catch (e) { if (!this.disposed && mine === this.ticket) this.fail(e, true, kind === 'refresh'); return false }
    finally { this.patch({ busy: false }) }
  }
}
