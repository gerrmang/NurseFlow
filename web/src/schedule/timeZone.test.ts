import { compareInstants, localTime, toInstant } from './timeZone'

it('converts ward local time independently of browser zone and handles midnight', () => {
  expect(toInstant('2026-11-02T00:00', 'Asia/Seoul')).toBe('2026-11-01T15:00:00.000Z')
  expect(localTime('2026-11-01T15:00:00Z', 'Asia/Seoul')).toBe('2026-11-02T00:00')
  expect(toInstant('2026-11-02T00:00', '+09:00')).toBe('2026-11-01T15:00:00.000Z')
  expect(toInstant('2026-11-02T00:00', 'UTC')).toBe('2026-11-02T00:00:00.000Z')
})
it('rejects nonexistent and repeated DST times rather than moving approved absences', () => {
  expect(() => toInstant('2026-03-08T02:30', 'America/New_York')).toThrow('존재하지 않거나 중복')
  expect(() => toInstant('2026-11-01T01:30', 'America/New_York')).toThrow('존재하지 않거나 중복')
  expect(toInstant('2026-11-01T03:30', 'America/New_York')).toBe('2026-11-01T08:30:00.000Z')
})
it('rejects impossible dates and unsupported zones', () => {
  expect(() => toInstant('2026-02-30T10:00', 'Asia/Seoul')).toThrow()
  expect(() => toInstant('2026-11-02T10:00', 'Invalid/Zone')).toThrow()
})
it('orders instants by absolute time including fractional nanoseconds and mixed offsets', () => {
  expect(compareInstants('2026-11-05T09:00:30.250+09:00', '2026-11-05T00:00:30.250Z')).toBe(0)
  expect(compareInstants('2026-11-05T09:00:30.250+09:00', '2026-11-05T04:00:00Z')).toBeLessThan(0)
  expect(compareInstants('2026-11-05T00:00:30.123456788Z', '2026-11-05T00:00:30.123456789Z')).toBeLessThan(0)
  expect(compareInstants('2026-11-05T00:00:30Z', '2026-11-05T00:00:30.000000001Z')).toBeLessThan(0)
  expect(compareInstants('2026-11-05T00:00:30.123456789Z', '2026-11-05T09:00:30.123456788+09:00')).toBeGreaterThan(0)
})
