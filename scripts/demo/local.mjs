import { parseArgs } from 'node:util';
import { createInterface } from 'node:readline';
import assert from 'node:assert/strict';
import { DemoError } from './api.mjs';
import { seed, verify } from './seed.mjs';
import { startRuntime } from './runtime.mjs';
import { shifts } from './data.mjs';

let runtime;
let shuttingDown = false;
const controller = new AbortController();
async function stop() {
  if (shuttingDown) return;
  shuttingDown = true;
  await runtime?.stop();
}
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => {
  controller.abort();
  if (runtime) void stop().then(() => process.exit(signal === 'SIGINT' ? 130 : 143));
});

try {
  const { values } = parseArgs({ options: {
    jar: { type: 'string' }, port: { type: 'string', default: '18096' }, verify: { type: 'boolean', default: false },
  } });
  if (!values.jar) throw new DemoError('--jar에 backend bootJar 경로를 지정하세요.');
  console.log('[가상 데이터 시연] 별도 메모리 DB·가상 OIDC 서버를 시작합니다.');
  runtime = await startRuntime({ jar: values.jar, port: Number(values.port), signal: controller.signal });
  if (shuttingDown) { await runtime.stop(); process.exit(130); }
  if (values.verify) {
    const api = await runtime.login();
    // Lose successful ward/team/staff/shift POST responses and resume through public GETs.
    for (const [suffix, occurrence] of [['/wards', 1], ['/teams', 1], ['/staff', 5], ['/shift-types', 1]]) {
      let creates = 0;
      await assert.rejects(seed(async (...args) => {
        const result = await api(...args);
        if (args[0] === 'POST' && args[1].endsWith(suffix) && ++creates === occurrence) throw new DemoError('검증용 응답 유실');
        return result;
      }), /검증용 응답 유실/);
    }
    const result = await seed(api);
    let writes = 0;
    const again = await seed(async (...args) => { if (args[0] !== 'GET') writes++; return api(...args); });
    assert.equal(again.wardId, result.wardId);
    assert.equal(writes, 0, '완료 후 재실행은 쓰기·중복 생성 없이 조회만 해야 합니다.');
    const root = `/api/v1/wards/${result.wardId}`;
    const before = await api('GET', `${root}/roster-eligibility`);
    await assert.rejects(api('PUT', `${root}/roster-eligibility`, { demoMode: false }, { 'If-Match': '"policy-999999"' }), /HTTP 412/);
    await api('PUT', `${root}/roster-eligibility`, { demoMode: false }, { 'If-Match': before.etag });
    const off = await api('GET', `${root}/roster-eligibility`);
    assert.equal(off.data.demoMode, false);
    assert.equal(off.data.items.length, 10);
    assert.ok(off.data.items.every(n => !n.eligible && n.reason === 'DEMO_MODE_DISABLED'));
    const outsider = await runtime.login('outsider');
    // The server intentionally hides wards from non-members with 404.
    await assert.rejects(outsider('GET', `${root}/staff`), /HTTP 404/);
    await seed(api);
    const types = await api('GET', `${root}/shift-types`);
    const day = types.data.items.find(s => s.code === 'D');
    await api('PUT', `${root}/shift-types/${day.shiftTypeId}`, { ...shifts[0], recognizedMinutes: 449 }, { 'If-Match': types.etag });
    await assert.rejects(seed(api), /기존 근무 유형이 시연 설정과 다릅니다/);
    const changed = await api('GET', `${root}/shift-types`);
    assert.equal(changed.data.items.find(s => s.code === 'D').recognizedMinutes, 449);
    await api('PUT', `${root}/shift-types/${day.shiftTypeId}`, shifts[0], { 'If-Match': changed.etag });
    await verify(api, result.wardId);
    console.log('[검증 성공] 4종 생성 응답 유실 복구·재실행 쓰기 0건·시연 모드 제외/복구·412·타 계정 404·기존 근무 설정 보존 확인.');
  } else {
    const show = async () => {
      try {
        const result = await seed(await runtime.login());
        process.exitCode = 0;
        console.log(`[시연 모드 ON / 가상 데이터] ${runtime.baseUrl} / 병동 ${result.wardId}`);
        console.log('A/B팀·간호사 10명(팀별 1~5단계)·D/E/N·편성 대상 10명 조회 검증 성공.');
      } catch (error) {
        process.exitCode = 1;
        console.error(error instanceof DemoError ? error.message : '가상 데이터 조회·생성 검증 실패.');
        console.error('메모리 DB는 유지됩니다. r로 상태를 다시 조회하거나 q로 종료하세요.');
      }
    };
    await show();
    console.log('r + Enter: 재실행 / Ctrl+C 또는 q + Enter: 종료·메모리 DB 폐기');
    console.log('실제 Kakao 로그인·Android/웹 시연 모드 표시는 별도 후속 범위입니다.');
    const input = createInterface({ input: process.stdin });
    // Sequential line handling prevents overlapping writes when r is pressed repeatedly.
    for await (const line of input) {
      if (line.trim() === 'q') { input.close(); break; }
      if (line.trim() === 'r') await show();
    }
  }
} catch (error) {
  console.error(error instanceof DemoError ? error.message : '시연 실행 또는 검증 실패. 사용법·JDK·빌드 상태를 확인하세요.');
  process.exitCode = 1;
} finally { await stop(); }
