import { ApiError, asApiError, ClientErrorCode } from '../api/http'
import type { NurseFlowApi } from '../api/nurseflowApi'
import type { CreateWardRequest, HospitalCandidate, HospitalSearchPage, WardMembership } from '../api/types'
import type { SessionManager } from '../session/SessionManager'

export interface CreationState {
  query: string
  searchQuery: string
  searching: boolean
  results: HospitalSearchPage | null
  searchError: string | null
  selected: HospitalCandidate | null
  wardName: string
  busy: boolean
  uncertain: boolean
  error: string | null
  created: WardMembership | null
}

const searchFailure = (code: string) => {
  if (code === 'HOSPITAL_SEARCH_NOT_CONFIGURED') return '서버에 병원 목록이 준비되지 않았어요. 잠시 후 다시 검색해 주세요.'
  if (code === 'HOSPITAL_SEARCH_UNAVAILABLE') return '병원 검색 서비스에 연결할 수 없어요. 잠시 후 다시 검색해 주세요.'
  if (code === 'INVALID_REQUEST') return '병원 이름을 확인해 주세요. 문자·숫자 2자 이상, 전체 2~50자로 검색해요.'
  return '병원을 검색하지 못했어요. 연결을 확인하고 다시 검색해 주세요.'
}

const creationErrors: Record<string, string> = {
  WARD_CREATION_LIMIT_REACHED: '이 계정이 만들 수 있는 병동 수를 모두 사용했어요. 내 병동 목록을 확인해 주세요.',
  WARD_ALREADY_CREATED: '같은 병원과 이름으로 이미 만든 병동이 있어요. 내 병동 목록을 확인해 주세요.',
  IDEMPOTENCY_KEY_REUSED: '요청 키가 다른 요청과 충돌했어요. 목록을 확인한 뒤 새 요청을 시작해 주세요.',
  HOSPITAL_NOT_FOUND: '선택한 병원이 목록에서 제외됐어요. 병원을 다시 검색해 선택해 주세요.',
  HOSPITAL_SEARCH_NOT_CONFIGURED: '서버에 병원 목록이 준비되지 않아 만들 수 없어요. 잠시 후 다시 시도해 주세요.',
  INVALID_REQUEST: '병원 선택과 병동 이름을 확인해 주세요.',
  FORBIDDEN: '병동을 만들 권한이 없어요. 계정 상태를 확인해 주세요.',
}

function isWardMembership(value: unknown): value is WardMembership {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const ward = value as Record<string, unknown>
  return typeof ward.wardId === 'string' && ward.wardId.trim().length > 0 &&
    typeof ward.wardName === 'string' && ward.wardName.trim().length > 0 &&
    Array.isArray(ward.roles) && ward.roles.every((role) => role === 'WARD_ADMIN' || role === 'WARD_MEMBER') &&
    typeof ward.canViewWardCalendar === 'boolean' &&
    (ward.hospitalName === undefined || ward.hospitalName === null || typeof ward.hospitalName === 'string')
}

/** Memory only, scoped to a session epoch. Navigation within the SPA retains the exact retry. */
export class WardCreationFlow {
  private state: CreationState = {
    query: '', searchQuery: '', searching: false, results: null, searchError: null,
    selected: null, wardName: '', busy: false, uncertain: false, error: null, created: null,
  }
  private readonly listeners = new Set<() => void>()
  private searchTicket = 0
  private pending: { key: string; request: CreateWardRequest } | null = null

  constructor(private readonly api: Pick<NurseFlowApi, 'searchHospitals' | 'createWard'>,
    private readonly newKey = () => crypto.randomUUID()) {}

  getSnapshot = () => this.state
  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    return () => { this.listeners.delete(listener) }
  }
  private update(patch: Partial<CreationState>) {
    this.state = { ...this.state, ...patch }
    this.listeners.forEach((listener) => listener())
  }
  private get locked() { return this.state.busy || this.state.uncertain || this.state.created !== null }

  setQuery(query: string) {
    if (this.locked) return
    ++this.searchTicket
    this.pending = null
    this.update({ query, results: null, selected: null, searchError: null, searching: false, error: null })
  }
  select(selected: HospitalCandidate) {
    if (this.locked) return
    this.pending = null
    this.update({ selected, error: null })
  }
  setWardName(wardName: string) {
    if (this.locked) return
    this.pending = null
    this.update({ wardName, error: null })
  }

  async search(page = 1) {
    if (this.locked || page < 1 || page > 100) return
    const query = (page === 1 ? this.state.query : this.state.searchQuery).trim()
    const mine = ++this.searchTicket
    // Java validates UTF-16 length and Unicode letters/numbers after punctuation removal.
    if (query.length < 2 || query.length > 50 || /\p{Cc}/u.test(query) ||
        (query.match(/[\p{L}\p{N}]/gu) ?? []).length < 2) {
      this.update({ searching: false, searchError: searchFailure('INVALID_REQUEST'), results: null, selected: null })
      return
    }
    this.pending = null
    this.update({ searching: true, searchError: null, results: null, selected: null, searchQuery: query, error: null })
    try {
      const results = await this.api.searchHospitals(query, page)
      if (mine === this.searchTicket) this.update({ results, searching: false })
    } catch (error) {
      if (mine !== this.searchTicket) return
      const failure = asApiError(error)
      this.update({ searching: false, searchError: searchFailure(failure.code) })
    }
  }

  async create() {
    if (this.state.busy || this.state.created) return
    if (!this.pending) {
      const { selected, wardName } = this.state
      if (!selected || !wardName.trim() || wardName.trim().length > 100) {
        this.update({ error: '병원을 선택하고 병동 이름을 1~100자로 입력해 주세요.' })
        return
      }
      this.pending = {
        key: this.newKey(),
        request: { hospital: { source: selected.source, externalId: selected.externalId }, wardName: wardName.trim(), timeZone: 'Asia/Seoul' },
      }
    }
    const pending = this.pending
    this.update({ busy: true, error: null })
    try {
      const created = await this.api.createWard(pending.request, pending.key)
      if (!isWardMembership(created)) throw new ApiError(201, ClientErrorCode.INVALID_RESPONSE)
      this.pending = null
      this.update({ created, busy: false, uncertain: false })
    } catch (error) {
      const failure = asApiError(error)
      const uncertain = this.state.uncertain || failure.status === 0 || failure.status >= 500 ||
        failure.code === ClientErrorCode.INVALID_RESPONSE
      this.update({
        busy: false, uncertain,
        error: uncertain
          ? `${creationErrors[failure.code] ? creationErrors[failure.code] + ' ' : ''}생성 결과를 확인하지 못했어요. 이미 만들어졌을 수 있어요. 같은 요청 재시도로 결과를 확인하거나 내 병동 목록을 확인해 주세요.`
          : creationErrors[failure.code] ?? '병동을 만들지 못했어요. 계정과 입력을 확인하고 다시 시도해 주세요.',
      })
    }
  }

  /** User explicitly acknowledges a possibly completed previous request before editing again. */
  startNew() {
    if (this.state.busy) return
    this.pending = null
    this.update({ uncertain: false, error: null, created: null })
  }
}

const flows = new WeakMap<SessionManager, { epoch: number; flow: WardCreationFlow }>()
export function getWardCreationFlow(manager: SessionManager, epoch: number) {
  const existing = flows.get(manager)
  if (existing?.epoch === epoch) return existing.flow
  const flow = new WardCreationFlow(manager.api)
  flows.set(manager, { epoch, flow })
  return flow
}
