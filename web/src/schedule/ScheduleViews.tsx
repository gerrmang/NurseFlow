import { useId } from 'react'
import type { Shift } from '../api/scheduleSettingsApi'
import { ShiftChip } from '../ui/ShiftChip'

export function ShiftCatalog({ shifts, selected, onSelect }: { shifts: Shift[]; selected: string; onSelect: (id: string) => void }) {
  const descriptionId = useId()
  return <div className="settings-catalog">
    <ul>{shifts.map((shift, index) => <li key={shift.shiftTypeId}>
      <button type="button" className="settings-shift-row" aria-label={`${shift.displayName} 편집`} aria-describedby={`${descriptionId}-${index}`} aria-pressed={selected === shift.shiftTypeId} onClick={() => onSelect(shift.shiftTypeId)}>
        <ShiftChip code={shift.code} /><span className="settings-shift-copy"><strong>{shift.displayName}</strong><span id={`${descriptionId}-${index}`}>{shift.code} · {shift.startTime}~{shift.endTime}{shift.spansMidnight ? ' (다음 날)' : ''} · {shift.active ? '활성' : '비활성'}<br />휴게 {shift.breakMinutes} / 인정 {shift.recognizedMinutes} / 월 환산 {shift.targetCreditMinutes}분</span></span><span aria-hidden="true">›</span>
      </button>
    </li>)}</ul>
    {shifts.length === 0 && <p className="notice">등록된 근무 유형이 없어요.</p>}
    <button type="button" className="secondary" onClick={() => onSelect('')}>새 근무 유형 입력</button>
  </div>
}

export function DemandSummary({ groups }: { groups: { key: string; date: string; team: string; code: string; count: number; senior: number }[] }) {
  return <div className="settings-table-scroll" tabIndex={0} role="region" aria-label="현재 팀별 수요 표">
    <table><caption className="sr-only">초안에 저장된 날짜·팀·근무별 필요 인원</caption>
      <thead><tr>{['날짜', '팀', '근무', '필요 인원', '선임 필수'].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
      <tbody>{groups.map(group => <tr key={group.key}><td>{group.date}</td><td>{group.team}</td><td><ShiftChip code={group.code} /></td><td>{group.count}명</td><td>{group.senior}칸</td></tr>)}</tbody>
    </table>
    {groups.length === 0 && <p className="notice">저장된 수요가 없어요.</p>}
  </div>
}
