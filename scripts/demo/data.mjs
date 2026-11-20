// Fictional only. The hospital CSV is a local importer fixture, never an official dataset.
export const hospitalId = 'B6-FICTIONAL-HOSPITAL';
export const hospitalName = 'B6 가상병원';
export const wardName = 'B6 가상 시연 병동';
export const teams = ['가상 A팀', '가상 B팀'];
export const nurses = teams.flatMap((team, index) => [1, 2, 3, 4, 5].map(stage => ({
  displayName: `가상 간호사 ${index === 0 ? 'A' : 'B'}${stage}`,
  team, stage, seniorEligible: stage >= 4,
  // Dates are fixed fictional history; explicit stageOverride keeps the demo stable over time.
  careerStartedOn: `${2025 - stage * 2}-01`,
})));
export const shifts = [
  { code: 'D', displayName: '가상 Day', kind: 'DAY', startTime: '07:00', endTime: '15:00', spansMidnight: false },
  { code: 'E', displayName: '가상 Evening', kind: 'EVENING', startTime: '15:00', endTime: '23:00', spansMidnight: false },
  { code: 'N', displayName: '가상 Night', kind: 'NIGHT', startTime: '23:00', endTime: '07:00', spansMidnight: true },
].map(shift => ({ ...shift, breakMinutes: 30, recognizedMinutes: 450, targetCreditMinutes: 450, active: true }));
