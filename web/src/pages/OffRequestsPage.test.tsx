import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { OffRequest } from '../api/offRequestApi'
import { setupApp } from '../test/harness'
import { json, USER_A } from '../test/fakeAuthServer'
import { OFF_ADMIN, OFF_MEMBER, OFF_ITEM, OFF_POLICY, OFF_ELIGIBILITY } from '../test/offRequestFixtures'

const path = `/wards/${OFF_ADMIN.wardId}/off-requests`
function setup(options: { admin?: boolean; late?: boolean; empty?: boolean; limit?: number; failure?: string; status?: OffRequest['status']; uncertain?: boolean } = {}) {
  let items: OffRequest[] = options.empty ? [] : [{ ...OFF_ITEM, status: options.status ?? 'SUBMITTED', afterDeadline: !!options.late }]
  let failure = options.failure
  let monthlyLimit = options.limit ?? 4
  const writes: { path: string; body: Record<string, unknown> | undefined }[] = []
  const reads: string[] = []
  const app = setupApp(path, { configure: ({ server, backend }) => {
    server.signInAs(USER_A)
    const base = backend.fetch
    vi.spyOn(backend, 'fetch').mockImplementation(async (input, init) => {
      if (input.endsWith('/wards')) return json(200, { items: [options.admin ? OFF_ADMIN : OFF_MEMBER] })
      if (input.includes('/roster-eligibility')) return json(200, OFF_ELIGIBILITY)
      if (input.includes('/off-requests')) {
        if (init?.method === 'POST' || init?.method === 'PUT') {
          const body = init.body ? JSON.parse(String(init.body)) : undefined
          writes.push({ path: input, body })
          if (input.endsWith('/policy')) { monthlyLimit = body.monthlyLimit; return new Response(JSON.stringify({ ...OFF_POLICY.value, monthlyLimit, deadlineDay: body.deadlineDay }), { status: 200, headers: { 'Content-Type': 'application/json', ETag: OFF_POLICY.etag } }) }
          const action = input.split('/').at(-1)
          if (action === 'off-requests') {
            const item: OffRequest = { ...OFF_ITEM, ...body, source: body.nurseId ? 'ADMIN' : 'NURSE', requestId: `new-${writes.length}`, afterDeadline: !!options.late }
            items.push(item)
            if (options.uncertain) return new Response(null, { status: 201 })
            return json(201, item)
          }
          const requestId = input.split('/').at(-2)!
          const item = items.find(item => item.requestId === requestId)!
          item.status = action === 'confirm' ? 'CONFIRMED' : action === 'reject' ? 'REJECTED' : 'WITHDRAWN'
          return json(200, item)
        }
        reads.push(input)
        if (failure) return json(failure === 'NURSE_IDENTITY_REQUIRED' ? 403 : 503, { code: failure })
        if (input.endsWith('/policy')) return new Response(JSON.stringify({ ...OFF_POLICY.value, monthlyLimit }), { headers: { 'Content-Type': 'application/json', ETag: OFF_POLICY.etag } })
        const month = new URL(input, 'http://local').searchParams.get('month')!
        items = items.map(item => ({ ...item, date: `${month}-${item.date.slice(-2)}` }))
        return json(200, { items, monthlyLimit, deadline: options.late ? '2000-01-01T00:00:00Z' : '2099-01-01T00:00:00Z' })
      }
      return base(input, init)
    })
  } })
  return { ...app, writes, reads, recover: () => { failure = undefined } }
}
async function selectDate(day = '02') {
  const month = (screen.getByLabelText('대상 월') as HTMLInputElement).value
  fireEvent.change(screen.getByLabelText('희망 날짜'), { target: { value: `${month}-${day}` } })
  return `${month}-${day}`
}
it('links both roles from the ward card and renders own status/source/history', async () => {
  const app = setup(); const section = await screen.findByRole('region', { name: '희망 오프 목록' })
  expect(section).toHaveTextContent('신청됨 · 간호사 본인 입력')
  expect(screen.getByRole('region', { name: '마감과 한도' })).toHaveTextContent('본인 신청 1 / 4일')
  expect(screen.queryByRole('form', { name: '희망 오프 정책' })).not.toBeInTheDocument()
  act(() => app.navigate('/wards'))
  expect(await screen.findByRole('link', { name: '희망 오프' })).toHaveAttribute('href', path)
})
it('submits a nurse request with only date and restores it after reentry', async () => {
  const app = setup({ empty: true }); await screen.findByRole('form', { name: '본인 신청' })
  const date = await selectDate(); await userEvent.click(screen.getByRole('button', { name: '신청' }))
  await waitFor(() => expect(screen.getByRole('region', { name: '마감과 한도' })).toHaveTextContent('본인 신청 1 / 4일'))
  expect(app.writes[0]?.body).toEqual({ date })
  act(() => app.navigate('/wards')); await screen.findByRole('heading', { name: '내 병동' }); act(() => app.navigate(path))
  expect(await screen.findByRole('region', { name: '희망 오프 목록' })).toHaveTextContent(date)
})
it('allows late exception requests but removes self withdrawal', async () => {
  const app = setup({ late: true }); await screen.findByRole('form', { name: '본인 신청' })
  expect(screen.getByText(/마감됐어요/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '철회' })).not.toBeInTheDocument()
  await selectDate(); await userEvent.click(screen.getByRole('button', { name: '예외 요청 제출' }))
  await waitFor(() => expect(app.writes).toHaveLength(1))
  await screen.findByText(/신청 완료/)
})
it('requires explicit confirmation for self withdrawal before deadline', async () => {
  const app = setup(); await screen.findByRole('form', { name: '본인 신청' })
  await userEvent.click(screen.getByRole('button', { name: '철회' })); expect(app.writes).toHaveLength(0)
  expect(screen.getByRole('region', { name: '요청 처리 확인' })).toHaveFocus()
  await userEvent.click(screen.getByRole('button', { name: '철회 실행' }))
  await waitFor(() => expect(screen.getByRole('region', { name: '희망 오프 목록' })).toHaveTextContent('철회됨'))
})
it('prevents nurses from withdrawing confirmed requests and enforces zero limit', async () => {
  setup({ status: 'CONFIRMED', limit: 0 }); await screen.findByRole('form', { name: '본인 신청' }); await selectDate()
  expect(screen.queryByRole('button', { name: '철회' })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: '신청' })).toBeDisabled()
})
it('lets admin proxy submit outside the nurse limit, confirm, filter and withdraw a confirmed request', async () => {
  const app = setup({ admin: true, limit: 0 }); await screen.findByRole('form', { name: '대리 입력' })
  await userEvent.selectOptions(screen.getByLabelText('대상 간호사'), OFF_ITEM.nurseId)
  const date = await selectDate(); await userEvent.click(screen.getByRole('button', { name: '대리 입력' }))
  await screen.findByText(/신청 완료/); expect(app.writes[0]?.body).toEqual({ date, nurseId: OFF_ITEM.nurseId })
  await userEvent.click(screen.getAllByRole('button', { name: '확정' })[0]!)
  expect(screen.getByRole('region', { name: '요청 처리 확인' })).toHaveTextContent('공개 근무표는 재확정 전까지 유지')
  await userEvent.click(screen.getByRole('button', { name: '확정 실행' })); await screen.findByText(/확정 완료/)
  await userEvent.selectOptions(screen.getByLabelText('상태 필터'), 'CONFIRMED')
  const section = screen.getByRole('region', { name: '희망 오프 목록' })
  expect(within(section).getAllByRole('listitem')).toHaveLength(1)
  await userEvent.click(within(section).getByRole('button', { name: '철회' })); await userEvent.click(screen.getByRole('button', { name: '철회 실행' }))
  await screen.findByText(/철회 완료/)
  expect(app.writes.at(-1)?.path).toContain('/withdraw')
})
it('admin rejects requests and saves policy zero with a deadline of 28', async () => {
  const app = setup({ admin: true }); await screen.findByRole('form', { name: '대리 입력' })
  await userEvent.click(screen.getByRole('button', { name: '반려' })); await userEvent.click(screen.getByRole('button', { name: '반려 실행' }))
  await screen.findByText(/반려 완료/)
  fireEvent.change(screen.getByLabelText('월 한도 (0~31일)'), { target: { value: '0' } })
  fireEvent.change(screen.getByLabelText('전달 마감일 (1~28일)'), { target: { value: '28' } })
  await userEvent.click(screen.getByRole('button', { name: '정책 저장' })); await screen.findByText(/정책 저장 완료/)
  expect(app.writes.at(-1)?.body).toEqual({ monthlyLimit: 0, deadlineDay: 28 })
})
it('blocks unknown write retries and month changes until latest result acknowledgement', async () => {
  const app = setup({ empty: true, uncertain: true }); await screen.findByRole('form', { name: '본인 신청' }); await selectDate()
  await userEvent.click(screen.getByRole('button', { name: '신청' }))
  await screen.findByRole('region', { name: '처리 결과 확인' })
  await waitFor(() => expect(screen.getByRole('button', { name: '목록에서 결과를 확인했어요' })).toBeEnabled())
  expect(screen.getByRole('button', { name: '신청' })).toBeDisabled()
  expect(screen.getByRole('button', { name: '다음 달' })).toBeDisabled()
  await userEvent.click(screen.getByRole('button', { name: '새로고침' }))
  await waitFor(() => expect(screen.getByRole('button', { name: '목록에서 결과를 확인했어요' })).toBeEnabled())
  expect(app.writes).toHaveLength(1)
  await userEvent.click(screen.getByRole('button', { name: '목록에서 결과를 확인했어요' }))
  expect(screen.getByRole('button', { name: '다음 달' })).toBeEnabled()
})
it.each(['NURSE_IDENTITY_REQUIRED', 'HTTP_503'])('shows %s without exposing stale requests and recovers', async failure => {
  const app = setup({ failure }); expect(await screen.findByRole('alert')).toBeInTheDocument()
  expect(screen.queryByRole('region', { name: '희망 오프 목록' })).not.toBeInTheDocument()
  app.recover(); await userEvent.click(screen.getByRole('button', { name: '새로고침' }))
  expect(await screen.findByRole('region', { name: '희망 오프 목록' })).toBeInTheDocument()
})
it('erases the screen on logout and guards direct signed-out URLs', async () => {
  const app = setup(); await screen.findByRole('region', { name: '희망 오프 목록' }); await act(() => app.manager.logout())
  expect(screen.queryByRole('region', { name: '희망 오프 목록' })).not.toBeInTheDocument()
  act(() => app.navigate(path)); expect(await screen.findByRole('button', { name: '카카오로 로그인' })).toBeInTheDocument()
})
