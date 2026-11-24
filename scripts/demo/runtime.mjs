import http from 'node:http';
import net from 'node:net';
import { generateKeyPairSync, randomBytes, sign } from 'node:crypto';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { access } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { createApi, DemoError } from './api.mjs';

const directory = path.dirname(fileURLToPath(import.meta.url));
const audience = 'b6-fictional-only';
const nonce = 'b6-fictional-nonce';

async function listen(server, port) {
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', resolve);
  });
  return server.address().port;
}

/** Owns only the Java process and JWKS server it creates. No file DB or external endpoints. */
export async function startRuntime({ jar, port = 18096, javaHome = process.env.JAVA_HOME, signal }) {
  if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new DemoError('포트는 1024~65535 정수여야 합니다.');
  await access(jar).catch(() => { throw new DemoError('backend bootJar가 없습니다. run.ps1로 먼저 빌드하세요.'); });
  // Fail before launching Java when the requested port is already in use.
  const probe = net.createServer();
  try { await listen(probe, port); } catch { throw new DemoError('시연 서버 포트가 사용 중입니다. 다른 포트를 지정하세요.'); }
  await new Promise(resolve => probe.close(resolve));
  const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'b6-fictional', alg: 'RS256', use: 'sig' };
  const provider = http.createServer((req, res) => {
    if (req.method === 'GET' && req.url === '/jwks') {
      res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
      res.end(JSON.stringify({ keys: [jwk] }));
    } else { res.writeHead(404); res.end(); }
  });
  const providerPort = await listen(provider, 0);
  const issuer = `http://127.0.0.1:${providerPort}`;
  const baseUrl = `http://127.0.0.1:${port}`;
  // Do not inherit existing Spring/NurseFlow/profile/proxy/JVM options from the user's environment.
  const env = Object.fromEntries(Object.entries(process.env).filter(([name]) =>
    /^(PATH|SystemRoot|WINDIR|TEMP|TMP|HOME|USERPROFILE|LOCALAPPDATA|JAVA_HOME)$/i.test(name)));
  env.NURSEFLOW_SESSION_SECRET = randomBytes(48).toString('base64url');
  const java = javaHome ? path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  const child = spawn(java, ['-jar', path.resolve(jar),
    '--spring.config.location=classpath:/application.yml', '--spring.profiles.active=',
    '--spring.datasource.url=jdbc:h2:mem:b6_fictional;DB_CLOSE_DELAY=-1',
    '--spring.datasource.username=sa', '--spring.datasource.password=',
    `--server.port=${port}`, '--server.address=127.0.0.1',
    `--nurseflow.auth.kakao.issuer=${issuer}`, `--nurseflow.auth.kakao.jwk-set-uri=${issuer}/jwks`,
    `--nurseflow.auth.kakao.audiences=${audience}`,
    `--nurseflow.hospital.dataset.path=${path.join(directory, 'hospitals-fictional.csv')}`,
    '--nurseflow.hospital.dataset.bundled=false', '--nurseflow.hospital.dataset.min-rows=1',
    '--logging.level.root=ERROR', '--spring.main.banner-mode=off',
  ], { cwd: directory, env, windowsHide: true, stdio: ['ignore', 'ignore', 'ignore'] });
  let launchFailed = false;
  child.on('error', () => { launchFailed = true; });
  let stopped = false;
  async function stop() {
    if (stopped) return;
    stopped = true;
    if (child.exitCode === null && child.signalCode === null && !launchFailed) {
      const exited = once(child, 'exit');
      child.kill();
      const ended = await Promise.race([exited.then(() => true), delay(5_000, undefined, { ref: false }).then(() => false)]);
      if (!ended) { child.kill('SIGKILL'); await exited; }
    }
    provider.closeAllConnections();
    await new Promise(resolve => provider.close(resolve));
  }
  function idToken(subject) {
    const now = Math.floor(Date.now() / 1000);
    const header = Buffer.from(JSON.stringify({ alg: 'RS256', kid: jwk.kid })).toString('base64url');
    const payload = Buffer.from(JSON.stringify({ iss: issuer, sub: `b6-fictional-${subject}`, aud: audience,
      iat: now, exp: now + 300, nonce, nickname: 'B6 가상 사용자' })).toString('base64url');
    const input = `${header}.${payload}`;
    return `${input}.${sign('RSA-SHA256', Buffer.from(input), privateKey).toString('base64url')}`;
  }
  async function login(subject = 'admin') {
    const { data } = await createApi(baseUrl)('POST', '/api/v1/auth/kakao/session', { idToken: idToken(subject), nonce });
    return createApi(baseUrl, data.accessToken);
  }
  try {
    let ready = false;
    for (let attempt = 0; attempt < 180; attempt++) {
      if (signal?.aborted) throw new DemoError('시연 시작이 취소됐습니다.');
      if (launchFailed || child.exitCode !== null || child.signalCode !== null) {
        throw new DemoError('시연 Java 서버 시작 실패. JDK 21과 최신 bootJar를 확인하세요.');
      }
      // Poll a read-only unauthenticated endpoint; login only after Spring accepts connections.
      try {
        const response = await fetch(`${baseUrl}/api/v1/wards`, { signal: AbortSignal.timeout(1_000), redirect: 'error' });
        await response.body?.cancel();
        if (response.status === 401) { ready = true; break; }
      } catch { /* Still starting. */ }
      await delay(500);
    }
    if (!ready) throw new DemoError('시연 서버 시작 대기 시간이 초과됐습니다.');
    // Prove this server trusts our ephemeral key before any seed writes.
    await login();
    if (signal?.aborted) throw new DemoError('시연 시작이 취소됐습니다.');
    return { baseUrl, login, stop };
  } catch (error) { await stop(); throw error; }
}
