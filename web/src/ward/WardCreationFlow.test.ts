import { ApiError } from '../api/http'
import type { HospitalCandidate, HospitalSearchPage, WardMembership } from '../api/types'
import { WardCreationFlow } from './WardCreationFlow'

const hospital: HospitalCandidate = { source: 'HIRA', externalId: 'fictional-hospital', name: '가상 병원', address: '가상 주소', category: null }
const page: HospitalSearchPage = { items: [hospital], page: 1, size: 10, totalCount: 1, attribution: '가상 출처' }
const ward: WardMembership = { wardId: 'fictional-ward', wardName: '가상 병동', hospitalName: hospital.name, roles: ['WARD_ADMIN'], canViewWardCalendar: true }
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}
function setup() {
  const api = { searchHospitals: vi.fn().mockResolvedValue(page), createWard: vi.fn().mockResolvedValue(ward) }
  const key = vi.fn().mockReturnValueOnce('key-1').mockReturnValueOnce('key-2')
  const flow = new WardCreationFlow(api, key)
  flow.setQuery('가상 병원')
  flow.select(hospital)
  flow.setWardName('  가상 병동  ')
  return { api, key, flow }
}

describe('hospital search', () => {
  it.each(['가', '(가)', ' '.repeat(3), '가'.repeat(51), '가\u0000나'])('rejects invalid query %j without a request', async (query) => {
    const { flow, api } = setup()
    flow.setQuery(query)
    await flow.search()
    expect(api.searchHospitals).not.toHaveBeenCalled()
    expect(flow.getSnapshot().searchError).toContain('2~50자')
  })

  it('searches only on submission, trims and retains the submitted query for paging', async () => {
    const { flow, api } = setup()
    flow.setQuery(' 가상 병원 ')
    expect(api.searchHospitals).not.toHaveBeenCalled()
    await flow.search()
    await flow.search(100)
    await flow.search(101)
    expect(api.searchHospitals.mock.calls).toEqual([['가상 병원', 1], ['가상 병원', 100]])
    expect(flow.getSnapshot().selected).toBeNull()
  })

  it('discards older results and results received after editing the query', async () => {
    const { flow, api } = setup()
    const old = deferred<HospitalSearchPage>()
    api.searchHospitals.mockReturnValueOnce(old.promise)
    const first = flow.search()
    flow.setQuery('새 병원')
    await flow.search()
    old.resolve({ ...page, attribution: '오래된 결과' })
    await first
    expect(flow.getSnapshot().results?.attribution).toBe('가상 출처')
    const late = deferred<HospitalSearchPage>()
    api.searchHospitals.mockReturnValueOnce(late.promise)
    const pending = flow.search()
    flow.setQuery('다른 병원')
    late.resolve(page)
    await pending
    expect(flow.getSnapshot().results).toBeNull()
  })

  it.each([
    ['HOSPITAL_SEARCH_NOT_CONFIGURED', '목록이 준비되지'],
    ['HOSPITAL_SEARCH_UNAVAILABLE', '연결할 수 없어요'],
  ])('distinguishes %s from empty results and recovers on retry', async (code, message) => {
    const { flow, api } = setup()
    api.searchHospitals.mockRejectedValueOnce(new ApiError(503, code))
    await flow.search()
    expect(flow.getSnapshot().searchError).toContain(message)
    expect(flow.getSnapshot().results).toBeNull()
    api.searchHospitals.mockResolvedValueOnce({ ...page, items: [], totalCount: 0 })
    await flow.search()
    expect(flow.getSnapshot().searchError).toBeNull()
    expect(flow.getSnapshot().results?.items).toEqual([])
  })
})

describe('ward creation', () => {
  it.each([undefined, null])('accepts an optional hospital name %j', async (hospitalName) => {
    const { flow, api } = setup()
    const response = { ...ward, hospitalName }
    api.createWard.mockResolvedValueOnce(response)
    await flow.create()
    expect(flow.getSnapshot()).toMatchObject({ created: response, busy: false, uncertain: false, error: null })
  })

  it.each([
    null, {}, [], 'invalid',
    { ...ward, wardId: undefined }, { ...ward, wardId: ' ' },
    { ...ward, wardName: null }, { ...ward, wardName: '' },
    { ...ward, roles: undefined }, { ...ward, roles: 'WARD_ADMIN' },
    { ...ward, roles: ['UNKNOWN'] },
    { ...ward, canViewWardCalendar: undefined }, { ...ward, canViewWardCalendar: 'true' },
    { ...ward, hospitalName: 123 },
  ].map((response) => [response]))('retains the exact retry and reports an uncertain result for malformed success %j', async (response) => {
    const { flow, api, key } = setup()
    api.createWard.mockResolvedValueOnce(response)
    await flow.create()
    expect(flow.getSnapshot()).toMatchObject({ created: null, busy: false, uncertain: true })
    expect(flow.getSnapshot().error).toContain('생성 결과를 확인하지 못했어요')
    flow.setWardName('바뀌면 안 되는 이름')
    await flow.create()
    expect(api.createWard.mock.calls[1]).toEqual(api.createWard.mock.calls[0])
    expect(key).toHaveBeenCalledTimes(1)
    expect(flow.getSnapshot()).toMatchObject({ created: ward, busy: false, uncertain: false })
  })

  it('coalesces double clicks and submits only the hospital identity and trimmed name', async () => {
    const { flow, api } = setup()
    const response = deferred<WardMembership>()
    api.createWard.mockReturnValueOnce(response.promise)
    const first = flow.create()
    await flow.create()
    flow.setWardName('다른 이름')
    expect(api.createWard.mock.calls).toEqual([[{ hospital: { source: 'HIRA', externalId: hospital.externalId }, wardName: '가상 병동', timeZone: 'Asia/Seoul' }, 'key-1']])
    response.resolve(ward)
    await first
    expect(flow.getSnapshot().created).toEqual(ward)
    await flow.create()
    expect(api.createWard).toHaveBeenCalledTimes(1)
  })

  it.each([new ApiError(0, 'NETWORK'), new ApiError(500, 'HTTP_500'), new ApiError(201, 'INVALID_RESPONSE')])('retains exact payload and key after lost result %s', async (error) => {
    const { flow, api, key } = setup()
    api.createWard.mockRejectedValueOnce(error).mockRejectedValueOnce(new ApiError(409, 'WARD_ALREADY_CREATED'))
    await flow.create()
    expect(flow.getSnapshot().uncertain).toBe(true)
    flow.setWardName('다른 이름')
    flow.setQuery('다른 병원')
    flow.select({ ...hospital, externalId: 'other' })
    await flow.create()
    expect(flow.getSnapshot().uncertain).toBe(true)
    await flow.create()
    expect(api.createWard.mock.calls[1]).toEqual(api.createWard.mock.calls[0])
    expect(api.createWard.mock.calls[2]).toEqual(api.createWard.mock.calls[0])
    expect(key).toHaveBeenCalledTimes(1)
    expect(flow.getSnapshot().created).toEqual(ward)
  })

  it('uses a new key only after acknowledging abandonment of the uncertain request', async () => {
    const { flow, api } = setup()
    api.createWard.mockRejectedValueOnce(new ApiError(0, 'NETWORK'))
    await flow.create()
    flow.startNew()
    flow.setWardName('새 병동')
    await flow.create()
    expect(api.createWard.mock.calls[1]).toEqual([{ hospital: { source: 'HIRA', externalId: hospital.externalId }, wardName: '새 병동', timeZone: 'Asia/Seoul' }, 'key-2'])
  })

  it.each([
    [409, 'WARD_CREATION_LIMIT_REACHED', '병동 수를 모두'],
    [409, 'WARD_ALREADY_CREATED', '이미 만든 병동'],
    [409, 'IDEMPOTENCY_KEY_REUSED', '키가 다른 요청과 충돌'],
    [422, 'HOSPITAL_NOT_FOUND', '목록에서 제외'],
    [503, 'HOSPITAL_SEARCH_NOT_CONFIGURED', '목록이 준비되지'],
  ])('shows actionable creation error %s %s', async (status, code, message) => {
    const { flow, api } = setup()
    api.createWard.mockRejectedValueOnce(new ApiError(status, code))
    await flow.create()
    expect(flow.getSnapshot().error).toContain(message)
    expect(flow.getSnapshot().created).toBeNull()
  })

  it('enforces the name boundary without truncating the user input', async () => {
    const { flow, api } = setup()
    flow.setWardName('가'.repeat(101))
    await flow.create()
    expect(api.createWard).not.toHaveBeenCalled()
    expect(flow.getSnapshot().wardName).toHaveLength(101)
    flow.setWardName('가'.repeat(100))
    await flow.create()
    expect(api.createWard).toHaveBeenCalledTimes(1)
  })
})
