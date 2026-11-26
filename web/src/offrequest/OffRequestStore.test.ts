import { ApiError } from '../api/http'
import type { OffRequestApi } from '../api/offRequestApi'
import { OFF_ADMIN, OFF_MEMBER, OFF_LIST, OFF_ITEM, OFF_POLICY, OFF_ELIGIBILITY } from '../test/offRequestFixtures'
import { deadlinePassed, OffRequestStore, ownCount } from './OffRequestStore'

function setup(admin = false) {
  const api = {
    list: vi.fn().mockResolvedValue(OFF_LIST), submit: vi.fn().mockResolvedValue(OFF_ITEM), decide: vi.fn().mockResolvedValue(OFF_ITEM),
    policy: vi.fn().mockResolvedValue(OFF_POLICY), savePolicy: vi.fn().mockResolvedValue(OFF_POLICY), eligibility: vi.fn().mockResolvedValue(OFF_ELIGIBILITY),
  } satisfies OffRequestApi
  const wards = { getWards: vi.fn().mockResolvedValue({ items: [admin ? OFF_ADMIN : OFF_MEMBER] }) }
  const store = new OffRequestStore(OFF_ADMIN.wardId, api, wards, new Date(2031, 1, 1))
  return { api, wards, store }
}
it('only fetches own list for a nurse, and admin policy and targets for an admin', async () => {
  const member = setup(); await member.store.reload()
  expect(member.api.policy).not.toHaveBeenCalled(); expect(member.api.eligibility).not.toHaveBeenCalled()
  const admin = setup(true); await admin.store.reload()
  expect(admin.store.getSnapshot().data?.policy).toEqual(OFF_POLICY)
  expect(admin.api.eligibility).toHaveBeenCalledOnce()
})
it('counts only active nurse sourced entries and makes the exact deadline exclusive', () => {
  expect(ownCount([OFF_ITEM, { ...OFF_ITEM, source: 'ADMIN' }, { ...OFF_ITEM, status: 'CONFIRMED' }, { ...OFF_ITEM, status: 'REJECTED' }, { ...OFF_ITEM, status: 'WITHDRAWN' }])).toBe(2)
  const deadline = Date.parse(OFF_LIST.deadline)
  expect(deadlinePassed(OFF_LIST, deadline - 1)).toBe(false); expect(deadlinePassed(OFF_LIST, deadline)).toBe(true)
})
it('allows late exception submission but blocks nurse withdrawal at the deadline and confirmed withdrawal', async () => {
  const { store, api } = setup(); api.list.mockResolvedValue({ ...OFF_LIST, deadline: '2000-01-01T00:00:00Z' })
  await store.reload(); await store.submit(OFF_ITEM.date)
  expect(api.submit).toHaveBeenCalledWith(OFF_ITEM.date, undefined)
  await store.decide(OFF_ITEM.requestId, 'withdraw'); await store.decide(OFF_ITEM.requestId, 'confirm')
  expect(api.decide).not.toHaveBeenCalled()
  api.list.mockResolvedValue({ ...OFF_LIST, items: [{ ...OFF_ITEM, status: 'CONFIRMED' }] }); await store.reload()
  await store.decide(OFF_ITEM.requestId, 'withdraw'); expect(api.decide).not.toHaveBeenCalled()
})
it('enforces zero limit for nurse but accepts an eligible proxy target for admin', async () => {
  const member = setup(); member.api.list.mockResolvedValue({ ...OFF_LIST, monthlyLimit: 0 }); await member.store.reload()
  await member.store.submit(OFF_ITEM.date); expect(member.api.submit).not.toHaveBeenCalled()
  const admin = setup(true); admin.api.list.mockResolvedValue({ ...OFF_LIST, monthlyLimit: 0 }); admin.api.policy.mockResolvedValue({ ...OFF_POLICY, value: { ...OFF_POLICY.value, monthlyLimit: 0 } }); await admin.store.reload()
  await admin.store.submit(OFF_ITEM.date); await admin.store.submit(OFF_ITEM.date, 'unknown')
  expect(admin.api.submit).not.toHaveBeenCalled()
  await admin.store.submit(OFF_ITEM.date, OFF_ITEM.nurseId); expect(admin.api.submit).toHaveBeenCalledOnce()
})
it('checks membership before mutations and discards admin data when role changes', async () => {
  const { store, wards, api } = setup(true); await store.reload()
  wards.getWards.mockResolvedValue({ items: [OFF_MEMBER] })
  await store.submit(OFF_ITEM.date, OFF_ITEM.nurseId)
  expect(api.submit).not.toHaveBeenCalled(); expect(store.getSnapshot().data).toBeNull(); expect(store.getSnapshot().busy).toBe(false)
})
it.each([new ApiError(0, 'NETWORK'), new ApiError(503, 'HTTP_503'), new ApiError(201, 'INVALID_RESPONSE')])('locks uncertain mutations through reload until explicit acknowledgement %#', async error => {
  const { store, api } = setup(); await store.reload(); api.submit.mockRejectedValue(error)
  await store.submit(OFF_ITEM.date); expect(store.getSnapshot().uncertain).toBe(true)
  await store.submit(OFF_ITEM.date); expect(api.submit).toHaveBeenCalledOnce()
  store.selectMonth('2031-04'); expect(store.getSnapshot().month).toBe('2031-03')
  await store.reload(); expect(store.getSnapshot().uncertain).toBe(true)
  store.acknowledge(); expect(store.getSnapshot().uncertain).toBe(false)
})
it('keeps writes blocked after successful write but failed reload and recovers by GET', async () => {
  const { store, api } = setup(); await store.reload(); api.list.mockRejectedValue(new ApiError(500, 'HTTP_500'))
  await store.submit(OFF_ITEM.date); expect(store.getSnapshot().stale).toBe(true)
  store.acknowledge(); await store.submit(OFF_ITEM.date); expect(api.submit).toHaveBeenCalledOnce()
  api.list.mockResolvedValue(OFF_LIST); await store.reload(); expect(store.getSnapshot().stale).toBe(false)
})
it.each(['OFF_REQUEST_DUPLICATE', 'OFF_REQUEST_LIMIT_REACHED', 'OFF_REQUEST_STATE_CONFLICT', 'OFF_REQUEST_DEADLINE_PASSED'])('refreshes definitive conflict %s without uncertain retry', async code => {
  const { store, api } = setup(); await store.reload(); api.submit.mockRejectedValue(new ApiError(409, code)); await store.submit(OFF_ITEM.date)
  expect(store.getSnapshot().uncertain).toBe(false); expect(store.getSnapshot().notice).toBeTruthy(); expect(api.list).toHaveBeenCalledTimes(2)
})
it('serializes writes and prevents reload or month changes while writing', async () => {
  const { store, api } = setup(); await store.reload()
  let resolve!: () => void; api.submit.mockImplementation(() => new Promise<void>(r => { resolve = r }) as never)
  const first = store.submit(OFF_ITEM.date); await vi.waitFor(() => expect(api.submit).toHaveBeenCalledOnce())
  await store.submit(OFF_ITEM.date); await store.reload(); store.selectMonth('2031-04')
  expect(api.list).toHaveBeenCalledOnce(); expect(store.getSnapshot().month).toBe('2031-03')
  resolve(); await first; expect(store.getSnapshot().busy).toBe(false)
})
it('uses exact policy ETag, accepts zero, and refreshes a stale revision', async () => {
  const { store, api } = setup(true); await store.reload(); api.savePolicy.mockRejectedValue(new ApiError(412, 'STALE_POLICY_REVISION'))
  await store.savePolicy({ monthlyLimit: 0, deadlineDay: 28 })
  expect(api.savePolicy).toHaveBeenCalledWith({ monthlyLimit: 0, deadlineDay: 28 }, OFF_POLICY.etag)
  expect(store.getSnapshot().notice).toContain('정책이 변경'); expect(store.getSnapshot().uncertain).toBe(false)
  await store.savePolicy({ monthlyLimit: 1.5, deadlineDay: 28 }); expect(api.savePolicy).toHaveBeenCalledOnce()
})
it('drops late month responses and disposes pending write results', async () => {
  const { store, api } = setup(); let resolve!: (v: typeof OFF_LIST) => void
  api.list.mockImplementationOnce(() => new Promise(r => { resolve = r }))
  const old = store.reload(); await vi.waitFor(() => expect(api.list).toHaveBeenCalledOnce())
  store.selectMonth('2031-04'); await vi.waitFor(() => expect(store.getSnapshot().loading).toBe(false))
  resolve(OFF_LIST); await old; expect(store.getSnapshot().month).toBe('2031-04')
  let done!: () => void; api.submit.mockImplementationOnce(() => new Promise<void>(r => { done = r }) as never)
  const write = store.submit('2031-04-01'); await vi.waitFor(() => expect(api.submit).toHaveBeenCalledOnce())
  store.dispose(); done(); await write; expect(api.list).toHaveBeenCalledTimes(2)
})
it.each([new ApiError(403, 'FORBIDDEN'), new ApiError(404, 'WARD_NOT_FOUND'), new ApiError(401, 'SESSION_EXPIRED')])('erases protected data on access loss %#', async error => {
  const { store, api } = setup(true); await store.reload(); api.submit.mockRejectedValue(error)
  await store.submit(OFF_ITEM.date, OFF_ITEM.nurseId)
  expect(store.getSnapshot().data).toBeNull(); expect(store.getSnapshot().stale).toBe(true)
})
it('rejects a mixed policy/list snapshot rather than editing with conflicting limits', async () => {
  const { store, api } = setup(true)
  api.policy.mockResolvedValue({ ...OFF_POLICY, value: { ...OFF_POLICY.value, monthlyLimit: 3 } })
  await store.reload(); expect(store.getSnapshot().stale).toBe(true); expect(store.getSnapshot().data).toBeNull()
})
it('ignores responses across disposal and reactivation, including a write reload', async () => {
  const { store, api } = setup(); await store.reload()
  let resolveOld!: (value: typeof OFF_LIST) => void
  api.list.mockImplementationOnce(() => new Promise(r => { resolveOld = r }))
  const mutation = store.submit(OFF_ITEM.date)
  await vi.waitFor(() => expect(api.list).toHaveBeenCalledTimes(2))
  store.dispose(); store.activate()
  await store.reload()
  const fresh = store.getSnapshot()
  expect(fresh.busy).toBe(false); expect(fresh.uncertain).toBe(true)
  resolveOld(OFF_LIST); await mutation
  expect(store.getSnapshot()).toBe(fresh)
})

it.each([new Date(2031, 10, 30), new Date(2031, 11, 31)])('opens next month including the year boundary %s', now => {
  const { api, wards } = setup()
  const store = new OffRequestStore(OFF_ADMIN.wardId, api, wards, now)
  expect(store.getSnapshot().month).toBe(now.getMonth() === 11 ? '2032-01' : '2031-12')
})
it('retries a mixed policy snapshot once and displays the consistent result', async () => {
  const { store, api } = setup(true)
  api.list.mockResolvedValueOnce({ ...OFF_LIST, monthlyLimit: 3 })
  await store.reload()
  expect(api.list).toHaveBeenCalledTimes(2)
  expect(api.policy).toHaveBeenCalledTimes(2)
  expect(store.getSnapshot().stale).toBe(false)
  expect(store.getSnapshot().data?.list.monthlyLimit).toBe(4)
  expect(api.savePolicy).not.toHaveBeenCalled()
})
it('bounds snapshot retries and explains repeated policy changes', async () => {
  const { store, api } = setup(true)
  api.list.mockResolvedValue({ ...OFF_LIST, monthlyLimit: 3 })
  await store.reload()
  expect(api.list).toHaveBeenCalledTimes(2)
  expect(store.getSnapshot().data).toBeNull()
  expect(store.getSnapshot().notice).toContain('조회 중 정책이 다시 변경')
})
it('discards a late snapshot retry after another month is selected', async () => {
  const { store, api } = setup(true)
  let resolve!: (value: typeof OFF_LIST) => void
  api.list.mockResolvedValueOnce({ ...OFF_LIST, monthlyLimit: 3 }).mockImplementationOnce(() => new Promise(r => { resolve = r }))
  const old = store.reload()
  await vi.waitFor(() => expect(api.list).toHaveBeenCalledTimes(2))
  store.selectMonth('2031-04')
  await vi.waitFor(() => expect(store.getSnapshot().loading).toBe(false))
  const fresh = store.getSnapshot()
  resolve(OFF_LIST); await old
  expect(store.getSnapshot()).toBe(fresh)
})
it('preserves request inputs and filters on rejection and clears only inputs on success', async () => {
  const { store, api } = setup(true); await store.reload()
  const draft = { date: OFF_ITEM.date, nurseId: OFF_ITEM.nurseId, filterNurse: OFF_ITEM.nurseId, filterStatus: 'SUBMITTED', exceptions: true }
  store.updateDraft(draft)
  api.submit.mockRejectedValueOnce(new ApiError(409, 'OFF_REQUEST_DUPLICATE'))
  await store.submit(draft.date, draft.nurseId)
  expect(store.getSnapshot().draft).toEqual(draft)
  await store.submit(draft.date, draft.nurseId)
  expect(store.getSnapshot().draft).toEqual({ ...draft, date: '', nurseId: '' })
  await store.reload()
  expect(store.getSnapshot().draft.filterNurse).toBe(draft.nurseId)
})
it('clears drafts on month change, access loss and disposal', async () => {
  const { store, api } = setup(); await store.reload()
  store.updateDraft({ date: OFF_ITEM.date, filterStatus: 'SUBMITTED' })
  store.selectMonth('2031-04')
  expect(store.getSnapshot().draft.date).toBe('')
  await vi.waitFor(() => expect(store.getSnapshot().loading).toBe(false))
  store.updateDraft({ date: '2031-04-01' })
  api.list.mockRejectedValueOnce(new ApiError(403, 'FORBIDDEN')); await store.reload()
  expect(store.getSnapshot().draft.date).toBe('')
  store.updateDraft({ filterStatus: 'SUBMITTED' }); store.dispose()
  expect(store.getSnapshot().draft.filterStatus).toBe('')
})
it('clears admin drafts after failed reload and subsequent role change', async () => {
  const { store, api, wards } = setup(true); await store.reload()
  store.updateDraft({ nurseId: OFF_ITEM.nurseId, filterNurse: OFF_ITEM.nurseId })
  api.list.mockRejectedValueOnce(new ApiError(503, 'HTTP_503')); await store.reload()
  wards.getWards.mockResolvedValue({ items: [OFF_MEMBER] }); await store.reload()
  expect(store.getSnapshot().draft.nurseId).toBe('')
  expect(store.getSnapshot().draft.filterNurse).toBe('')
})
