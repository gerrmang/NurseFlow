import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { WardMembership } from '../api/types'
import { ApiError } from '../api/http'
import { json, USER_A, USER_B } from '../test/fakeAuthServer'
import { setupApp } from '../test/harness'

const hospital = { source: 'HIRA', externalId: 'fictional-a', name: '가상 병원', address: '가상 주소 가', category: null }
const created: WardMembership = { wardId: 'fictional-ward', wardName: '검증 병동', hospitalName: '가상 병원', roles: ['WARD_ADMIN'], canViewWardCalendar: true }

function open() {
  const app = setupApp('/wards/new', { configure: ({ server }) => server.signInAs(USER_A) })
  const base = app.backend.fetch
  const create = vi.fn().mockResolvedValue(json(201, created))
  const search = vi.fn().mockResolvedValue(json(200, {
    items: [hospital, { ...hospital, externalId: 'fictional-b', address: '가상 주소 나' }],
    page: 1, size: 10, totalCount: 2, attribution: '가상 공식 목록 출처',
  }))
  vi.spyOn(app.backend, 'fetch').mockImplementation(async (url, init) => {
    if (url.includes('/hospitals/search?')) return search(url, init)
    if (url === '/api/v1/wards' && init?.method === 'POST') return create(url, init)
    return base(url, init)
  })
  return { ...app, create, search, user: userEvent.setup() }
}

async function fill(app: ReturnType<typeof open>) {
  await app.user.type(await screen.findByLabelText('병원 이름'), '가상 병원')
  expect(app.search).not.toHaveBeenCalled()
  await app.user.click(screen.getByRole('button', { name: '검색' }))
  await app.user.click((await screen.findAllByRole('radio'))[1]!)
  await app.user.type(screen.getByLabelText('병동 이름'), '검증 병동')
}

function unloadIsBlocked() {
  const event = new Event('beforeunload', { cancelable: true })
  window.dispatchEvent(event)
  return event.defaultPrevented
}

it('tracks a pending creation and its late success after navigating to the ward list', async () => {
  const app = open()
  let complete!: (response: Response) => void
  app.create.mockReturnValueOnce(new Promise<Response>((resolve) => { complete = resolve }))
  await fill(app)
  await app.user.click(screen.getByRole('button', { name: '병동 만들기' }))
  await screen.findByRole('button', { name: '생성 결과 확인 중…' })
  expect(unloadIsBlocked()).toBe(true)
  await app.user.click(screen.getByRole('link', { name: '내 병동 목록 확인' }))
  await screen.findByRole('heading', { name: '내 병동' })
  expect(unloadIsBlocked()).toBe(true)
  await act(async () => { complete(json(201, created)) })
  await waitFor(() => expect(unloadIsBlocked()).toBe(false))
})

it.each(['logout', 'unmount'])('removes the pending warning on %s', async (boundary) => {
  const app = open()
  app.create.mockRejectedValueOnce(new ApiError(0, 'NETWORK'))
  await fill(app)
  await app.user.click(screen.getByRole('button', { name: '병동 만들기' }))
  await screen.findByRole('button', { name: '같은 요청 재시도' })
  expect(unloadIsBlocked()).toBe(true)
  if (boundary === 'logout') await act(async () => { await app.manager.logout() })
  else app.unmount()
  expect(unloadIsBlocked()).toBe(false)
})

it('keeps the unload warning on the ward list until a successful retry or explicit abandonment', async () => {
  const app = open()
  expect(unloadIsBlocked()).toBe(false)
  app.create.mockRejectedValueOnce(new ApiError(0, 'NETWORK'))
  await fill(app)
  await app.user.click(screen.getByRole('button', { name: '병동 만들기' }))
  await screen.findByRole('button', { name: '같은 요청 재시도' })
  expect(unloadIsBlocked()).toBe(true)
  await app.user.click(screen.getAllByRole('link', { name: '내 병동 목록 확인' })[0]!)
  await screen.findByRole('heading', { name: '내 병동' })
  expect(unloadIsBlocked()).toBe(true)
  await app.user.click(screen.getByRole('link', { name: '병동 만들기' }))
  await app.user.click(await screen.findByRole('button', { name: '같은 요청 재시도' }))
  await screen.findByRole('heading', { name: '병동을 만들었어요' })
  expect(unloadIsBlocked()).toBe(false)
  await app.user.click(screen.getByRole('button', { name: '다른 병동 만들기' }))
  app.create.mockRejectedValueOnce(new ApiError(0, 'NETWORK'))
  await app.user.click(screen.getByRole('button', { name: '병동 만들기' }))
  await screen.findByRole('button', { name: '같은 요청 재시도' })
  expect(unloadIsBlocked()).toBe(true)
  await app.user.click(screen.getByRole('button', { name: '입력을 바꿔 새 요청' }))
  await app.user.click(screen.getByRole('button', { name: '확인하고 새 요청 시작' }))
  expect(unloadIsBlocked()).toBe(false)
})

it('removes the previous session unload warning when the account changes', async () => {
  const app = open()
  app.create.mockRejectedValueOnce(new ApiError(0, 'NETWORK'))
  await fill(app)
  await app.user.click(screen.getByRole('button', { name: '병동 만들기' }))
  await screen.findByRole('button', { name: '같은 요청 재시도' })
  expect(unloadIsBlocked()).toBe(true)
  app.server.signInAs(USER_B)
  await act(async () => { app.server.expireAllAccessTokens(); await app.manager.api.getMe().catch(() => undefined) })
  await waitFor(() => expect(screen.getByLabelText('병동 이름')).toHaveValue(''))
  expect(unloadIsBlocked()).toBe(false)
  app.unmount()
  expect(unloadIsBlocked()).toBe(false)
})

it.each([null, {}])('shows a retry for malformed 201 JSON %j and reuses the original key', async (body) => {
  const app = open()
  app.create.mockResolvedValueOnce(json(201, body))
  await fill(app)
  await app.user.click(screen.getByRole('button', { name: '병동 만들기' }))
  await screen.findByRole('button', { name: '같은 요청 재시도' })
  expect(screen.getByRole('alert')).toHaveTextContent('생성 결과를 확인하지 못했어요')
  expect(screen.getByLabelText('병동 이름')).toBeDisabled()
  expect(screen.queryByRole('heading', { name: '병동을 만들었어요' })).not.toBeInTheDocument()
  await app.user.click(screen.getByRole('button', { name: '같은 요청 재시도' }))
  await screen.findByRole('heading', { name: '병동을 만들었어요' })
  expect(app.create.mock.calls[1]![1].body).toEqual(app.create.mock.calls[0]![1].body)
  expect(new Headers(app.create.mock.calls[1]![1].headers).get('Idempotency-Key')).toBe(new Headers(app.create.mock.calls[0]![1].headers).get('Idempotency-Key'))
})

it('selects same-named hospitals by address, sends the contract and shows setup status', async () => {
  const app = open()
  await fill(app)
  expect(screen.getByText('가상 공식 목록 출처')).toBeInTheDocument()
  await app.user.click(screen.getByRole('button', { name: '병동 만들기' }))
  expect(await screen.findByRole('heading', { name: '병동을 만들었어요' })).toBeInTheDocument()
  const [url, init] = app.create.mock.calls[0]!
  expect(url).toBe('/api/v1/wards')
  expect(JSON.parse(init.body)).toEqual({ hospital: { source: 'HIRA', externalId: 'fictional-b' }, wardName: '검증 병동', timeZone: 'Asia/Seoul' })
  expect(new Headers(init.headers).get('Idempotency-Key')).toBeTruthy()
  expect(screen.getByRole('region', { name: '설정 안내' })).toHaveTextContent('설정 완료 여부는 아직 확인하지 않아요')
  expect(screen.getByRole('link', { name: '초대 관리' })).toHaveAttribute('href', '/wards/fictional-ward/invitations')
})

it('retains the exact retry across ward-list navigation and requires confirmation before editing', async () => {
  const app = open()
  app.create.mockRejectedValueOnce(new ApiError(0, 'NETWORK')).mockRejectedValueOnce(new ApiError(0, 'NETWORK'))
  await fill(app)
  await app.user.click(screen.getByRole('button', { name: '병동 만들기' }))
  await screen.findByRole('button', { name: '같은 요청 재시도' })
  expect(screen.getByLabelText('병동 이름')).toBeDisabled()
  await app.user.click(screen.getAllByRole('link', { name: '내 병동 목록 확인' })[0]!)
  await screen.findByRole('heading', { name: '내 병동' })
  await app.user.click(screen.getByRole('link', { name: '병동 만들기' }))
  await app.user.click(await screen.findByRole('button', { name: '같은 요청 재시도' }))
  expect(app.create.mock.calls[1]![1].body).toEqual(app.create.mock.calls[0]![1].body)
  expect(new Headers(app.create.mock.calls[1]![1].headers).get('Idempotency-Key')).toBe(new Headers(app.create.mock.calls[0]![1].headers).get('Idempotency-Key'))
  await app.user.click(screen.getByRole('button', { name: '입력을 바꿔 새 요청' }))
  expect(screen.getByLabelText('병동 이름')).toBeDisabled()
  await app.user.click(screen.getByRole('button', { name: '확인하고 새 요청 시작' }))
  expect(screen.getByLabelText('병동 이름')).toBeEnabled()
})

it('loads the server ward list after success instead of synthesizing memberships', async () => {
  const app = open()
  await fill(app)
  await app.user.click(screen.getByRole('button', { name: '병동 만들기' }))
  await screen.findByRole('heading', { name: '병동을 만들었어요' })
  vi.spyOn(app.manager.api, 'getWards').mockResolvedValue({ items: [created] })
  await app.user.click(screen.getByRole('link', { name: '내 병동 목록 확인' }))
  expect(await screen.findByText('검증 병동')).toBeInTheDocument()
  expect(screen.getByRole('list', { name: '소속 병동' })).toHaveTextContent('관리자')
})

it('discards the previous account draft after session identity changes', async () => {
  const app = open()
  await fill(app)
  app.server.signInAs(USER_B)
  await act(async () => { app.server.expireAllAccessTokens(); await app.manager.api.getMe().catch(() => undefined) })
  await waitFor(() => expect(screen.getByLabelText('병동 이름')).toHaveValue(''))
  expect(screen.getByLabelText('병원 이름')).toHaveValue('')
  expect(screen.queryByRole('radio')).not.toBeInTheDocument()
})

it('separates an empty search from a failed search and lets the user retry', async () => {
  const app = open()
  app.search.mockResolvedValueOnce(json(200, { items: [], page: 1, size: 10, totalCount: 0, attribution: '가상 출처' }))
    .mockResolvedValueOnce(json(503, { code: 'HOSPITAL_SEARCH_UNAVAILABLE' }))
  await app.user.type(await screen.findByLabelText('병원 이름'), '가상 병원')
  await app.user.click(screen.getByRole('button', { name: '검색' }))
  expect(await screen.findByText(/검색 결과가 없어요/)).toBeInTheDocument()
  await app.user.click(screen.getByRole('button', { name: '검색' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('검색 서비스에 연결할 수 없어요')
  expect(screen.queryByText(/검색 결과가 없어요/)).not.toBeInTheDocument()
  await app.user.click(screen.getByRole('button', { name: '검색' }))
  expect(await screen.findAllByRole('radio')).toHaveLength(2)
})
