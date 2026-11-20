// Local, fictional OIDC only. Never use this provider in deployment configuration.
import http from 'node:http';
import crypto from 'node:crypto';
const issuer = 'http://127.0.0.1:18090';
const { privateKey, publicKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'b5a-fictional', alg: 'RS256', use: 'sig' };
const json = (res, value) => { res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' }); res.end(JSON.stringify(value)); };
http.createServer((req, res) => {
  const url = new URL(req.url, issuer);
  if (url.pathname === '/jwks') return json(res, { keys: [jwk] });
  if (url.pathname === '/id-token' && /^(admin|member|other)-[a-f0-9-]+$/.test(url.searchParams.get('account') ?? '')) {
    const now = Math.floor(Date.now() / 1000);
    const header = Buffer.from(JSON.stringify({ alg: 'RS256', kid: jwk.kid })).toString('base64url');
    const body = Buffer.from(JSON.stringify({ iss: issuer, sub: url.searchParams.get('account'), aud: 'b5a-fixture',
      iat: now, exp: now + 300, nonce: 'fictional-nonce', nickname: '가상 B5a 사용자' })).toString('base64url');
    const input = `${header}.${body}`;
    return json(res, { idToken: `${input}.${crypto.sign('RSA-SHA256', Buffer.from(input), privateKey).toString('base64url')}` });
  }
  res.writeHead(404); res.end();
}).listen(18090, '127.0.0.1', () => console.log('B5a fictional OIDC ready on loopback; request/token logging disabled'));
