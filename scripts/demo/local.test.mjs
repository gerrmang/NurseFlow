import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Opt-in: builds are kept separate, and each child owns an isolated real Spring/H2 runtime.
const jar = process.env.NURSEFLOW_B6_TEST_JAR;
const local = fileURLToPath(new URL('./local.mjs', import.meta.url));

async function run({ failureAt, afterFailure, successThenRetry = false, port }) {
  const hook = `
    const original = globalThis.fetch;
    let searches = 0;
    globalThis.fetch = (url, options) => {
      if (String(url).includes('/hospitals/search') && ++searches === ${failureAt}) {
        return Promise.resolve(new Response('{}', { status: 503 }));
      }
      return original(url, options);
    };
  `;
  const child = spawn(process.execPath, ['--import', `data:text/javascript,${encodeURIComponent(hook)}`,
    local, '--jar', jar, '--port', String(port)], { env: process.env, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  let output = '', errors = '', handledFailure = false, retriedSuccess = false, ended = false;
  const timer = setTimeout(() => child.kill(), 60_000);
  child.stdin.on('error', () => {}); // A failed process may close stdin before a scripted command arrives.
  child.stdout.setEncoding('utf8');
  child.stderr.setEncoding('utf8');
  child.stdout.on('data', chunk => {
    output += chunk;
    const successes = (output.match(/조회 검증 성공/g) ?? []).length;
    if (successThenRetry && successes === 1 && !retriedSuccess) {
      retriedSuccess = true;
      child.stdin.write('r\n');
    } else if (!successThenRetry && handledFailure && successes === 1 && afterFailure === 'retry' && !ended) {
      ended = true;
      child.stdin.end('q\n');
    }
  });
  child.stderr.on('data', chunk => {
    errors += chunk;
    if (!handledFailure && errors.includes('HTTP 503')) {
      handledFailure = true;
      if (afterFailure === 'retry') child.stdin.write('r\n');
      else child.stdin.end(afterFailure === 'quit' ? 'q\n' : undefined);
    }
  });
  try {
    const code = await new Promise((resolve, reject) => {
      child.once('error', reject);
      child.once('exit', resolve);
    });
    assert.ok(handledFailure, '실제 CLI에서 주입한 HTTP 503 오류가 처리돼야 한다.');
    return { code, output };
  } finally { clearTimeout(timer); }
}

test('시드 실패 후 EOF로 종료하면 실패 코드가 전달된다', { skip: !jar }, async () => {
  const result = await run({ failureAt: 1, afterFailure: 'eof', port: 18106 });
  assert.equal(result.code, 1);
  assert.ok(!result.output.includes('조회 검증 성공'));
});
test('시드 실패 후 q로 종료하면 실패 코드가 전달된다', { skip: !jar }, async () => {
  const result = await run({ failureAt: 1, afterFailure: 'quit', port: 18107 });
  assert.equal(result.code, 1);
});
test('실패 뒤 r로 복구·검증을 마치면 성공 코드로 종료한다', { skip: !jar }, async () => {
  const result = await run({ failureAt: 1, afterFailure: 'retry', port: 18108 });
  assert.equal(result.code, 0);
  assert.equal((result.output.match(/조회 검증 성공/g) ?? []).length, 1);
});
test('최초 성공 뒤 r이 실패하면 마지막 실패 상태로 종료한다', { skip: !jar }, async () => {
  const result = await run({ failureAt: 2, afterFailure: 'quit', successThenRetry: true, port: 18109 });
  assert.equal(result.code, 1);
  assert.equal((result.output.match(/조회 검증 성공/g) ?? []).length, 1);
});
