import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupApp } from '../test/harness'
import { json, USER_A, USER_B } from '../test/fakeAuthServer'
import { WARD_1 } from '../test/fakeInvitationBackend'

const path = `/wards/${WARD_1.wardId}/published-roster`
function setup(options: { admin?: boolean; permission?: boolean; code?: string; status?: number; empty?: boolean } = {}) {
  const requests: string[] = []
  let failure = options.code
  const app = setupApp(path, { configure: ({ server, backend }) => {
    server.signInAs(USER_A)
    const base = backend.fetch
    vi.spyOn(backend, 'fetch').mockImplementation(async (input, init) => {
      if (input.endsWith('/wards')) return json(200, { items: [{ ...WARD_1, roles: [options.admin ? 'WARD_ADMIN' : 'WARD_MEMBER'], canViewWardCalendar: options.permission !== false }] })
      if (input.includes('/published-rosters')) {
        requests.push(input)
        if (failure) return json(options.status ?? 404, { code: failure })
        const month = new URL(input, 'http://local').searchParams.get('month')!
        const common = { wardId: WARD_1.wardId, month, revision: 4, timeZone: 'Asia/Seoul' }
        const start = `${month}-01T13:00:00Z`, end = `${month}-01T22:00:00Z`
        return input.includes('/mine?') ? json(200, { ...common, assignments: options.empty ? [] : [{ date: `${month}-01`, shiftType: '가상N', startsAt: start, endsAt: end }] })
          : json(200, { ...common, staffing: { required: 2, assigned: 1, unfilled: 1, hardRuleViolations: 0 }, slots: [
            { slotId: '1', date: `${month}-01`, shiftCode: '가상N', shiftKind: 'NIGHT', startAt: start, endAt: end, status: 'FILLED', assignment: { nurseId: 'a', displayName: '가상 간호사', locked: false } },
            { slotId: '2', date: `${month}-01`, shiftCode: '가상N', shiftKind: 'NIGHT', startAt: start, endAt: end, status: 'UNFILLED', assignment: null },
          ] })
      }
      return base(input, init)
    })
  } })
  return { ...app, requests, recover: () => { failure = undefined } }
}

it('offers published roster entry for both nurses and admins on the ward list', async () => {
  const app = setup(); act(() => app.navigate('/wards'))
  expect(await screen.findByRole('link', { name: '공개 근무표' })).toHaveAttribute('href', path)
})
it('renders own night shift with ward timezone, selected date details and no inferred off', async () => {
  setup()
  const section = await screen.findByRole('region', { name: '본인 공개 근무표' })
  expect(section).toHaveTextContent('병동 시간대 Asia/Seoul')
  const month = (screen.getByLabelText('대상 월') as HTMLInputElement).value
  expect(within(screen.getByRole('region', { name: '선택 날짜 근무' })).getByText(`22:00 → ${month}-02 07:00`)).toBeInTheDocument()
  expect(section).toHaveTextContent('근무가 표시되지 않은 날짜는 휴가·오프 여부를 뜻하지 않습니다.')
})
it('renders full roster for admin including unfilled slots and supports date selection', async () => {
  const app = setup({ admin: true }); const user = userEvent.setup()
  const section = await screen.findByRole('region', { name: '병동 전체 공개 근무표' })
  expect(section).toHaveTextContent('필요 2 · 배정 1 · 미충원 1')
  expect(screen.getByRole('region', { name: '선택 날짜 근무' })).toHaveTextContent('가상 간호사')
  const month = (screen.getByLabelText('대상 월') as HTMLInputElement).value
  await user.click(screen.getByRole('button', { name: `${month}-02 보기` }))
  expect(screen.getByRole('region', { name: '선택 날짜 근무' })).toHaveTextContent('표시할 근무가 없습니다.')
  expect(app.requests.every(request => !request.includes('/mine?'))).toBe(true)
})
it('hides ward tab without permission, including for an admin', async () => {
  setup({ permission: false, admin: true, empty: true })
  expect(await screen.findByText('이번 달 배정된 근무가 없습니다.')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '병동 전체' })).not.toBeInTheDocument()
})
it.each([['PUBLISHED_ROSTER_NOT_FOUND', 404, '아직 확정된 근무표가 없습니다.'], ['WARD_NOT_FOUND', 404, '소속 병동을 확인할 수 없습니다.'], ['FORBIDDEN', 403, '이 근무표를 조회할 권한이 없습니다.'], ['NETWORK', 0, '서버에 연결할 수 없어요.']])('distinguishes %s and retries', async (code, status, message) => {
  // NETWORK uses a synthetic server code to exercise the same user-facing mapping.
  const app = setup({ code, status: status || 500 })
  expect(await screen.findByText(new RegExp(message.replaceAll('.', '\\.')))).toBeInTheDocument()
  app.recover(); await userEvent.click(screen.getByRole('button', { name: '다시 시도' }))
  expect(await screen.findByRole('region', { name: '본인 공개 근무표' })).toBeInTheDocument()
})
it('explains missing nurse identity and offers ward view', async () => {
  const app = setup({ code: 'NURSE_IDENTITY_REQUIRED', status: 403 })
  expect(await screen.findByText(/활성 간호사 기록이 없어/)).toBeInTheDocument()
  app.recover(); await userEvent.click(screen.getByRole('button', { name: '전체 보기' }))
  expect(await screen.findByRole('region', { name: '병동 전체 공개 근무표' })).toBeInTheDocument()
})
it('moves months, changes mode and refetches when returning from ward list', async () => {
  const app = setup(); await screen.findByRole('region', { name: '본인 공개 근무표' })
  await userEvent.click(screen.getByRole('button', { name: '다음 달' }))
  await waitFor(() => expect(app.requests.at(-1)).toContain((screen.getByLabelText('대상 월') as HTMLInputElement).value))
  await userEvent.click(screen.getByRole('button', { name: '병동 전체' }))
  await screen.findByRole('region', { name: '병동 전체 공개 근무표' })
  const count = app.requests.length
  act(() => app.navigate('/wards')); await screen.findByRole('heading', { name: '내 병동' })
  act(() => app.navigate(path)); await screen.findByRole('region', { name: '본인 공개 근무표' })
  expect(app.requests.length).toBeGreaterThan(count)
})
it('removes schedule immediately on logout and never restores it for a signed out visitor', async () => {
  const app = setup(); await screen.findByRole('region', { name: '본인 공개 근무표' })
  await act(() => app.manager.logout())
  expect(screen.queryByRole('region', { name: '본인 공개 근무표' })).not.toBeInTheDocument()
  expect(await screen.findByRole('button', { name: '카카오로 로그인' })).toBeInTheDocument()
})
it('drops the old schedule across an account change even when the new account cannot load wards', async () => {
  const app = setup(); await screen.findByRole('region', { name: '본인 공개 근무표' })
  const gate = app.server.hold('exchange'); app.server.registerCode('other', USER_B)
  let pending!: Promise<void>
  act(() => { pending = app.manager.completeLogin('other', 'fictional-state') })
  expect(screen.queryByRole('region', { name: '본인 공개 근무표' })).not.toBeInTheDocument()
  vi.spyOn(app.backend, 'fetch').mockResolvedValue(json(500, { code: 'INTERNAL_ERROR' }))
  await act(async () => { gate(); await pending })
  expect(await screen.findByRole('alert')).toHaveTextContent('일시적인 문제')
  expect(screen.queryByText(/공개 revision/)).not.toBeInTheDocument()
})
