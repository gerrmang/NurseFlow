import { useId } from 'react'
import type { OffRequest } from '../api/offRequestApi'
import { monthDates } from '../roster/calendar'

const statusLabel = { SUBMITTED: '신청', CONFIRMED: '확정', REJECTED: '반려', WITHDRAWN: '철회' }

export function OffCalendar({ month, items, selected, disabled, onSelect }: { month: string; items: OffRequest[]; selected: string; disabled: boolean; onSelect: (date: string) => void }) {
  const descriptionId = useId()
  return <div className="off-calendar-wrap">
    <div className="off-calendar" aria-label={`${month} 희망 날짜 선택`}>
      {['일', '월', '화', '수', '목', '금', '토'].map(day => <span className="off-weekday" key={day}>{day}</span>)}
      {monthDates(month).map((date, index) => {
        if (!date) return <span key={`blank-${index}`} />
        const requests = items.filter(item => item.date === date)
        return <button type="button" className="off-calendar-day" key={date} disabled={disabled} aria-label={`${date} 선택`} aria-describedby={`${descriptionId}-${date}`} aria-pressed={selected === date} onClick={() => onSelect(date)}>
          <span>{Number(date.slice(-2))}</span>
          <span id={`${descriptionId}-${date}`} className="off-date-status">{requests.length === 0 && <span className="sr-only">등록된 요청 없음</span>}{requests.map(item => <span key={item.requestId} className={`off-dot off-status-${item.status.toLowerCase()}`} title={statusLabel[item.status]}>{statusLabel[item.status]}</span>)}</span>
        </button>
      })}
    </div>
    <p className="notice">날짜를 선택한 뒤 신청 버튼을 눌러 주세요.</p>
  </div>
}

export function OffRequestGrid({ month, items, name }: { month: string; items: OffRequest[]; name: (id: string) => string }) {
  const dates = monthDates(month).filter(date => date !== null)
  const nurses = new Map<string, Map<string, OffRequest[]>>()
  for (const item of items) {
    const days = nurses.get(item.nurseId) ?? new Map<string, OffRequest[]>()
    days.set(item.date, [...(days.get(item.date) ?? []), item])
    nurses.set(item.nurseId, days)
  }
  return <div className="off-grid-scroll" tabIndex={0} role="region" aria-label="병동 희망 오프 월간 표">
    <table className="off-grid">
      <caption className="sr-only">날짜별 희망 오프. 요청을 선택하면 아래 처리 카드로 이동합니다.</caption>
      <thead><tr><th scope="col">간호사</th>{dates.map(date => <th scope="col" key={date}>{Number(date.slice(-2))}</th>)}</tr></thead>
      <tbody>{[...nurses].map(([id, days]) => <tr key={id}><th scope="row">{name(id)}</th>{dates.map(date => <td key={date}>
        {days.get(date)?.map(item => <a key={item.requestId} className={`off-grid-request off-status-${item.status.toLowerCase()}`} href={`#off-request-${item.requestId}`} aria-label={`${name(id)} ${date} ${statusLabel[item.status]} 상세`}>{statusLabel[item.status]}</a>)}
      </td>)}</tr>)}</tbody>
    </table>
  </div>
}
