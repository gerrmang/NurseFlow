// 로컬 통합 검증 전용 OIDC 발급자. 운영·공유 환경에서 사용하지 않는다.
// - 127.0.0.1에만 바인딩하고, 실행할 때마다 새 RSA 키를 메모리에서 만든다(키·토큰을 파일에 남기지 않음).
// - 서버가 NURSEFLOW_OIDC_ISSUER_URI로 이 주소를 신뢰하도록 설정한 경우에만 의미가 있다. 서버의 JWT 검증 경로는 운영과 같다.
// 사용: node scripts/local-e2e/mock-oidc-issuer.mjs  (PORT 기본 18090, AUDIENCE 기본 nurseflow-api)
//      GET /token?sub=<합성 subject>&ttl=<초, 음수면 만료 토큰>
import http from 'node:http';
import crypto from 'node:crypto';

const port = Number(process.env.PORT || 18090);
const issuer = `http://127.0.0.1:${port}`;
const audience = process.env.AUDIENCE || 'nurseflow-api';
const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const kid = crypto.randomUUID();
const jwk = { ...publicKey.export({ format: 'jwk' }), kid, alg: 'RS256', use: 'sig' };
const b64 = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');

function sign(sub, ttlSeconds) {
  const now = Math.floor(Date.now() / 1000);
  const header = b64({ alg: 'RS256', typ: 'JWT', kid });
  const payload = b64({ iss: issuer, aud: audience, sub, iat: now, nbf: now - 5, exp: now + ttlSeconds });
  const signature = crypto.sign('RSA-SHA256', Buffer.from(`${header}.${payload}`), privateKey).toString('base64url');
  return `${header}.${payload}.${signature}`;
}

function json(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(body));
}

http.createServer((req, res) => {
  const url = new URL(req.url, issuer);
  if (url.pathname === '/.well-known/openid-configuration') {
    return json(res, 200, {
      issuer,
      jwks_uri: `${issuer}/jwks`,
      authorization_endpoint: `${issuer}/authorize`,
      response_types_supported: ['code'],
      subject_types_supported: ['public'],
      id_token_signing_alg_values_supported: ['RS256'],
    });
  }
  if (url.pathname === '/jwks') return json(res, 200, { keys: [jwk] });
  if (url.pathname === '/token') {
    const sub = url.searchParams.get('sub');
    if (!sub || !/^[A-Za-z0-9._-]{1,64}$/.test(sub)) return json(res, 400, { error: 'invalid_sub' });
    const ttl = Number(url.searchParams.get('ttl') ?? 600);
    return json(res, 200, { access_token: sign(sub, Number.isFinite(ttl) ? ttl : 600) });
  }
  return json(res, 404, { error: 'not_found' });
}).listen(port, '127.0.0.1', () => console.log(`mock issuer ready ${issuer}`));
