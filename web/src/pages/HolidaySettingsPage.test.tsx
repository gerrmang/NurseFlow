import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupApp } from '../test/harness'
import { USER_A } from '../test/fakeAuthServer'
import { ADMIN_WARD, taggedJson } from '../test/scheduleFixtures'
import { HOLIDAY_CALENDAR, HOLIDAY_POLICY } from '../test/holidayFixtures'

beforeEach(() => { vi.useFakeTimers({ toFake: ['Date'] }); vi.setSystemTime(new Date('2026-11-27T00:00:00Z')) })
afterEach(() => vi.useRealTimers())
function open(admin = true, configured = true) {
  const app = setupApp(`/wards/${ADMIN_WARD.wardId}/holiday-settings`, { configure: ({ server, backend }) => { server.signInAs(USER_A); backend.addMember(ADMIN_WARD.wardId, USER_A.userId, admin ? ['WARD_ADMIN'] : ['WARD_MEMBER']) } })
  let calendar = { ...structuredClone(HOLIDAY_CALENDAR), holidaySource: { configured, fetchedAt: null } }, policy = structuredClone(HOLIDAY_POLICY)
  const writes = vi.fn(async (_url: string, _init: RequestInit) => {})
  const base = app.backend.fetch
  vi.spyOn(app.backend, 'fetch').mockImplementation(async (url, init) => {
    if (!url.includes('/holiday-calendar') && !url.includes('/target-hour-policy')) return base(url, init)
    if (init?.method === 'PUT') {
      await writes(url, init)
      if (url.includes('/target-hour-policy')) policy = { ...policy, ...JSON.parse(init.body as string) }
      else calendar = { ...calendar, days: JSON.parse(init.body as string).days }
    }
    return taggedJson(url.includes('/target-hour-policy') ? policy : calendar)
  })
  return { ...app, writes, user: userEvent.setup() }
}
it('blocks a member even when navigating directly', async () => {
  const app = open(false); expect(await screen.findByRole('alert')).toHaveTextContent('관리 권한')
  expect(screen.queryByRole('heading', { name: '휴일 달력' })).not.toBeInTheDocument()
  expect(vi.mocked(app.backend.fetch).mock.calls.some(([p]) => p.includes('/holiday-calendar'))).toBe(false)
})
it('stages an override without writing, shows both origins, then confirms whole-list save', async () => {
  const app = open(); await app.user.click(await screen.findByRole('button', { name: /2026-11-03 · 공휴일/ }))
  fireEvent.change(screen.getByLabelText('날짜 구분'), { target: { value: 'WARD_WORKDAY' } })
  await app.user.click(screen.getByRole('button', { name: '달력에 적용' }))
  expect(app.writes).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: /2026-11-03 · 근무일 지정 · 병동 지정 · 국가 공휴일 가상 공휴일/ })).toBeInTheDocument()
  await app.user.click(screen.getByRole('button', { name: '달력 저장' }))
  expect(screen.getByRole('region', { name: '휴일 설정 변경 확인' })).toHaveFocus()
  expect(app.writes).not.toHaveBeenCalled()
  await app.user.click(screen.getByRole('button', { name: '확인' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledTimes(1))
  expect(JSON.parse(app.writes.mock.calls[0]![1].body as string)).toEqual({ days: [{ date: '2026-11-03', kind: 'WARD_WORKDAY', name: null }] })
})
it('preserves edits when discarding for a month change is cancelled', async () => {
  const app = open(); const field = await screen.findByLabelText('하루 기준시간 (분)')
  fireEvent.change(field, { target: { value: '420' } }); fireEvent.change(screen.getByLabelText('대상 월'), { target: { value: '2026-12' } })
  await app.user.click(screen.getByRole('button', { name: '취소' }))
  expect(field).toHaveValue(420); expect(screen.getByLabelText('대상 월')).toHaveValue('2026-11'); expect(app.writes).not.toHaveBeenCalled()
})
it('keeps blank dedicated defaults distinct from zero and preserves policy draft on calendar save', async () => {
  const app = open(); const field = await screen.findByLabelText('Night 전담 월 목표 (분)')
  expect(field).toHaveValue(null); fireEvent.change(field, { target: { value: '0' } })
  await app.user.click(screen.getByRole('button', { name: /2026-11-03 · 공휴일/ })); await app.user.click(screen.getByRole('button', { name: '달력에 적용' })); await app.user.click(screen.getByRole('button', { name: '달력 저장' })); await app.user.click(screen.getByRole('button', { name: '확인' }))
  await waitFor(() => expect(screen.getByRole('button', { name: '목표시간 정책 저장' })).toBeEnabled())
  expect(field).toHaveValue(0)
  await app.user.click(screen.getByRole('button', { name: '목표시간 정책 저장' })); await app.user.click(screen.getByRole('button', { name: '확인' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledTimes(2))
  const body = JSON.parse(app.writes.mock.calls[1]![1].body as string)
  expect(body.nightDedicatedTargetMinutes).toBe(0); expect(body.nightDedicatedNightCreditMinutes).toBeNull()
})
it('explains an unconfigured source and disables refresh', async () => {
  const app = open(true, false); await screen.findByText('자동 공휴일이 꺼져 있어 직접 입력이 필요해요.')
  await app.user.click(screen.getByText('국가 공휴일 갱신', { selector: 'summary' }))
  expect(screen.getByRole('button', { name: '공휴일 지금 가져오기' })).toBeDisabled(); expect(app.writes).not.toHaveBeenCalled()
})
