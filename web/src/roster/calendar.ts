export function monthDates(month: string): (string | null)[] {
  // ISO date parsing avoids Date's special handling of numeric years below 100.
  const first = new Date(`${month}-01T00:00:00Z`)
  const count = new Date(first.getTime())
  count.setUTCMonth(count.getUTCMonth() + 1)
  count.setUTCDate(0)
  return [...Array<null>(first.getUTCDay()).fill(null), ...Array.from({ length: count.getUTCDate() }, (_, i) => `${month}-${String(i + 1).padStart(2, '0')}`)]
}

export function shiftTime(start: string, end: string, timeZone: string): string {
  const time = new Intl.DateTimeFormat('ko-KR', { timeZone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
  const day = new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' })
  const a = new Date(start), b = new Date(end)
  const parts = day.formatToParts(b)
  const endDate = ['year', 'month', 'day'].map(type => parts.find(part => part.type === type)!.value).join('-')
  return `${time.format(a)} → ${day.format(a) === day.format(b) ? '' : `${endDate} `}${time.format(b)}`
}
