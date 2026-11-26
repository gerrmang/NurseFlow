// Isolated acceptance fixture. Reuses the fictional B5a OIDC provider; no production keys or file DB.
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { randomBytes } from 'node:crypto';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const children = [];
let stopping = false;
async function stop() {
  if (stopping) return;
  stopping = true;
  await Promise.all(children.map(async child => {
    if (child.exitCode !== null || child.signalCode !== null) return;
    const ended = once(child, 'exit').catch(() => {});
    child.kill();
    await Promise.race([ended, delay(5_000, undefined, { ref: false })]);
    if (child.exitCode === null && child.signalCode === null) child.kill('SIGKILL');
  }));
}
process.on('SIGINT', () => { void stop(); });
process.on('SIGTERM', () => { void stop(); });
const env = Object.fromEntries(Object.entries(process.env).filter(([name]) => /^(PATH|SystemRoot|WINDIR|TEMP|TMP|HOME|USERPROFILE|LOCALAPPDATA|JAVA_HOME)$/i.test(name)));
try {
  for (const port of [18090, 18091]) {
    const probe = net.createServer();
    await new Promise((resolve, reject) => { probe.once('error', reject); probe.listen(port, '127.0.0.1', resolve); });
    await new Promise(resolve => probe.close(resolve));
  }
  function start(command, args, childEnv = env) {
    const child = spawn(command, args, { cwd: root, env: childEnv, windowsHide: true, stdio: 'ignore' });
    child.on('error', () => { void stop(); }); children.push(child); return child;
  }
  start(process.execPath, ['android/scripts/b5a-oidc-fixture.mjs']);
  const java = env.JAVA_HOME ? path.join(env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  const server = start(java, ['-jar', 'backend/build/libs/nurseflow-backend-0.1.0-SNAPSHOT.jar',
    '--spring.config.location=classpath:/application.yml', '--spring.profiles.active=',
    '--spring.datasource.url=jdbc:h2:mem:b8a_fictional;DB_CLOSE_DELAY=-1', '--spring.datasource.username=sa', '--spring.datasource.password=',
    '--server.port=18091', '--server.address=127.0.0.1',
    '--nurseflow.auth.kakao.issuer=http://127.0.0.1:18090', '--nurseflow.auth.kakao.jwk-set-uri=http://127.0.0.1:18090/jwks', '--nurseflow.auth.kakao.audiences=b5a-fixture',
    `--nurseflow.hospital.dataset.path=${path.join(root, 'scripts/demo/hospitals-fictional.csv')}`, '--nurseflow.hospital.dataset.bundled=false', '--nurseflow.hospital.dataset.min-rows=1',
    '--nurseflow.holiday.refresh-cron=-', '--logging.level.root=ERROR', '--spring.main.banner-mode=off'],
  { ...env, NURSEFLOW_SESSION_SECRET: randomBytes(48).toString('base64url') });
  let ready = false;
  for (let attempt = 0; attempt < 120 && !stopping; attempt++) {
    if (server.exitCode !== null || children.some(child => child.exitCode !== null)) break;
    try {
      const response = await fetch('http://127.0.0.1:18091/api/v1/wards', { signal: AbortSignal.timeout(1_000) });
      await response.body?.cancel();
      if (response.status === 401) { ready = true; break; }
    } catch { /* Startup only. Never log requests or tokens. */ }
    await delay(500);
  }
  if (!ready) throw new Error('Fixture startup failed');
  console.log('B8a isolated Spring/OIDC ready: loopback 18091/18090. Fictional identities, memory DB, no request logging.');
  await once(server, 'exit');
} catch { console.error('B8a fixture could not start. Check free ports, JDK 21 and bootJar.'); process.exitCode = 1; }
finally { await stop(); }
