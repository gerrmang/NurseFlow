import { createHash } from 'node:crypto';
import { requireDemo } from './api.mjs';
import { hospitalId, hospitalName, wardName, teams, nurses, shifts } from './data.mjs';

const key = label => {
  const hex = createHash('sha256').update(`nurseflow-b6-v1:${label}`).digest('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-4${hex.slice(13, 16)}-a${hex.slice(17, 20)}-${hex.slice(20, 32)}`;
};
function unique(items, predicate) {
  const found = items.filter(predicate);
  requireDemo(found.length <= 1, '동일한 시연 이름이 여러 개입니다. 중복 상태를 먼저 확인하세요.');
  return found[0];
}
const matches = (actual, expected) => Object.entries(expected).every(([k, v]) => actual[k] === v);

/** Sequential writes only. A lost POST response is recovered by GET on the next run, never blind retry. */
export async function seed(api) {
  const get = async path => (await api('GET', `/api/v1${path}`)).data;
  const write = async (method, path, body, headers) => (await api(method, `/api/v1${path}`, body, headers)).data;
  const hospitals = await get('/hospitals/search?query=B6');
  const hospital = unique(hospitals.items, h => h.externalId === hospitalId && h.name === hospitalName && h.source === 'HIRA');
  requireDemo(hospital, 'B6 가상 병원 목록이 없습니다. 전용 시연 실행 명령을 사용하세요.');
  const wards = await get('/wards');
  let ward = unique(wards.items, w => w.wardName === wardName);
  if (!ward) ward = await write('POST', '/wards', {
    hospital: { source: hospital.source, externalId: hospital.externalId }, wardName, timeZone: 'Asia/Seoul',
  }, { 'Idempotency-Key': key('ward') });
  requireDemo(ward.hospitalName === hospitalName && ward.roles.includes('WARD_ADMIN'), '시연 병동 관리자 권한·병원을 확인할 수 없습니다.');
  const root = `/wards/${ward.wardId}`;
  const currentTeams = (await get(`${root}/teams`)).items;
  requireDemo(currentTeams.every(t => teams.includes(t.name)), '시연 이외의 팀이 있어 중단합니다.');
  for (const [index, name] of teams.entries()) {
    let team = unique(currentTeams, t => t.name === name);
    if (!team) {
      team = await write('POST', `${root}/teams`, { name, sortOrder: index + 1 });
      currentTeams.push(team);
    }
    requireDemo(team.sortOrder === index + 1, '기존 시연 팀 순서가 달라 중단합니다.');
  }
  const current = (await get(`${root}/staff?includeInactive=true`)).items;
  requireDemo(current.every(n => nurses.some(spec => spec.displayName === n.displayName) && !n.linkedAccount && n.active),
    '시연 이외의 간호사·계정 연결·비활성 기록이 있어 중단합니다.');
  const policy = await get(`${root}/career-policy`);
  requireDemo(policy.ranks.length > 0, '경력 직급 목록이 없습니다.');
  for (const spec of nurses) {
    let nurse = unique(current, n => n.displayName === spec.displayName);
    if (!nurse) {
      nurse = await write('POST', `${root}/staff`, { displayName: spec.displayName });
      current.push(nurse);
    }
    const path = `${root}/staff/${nurse.nurseId}`;
    const team = currentTeams.find(t => t.name === spec.team);
    if (nurse.team?.teamId !== team.teamId) nurse = await write('PUT', `${path}/team`, { teamId: team.teamId });
    if (nurse.profile.careerStartedOn !== spec.careerStartedOn || nurse.profile.rank !== policy.ranks[0]) {
      nurse = await write('PUT', `${path}/profile`, { careerStartedOn: spec.careerStartedOn, rank: policy.ranks[0] });
    }
    if (nurse.profile.status !== 'CONFIRMED') nurse = await write('POST', `${path}/profile/confirm`);
    if (nurse.stageOverride !== spec.stage || nurse.seniorEligible !== spec.seniorEligible) {
      await write('PUT', `${path}/roster-settings`, { seniorEligible: spec.seniorEligible, stageOverride: spec.stage });
    }
  }
  const staff = (await get(`${root}/staff`)).items;
  for (const team of currentTeams) {
    const ordered = nurses.filter(n => n.team === team.name).sort((a, b) => b.stage - a.stage)
      .map(n => staff.find(row => row.displayName === n.displayName));
    if (ordered.some((n, i) => n.teamOrder !== i + 1)) {
      await write('PUT', `${root}/teams/${team.teamId}/order`, { nurseIds: ordered.map(n => n.nurseId) });
    }
  }
  for (const spec of shifts) {
    // Every policy write changes ETag. Re-read before the next creation, including after a previous run.
    const snapshot = await api('GET', `/api/v1${root}/shift-types?includeInactive=true`);
    requireDemo(snapshot.data.items.every(s => shifts.some(v => v.code === s.code)), '시연 이외의 근무 유형이 있어 중단합니다.');
    const existing = unique(snapshot.data.items, s => s.code === spec.code);
    if (existing) {
      requireDemo(matches(existing, spec), '기존 근무 유형이 시연 설정과 다릅니다. 자동으로 덮어쓰지 않습니다.');
    } else {
      requireDemo(snapshot.etag, '정책 ETag가 없습니다.');
      await write('POST', `${root}/shift-types`, spec,
        { 'If-Match': snapshot.etag, 'Idempotency-Key': key(`${ward.wardId}:shift:${spec.code}:${snapshot.etag}`) });
    }
  }
  const eligibility = await api('GET', `/api/v1${root}/roster-eligibility`);
  if (!eligibility.data.demoMode) {
    requireDemo(eligibility.etag, '시연 모드 정책 ETag가 없습니다.');
    await write('PUT', `${root}/roster-eligibility`, { demoMode: true }, { 'If-Match': eligibility.etag });
  }
  await verify(api, ward.wardId);
  return { wardId: ward.wardId, teams: 2, nurses: 10, shifts: 3, demoMode: true };
}

export async function verify(api, wardId) {
  const get = async suffix => (await api('GET', `/api/v1/wards/${wardId}/${suffix}`)).data;
  const teamRows = (await get('teams')).items;
  const staff = (await get('staff?includeInactive=true')).items;
  const types = (await get('shift-types?includeInactive=true')).items;
  const eligibility = await get('roster-eligibility');
  requireDemo(teamRows.length === 2 && staff.length === 10 && types.length === 3, '시연 데이터 수량 검증 실패.');
  for (const [index, name] of teams.entries()) {
    const team = unique(teamRows, t => t.name === name);
    requireDemo(team?.sortOrder === index + 1, '팀 조회 검증 실패.');
    for (const spec of nurses.filter(n => n.team === name)) {
      const nurse = unique(staff, n => n.displayName === spec.displayName);
      requireDemo(nurse && nurse.active && !nurse.linkedAccount && nurse.team?.teamId === team.teamId &&
        nurse.rosterStage === spec.stage && nurse.stageOverride === spec.stage && nurse.teamOrder === 6 - spec.stage &&
        nurse.seniorEligible === spec.seniorEligible && nurse.profile.status === 'CONFIRMED' &&
        nurse.profile.careerStartedOn === spec.careerStartedOn && nurse.profile.hospitalJoinedOn === null,
      '간호사 경력·팀·순서·선임 조회 검증 실패.');
    }
  }
  requireDemo(shifts.every(spec => types.some(t => matches(t, spec))), '근무 유형 시간·휴게·환산 조회 검증 실패.');
  requireDemo(eligibility.demoMode && eligibility.items.length === 10 &&
    eligibility.items.every(n => n.eligible && n.reason === 'ELIGIBLE' && staff.some(s => s.nurseId === n.nurseId)),
  '서버 시연 모드·편성 대상 검증 실패.');
}
