import { act, cleanup, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '../api/http'
import { LOGIN_RETURN_KEY } from '../invite/pendingInvite'
import { FakeAuthServer, USER_A, USER_B } from '../test/fakeAuthServer'
import { FakeInvitationBackend, WARD_1 } from '../test/fakeInvitationBackend'
import { FakeStorage } from '../test/fakeStorage'
import { setupApp } from '../test/harness'

const location = () => screen.getByTestId('location').textContent

function world() {
  const server = new FakeAuthServer()
  const backend = new FakeInvitationBackend(server)
  const storage = new FakeStorage()
  return { server, backend, storage }
}

/** Opens an invitation link as a signed-in user (member of nothing yet). */
function openSignedIn(w: ReturnType<typeof world>, token: string, user = USER_A, strict = true) {
  w.server.signInAs(user)
  return setupApp(`/invite#token=${token}`, { ...w, strict })
}

const joinButton = () => screen.findByRole('button', { name: '가입하기' })

describe('invitation link intake (StrictMode)', () => {
  it('registers the invitation once, removes the token from the address and shows the preview', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_MEMBER' })
    const app = openSignedIn(w, token)

    expect(await screen.findByText(WARD_1.wardName)).toBeInTheDocument()
    expect(screen.getByText(WARD_1.hospitalName)).toBeInTheDocument()
    expect(screen.getByText('간호사')).toBeInTheDocument()
    expect(screen.getByLabelText(/병원 입사 연월/)).toBeInTheDocument()
    expect(location()).toBe('/invite')
    // StrictMode ran initializers and effects twice, yet there is exactly one invitation flow.
    expect(app.store.getInvite()?.seq).toBe(1)
    // The token is never rendered or kept in the address.
    expect(document.body.textContent).not.toContain(token)
    expect(location()).not.toContain(token)
    // Preview carries the token in the body only, never in the URL.
    const preview = app.backend.calls.filter((call) => call.op === 'preview')
    expect(preview.length).toBeGreaterThan(0)
    for (const call of preview) {
      expect(call.path).toBe('/invitations/preview')
      expect(call.body).toEqual({ token })
    }
  })

  it('does not lose or duplicate the invitation without StrictMode either', async () => {
    const w = world()
    const token = w.backend.seedInvitation({})
    const app = openSignedIn(w, token, USER_A, false)
    expect(await screen.findByText(WARD_1.wardName)).toBeInTheDocument()
    expect(app.store.getInvite()?.seq).toBe(1)
    expect(app.backend.count('preview')).toBe(1)
  })

  it('shows a link error for a malformed fragment and removes it from the address', async () => {
    const w = world()
    w.server.signInAs(USER_A)
    setupApp('/invite#token=not-a-token', { ...w })
    expect(await screen.findByText('올바른 초대 링크가 아니에요. 받은 링크를 다시 확인해 주세요.')).toBeInTheDocument()
    expect(location()).toBe('/invite')
    expect(w.backend.count('preview')).toBe(0)
  })
})

describe('joining', () => {
  it('WARD_MEMBER: sends the hospital join month, then shows the refreshed ward list', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_MEMBER' })
    const app = openSignedIn(w, token)
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText(/병원 입사 연월/), '2021-03')
    await user.click(await joinButton())

    expect(await screen.findByText(`${WARD_1.wardName} 병동에 가입했어요.`)).toBeInTheDocument()
    expect(location()).toBe('/wards')
    expect(app.backend.calls.find((call) => call.op === 'redeem')?.body).toEqual({ token, hospitalJoinedOn: '2021-03' })
    // The ward list was read again after joining.
    expect(await screen.findByRole('list', { name: '소속 병동' })).toHaveTextContent(WARD_1.wardName)
    expect(app.store.getInvite()).toBeNull()
  })

  it('WARD_ADMIN: no month input and no hospitalJoinedOn in the request', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const app = openSignedIn(w, token)
    const user = userEvent.setup()
    await screen.findByText('관리자 초대는 입사 연월 입력 없이 가입해요.')
    expect(screen.queryByLabelText(/병원 입사 연월/)).not.toBeInTheDocument()
    await user.click(await joinButton())
    await screen.findByText(`${WARD_1.wardName} 병동에 가입했어요.`)
    const body = app.backend.calls.find((call) => call.op === 'redeem')?.body as Record<string, unknown>
    expect(body).toEqual({ token })
    expect('hospitalJoinedOn' in body).toBe(false)
  })

  it.each([
    ['', '병원 입사 연월을 입력해 주세요.'],
    ['1949-12', '병원 입사 연월이 올바르지 않아요. 미래 연월은 입력할 수 없어요.'],
    ['2021-13', '병원 입사 연월이 올바르지 않아요. 미래 연월은 입력할 수 없어요.'],
  ])('checks the join month "%s" before sending', async (value, message) => {
    const w = world()
    const token = w.backend.seedInvitation({})
    const app = openSignedIn(w, token)
    const user = userEvent.setup()
    const input = await screen.findByLabelText(/병원 입사 연월/)
    if (value) await user.type(input, value)
    await user.click(await joinButton())
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(app.backend.count('redeem')).toBe(0)
  })

  it('shows the server judgement for a join month the client accepted', async () => {
    const w = world()
    const token = w.backend.seedInvitation({})
    const app = openSignedIn(w, token)
    app.backend.failWith('redeem', 400, 'INVALID_HOSPITAL_JOIN_MONTH')
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText(/병원 입사 연월/), '2021-03')
    await user.click(await joinButton())
    expect(await screen.findByRole('alert')).toHaveTextContent('병원 입사 연월이 올바르지 않아요')
    // The form stays usable for a correction.
    expect(screen.getByRole('button', { name: '가입하기' })).toBeEnabled()
  })

  it('sends one request even when two clicks arrive before the screen re-renders', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const app = openSignedIn(w, token)
    const button = await joinButton()
    const release = app.backend.hold('redeem')
    act(() => {
      button.click()
      button.click()
    })
    await waitFor(() => expect(app.backend.count('redeem')).toBe(1))
    release()
    await screen.findByText(`${WARD_1.wardName} 병동에 가입했어요.`)
    expect(app.backend.count('redeem')).toBe(1)
  })

  it('sends one request for a double click', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const app = openSignedIn(w, token)
    const release = app.backend.hold('redeem')
    const user = userEvent.setup()
    await user.dblClick(await joinButton())
    await waitFor(() => expect(app.backend.count('redeem')).toBe(1))
    release()
    await screen.findByText(`${WARD_1.wardName} 병동에 가입했어요.`)
    expect(app.backend.count('redeem')).toBe(1)
  })
})

describe('invitation errors are told apart by server code', () => {
  it.each([
    ['expired', { expiresInMs: -1000 }, '만료된 초대예요. 관리자에게 새 초대를 요청해 주세요.'],
    ['revoked', { revoked: true }, '철회된 초대예요. 관리자에게 문의해 주세요.'],
    ['already used', { redeemed: true }, '이미 사용된 초대예요. 초대는 한 번만 사용할 수 있어요.'],
  ])('%s invitation at preview: closing is the only action', async (_name, seed, message) => {
    const w = world()
    const token = w.backend.seedInvitation(seed)
    const app = openSignedIn(w, token)
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: '닫기' }))
    expect(location()).toBe('/wards')
    expect(app.store.getInvite()).toBeNull()
  })

  it('unknown token', async () => {
    const w = world()
    w.backend.seedInvitation({})
    openSignedIn(w, 'T' + '9'.repeat(42))
    expect(await screen.findByRole('alert')).toHaveTextContent('초대를 찾을 수 없어요. 링크를 다시 확인해 주세요.')
  })

  it('already a member: explains the invitation was not used', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    w.backend.addMember(WARD_1.wardId, USER_A.userId, ['WARD_MEMBER'])
    openSignedIn(w, token)
    const user = userEvent.setup()
    await user.click(await joinButton())
    expect(await screen.findByRole('alert')).toHaveTextContent('기존 소속 또는 연결된 간호사 기록이 있어 가입할 수 없어요. 이 초대는 사용되지 않았어요. 병동 목록에 없다면 관리자에게 소속 상태를 확인해 주세요.')
    await user.click(screen.getByRole('button', { name: '내 병동으로' }))
    expect(location()).toBe('/wards')
  })

  it('nurse record already linked / admin conflict show their own messages', async () => {
    for (const [code, message] of [
      ['NURSE_ALREADY_LINKED', '이미 다른 계정과 연결된 간호사 기록이에요'],
      ['ADMIN_NURSE_RECORD_CONFLICT', '간호사 기록이 있는 계정은 관리자 초대를 수락할 수 없어요'],
    ] as const) {
      const w = world()
      const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
      const app = openSignedIn(w, token)
      app.backend.failWith('redeem', 409, code)
      await userEvent.setup().click(await joinButton())
      expect(await screen.findByRole('alert')).toHaveTextContent(message)
      cleanup()
    }
  })

  it('never shows an unknown server code or detail to the user', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const app = openSignedIn(w, token)
    app.backend.failWith('redeem', 400, 'SOME_NEW_INTERNAL_CODE')
    await userEvent.setup().click(await joinButton())
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('요청을 처리하지 못했어요')
    expect(alert.textContent).not.toContain('SOME_NEW_INTERNAL_CODE')
  })

  it('preview network failure is retryable', async () => {
    const w = world()
    const token = w.backend.seedInvitation({})
    w.server.signInAs(USER_A)
    setupApp(`/invite#token=${token}`, { ...w, strict: false, configure: ({ backend }) => backend.failBefore('preview') })
    expect(await screen.findByRole('alert')).toHaveTextContent('서버에 연결할 수 없어요')
    await userEvent.setup().click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByText(WARD_1.wardName)).toBeInTheDocument()
    expect(w.backend.count('preview')).toBe(2)
  })
})

describe('lost responses after a join request', () => {
  it('(a) processed on the server, response lost: no resend, result check finds the ward', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const app = openSignedIn(w, token)
    app.backend.loseResponseAfter('redeem')
    const user = userEvent.setup()
    await user.click(await joinButton())
    expect(await screen.findByRole('alert')).toHaveTextContent('가입 요청 결과를 확인하지 못했어요')
    expect(app.backend.count('redeem')).toBe(1)
    expect(screen.queryByRole('button', { name: '가입하기' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '결과 확인' }))
    expect(await screen.findByText(`${WARD_1.wardName} 병동에 가입했어요.`)).toBeInTheDocument()
    expect(app.backend.count('redeem')).toBe(1)
  })

  it('(b) failed before processing: result check re-previews, then a manual retry is offered', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const app = openSignedIn(w, token)
    app.backend.failBefore('redeem')
    const user = userEvent.setup()
    await user.click(await joinButton())
    await screen.findByRole('button', { name: '결과 확인' })
    expect(app.backend.count('redeem')).toBe(1)

    await user.click(screen.getByRole('button', { name: '결과 확인' }))
    expect(await screen.findByText('가입이 확인되지 않았어요. 초대가 아직 유효하니 다시 가입할 수 있어요.')).toBeInTheDocument()
    expect(screen.queryByText(/가입이 완료되지 않았어요/)).not.toBeInTheDocument()
    expect(app.backend.count('redeem')).toBe(1)

    await user.click(screen.getByRole('button', { name: '가입하기' }))
    expect(await screen.findByText(`${WARD_1.wardName} 병동에 가입했어요.`)).toBeInTheDocument()
    expect(app.backend.count('redeem')).toBe(2)
  })

  it('(c) the result check itself fails: stays unconfirmed, neither joined nor not-joined is claimed', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const app = openSignedIn(w, token)
    app.backend.loseResponseAfter('redeem')
    const user = userEvent.setup()
    await user.click(await joinButton())
    await screen.findByRole('button', { name: '결과 확인' })

    app.backend.failBefore('wards')
    await user.click(screen.getByRole('button', { name: '결과 확인' }))
    expect(await screen.findByText(/결과를 아직 확인하지 못했어요/)).toBeInTheDocument()
    expect(screen.queryByText(/병동에 가입했어요/)).not.toBeInTheDocument()
    expect(screen.queryByText(/다시 가입할 수 있어요/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '가입하기' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '결과 확인' }))
    expect(await screen.findByText(`${WARD_1.wardName} 병동에 가입했어요.`)).toBeInTheDocument()
    expect(app.backend.count('redeem')).toBe(1)
  })

  it('(c) ward missing and the preview lookup fails: stays unconfirmed', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const app = openSignedIn(w, token)
    app.backend.failBefore('redeem')
    const user = userEvent.setup()
    await user.click(await joinButton())
    await screen.findByRole('button', { name: '결과 확인' })

    app.backend.failBefore('preview')
    await user.click(screen.getByRole('button', { name: '결과 확인' }))
    expect(await screen.findByText(/결과를 아직 확인하지 못했어요/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '가입하기' })).not.toBeInTheDocument()
  })

  it('redeem 409 "already used" while the ward is missing from my list: conflict, stays unconfirmed', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const app = openSignedIn(w, token)
    const user = userEvent.setup()
    const button = await joinButton()
    // Someone else used the link between preview and join.
    app.backend.invitations[0]!.redeemedAt = Date.now()
    await user.click(button)
    expect(await screen.findByText(/사용된 것으로 보이지만 내 병동 목록에서는 확인되지 않았어요/)).toBeInTheDocument()
    expect(screen.queryByText(/병동에 가입했어요/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '결과 확인' })).toBeEnabled()
  })

  it('redeem 409 "already used" and the ward is in my list: counts as joined', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const app = openSignedIn(w, token)
    const button = await joinButton()
    // Joined earlier (e.g. the first response was lost and the link was reused by a retry).
    app.backend.invitations[0]!.redeemedAt = Date.now()
    app.backend.addMember(WARD_1.wardId, USER_A.userId, ['WARD_ADMIN'])
    await userEvent.setup().click(button)
    expect(await screen.findByText(`${WARD_1.wardName} 병동에 가입했어요.`)).toBeInTheDocument()
  })
})

describe('session and account changes', () => {
  it('a preview answered after logout is discarded and the invitation is dropped', async () => {
    const w = world()
    const token = w.backend.seedInvitation({})
    w.server.signInAs(USER_A)
    const release = w.backend.hold('preview')
    const app = setupApp(`/invite#token=${token}`, { ...w, strict: false })
    await waitFor(() => expect(w.backend.count('preview')).toBe(1))
    await act(async () => {
      await app.manager.logout()
    })
    release()
    await act(async () => {
      await Promise.resolve()
    })
    expect(screen.queryByText(WARD_1.wardName)).not.toBeInTheDocument()
    expect(app.store.getInvite()).toBeNull()
  })

  it('switching to another account drops the first account\'s invitation and never joins it', async () => {
    const w = world()
    const token = w.backend.seedInvitation({})
    const app = openSignedIn(w, token)
    await screen.findByText(WARD_1.wardName)
    w.server.registerCode('code-b', USER_B)
    await act(async () => {
      await app.manager.completeLogin('code-b', 'state-b')
    })
    await waitFor(() => expect(app.store.getInvite()).toBeNull())
    expect(await screen.findByText('새로고침이나 로그인 화면에서 돌아온 뒤 초대가 보이지 않으면, 받은 초대 링크를 다시 열거나 아래에 붙여넣어 주세요.')).toBeInTheDocument()
    expect(w.backend.count('redeem')).toBe(0)
  })

  it('logout drops the invitation and a later login does not bring it back', async () => {
    const w = world()
    const token = w.backend.seedInvitation({})
    const app = openSignedIn(w, token)
    await screen.findByText(WARD_1.wardName)
    await act(async () => {
      await app.manager.logout()
    })
    expect(app.store.getInvite()).toBeNull()
    expect(w.storage.items.size).toBe(0)
  })
})

describe('signed out: login round trip', () => {
  it('shows no ward details before login (preview needs a session) and saves only on the login button', async () => {
    const w = world()
    const token = w.backend.seedInvitation({})
    const app = setupApp(`/invite#token=${token}`, { ...w })
    expect(await screen.findByRole('button', { name: '카카오로 로그인' })).toBeInTheDocument()
    expect(w.backend.count('preview')).toBe(0)
    expect(w.storage.items.size).toBe(0)
    expect(location()).toBe('/invite')

    await userEvent.setup().click(screen.getByRole('button', { name: '카카오로 로그인' }))
    await waitFor(() => expect(app.redirect).toHaveBeenCalledTimes(1))
    expect(w.storage.items.size).toBe(1)
    const saved = JSON.parse(w.storage.items.get(LOGIN_RETURN_KEY)!) as Record<string, unknown>
    expect(Object.keys(saved).sort()).toEqual(['expiresAt', 'token', 'v'])
    expect(saved.token).toBe(token)
  })

  it('when saving fails, warns first and logs in only on explicit confirmation', async () => {
    const w = world()
    const token = w.backend.seedInvitation({})
    w.storage.failSet = true
    const app = setupApp(`/invite#token=${token}`, { ...w })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: '카카오로 로그인' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('로그인 중에 초대를 기억해 둘 수 없어요')
    expect(app.redirect).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: '그래도 카카오로 로그인' }))
    await waitFor(() => expect(app.redirect).toHaveBeenCalledTimes(1))
    expect(w.storage.items.size).toBe(0)
  })

  it('cancel after a failed login start drops the invitation and the saved entry', async () => {
    const w = world()
    const token = w.backend.seedInvitation({})
    w.server.failNext('authorize', new ApiError(503, 'IDENTITY_PROVIDER_NOT_CONFIGURED'))
    const app = setupApp(`/invite#token=${token}`, { ...w })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: '카카오로 로그인' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('웹 로그인 설정이 아직 완료되지 않았어요')
    expect(w.storage.items.size).toBe(1)
    await user.click(screen.getByRole('button', { name: '초대 취소' }))
    expect(app.store.getInvite()).toBeNull()
    expect(w.storage.items.size).toBe(0)
    expect(await screen.findByText('새로고침이나 로그인 화면에서 돌아온 뒤 초대가 보이지 않으면, 받은 초대 링크를 다시 열거나 아래에 붙여넣어 주세요.')).toBeInTheDocument()
  })

  it('a stale saved entry is swept when the app starts anywhere but the callback', async () => {
    const w = world()
    w.storage.items.set(LOGIN_RETURN_KEY, 'leftover')
    const app = setupApp('/login', { ...w })
    await screen.findByRole('button', { name: '카카오로 로그인' })
    app.store.sweepStaleEntry()
    expect(w.storage.items.size).toBe(0)
  })

  async function roundTrip(w: ReturnType<typeof world>, token: string) {
    const first = setupApp(`/invite#token=${token}`, { ...w })
    await userEvent.setup().click(await screen.findByRole('button', { name: '카카오로 로그인' }))
    await waitFor(() => expect(first.redirect).toHaveBeenCalled())
    cleanup()
  }

  it('login success: the invitation comes back once, as the new account, even under StrictMode', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    await roundTrip(w, token)
    w.server.registerCode('code-1', USER_A)
    const app = setupApp('/auth/kakao/callback?code=code-1&state=state-1', { ...w })
    expect(await screen.findByText(WARD_1.wardName)).toBeInTheDocument()
    expect(location()).toBe('/invite')
    expect(w.storage.items.size).toBe(0)
    expect(app.store.getInvite()?.seq).toBe(1)
    expect(app.store.getInvite()?.owner?.userId).toBe(USER_A.userId)
    expect(w.server.calls.filter((call) => call === 'exchange')).toHaveLength(1)
  })

  it('login cancelled at Kakao: back on the invitation, which can start login again', async () => {
    const w = world()
    const token = w.backend.seedInvitation({})
    await roundTrip(w, token)
    const app = setupApp('/auth/kakao/callback?error=access_denied', { ...w })
    expect(await screen.findByText('카카오 로그인을 취소했어요.')).toBeInTheDocument()
    expect(location()).toBe('/invite')
    expect(app.store.getInvite()?.token).toBe(token)
    expect(screen.getByRole('button', { name: '카카오로 로그인' })).toBeInTheDocument()
    expect(w.storage.items.size).toBe(0)
  })

  it('code exchange fails: back on the invitation with the failure notice', async () => {
    const w = world()
    const token = w.backend.seedInvitation({})
    await roundTrip(w, token)
    const app = setupApp('/auth/kakao/callback?code=unknown&state=s', { ...w })
    expect(await screen.findByRole('button', { name: '카카오로 로그인' })).toBeInTheDocument()
    expect(location()).toBe('/invite')
    expect(app.store.getInvite()?.token).toBe(token)
    expect(screen.getByText(/로그인 시간이 지났거나/)).toBeInTheDocument()
  })

  it('login without an invitation keeps going to the ward list', async () => {
    const w = world()
    w.server.registerCode('code-2', USER_A)
    setupApp('/auth/kakao/callback?code=code-2&state=s', { ...w })
    await screen.findByText(`${USER_A.displayName}님, 안녕하세요.`)
    expect(location()).toBe('/wards')
  })

  it('an expired saved entry is not restored; the user is told to reopen the link', async () => {
    const w = world()
    const token = w.backend.seedInvitation({})
    const clock = { t: Date.now() }
    const first = setupApp(`/invite#token=${token}`, { ...w, clock })
    await userEvent.setup().click(await screen.findByRole('button', { name: '카카오로 로그인' }))
    await waitFor(() => expect(first.redirect).toHaveBeenCalled())
    cleanup()
    clock.t += 10 * 60_000
    w.server.registerCode('code-3', USER_A)
    const app = setupApp('/auth/kakao/callback?code=code-3&state=s', { ...w, clock })
    expect(await screen.findByText(/초대 확인이 만료됐어요/)).toBeInTheDocument()
    expect(location()).toBe('/wards')
    expect(app.store.getInvite()).toBeNull()
  })

  it('a damaged saved entry is not restored', async () => {
    const w = world()
    w.storage.items.set(LOGIN_RETURN_KEY, '{oops')
    w.server.registerCode('code-4', USER_A)
    const app = setupApp('/auth/kakao/callback?code=code-4&state=s', { ...w })
    expect(await screen.findByText(/초대를 이어가지 못했어요/)).toBeInTheDocument()
    expect(location()).toBe('/wards')
    expect(app.store.getInvite()).toBeNull()
  })

  it('a read failure falls back to reopening the link', async () => {
    const w = world()
    w.storage.failGet = true
    w.server.registerCode('code-5', USER_A)
    const app = setupApp('/auth/kakao/callback?code=code-5&state=s', { ...w })
    expect(await screen.findByText(/브라우저 저장소를 읽을 수 없음/)).toBeInTheDocument()
    expect(app.store.getInvite()).toBeNull()
  })

  it('a removal failure is not treated as consumed: no automatic restore, recovery guidance instead', async () => {
    const w = world()
    const token = w.backend.seedInvitation({})
    w.storage.items.set(LOGIN_RETURN_KEY, JSON.stringify({ v: 1, token, expiresAt: Date.now() + 5 * 60_000 }))
    w.storage.failRemove = true
    w.server.registerCode('code-6', USER_A)
    const app = setupApp('/auth/kakao/callback?code=code-6&state=s', { ...w })
    expect(await screen.findByText(/브라우저 저장소를 정리할 수 없음/)).toBeInTheDocument()
    expect(location()).toBe('/wards')
    expect(app.store.getInvite()).toBeNull()
    expect(w.backend.count('preview')).toBe(0)
  })
})

describe('paste and waiting invitation', () => {
  it('pastes a link on the ward list and continues to the preview', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    w.server.signInAs(USER_A)
    setupApp('/wards', { ...w })
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('초대 링크 또는 코드'), `http://localhost:3000/invite#token=${token}`)
    await user.click(screen.getByRole('button', { name: '초대 확인' }))
    expect(await screen.findByText(WARD_1.wardName)).toBeInTheDocument()
    expect(location()).toBe('/invite')
  })

  it('rejects text that is not an invitation link and clears what was typed', async () => {
    const w = world()
    w.server.signInAs(USER_A)
    setupApp('/wards', { ...w })
    const user = userEvent.setup()
    const input = await screen.findByLabelText('초대 링크 또는 코드')
    await user.type(input, 'https://evil.example.test/invite#token=' + 'A'.repeat(43))
    await user.click(screen.getByRole('button', { name: '초대 확인' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('올바른 초대 링크가 아니에요')
    expect(input).toHaveValue('')
    expect(location()).toBe('/wards')
  })

  it('a waiting invitation shows a banner on the ward list and can be continued or cancelled', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    w.server.signInAs(USER_A)
    const app = setupApp('/wards', { ...w })
    await screen.findByText('아직 소속된 병동이 없어요.')
    act(() => {
      app.store.offer(token)
    })
    const user = userEvent.setup()
    expect(await screen.findByText('확인 중인 초대가 있어요.')).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: '이어서 하기' }))
    expect(await screen.findByText(WARD_1.wardName)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '초대 취소' }))
    expect(location()).toBe('/wards')
    expect(screen.queryByText('확인 중인 초대가 있어요.')).not.toBeInTheDocument()
  })
})


describe('PR review regressions', () => {
  it('warns when a different hash link arrives during redemption and preserves the original request', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const other = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const app = openSignedIn(w, token)
    const release = app.backend.hold('redeem')
    await userEvent.setup().click(await joinButton())
    await waitFor(() => expect(app.backend.count('redeem')).toBe(1))
    await act(() => app.navigate('/invite#token=' + other))
    expect(await screen.findByRole('alert')).toHaveTextContent('새 링크를 받지 않았어요')
    expect(location()).toBe('/invite')
    expect(app.store.getInvite()?.token).toBe(token)
    expect(app.backend.count('redeem')).toBe(1)
    release()
    await screen.findByText(WARD_1.wardName + ' 병동에 가입했어요.')
    expect(app.backend.calls.find((call) => call.op === 'redeem')?.body).toEqual({ token })
  })

  it('drops the old invite and never redeems it for the new cookie identity', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const app = openSignedIn(w, token)
    await joinButton()
    w.server.expireAllAccessTokens()
    w.server.signInAs(USER_B)
    await userEvent.setup().click(await joinButton())
    await waitFor(() => expect(app.manager.getState()).toMatchObject({ status: 'signedIn', user: USER_B }))
    expect(app.backend.count('redeem')).toBe(0)
    expect(app.store.getInvite()).toBeNull()
    expect(await screen.findByText(/초대가 보이지 않으면/)).toBeInTheDocument()
  })

  it('sends a syntactically valid future month for the server to judge', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_MEMBER' })
    const app = openSignedIn(w, token)
    await userEvent.setup().type(await screen.findByLabelText(/병원 입사 연월/), '2999-01')
    await userEvent.setup().click(await joinButton())
    expect(await screen.findByRole('alert')).toHaveTextContent('미래 연월은 입력할 수 없어요')
    expect(app.backend.count('redeem')).toBe(1)
  })

  it('uses invitee guidance for a join 403', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const app = openSignedIn(w, token)
    app.backend.failWith('redeem', 403, 'NURSE_IDENTITY_REQUIRED')
    await userEvent.setup().click(await joinButton())
    expect(await screen.findByRole('alert')).toHaveTextContent('이 계정으로 초대를 확인하거나 가입할 수 없어요')
    expect(screen.queryByText('이 병동의 관리자 권한이 없어요.')).not.toBeInTheDocument()
  })

  it('explains how to recover an invitation after a clean page reload', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    openSignedIn(w, token)
    await joinButton()
    cleanup()
    const second = setupApp('/invite', w)
    expect(await screen.findByText(/새로고침이나 로그인 화면에서 돌아온 뒤 초대가 보이지 않으면/)).toBeInTheDocument()
    expect(second.store.getInvite()).toBeNull()
  })

  it('consumes the joined banner route state and does not repeat it after remount', async () => {
    const w = world()
    const token = w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    openSignedIn(w, token)
    await userEvent.setup().click(await joinButton())
    await screen.findByText(WARD_1.wardName + ' 병동에 가입했어요.')
    await waitFor(() => expect(screen.getByTestId('route-state')).toHaveTextContent('null'))
    cleanup()
    setupApp('/wards', w)
    await screen.findByText(USER_A.displayName + '님, 안녕하세요.')
    expect(screen.queryByText(WARD_1.wardName + ' 병동에 가입했어요.')).not.toBeInTheDocument()
  })
})
