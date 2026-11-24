// All local inputs use the roster's zone, never the browser's zone.
/** Compare valid API instants without losing submillisecond precision supported by Java Instant. */
export function compareInstants(a: string, b: string): number {
  const milliseconds = Date.parse(a) - Date.parse(b)
  if (milliseconds !== 0) return milliseconds
  const fraction = (value: string) => Number((/\.(\d+)(?:Z|[+-]\d{2}:\d{2})$/.exec(value)?.[1] ?? '').padEnd(9, '0'))
  return fraction(a) - fraction(b)
}
function fixed(zone: string): number | null {
  if (['UTC', 'GMT', 'UT', 'Z'].includes(zone)) return 0
  const m = /^(?:UTC|GMT|UT)?([+-])(\d{1,2})(?::?(\d{2}))?(?::?(\d{2}))?$/.exec(zone)
  return m ? (m[1] === '-' ? -1 : 1) * (Number(m[2]) * 3600 + Number(m[3] ?? 0) * 60 + Number(m[4] ?? 0)) * 1000 : null
}
export function localTime(instant: string, zone: string): string {
  const offset = fixed(zone)
  if (offset !== null) return new Date(Date.parse(instant) + offset).toISOString().slice(0, 16)
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(new Date(instant))
  const get = (type: string) => parts.find(p => p.type === type)!.value
  return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`
}
export function toInstant(local: string, zone: string): string {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(local)) throw new Error('날짜와 시각을 입력해 주세요.')
  const wall = Date.parse(`${local}:00Z`)
  if (!Number.isFinite(wall) || new Date(wall).toISOString().slice(0, 16) !== local) throw new Error('유효한 날짜와 시각을 입력해 주세요.')
  const offset = fixed(zone)
  if (offset !== null) return new Date(wall - offset).toISOString()
  // Obtain a nearby candidate, then detect skipped/repeated times at DST transitions.
  let candidate = wall
  for (let n = 0; n < 3; n++) candidate += wall - Date.parse(`${localTime(new Date(candidate).toISOString(), zone)}:00Z`)
  const matches = new Set<number>()
  for (let delta = -180; delta <= 180; delta += 15) {
    const value = candidate + delta * 60_000
    if (localTime(new Date(value).toISOString(), zone) === local) matches.add(value)
  }
  if (matches.size !== 1) throw new Error('병동 시간대에서 존재하지 않거나 중복되는 시각이에요. 다른 시각을 선택해 주세요.')
  return new Date([...matches][0]!).toISOString()
}
