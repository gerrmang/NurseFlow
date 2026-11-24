// Development-only OIDC fixture for a separate in-memory backend on 18091 and web on 5174.
// Never configure production with this provider. All identities/credentials are fictional;
// generated signing keys and authorization codes remain in memory. Seed once per fresh DB.
import http from 'node:http'
import crypto from 'node:crypto'
const issuer = 'http://localhost:18090';
const backend = 'http://127.0.0.1:18091/api/v1';
const origin = 'http://localhost:5174';
const client = 'b3b-fictional-client';
const secret = 'b3b-fictional-secret';
const { privateKey, publicKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'b3b-test', alg: 'RS256', use: 'sig' };
const codes = new Map(), attempts = new Map();
const json = (res, status, body) => { res.writeHead(status, { 'content-type': 'application/json', 'cache-control': 'no-store' }); res.end(JSON.stringify(body)); };
const codeFor = (account, params) => { const code = crypto.randomUUID(); codes.set(code, { account, nonce: params.get('nonce'), redirect: params.get('redirect_uri') }); return code; };
const jwt = pending => {
  const now = Math.floor(Date.now()/1000);
  const head = Buffer.from(JSON.stringify({ alg: 'RS256', kid: 'b3b-test' })).toString('base64url');
  const body = Buffer.from(JSON.stringify({ iss: issuer, sub: `b3b-${pending.account}`, aud: client, iat: now, exp: now+300, nonce: pending.nonce, nickname: `가상 ${pending.account}` })).toString('base64url');
  return `${head}.${body}.${crypto.sign('RSA-SHA256', Buffer.from(`${head}.${body}`), privateKey).toString('base64url')}`;
};
async function api(path, token, body, method = 'GET', extra = {}) {
  const response = await fetch(backend+path, { method, headers: { 'content-type': 'application/json', Origin: origin, 'X-NurseFlow-Client':'web', ...(token?{Authorization:`Bearer ${token}`} : {}), ...extra }, ...(body===undefined?{}:{body:JSON.stringify(body)}) });
  const payload = response.status===204?null:await response.json();
  if(!response.ok) throw new Error(`API ${path}: ${response.status} ${payload?.code||''}`);
  return { response, payload };
}
async function seed() {
  const started = await api('/auth/web/kakao/authorization', null, { redirectUri:origin+'/auth/kakao/callback' }, 'POST');
  const params = new URL(started.payload.authorizationUrl).searchParams;
  const code = codeFor('admin', params);
  const cookie = started.response.headers.getSetCookie().map(c=>c.split(';')[0]).join('; ');
  const session = await api('/auth/web/kakao/code', null, { code, state:params.get('state') }, 'POST', {Cookie:cookie});
  const token = session.payload.accessToken;
  const hospital = (await api('/hospitals/search?query=365',token)).payload.items[0];
  const ward = (await api('/wards',token,{hospital:{source:hospital.source,externalId:hospital.externalId},wardName:'B3b 가상 검증 병동'},'POST',{'Idempotency-Key':crypto.randomUUID()})).payload;
  await api(`/wards/${ward.wardId}/staff`,token,{displayName:'가상 연결 간호사',qualifications:[]},'POST');
  return { wardId:ward.wardId, seeded:true };
}
http.createServer(async(req,res)=>{
  try {
    const url=new URL(req.url,issuer);
    if(url.pathname==='/.well-known/jwks.json') return json(res,200,{keys:[jwk]});
    if(url.pathname==='/__seed'&&req.method==='POST') return json(res,200,await seed());
    if(url.pathname==='/authorize') {
      if(url.searchParams.get('client_id')!==client || url.searchParams.get('redirect_uri')!==origin+'/auth/kakao/callback') return json(res,400,{});
      const id=crypto.randomUUID();attempts.set(id,url.searchParams);
      res.writeHead(200,{'content-type':'text/html;charset=utf-8','cache-control':'no-store'});
      return res.end(`<h1>가상 OIDC 테스트 전용</h1><p>실제 Kakao 계정 인증이 아닙니다.</p><form method="post" action="/approve"><input type="hidden" name="attempt" value="${id}"><button name="account" value="admin">가상 관리자</button><button name="account" value="nurse">가상 간호사</button><button name="account" value="admin2">가상 추가 관리자</button></form>`);
    }
    const chunks=[];for await(const c of req)chunks.push(c);const form=new URLSearchParams(Buffer.concat(chunks).toString());
    if(url.pathname==='/approve'&&req.method==='POST') {
      const params=attempts.get(form.get('attempt'));attempts.delete(form.get('attempt'));
      const account=form.get('account');if(!params||!['admin','nurse','admin2'].includes(account))return json(res,400,{});
      const target=new URL(params.get('redirect_uri'));target.searchParams.set('code',codeFor(account,params));target.searchParams.set('state',params.get('state'));
      res.writeHead(302,{location:target.toString(),'cache-control':'no-store'});return res.end();
    }
    if(url.pathname==='/token'&&req.method==='POST') {
      const pending=codes.get(form.get('code'));codes.delete(form.get('code'));
      if(!pending||form.get('client_id')!==client||form.get('client_secret')!==secret||form.get('redirect_uri')!==pending.redirect)return json(res,400,{error:'invalid_grant'});
      return json(res,200,{access_token:'fictional-provider-access',token_type:'bearer',expires_in:300,id_token:jwt(pending)});
    }
    json(res,404,{});
  } catch(error) { json(res,500,{error:error.message}); }
}).listen(18090,'127.0.0.1',()=>console.log('B3b fictional OIDC provider ready on loopback; no request logging'));
