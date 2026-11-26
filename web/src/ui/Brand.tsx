import mark from '../assets/nurseflow-mark.svg'
import compactMark from '../assets/nurseflow-mark-36.svg'

export function Brand({ compact = false }: { compact?: boolean }) {
  return <span className={`nf-brand${compact ? ' nf-brand-compact' : ''}`}>
    <img src={compact ? compactMark : mark} width={compact ? 36 : 40} height={compact ? 36 : 40} alt="" />
    {!compact && <span className="nf-brand-copy"><span className="nf-wordmark">Nurse<span>Flow</span></span><span className="nf-brand-caption">너스플로우 · 병동 근무표</span></span>}
  </span>
}
