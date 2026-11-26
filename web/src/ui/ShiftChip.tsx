/** Unknown/custom server codes keep their own label and a neutral treatment. */
export function ShiftChip({ code }: { code: string }) {
  const known = ['D', 'E', 'N', 'OF', 'SOF', 'VY', 'AM', 'PM'].includes(code)
  return <span className={`shift-chip shift-${known ? code.toLowerCase() : 'custom'}`}>{code}</span>
}
