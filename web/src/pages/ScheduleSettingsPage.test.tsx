import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '../api/http'
import type { Absence } from '../api/scheduleSettingsApi'
import { setupApp } from '../test/harness'
import { USER_A } from '../test/fakeAuthServer'
import { ADMIN_WARD, NURSE_A, TEAM_A, ROSTER, SHIFT, taggedJson } from '../test/scheduleFixtures'

beforeEach(() => { vi.useFakeTimers({ toFake: ['Date'] }); vi.setSystemTime(new Date('2026-11-24T00:00:00Z')) })
afterEach(() => vi.useRealTimers())

function open(admin = true, assigned = false) {
  const app = setupApp(`/wards/${ADMIN_WARD.wardId}/schedule-settings`, { configure: ({ server, backend }) => { server.signInAs(USER_A); backend.addMember(ADMIN_WARD.wardId, USER_A.userId, admin ? ['WARD_ADMIN'] : ['WARD_MEMBER']) } })
  const writes = vi.fn(async (_path: string, _init: RequestInit) => taggedJson(SHIFT))
  const preview = vi.fn(async () => taggedJson({ shiftType: SHIFT, policyRevision: 1, flagsDrafts: true, rewritesDraftSlotTimes: true, affectedDrafts: [{ rosterId: ROSTER.rosterId, month: '2026-11', revision: 1, slots: 2, assignedSlots: 0 }], publishedRostersUsingType: 1 }))
  const pattern = vi.fn(async () => taggedJson({ workPattern: 'GENERAL' }))
  const data = { roster: { ...ROSTER, slots: assigned ? [{ slotId: 'slot', date: '2026-11-01', shiftTypeId: SHIFT.shiftTypeId, shiftCode: 'D', shiftKind: 'DAY', teamId: TEAM_A.teamId, seniorRequired: true, startAt: '2026-11-01T00:00:00Z', endAt: '2026-11-01T08:00:00Z', status: 'FILLED' as const, assignment: { nurseId: NURSE_A.nurseId, displayName: NURSE_A.displayName, locked: false } }] : [] }, shifts: [SHIFT], draft: true, absences: [] as Absence[] }
  const base = app.backend.fetch
  vi.spyOn(app.backend, 'fetch').mockImplementation(async (url, init) => {
    const path = url.replace('/api/v1', '')
    if (path.endsWith('/preview')) return preview()
    if (path.includes('/rosters') || path.includes('/shift-types') || path.includes('/work-pattern')) {
      if (init?.method && init.method !== 'GET') return writes(path, init)
      if (path.includes('/rosters?month=')) return taggedJson({ items: data.draft ? [{ ...data.roster, month: decodeURIComponent(path.split('month=')[1]!) }] : [] })
      if (path.endsWith('/absences')) return taggedJson({ items: data.absences })
      if (path.endsWith('/work-pattern')) return pattern()
      if (path.includes('/shift-types')) return taggedJson({ items: data.shifts, policyRevision: 1 })
      return taggedJson(data.roster, '"roster-r1"')
    }
    if (path.endsWith('/teams')) return taggedJson({ items: [TEAM_A] })
    if (path.endsWith('/staff?includeInactive=true')) return taggedJson({ items: [NURSE_A] })
    return base(url, init)
  })
  return { ...app, user: userEvent.setup(), writes, preview, pattern, data }
}
it('blocks a member navigating directly to schedule settings', async () => {
  const app = open(false)
  expect(await screen.findByRole('alert')).toHaveTextContent('관리 권한')
  expect(screen.queryByLabelText('근무 코드')).not.toBeInTheDocument()
  expect(vi.mocked(app.backend.fetch).mock.calls.some(([path]) => path.includes('/shift-types'))).toBe(false)
})
it('opens the catalog row for editing and focuses the impact confirmation before any write', async () => {
  const app = open()
  const row = await screen.findByRole('button', { name: `${SHIFT.displayName} 편집` })
  expect(row).toHaveAccessibleDescription(/07:00~15:00/)
  await app.user.click(row)
  expect(screen.getByLabelText('편집할 근무 유형')).toHaveValue(SHIFT.shiftTypeId)
  fireEvent.change(screen.getByLabelText('종료 시각'), { target: { value: '16:00' } })
  await app.user.click(screen.getByRole('button', { name: '변경 영향 확인' }))
  expect(await screen.findByRole('region', { name: '설정 변경 확인' })).toHaveFocus()
  expect(app.writes).not.toHaveBeenCalled()
  await app.user.click(screen.getByRole('button', { name: '취소' }))
  expect(screen.getByLabelText('종료 시각')).toHaveValue('16:00')
  expect(app.writes).not.toHaveBeenCalled()
})
it('validates midnight and recognized minutes before sending a create', async () => {
  const app = open()
  await app.user.type(await screen.findByLabelText('근무 코드'), 'N')
  await app.user.type(screen.getByLabelText('표시 이름'), '가상 Night')
  fireEvent.change(screen.getByLabelText('시작 시각'), { target: { value: '22:00' } })
  fireEvent.change(screen.getByLabelText('종료 시각'), { target: { value: '07:00' } })
  await app.user.click(screen.getByRole('button', { name: '근무 유형 생성' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('자정 경과')
  expect(app.writes).not.toHaveBeenCalled()
  await app.user.click(screen.getByLabelText('종료는 다음 날'))
  await app.user.click(screen.getByRole('button', { name: '근무 유형 생성' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledOnce())
  expect(JSON.parse(app.writes.mock.calls[0]![1].body as string)).toMatchObject({ spansMidnight: true, startTime: '22:00', endTime: '07:00' })
})
it('previews affected drafts before shift updates and sends the original policy tag', async () => {
  const app = open(); await app.user.selectOptions(await screen.findByLabelText('편집할 근무 유형'), SHIFT.shiftTypeId)
  fireEvent.change(screen.getByLabelText('종료 시각'), { target: { value: '16:00' } })
  await app.user.click(screen.getByRole('button', { name: '변경 영향 확인' }))
  expect(await screen.findByRole('region', { name: '설정 변경 확인' })).toHaveTextContent('해당 근무 2칸')
  expect(app.writes).not.toHaveBeenCalled()
  await app.user.click(screen.getByRole('button', { name: '확인하고 저장' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledOnce())
  expect(new Headers(app.writes.mock.calls[0]![1].headers).get('If-Match')).toBe('"policy-1"')
})
it('does not fabricate editable legacy OTHER input', async () => {
  const app = open(); app.data.shifts = [{ ...SHIFT, kind: 'OTHER' }]
  await app.user.selectOptions(await screen.findByLabelText('편집할 근무 유형'), SHIFT.shiftTypeId)
  expect(screen.queryByLabelText('근무 코드')).not.toBeInTheDocument()
  await app.user.click(screen.getByRole('button', { name: '선택 유형 비활성화' }))
  expect(app.preview).not.toHaveBeenCalled()
  expect(await screen.findByRole('region', { name: '설정 변경 확인' })).toHaveTextContent('기존 초안은 재검증')
})
it('requires explicit whole replacement and includes team demand and qualifications', async () => {
  const app = open(); await screen.findByLabelText('근무 코드')
  await app.user.click(screen.getByRole('button', { name: '팀별 필요 인원' }))
  await app.user.click(screen.getByRole('button', { name: '수요 항목 추가' }))
  expect(screen.getByRole('button', { name: '수요 전체 교체 확인' })).toBeDisabled()
  await app.user.type(screen.getByLabelText('필요 자격 (쉼표 구분)'), ' ICU, CRRT , , ')
  await app.user.click(screen.getByRole('checkbox', { name: /기존 수요·필요 자격 전체/ }))
  await app.user.click(screen.getByRole('button', { name: '수요 전체 교체 확인' }))
  expect(app.writes).not.toHaveBeenCalled()
  app.writes.mockResolvedValueOnce(taggedJson(ROSTER, '"roster-r2"'))
  await app.user.click(screen.getByRole('button', { name: '확인하고 저장' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledOnce())
  expect(JSON.parse(app.writes.mock.calls[0]![1].body as string)).toEqual({ items: [{ date: '2026-11-01', teamId: TEAM_A.teamId, shiftTypeId: SHIFT.shiftTypeId, requiredCount: 1, requiredQualifications: ['ICU', 'CRRT'] }] })
})
it('keeps demand rows and allows correction and resubmission after server validation rejection', async () => {
  const app = open(); await screen.findByLabelText('근무 코드')
  await app.user.click(screen.getByRole('button', { name: '팀별 필요 인원' }))
  await app.user.click(screen.getByRole('button', { name: '수요 항목 추가' }))
  fireEvent.change(screen.getByLabelText('필요 인원'), { target: { value: '12' } })
  await app.user.type(screen.getByLabelText('필요 자격 (쉼표 구분)'), ' ICU, CRRT ')
  await app.user.click(screen.getByRole('checkbox', { name: /기존 수요·필요 자격 전체/ }))
  await app.user.click(screen.getByRole('button', { name: '수요 전체 교체 확인' }))
  app.writes.mockRejectedValueOnce(new ApiError(400, 'TOO_MANY_SLOTS'))
  await app.user.click(screen.getByRole('button', { name: '확인하고 저장' }))
  expect(await screen.findByText('전체 필요 인원이 서버 상한을 초과했어요.')).toBeInTheDocument()
  expect(screen.getByLabelText('필요 인원')).toHaveValue(12)
  expect(screen.getByLabelText('필요 자격 (쉼표 구분)')).toHaveValue(' ICU, CRRT ')
  fireEvent.change(screen.getByLabelText('필요 인원'), { target: { value: '2' } })
  await app.user.click(screen.getByRole('checkbox', { name: /기존 수요·필요 자격 전체/ }))
  await app.user.click(screen.getByRole('button', { name: '수요 전체 교체 확인' }))
  app.writes.mockResolvedValueOnce(taggedJson(ROSTER, '"roster-r2"'))
  await app.user.click(screen.getByRole('button', { name: '확인하고 저장' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledTimes(2))
  expect(JSON.parse(app.writes.mock.calls[1]![1].body as string).items[0]).toMatchObject({ requiredCount: 2, requiredQualifications: ['ICU', 'CRRT'] })
})
it('keeps new shift input after a duplicate code rejection', async () => {
  const app = open()
  await app.user.type(await screen.findByLabelText('근무 코드'), 'D2')
  await app.user.type(screen.getByLabelText('표시 이름'), '가상 Day 2')
  app.writes.mockRejectedValueOnce(new ApiError(400, 'SHIFT_TYPE_CODE_DUPLICATE'))
  await app.user.click(screen.getByRole('button', { name: '근무 유형 생성' }))
  expect(await screen.findByText('비활성 유형을 포함해 같은 근무 코드가 있어요.')).toBeInTheDocument()
  expect(screen.getByLabelText('근무 코드')).toHaveValue('D2')
  expect(screen.getByLabelText('표시 이름')).toHaveValue('가상 Day 2')
  expect(screen.getByRole('button', { name: '근무 유형 생성' })).toBeEnabled()
})
it.each(['preview', 'save'])('keeps the selected shift and edits after a %s validation rejection', async phase => {
  const app = open()
  await app.user.selectOptions(await screen.findByLabelText('편집할 근무 유형'), SHIFT.shiftTypeId)
  fireEvent.change(screen.getByLabelText('종료 시각'), { target: { value: '16:00' } })
  if (phase === 'preview') app.preview.mockRejectedValueOnce(new ApiError(400, 'INVALID_SHIFT_MINUTES'))
  else app.writes.mockRejectedValueOnce(new ApiError(400, 'INVALID_SHIFT_MINUTES'))
  await app.user.click(screen.getByRole('button', { name: '변경 영향 확인' }))
  if (phase === 'save') await app.user.click(await screen.findByRole('button', { name: '확인하고 저장' }))
  expect(await screen.findByText('휴게·인정·환산 시간을 확인해 주세요.')).toBeInTheDocument()
  expect(screen.getByLabelText('편집할 근무 유형')).toHaveValue(SHIFT.shiftTypeId)
  expect(screen.getByLabelText('종료 시각')).toHaveValue('16:00')
  expect(screen.getByRole('button', { name: '변경 영향 확인' })).toBeEnabled()
})
it.each([2026, 2027])('uses the local month just after midnight on the first day (%i)', async year => {
  vi.setSystemTime(new Date(year, 0, 1, 0, 30))
  const app = open(); app.data.draft = false
  await screen.findByLabelText('근무 코드')
  await app.user.click(screen.getByRole('button', { name: '팀별 필요 인원' }))
  expect(screen.getByLabelText('대상 월')).toHaveValue(`${year}-01`)
  await app.user.click(screen.getByRole('button', { name: '이 달 초안 만들기' }))
  expect(screen.getByRole('region', { name: '설정 변경 확인' })).toHaveTextContent(`${year}-01 초안을 만들까요?`)
})
it('blocks demand replacement when any assignment already exists', async () => {
  const app = open(true, true); await screen.findByLabelText('근무 코드')
  await app.user.click(screen.getByRole('button', { name: '팀별 필요 인원' }))
  expect(screen.getByRole('alert')).toHaveTextContent('이미 배치')
  expect(screen.getByRole('button', { name: '수요 항목 추가' })).toBeDisabled()
})
it('does not automatically create a draft on entering an empty month', async () => {
  const app = open(); app.data.draft = false; await screen.findByLabelText('근무 코드')
  await app.user.click(screen.getByRole('button', { name: '승인 VY·반차' }))
  expect(screen.getByRole('button', { name: '이 달 초안 만들기' })).toBeEnabled(); expect(app.writes).not.toHaveBeenCalled()
})
it('converts absence input in the ward zone without collecting a reason', async () => {
  const app = open(); await screen.findByLabelText('근무 코드'); await app.user.click(screen.getByRole('button', { name: '승인 VY·반차' }))
  await app.user.selectOptions(screen.getByLabelText('부재 간호사'), NURSE_A.nurseId)
  fireEvent.change(screen.getByLabelText('부재 시작'), { target: { value: '2026-11-02T09:00' } })
  fireEvent.change(screen.getByLabelText('부재 종료'), { target: { value: '2026-11-02T13:00' } })
  await app.user.selectOptions(screen.getByLabelText('부재 구분'), 'APPROVED_HALF_DAY')
  await app.user.click(screen.getByRole('button', { name: '부재 등록 확인' }))
  app.writes.mockResolvedValueOnce(taggedJson({ absenceId: 'absence', nurseId: NURSE_A.nurseId, kind: 'APPROVED_HALF_DAY', startsAt: '2026-11-02T00:00:00Z', endsAt: '2026-11-02T04:00:00Z' }, '"roster-r2"'))
  await app.user.click(screen.getByRole('button', { name: '확인하고 저장' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledOnce())
  expect(JSON.parse(app.writes.mock.calls[0]![1].body as string)).toEqual({ nurseId: NURSE_A.nurseId, kind: 'APPROVED_HALF_DAY', startsAt: '2026-11-02T00:00:00.000Z', endsAt: '2026-11-02T04:00:00.000Z' })
  expect(screen.queryByLabelText(/사유/)).not.toBeInTheDocument()
})
it('requires result acknowledgement before resending an uncertain creation', async () => {
  const app = open(); await app.user.type(await screen.findByLabelText('근무 코드'), 'D2'); await app.user.type(screen.getByLabelText('표시 이름'), '가상 Day 2')
  app.writes.mockRejectedValueOnce(new ApiError(0, 'NETWORK'))
  await app.user.click(screen.getByRole('button', { name: '근무 유형 생성' }))
  expect(await screen.findByRole('region', { name: '저장 결과 확인' })).toHaveTextContent('이미 처리됐을 수 있어요')
  expect(screen.getByRole('button', { name: '근무 유형 생성' })).toBeDisabled()
  await app.user.click(screen.getByRole('button', { name: '최신 결과를 확인했어요' }))
  expect(screen.getByRole('button', { name: '근무 유형 생성' })).toBeEnabled()
})
it('keeps absence input after server validation rejection', async () => {
  const app = open(); await screen.findByLabelText('근무 코드')
  await app.user.click(screen.getByRole('button', { name: '승인 VY·반차' }))
  await app.user.selectOptions(screen.getByLabelText('부재 간호사'), NURSE_A.nurseId)
  fireEvent.change(screen.getByLabelText('부재 시작'), { target: { value: '2026-11-02T09:00' } })
  fireEvent.change(screen.getByLabelText('부재 종료'), { target: { value: '2026-11-02T13:00' } })
  await app.user.selectOptions(screen.getByLabelText('부재 구분'), 'APPROVED_HALF_DAY')
  await app.user.click(screen.getByRole('button', { name: '부재 등록 확인' }))
  app.writes.mockRejectedValueOnce(new ApiError(400, 'INVALID_ABSENCE'))
  await app.user.click(screen.getByRole('button', { name: '확인하고 저장' }))
  expect(await screen.findByText('부재 시작·종료와 구분을 확인해 주세요.')).toBeInTheDocument()
  expect(screen.getByLabelText('부재 간호사')).toHaveValue(NURSE_A.nurseId)
  expect(screen.getByLabelText('부재 구분')).toHaveValue('APPROVED_HALF_DAY')
  expect(screen.getByLabelText('부재 시작')).toHaveValue('2026-11-02T09:00')
  expect(screen.getByLabelText('부재 종료')).toHaveValue('2026-11-02T13:00')
  expect(screen.getByRole('button', { name: '부재 등록 확인' })).toBeEnabled()
})
it('clears old settings on logout', async () => {
  const app = open(); await screen.findByLabelText('근무 코드'); await act(() => app.manager.logout())
  expect(screen.queryByLabelText('근무 코드')).not.toBeInTheDocument()
  expect(screen.getByTestId('location')).toHaveTextContent('/login')
})
it('keeps the nurse selection and displays the reloaded pattern after saving', async () => {
  const app = open(); await screen.findByLabelText('근무 코드'); await app.user.click(screen.getByRole('button', { name: '근무 형태' }))
  await app.user.selectOptions(screen.getByLabelText('간호사', { exact: true }), NURSE_A.nurseId)
  await app.user.selectOptions(await screen.findByLabelText('근무 형태', { exact: true }), 'NIGHT_ONLY')
  await app.user.click(screen.getByRole('button', { name: '근무 형태 저장' }))
  const readsBeforeSave = app.pattern.mock.calls.length
  app.writes.mockResolvedValueOnce(taggedJson({ workPattern: 'NIGHT_ONLY' })); app.pattern.mockImplementation(async () => taggedJson({ workPattern: 'NIGHT_ONLY' }))
  await app.user.click(screen.getByRole('button', { name: '확인하고 저장' }))
  await waitFor(() => expect(screen.getByLabelText('근무 형태', { exact: true })).toHaveValue('NIGHT_ONLY'))
  expect(screen.getByLabelText('간호사', { exact: true })).toHaveValue(NURSE_A.nurseId)
  expect(app.pattern.mock.calls.length).toBeGreaterThan(readsBeforeSave)
})
it('does not substitute GENERAL when a pattern read fails', async () => {
  const app = open(); await screen.findByLabelText('근무 코드'); app.pattern.mockRejectedValue(new ApiError(503, 'UNAVAILABLE'))
  await app.user.click(screen.getByRole('button', { name: '근무 형태' })); await app.user.selectOptions(screen.getByLabelText('간호사', { exact: true }), NURSE_A.nurseId)
  expect(await screen.findByRole('alert')).toHaveTextContent('저장할 수 없어요')
  expect(screen.queryByRole('button', { name: '근무 형태 저장' })).not.toBeInTheDocument()
  app.pattern.mockImplementation(async () => taggedJson({ workPattern: 'NIGHT_ONLY' }))
  await app.user.click(screen.getByRole('button', { name: '다시 조회' }))
  expect(await screen.findByLabelText('근무 형태', { exact: true })).toHaveValue('NIGHT_ONLY')
})

const existingAbsence: Absence = { absenceId: 'existing-absence', nurseId: NURSE_A.nurseId, kind: 'APPROVED_HALF_DAY', startsAt: '2026-11-05T00:00:30.250Z', endsAt: '2026-11-05T04:00:45.123456789Z' }
async function editAbsence(absence = existingAbsence, zone = 'Asia/Seoul') {
  const app = open()
  app.data.absences = [absence]
  app.data.roster.timeZone = zone
  app.writes.mockImplementation(async () => taggedJson(absence, '"roster-r2"'))
  await screen.findByLabelText('근무 코드')
  await app.user.click(screen.getByRole('button', { name: '승인 VY·반차' }))
  await app.user.click(screen.getByRole('button', { name: '부재 수정' }))
  return app
}
it.each([
  ['Asia/Seoul', existingAbsence.startsAt, existingAbsence.endsAt],
  ['Asia/Seoul', '2026-11-05T09:00:30.250+09:00', '2026-11-05T04:00:45.123456789Z'],
  ['Asia/Seoul', '2026-11-05T00:00:30.123456788Z', '2026-11-05T00:00:30.123456789Z'],
  ['America/New_York', '2026-11-01T05:30:00Z', '2026-11-01T06:30:00Z'],
  ['America/New_York', '2026-11-01T06:30:30.250Z', '2026-11-01T07:30:45.123456789Z'],
])('preserves original instants when only absence kind changes (%s, %s)', async (zone, startsAt, endsAt) => {
  const app = await editAbsence({ ...existingAbsence, startsAt, endsAt }, zone)
  await app.user.selectOptions(screen.getByLabelText('부재 구분'), 'APPROVED_LEAVE')
  await app.user.click(screen.getByRole('button', { name: '부재 수정 확인' }))
  await app.user.click(screen.getByRole('button', { name: '확인하고 저장' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledOnce())
  expect(JSON.parse(app.writes.mock.calls[0]![1].body as string)).toEqual({ nurseId: NURSE_A.nurseId, kind: 'APPROVED_LEAVE', startsAt, endsAt })
})
it.each([
  ['부재 시작', '2026-11-05T10:00', '2026-11-05T01:00:00.000Z', existingAbsence.endsAt],
  ['부재 종료', '2026-11-05T14:00', existingAbsence.startsAt, '2026-11-05T05:00:00.000Z'],
])('converts only the changed absence endpoint (%s)', async (field, value, startsAt, endsAt) => {
  const app = await editAbsence()
  fireEvent.change(screen.getByLabelText(field), { target: { value } })
  await app.user.click(screen.getByRole('button', { name: '부재 수정 확인' }))
  await app.user.click(screen.getByRole('button', { name: '확인하고 저장' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledOnce())
  expect(JSON.parse(app.writes.mock.calls[0]![1].body as string)).toMatchObject({ startsAt, endsAt })
})
it('still rejects a changed endpoint in a repeated DST time', async () => {
  const app = await editAbsence({ ...existingAbsence, startsAt: '2026-11-01T04:30:00Z', endsAt: '2026-11-01T07:30:00Z' }, 'America/New_York')
  fireEvent.change(screen.getByLabelText('부재 시작'), { target: { value: '2026-11-01T01:15' } })
  await app.user.click(screen.getByRole('button', { name: '부재 수정 확인' }))
  expect(screen.getByRole('alert')).toHaveTextContent('중복되는 시각')
  expect(app.writes).not.toHaveBeenCalled()
})
it('validates chronological order using original instants even with different offsets', async () => {
  const app = await editAbsence({ ...existingAbsence, startsAt: '2026-11-05T09:00:30+09:00', endsAt: '2026-11-05T04:00:45Z' })
  fireEvent.change(screen.getByLabelText('부재 종료'), { target: { value: '2026-11-05T08:00' } })
  await app.user.click(screen.getByRole('button', { name: '부재 수정 확인' }))
  expect(screen.getByRole('alert')).toHaveTextContent('종료는 시작보다 뒤')
  expect(app.writes).not.toHaveBeenCalled()
})
