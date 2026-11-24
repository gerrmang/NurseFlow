import test from 'node:test';
import assert from 'node:assert/strict';
import { createApi, localBase } from './api.mjs';

test('외부 서버·URL 인증정보·다른 경로를 거부한다', () => {
  for (const url of ['https://example.com', 'http://localhost:8080', 'http://127.0.0.1.evil.test',
    'http://user:secret@127.0.0.1', 'http://127.0.0.1/api', 'http://127.0.0.1?token=secret']) {
    assert.throws(() => localBase(url));
  }
  assert.equal(localBase('http://127.0.0.1:18096/'), 'http://127.0.0.1:18096');
});

test('응답 유실 시 쓰기를 재전송하지 않고 비밀값을 오류에 포함하지 않는다', async () => {
  let calls = 0;
  const api = createApi('http://127.0.0.1:18096', 'private-token', async () => {
    calls++;
    throw new Error('private-token request-body private-id-token');
  });
  await assert.rejects(api('POST', '/api/v1/wards', { token: 'private-id-token' }), error => {
    assert.ok(!error.message.includes('private-'));
    assert.match(error.message, /자동 재전송하지 않습니다/);
    return true;
  });
  assert.equal(calls, 1);
});

test('오류 본문을 노출하지 않고 401·403·409·412·500 상태를 전달한다', async () => {
  for (const status of [401, 403, 409, 412, 500]) {
    const api = createApi('http://127.0.0.1:18096', 'private-token', async () =>
      new Response('private-token private-details', { status }));
    await assert.rejects(api('GET', '/api/v1/wards'), error => {
      assert.match(error.message, new RegExp(`HTTP ${status}`));
      assert.ok(!error.message.includes('private-'));
      return true;
    });
  }
});

test('리다이렉트 거부·요청 제한시간을 설정하고 API 외 경로는 전송 전에 막는다', async () => {
  let calls = 0;
  const api = createApi('http://127.0.0.1:18096', 'private-token', async (_url, options) => {
    calls++;
    assert.equal(options.redirect, 'error');
    assert.ok(options.signal instanceof AbortSignal);
    return Response.json({ items: [] });
  });
  for (const path of ['//example.com', '/api/v1/../auth', '/api/v1/\\example.com']) {
    await assert.rejects(api('GET', path));
  }
  assert.equal(calls, 0);
  await api('GET', '/api/v1/wards');
  assert.equal(calls, 1);
});

test('비정상 JSON을 원문 노출 없이 처리한다', async () => {
  const api = createApi('http://127.0.0.1:18096', undefined, async () => new Response('private-token'));
  await assert.rejects(api('GET', '/api/v1/wards'), /응답 형식이 올바르지 않습니다/);
});
