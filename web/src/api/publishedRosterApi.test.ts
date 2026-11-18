import { createPublishedRosterApi } from './publishedRosterApi'
import { ApiError } from './http'

it('only queries the published endpoints and encodes ward IDs and month', async () => {
  const fetch = vi.fn().mockImplementation(async () => new Response('{}'))
  const api = createPublishedRosterApi(fetch)
  await api.mine('ward/a', '2026-11'); await api.ward('ward/a', '2026-12')
  expect(fetch.mock.calls.map(call => call[0])).toEqual(['/wards/ward%2Fa/published-rosters/mine?month=2026-11', '/wards/ward%2Fa/published-rosters?month=2026-12'])
})
it('preserves server error codes instead of treating every 404 as unpublished', async () => {
  const api = createPublishedRosterApi(async () => new Response(JSON.stringify({ code: 'WARD_NOT_FOUND' }), { status: 404 }))
  await expect(api.mine('a', '2026-11')).rejects.toEqual(new ApiError(404, 'WARD_NOT_FOUND'))
})
