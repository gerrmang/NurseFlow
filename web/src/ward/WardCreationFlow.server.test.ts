/// <reference types="node" />
// @vitest-environment node
// Opt-in only: requires the fictional OIDC fixture and an isolated in-memory backend.
import process from 'node:process'
import { createNurseFlowApi } from '../api/nurseflowApi'
import { ApiError } from '../api/http'
import type { WebSession } from '../api/types'
import { WardCreationFlow } from './WardCreationFlow'

it.skipIf(process.env.NURSEFLOW_B4B_E2E !== '1')('reconciles request and response loss against the real backend without duplicate wards', async () => {
  const base = 'http://127.0.0.1:18091/api/v1'
  const origin = 'http://localhost:5174'
  const headers = { 'Content-Type': 'application/json', Origin: origin, 'X-NurseFlow-Client': 'web' }
  const start = await fetch(`${base}/auth/web/kakao/authorization`, {
    method: 'POST', headers, body: JSON.stringify({ redirectUri: `${origin}/auth/kakao/callback` }),
  })
  expect(start.status).toBe(200)
  const { authorizationUrl } = await start.json() as { authorizationUrl: string }
  const login = await fetch(authorizationUrl)
  const attempt = (await login.text()).match(/name="attempt" value="([^"]+)"/)?.[1]
  expect(attempt).toBeTruthy()
  const approved = await fetch('http://127.0.0.1:18090/approve', {
    method: 'POST', redirect: 'manual', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ attempt: attempt!, account: 'admin2' }),
  })
  const callback = new URL(approved.headers.get('location')!)
  const exchanged = await fetch(`${base}/auth/web/kakao/code`, {
    method: 'POST', headers: { ...headers, Cookie: start.headers.getSetCookie().map((cookie) => cookie.split(';')[0]).join('; ') },
    body: JSON.stringify({ code: callback.searchParams.get('code'), state: callback.searchParams.get('state') }),
  })
  expect(exchanged.status).toBe(200)
  const session = await exchanged.json() as WebSession
  let loss: 'before' | 'after' | null = null
  const writes: { key: string | null; body: RequestInit['body'] }[] = []
  const api = createNurseFlowApi(async (path, init) => {
    const requestHeaders = new Headers(init?.headers)
    requestHeaders.set('Authorization', `Bearer ${session.accessToken}`)
    requestHeaders.set('Origin', origin)
    if (path === '/wards' && init?.method === 'POST') {
      writes.push({ key: requestHeaders.get('Idempotency-Key'), body: init.body })
      if (loss === 'before') { loss = null; throw new ApiError(0, 'NETWORK') }
    }
    const response = await fetch(base + path, { ...init, headers: requestHeaders })
    if (path === '/wards' && init?.method === 'POST' && loss === 'after') {
      loss = null
      expect(response.status).toBe(201)
      await response.arrayBuffer()
      throw new ApiError(0, 'NETWORK')
    }
    return response
  })
  const hospitals = await api.searchHospitals('365', 1)
  expect(hospitals.items.length).toBeGreaterThan(0)
  const before = (await api.getWards()).items.length
  for (const mode of ['before', 'after'] as const) {
    const flow = new WardCreationFlow(api)
    flow.select(hospitals.items[0]!)
    flow.setWardName(`B4b 가상 ${mode} ${crypto.randomUUID()}`)
    loss = mode
    const index = writes.length
    await flow.create()
    expect(flow.getSnapshot().uncertain).toBe(true)
    await flow.create()
    const ward = flow.getSnapshot().created
    expect(ward?.roles).toContain('WARD_ADMIN')
    expect(flow.getSnapshot().uncertain).toBe(false)
    expect(writes[index + 1]).toEqual(writes[index])
    const wards = (await api.getWards()).items
    expect(wards.filter((item) => item.wardId === ward?.wardId)).toHaveLength(1)
    const request = JSON.parse(writes[index]!.body as string)
    expect(await api.createWard(request, writes[index]!.key!)).toEqual(ward)
    await expect(api.createWard(request, crypto.randomUUID())).rejects.toMatchObject({ status: 409, code: 'WARD_ALREADY_CREATED' })
    await expect(api.createWard({ ...request, wardName: '충돌 검증' }, writes[index]!.key!)).rejects.toMatchObject({ status: 409, code: 'IDEMPOTENCY_KEY_REUSED' })
  }
  expect((await api.getWards()).items).toHaveLength(before + 2)
}, 30_000)
