// Opt-in only. Existing b3b-browser-fixture.mjs + separate in-memory Spring on 18091 + Vite 5174.
// No production endpoints, real identities, or persisted credentials.
import crypto from 'node:crypto'
if (process.env.NURSEFLOW_B8B_BROWSER_FIXTURE !== '1') throw new Error('가상 브라우저 서버를 시작한 뒤 NURSEFLOW_B8B_BROWSER_FIXTURE=1로 실행하세요.')
const base = 'http://127.0.0.1:18091/api/v1'
const origin = 'http://localhost:5174'
const common = { 'Content-Type': 'application/json', Origin: origin, 'X-NurseFlow-Client': 'web' }
async function checked(response) {
  if (!response.ok) throw new Error(`가상 검증 준비 실패: HTTP ${response.status}`)
  return response.json()
}
async function login(account) {
  const start = await fetch(`${base}/auth/web/kakao/authorization`, { method: 'POST', headers: common, body: JSON.stringify({ redirectUri: `${origin}/auth/kakao/callback` }) })
  const { authorizationUrl } = await checked(start)
  if (!authorizationUrl.startsWith('http://localhost:18090/authorize?')) throw new Error('가상 OIDC 공급자가 아닙니다.')
  const page = await fetch(authorizationUrl)
  const attempt = (await page.text()).match(/name="attempt" value="([^"]+)"/)?.[1]
  if (!attempt) throw new Error('가상 OIDC 화면 없음')
  const approved = await fetch('http://127.0.0.1:18090/approve', { method: 'POST', redirect: 'manual', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ attempt, account }) })
  const callback = new URL(approved.headers.get('location'))
  const session = await checked(await fetch(`${base}/auth/web/kakao/code`, { method: 'POST', headers: { ...common, Cookie: start.headers.getSetCookie().map(cookie => cookie.split(';')[0]).join('; ') }, body: JSON.stringify({ code: callback.searchParams.get('code'), state: callback.searchParams.get('state') }) }))
  return async (path, body, method = 'GET') => checked(await fetch(base + path, { method, headers: { ...common, Authorization: `Bearer ${session.accessToken}`, 'Idempotency-Key': crypto.randomUUID() }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) }))
}
const admin = await login('admin'), nurse = await login('nurse')
const hospital = (await admin('/hospitals/search?query=가상')).items[0]
const ward = await admin('/wards', { hospital: { source: hospital.source, externalId: hospital.externalId }, wardName: 'B8b 가상 화면 검증', timeZone: 'Asia/Seoul' }, 'POST')
const root = `/wards/${ward.wardId}`
const invite = await admin(`${root}/invitations`, { role: 'WARD_MEMBER' }, 'POST')
await nurse('/invitations/redeem', { token: invite.token, hospitalJoinedOn: '2024-01' }, 'POST')
const linked = (await admin(`${root}/staff?includeInactive=false`)).items[0]
const today = new Date(Date.now() + 9 * 3600_000).toISOString().slice(0, 10)
await nurse(`${root}/off-requests`, { date: today }, 'POST')
await admin(`${root}/off-requests`, { date: `${today.slice(0, 7)}-${today.endsWith('-01') ? '02' : '01'}`, nurseId: linked.nurseId }, 'POST')
console.log(`가상 관리자·간호사 준비 완료: http://localhost:5174/wards/${ward.wardId}/off-requests`)
