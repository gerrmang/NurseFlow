import test from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import { fileURLToPath } from 'node:url';
import { startRuntime } from './runtime.mjs';

test('사용 중인 포트를 빼앗거나 기존 서비스를 종료하지 않는다', async () => {
  const existing = net.createServer();
  await new Promise(resolve => existing.listen(0, '127.0.0.1', resolve));
  try {
    await assert.rejects(startRuntime({ jar: fileURLToPath(new URL('./runtime.mjs', import.meta.url)),
      port: existing.address().port }), /포트가 사용 중/);
    assert.ok(existing.listening);
  } finally { await new Promise(resolve => existing.close(resolve)); }
});

test('유효하지 않은 포트와 없는 bootJar는 서버 실행 전에 거부한다', async () => {
  for (const port of [0, 80, NaN, 65536]) {
    await assert.rejects(startRuntime({ jar: 'unused', port }), /포트는/);
  }
  await assert.rejects(startRuntime({ jar: fileURLToPath(new URL('./absent.jar', import.meta.url)) }), /bootJar가 없습니다/);
});
