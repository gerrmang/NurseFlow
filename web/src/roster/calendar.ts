export function monthDates(month: string): (string | null)[] {
  // ISO date parsing avoids Date's special handling of numeric years below 100.
  const first = new Date(`${month}-01T00:00:00Z`)
  const count = new Date(first.getTime())
  count.setUTCMonth(count.getUTCMonth() + 1)
  count.setUTCDate(0)
  return [...Array<null>(first.getUTCDay()).fill(null), ...Array.from({ length: count.getUTCDate() }, (_, i) => `${month}-${String(i + 1).padStart(2, '0')}`)]
}

function fixedOffsetSeconds(timeZone: string): number | null {
  if (/^(Z|UTC|GMT|UT)$/.test(timeZone)) return 0
  // Java ZoneId also accepts prefixed offsets and offsets with seconds.
  const offset = /^(?:UTC|GMT|UT)?([+-])(\d{1,2})(?::?(\d{2}))?(?::?(\d{2}))?$/.exec(timeZone)
  if (!offset) return null
  const seconds = Number(offset[2]) * 3600 + Number(offset[3] ?? 0) * 60 + Number(offset[4] ?? 0)
  return offset[1] === '-' ? -seconds : seconds
}

export function shiftTime(start: string, end: string, timeZone: string): string {
  const offset = fixedOffsetSeconds(timeZone)
  // Format shifted instants in UTC so this works without Intl offset-zone support.
  if (offset !== null) timeZone = 'UTC'
  const time = new Intl.DateTimeFormat('ko-KR', { timeZone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
  const day = new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' })
  const adjustment = (offset ?? 0) * 1000
  const a = new Date(Date.parse(start) + adjustment), b = new Date(Date.parse(end) + adjustment)
  const parts = day.formatToParts(b)
  const endDate = ['year', 'month', 'day'].map(type => parts.find(part => part.type === type)!.value).join('-')
  return `${time.format(a)} → ${day.format(a) === day.format(b) ? '' : `${endDate} `}${time.format(b)}`
}
