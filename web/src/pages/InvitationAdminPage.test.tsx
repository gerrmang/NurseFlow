import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FakeAuthServer, USER_A } from '../test/fakeAuthServer'
import { FakeInvitationBackend, WARD_1 } from '../test/fakeInvitationBackend'
import { FakeStorage } from '../test/fakeStorage'
import { setupApp } from '../test/harness'

const location = () => screen.getByTestId('location').textContent
const ADMIN_PATH = `/wards/${WARD_1.wardId}/invitations`

function world(roles: ('WARD_ADMIN' | 'WARD_MEMBER')[] = ['WARD_ADMIN']) {
  const server = new FakeAuthServer()
  const backend = new FakeInvitationBackend(server)
  const storage = new FakeStorage()
  backend.addMember(WARD_1.wardId, USER_A.userId, roles)
  server.signInAs(USER_A)
  return { server, backend, storage }
}

function open(w: ReturnType<typeof world>, strict = true) {
  return setupApp(ADMIN_PATH, { ...w, strict })
}

const issueButton = () => screen.findByRole('button', { name: '초대 링크 만들기' })

describe('invitation list', () => {
  it('shows status, role and a fixed target label for each invitation (no memo, no names from the server list)', async () => {
    const w = world()
    const kim = w.backend.addNurse({ displayName: '가상 간호사 다' })
    const lee = w.backend.addNurse({ displayName: '가상 간호사 라', linkedAccount: true })
    w.backend.seedInvitation({ role: 'WARD_MEMBER', nurseId: kim.nurseId })
    w.backend.seedInvitation({ role: 'WARD_MEMBER', nurseId: lee.nurseId, redeemed: true })
    w.backend.seedInvitation({ role: 'WARD_ADMIN', revoked: true })
    w.backend.seedInvitation({ role: 'WARD_MEMBER', expiresInMs: -1000 })
    open(w)

    const list = await screen.findByRole('list')
    const rows = within(list).getAllByRole('listitem')
    expect(rows).toHaveLength(4)
    // Newest first.
    expect(rows[0]).toHaveTextContent('만료됨 · 간호사')
    expect(rows[0]).toHaveTextContent('새 간호사 초대 · 가입 시 기록 생성')
    expect(rows[1]).toHaveTextContent('철회됨 · 관리자')
    expect(rows[1]).toHaveTextContent('관리자 초대 · 대상 미지정')
    expect(rows[2]).toHaveTextContent('가입 완료 · 간호사')
    expect(rows[2]).toHaveTextContent('가상 간호사 라 · 가입됨')
    expect(rows[3]).toHaveTextContent('사용 대기 · 간호사')
    expect(rows[3]).toHaveTextContent('가상 간호사 다 · 미가입')
    // Reissue/revoke only for links that can still be acted on.
    expect(within(rows[0]!).getByRole('button', { name: '재발급' })).toBeInTheDocument()
    expect(within(rows[1]!).queryByRole('button')).not.toBeInTheDocument()
    expect(within(rows[2]!).queryByRole('button')).not.toBeInTheDocument()
    expect(within(rows[3]!).getByRole('button', { name: '철회' })).toBeInTheDocument()
  })

  it('offers only active, unlinked nurses without a pending invitation as link targets', async () => {
    const w = world()
    w.backend.addNurse({ displayName: '가상 후보 가' })
    w.backend.addNurse({ displayName: '가상 연결됨', linkedAccount: true })
    w.backend.addNurse({ displayName: '가상 비활성', active: false })
    const invited = w.backend.addNurse({ displayName: '가상 초대중' })
    w.backend.seedInvitation({ role: 'WARD_MEMBER', nurseId: invited.nurseId })
    open(w)

    const select = await screen.findByLabelText('연결할 간호사 기록')
    const options = within(select).getAllByRole('option').map((option) => option.textContent)
    expect(options).toEqual(['연결하지 않음 · 가입 시 새 기록 생성', '가상 후보 가'])
  })

  it('hides the nurse picker for admin invitations', async () => {
    const w = world()
    open(w)
    await screen.findByLabelText('연결할 간호사 기록')
    await userEvent.setup().selectOptions(screen.getByLabelText('역할'), 'WARD_ADMIN')
    expect(screen.queryByLabelText('연결할 간호사 기록')).not.toBeInTheDocument()
  })
})

describe('issuing', () => {
  it('copies a web link built from this origin; the server inviteUrl is not used; the token is never displayed', async () => {
    const w = world()
    const app = open(w)
    const user = userEvent.setup()
    await user.click(await issueButton())

    const panel = await screen.findByRole('region', { name: '발급된 초대 링크' })
    const token = (app.backend.invitations[0]!).token
    expect(panel.textContent).not.toContain(token)
    expect(document.body.textContent).not.toContain(token)
    expect(within(panel).getByRole('note')).toHaveTextContent('이 PC에서만 열려요')

    await user.click(within(panel).getByRole('button', { name: '웹 링크 복사' }))
    expect(await navigator.clipboard.readText()).toBe(`${window.location.origin}/invite#token=${token}`)
    expect(await navigator.clipboard.readText()).not.toContain('nurseflow://')

    await user.click(within(panel).getByRole('button', { name: '앱 붙여넣기용 복사' }))
    expect(await navigator.clipboard.readText()).toBe(`nurseflow://invite#token=${token}`)
    expect(document.body.textContent).not.toContain(token)

    await user.click(within(panel).getByRole('button', { name: '닫기' }))
    expect(screen.queryByRole('region', { name: '발급된 초대 링크' })).not.toBeInTheDocument()
    expect(document.body.textContent).not.toContain(token)
  })

  it('sends role only, or role with the chosen nurse, and never a nurse for an admin invitation', async () => {
    const w = world()
    const nurse = w.backend.addNurse({ displayName: '가상 후보 가' })
    const app = open(w)
    const user = userEvent.setup()
    await user.click(await issueButton())
    await screen.findByRole('region', { name: '발급된 초대 링크' })
    await user.selectOptions(screen.getByLabelText('연결할 간호사 기록'), nurse.nurseId)
    await user.click(screen.getByRole('button', { name: '초대 링크 만들기' }))
    await waitFor(() => expect(app.backend.count('issue')).toBe(2))
    await user.selectOptions(screen.getByLabelText('역할'), 'WARD_ADMIN')
    await user.click(screen.getByRole('button', { name: '초대 링크 만들기' }))
    await waitFor(() => expect(app.backend.count('issue')).toBe(3))
    const bodies = app.backend.calls.filter((call) => call.op === 'issue').map((call) => call.body)
    expect(bodies).toEqual([{ role: 'WARD_MEMBER' }, { role: 'WARD_MEMBER', nurseId: nurse.nurseId }, { role: 'WARD_ADMIN' }])
  })

  it('sends one request while busy (double click) and disables the button', async () => {
    const w = world()
    const app = open(w)
    const release = app.backend.hold('issue')
    const user = userEvent.setup()
    await user.dblClick(await issueButton())
    await waitFor(() => expect(app.backend.count('issue')).toBe(1))
    expect(screen.getByRole('button', { name: '처리하는 중…' })).toBeDisabled()
    release()
    await screen.findByRole('region', { name: '발급된 초대 링크' })
    expect(app.backend.count('issue')).toBe(1)
  })

  it('sends one request even when two clicks arrive before the screen re-renders', async () => {
    const w = world()
    const app = open(w)
    const button = await issueButton()
    const release = app.backend.hold('issue')
    act(() => {
      button.click()
      button.click()
    })
    await waitFor(() => expect(app.backend.count('issue')).toBe(1))
    release()
    await screen.findByRole('region', { name: '발급된 초대 링크' })
    expect(app.backend.count('issue')).toBe(1)
  })

  it('response lost after the server created it: no resend, the list is reloaded and the user told to reissue', async () => {
    const w = world()
    const app = open(w)
    await issueButton()
    app.backend.loseResponseAfter('issue')
    await userEvent.setup().click(screen.getByRole('button', { name: '초대 링크 만들기' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('초대 발급 결과를 확인하지 못했어요.')
    expect(alert).toHaveTextContent('목록을 새로 불러왔어요.')
    expect(alert).toHaveTextContent('재발급해 주세요')
    expect(app.backend.count('issue')).toBe(1)
    expect(screen.queryByRole('region', { name: '발급된 초대 링크' })).not.toBeInTheDocument()
    // The invitation the server did create is visible in the reloaded list.
    expect(within(screen.getByRole('list')).getAllByRole('listitem')).toHaveLength(1)
  })

  it('response lost and the reload also fails: does NOT claim the list was refreshed', async () => {
    const w = world()
    const app = open(w)
    await issueButton()
    app.backend.loseResponseAfter('issue')
    app.backend.failBefore('list')
    await userEvent.setup().click(screen.getByRole('button', { name: '초대 링크 만들기' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('결과를 확인하지 못했어요.')
    expect(alert).toHaveTextContent('목록도 불러오지 못했어요.')
    expect(alert.textContent).not.toContain('목록을 새로 불러왔어요')
    expect(await screen.findByText('최신 목록이 아닐 수 있어요. 새로고침으로 다시 불러와 주세요.')).toBeInTheDocument()
    expect(app.backend.count('issue')).toBe(1)
  })

  it('failure before processing is described as unknown too and is not resent', async () => {
    const w = world()
    const app = open(w)
    await issueButton()
    app.backend.failBefore('issue')
    await userEvent.setup().click(screen.getByRole('button', { name: '초대 링크 만들기' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('초대 발급 결과를 확인하지 못했어요.')
    expect(app.backend.count('issue')).toBe(1)
    expect(screen.getByText('아직 만든 초대가 없어요.')).toBeInTheDocument()
  })

  it('success but the reload fails: shows the link panel and says the list could not be refreshed', async () => {
    const w = world()
    const app = open(w)
    await issueButton()
    app.backend.failBefore('list')
    await userEvent.setup().click(screen.getByRole('button', { name: '초대 링크 만들기' }))
    expect(await screen.findByRole('region', { name: '발급된 초대 링크' })).toBeInTheDocument()
    expect(await screen.findByRole('alert')).toHaveTextContent('초대 링크를 만들었어요. 다만 목록을 새로 불러오지 못했어요.')
  })

  it.each([
    ['INVITATION_ALREADY_PENDING', 409, '사용 가능한 초대가 이미 있어요'],
    ['NURSE_ALREADY_LINKED', 409, '이미 계정과 연결된 간호사 기록이에요'],
    ['NURSE_NOT_FOUND', 404, '간호사 기록을 찾을 수 없어요'],
  ])('%s is shown with its own message and the list is reloaded', async (code, status, message) => {
    const w = world()
    const app = open(w)
    await issueButton()
    app.backend.failWith('issue', status, code)
    await userEvent.setup().click(screen.getByRole('button', { name: '초대 링크 만들기' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(message)
    expect(alert).toHaveTextContent('목록을 새로 불러왔어요.')
    expect(alert.textContent).not.toContain(code)
  })
})

describe('reissue and revoke', () => {
  it('reissue asks for confirmation, revokes the old link, and shows the new one once', async () => {
    const w = world()
    w.backend.seedInvitation({ role: 'WARD_MEMBER' })
    const app = open(w)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: '재발급' }))
    expect(app.backend.count('reissue')).toBe(0)
    expect(screen.getByRole('group', { name: '재발급 확인' })).toHaveTextContent('이전 링크는 철회할게요')
    await user.click(screen.getByRole('button', { name: '재발급 확인' }))

    expect(await screen.findByRole('region', { name: '발급된 초대 링크' })).toHaveTextContent('새 링크')
    expect(app.backend.count('reissue')).toBe(1)
    const rows = within(screen.getByRole('list')).getAllByRole('listitem')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('사용 대기')
    expect(rows[1]).toHaveTextContent('철회됨')
  })

  it('cancelling the confirmation sends nothing', async () => {
    const w = world()
    w.backend.seedInvitation({})
    const app = open(w)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: '철회' }))
    await user.click(screen.getByRole('button', { name: '취소' }))
    expect(app.backend.count('revoke')).toBe(0)
    expect(screen.getByRole('button', { name: '철회' })).toBeInTheDocument()
  })

  it('revoke removes the link; a lost response is reported as unknown with the reloaded state', async () => {
    const w = world()
    w.backend.seedInvitation({})
    const app = open(w)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: '철회' }))
    app.backend.loseResponseAfter('revoke')
    await user.click(screen.getByRole('button', { name: '철회 확인' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('철회 결과를 확인하지 못했어요.')
    expect(alert).toHaveTextContent('목록을 새로 불러왔어요. 상태를 확인하고 필요하면 다시 철회해 주세요.')
    expect(within(screen.getByRole('list')).getByRole('listitem')).toHaveTextContent('철회됨')
    expect(app.backend.count('revoke')).toBe(1)
  })

  it('revoking an already used link reports it and reloads', async () => {
    const w = world()
    w.backend.seedInvitation({})
    const app = open(w)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: '철회' }))
    app.backend.invitations[0]!.redeemedAt = Date.now()
    await user.click(screen.getByRole('button', { name: '철회 확인' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('이미 사용된 초대예요')
    expect(within(screen.getByRole('list')).getByRole('listitem')).toHaveTextContent('가입 완료')
  })

  it('reissuing a revoked link (changed elsewhere) is reported and never retried', async () => {
    const w = world()
    w.backend.seedInvitation({})
    const app = open(w)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: '재발급' }))
    app.backend.invitations[0]!.revokedAt = Date.now()
    await user.click(screen.getByRole('button', { name: '재발급 확인' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('철회된 초대예요')
    expect(app.backend.count('reissue')).toBe(1)
  })
})

describe('permissions and session', () => {
  it('a ward member without admin rights is told so, without a list', async () => {
    const w = world(['WARD_MEMBER'])
    const app = open(w)
    expect(await screen.findByRole('alert')).toHaveTextContent('이 병동의 관리자만 초대를 관리할 수 있어요.')
    expect(app.backend.count('list')).toBe(0)
  })

  it('the server has the last word: a 403 on the list shows the same screen', async () => {
    const w = world()
    const app = open(w)
    await issueButton()
    app.backend.failWith('list', 403, 'WARD_ADMIN_REQUIRED')
    await userEvent.setup().click(screen.getByRole('button', { name: '새로고침' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('이 병동의 관리자만 초대를 관리할 수 있어요.')
  })

  it('a first-load failure offers a retry that loads the list', async () => {
    const w = world()
    w.backend.failBefore('list')
    setupApp(ADMIN_PATH, { ...w, strict: false })
    expect(await screen.findByRole('alert')).toHaveTextContent('서버에 연결할 수 없어요')
    await userEvent.setup().click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('button', { name: '초대 링크 만들기' })).toBeInTheDocument()
  })

  it('a list answered after logout is discarded', async () => {
    const w = world()
    const release = w.backend.hold('list')
    const app = setupApp(ADMIN_PATH, { ...w, strict: false })
    await waitFor(() => expect(w.backend.count('list')).toBe(1))
    await act(async () => {
      await app.manager.logout()
    })
    release()
    await act(async () => {
      await Promise.resolve()
    })
    expect(screen.queryByRole('button', { name: '초대 링크 만들기' })).not.toBeInTheDocument()
    expect(location()).toBe('/login')
  })

  it('a link shown on screen disappears with the session', async () => {
    const w = world()
    const app = open(w)
    await userEvent.setup().click(await issueButton())
    await screen.findByRole('region', { name: '발급된 초대 링크' })
    await act(async () => {
      await app.manager.logout()
    })
    expect(screen.queryByRole('region', { name: '발급된 초대 링크' })).not.toBeInTheDocument()
  })
})

describe('entry from the ward list', () => {
  it('only admin wards get an invitation management link', async () => {
    const w = world()
    const memberWard = { wardId: '10000000-0000-4000-8000-000000000002', wardName: '가상 외과 병동', hospitalName: null }
    w.backend.addWard(memberWard)
    w.backend.addMember(memberWard.wardId, USER_A.userId, ['WARD_MEMBER'])
    setupApp('/wards', { ...w })
    const links = await screen.findAllByRole('link', { name: '초대 관리' })
    expect(links).toHaveLength(1)
    await userEvent.setup().click(links[0]!)
    expect(await screen.findByRole('button', { name: '초대 링크 만들기' })).toBeInTheDocument()
    expect(location()).toBe(ADMIN_PATH)
  })
})

const panelName = { name: '발급된 초대 링크' }

/** Issues one invitation and returns the opened page, with the clipboard writer under test control. */
async function issueFirst(w: ReturnType<typeof world>) {
  const app = open(w)
  const user = userEvent.setup()
  const writeText = vi.spyOn(navigator.clipboard, 'writeText')
  await user.click(await issueButton())
  const panel = await screen.findByRole('region', panelName)
  return { app, user, writeText, panel }
}

async function reissueFirstRow(user: ReturnType<typeof userEvent.setup>) {
  await user.click(within(screen.getByRole('list')).getAllByRole('button', { name: '재발급' })[0]!)
  await user.click(screen.getByRole('button', { name: '재발급 확인' }))
}

describe('issued link panel follows the invitation it shows', () => {
  afterEach(() => vi.restoreAllMocks())

  it('a manual-copy box of the old link does not survive a reissue', async () => {
    const w = world()
    const { user, writeText, panel } = await issueFirst(w)
    writeText.mockRejectedValueOnce(new Error('denied'))
    await user.click(within(panel).getByRole('button', { name: '웹 링크 복사' }))
    const oldToken = w.backend.invitations[0]!.token
    expect(await screen.findByLabelText('링크')).toHaveValue(`${window.location.origin}/invite#token=${oldToken}`)

    await reissueFirstRow(user)
    await waitFor(() => expect(w.backend.count('reissue')).toBe(1))
    const next = await screen.findByRole('region', panelName)
    await within(next).findByRole('heading', { name: /새 링크/ })
    expect(screen.queryByLabelText('링크')).not.toBeInTheDocument()
    expect(screen.queryByText('웹 링크를 복사했어요.')).not.toBeInTheDocument()
    expect(document.body.textContent).not.toContain(oldToken)
  })

  it.each([
    ['succeeds', true],
    ['fails', false],
  ])('a late copy of the old link that %s after the new link is shown does not touch the new panel', async (_name, succeeds) => {
    const w = world()
    const { user, writeText, panel } = await issueFirst(w)
    let settle!: () => void
    writeText.mockImplementationOnce(
      () =>
        new Promise<void>((resolve, reject) => {
          settle = () => (succeeds ? resolve() : reject(new Error('denied')))
        }),
    )
    await user.click(within(panel).getByRole('button', { name: '웹 링크 복사' }))
    await reissueFirstRow(user)
    await within(await screen.findByRole('region', panelName)).findByRole('heading', { name: /새 링크/ })

    await act(async () => {
      settle()
      await Promise.resolve()
    })
    expect(screen.queryByText('웹 링크를 복사했어요.')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('링크')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('within one panel the latest copy attempt wins over an earlier slow one', async () => {
    const w = world()
    const { user, writeText, panel } = await issueFirst(w)
    let slow!: () => void
    writeText.mockImplementationOnce(() => new Promise<void>((resolve) => (slow = resolve)))
    await user.click(within(panel).getByRole('button', { name: '웹 링크 복사' }))
    writeText.mockRejectedValueOnce(new Error('denied'))
    await user.click(within(panel).getByRole('button', { name: '앱 붙여넣기용 복사' }))
    expect(await screen.findByLabelText('링크')).toBeInTheDocument()
    await act(async () => {
      slow()
      await Promise.resolve()
    })
    expect(screen.queryByText('웹 링크를 복사했어요.')).not.toBeInTheDocument()
    expect(screen.getByLabelText('링크')).toBeInTheDocument()
  })

  it('a nurse that stopped being a candidate is not sent even though it was selected earlier', async () => {
    const w = world()
    const nurse = w.backend.addNurse({ displayName: '가상 후보 가' })
    const app = open(w)
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('연결할 간호사 기록'), nurse.nurseId)
    nurse.linkedAccount = true // another admin linked this record
    const before = app.backend.count('list')
    await user.click(screen.getByRole('button', { name: '새로고침' }))
    await waitFor(() => expect(app.backend.count('list')).toBe(before + 1))
    await waitFor(() => expect(screen.getByLabelText('연결할 간호사 기록')).toHaveValue(''))

    await user.click(screen.getByRole('button', { name: '초대 링크 만들기' }))
    await screen.findByRole('region', panelName)
    expect(app.backend.calls.filter((call) => call.op === 'issue').map((call) => call.body)).toEqual([{ role: 'WARD_MEMBER' }])
  })

  it('revoking the invitation behind the panel turns the panel into an unusable notice', async () => {
    const w = world()
    const { user, writeText } = await issueFirst(w)
    await user.click(within(screen.getByRole('list')).getByRole('button', { name: '철회' }))
    await user.click(screen.getByRole('button', { name: '철회 확인' }))
    const panel = await screen.findByRole('region', panelName)
    await within(panel).findByText(/철회돼서 더 이상 복사하거나 사용할 수 없어요/)
    expect(within(panel).queryByRole('button', { name: '웹 링크 복사' })).not.toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: '앱 붙여넣기용 복사' })).not.toBeInTheDocument()
    expect(panel.textContent).not.toContain('24시간')
    expect(writeText).not.toHaveBeenCalled()
    await user.click(within(panel).getByRole('button', { name: '닫기' }))
    expect(screen.queryByRole('region', panelName)).not.toBeInTheDocument()
  })

  it('a reissue whose response is lost: the old panel is marked revoked after the reload', async () => {
    const w = world()
    const { user, panel } = await issueFirst(w)
    expect(within(panel).getByRole('button', { name: '웹 링크 복사' })).toBeInTheDocument()
    w.backend.loseResponseAfter('reissue')
    await reissueFirstRow(user)
    expect(await screen.findByRole('alert')).toHaveTextContent('재발급 결과를 확인하지 못했어요.')
    const after = screen.getByRole('region', panelName)
    await within(after).findByText(/철회돼서/)
    expect(within(after).queryByRole('button', { name: '웹 링크 복사' })).not.toBeInTheDocument()
    expect(w.backend.count('reissue')).toBe(1)
  })

  it('revoking an unrelated invitation leaves the usable panel alone', async () => {
    const w = world()
    w.backend.seedInvitation({ role: 'WARD_ADMIN' })
    const { user } = await issueFirst(w)
    const rows = within(screen.getByRole('list')).getAllByRole('listitem')
    expect(rows).toHaveLength(2)
    await user.click(within(rows[1]!).getByRole('button', { name: '철회' }))
    await user.click(screen.getByRole('button', { name: '철회 확인' }))
    await waitFor(() => expect(within(screen.getByRole('list')).getAllByRole('listitem')[1]).toHaveTextContent('철회됨'))
    const panel = screen.getByRole('region', panelName)
    expect(within(panel).getByRole('button', { name: '웹 링크 복사' })).toBeInTheDocument()
    expect(panel.textContent).toContain('24시간')
  })

  async function revokeRow(user: ReturnType<typeof userEvent.setup>, index = 0) {
    const rows = within(screen.getByRole('list')).getAllByRole('listitem')
    await user.click(within(rows[index]!).getByRole('button', { name: '철회' }))
    await user.click(screen.getByRole('button', { name: '철회 확인' }))
  }

  it('revoke succeeded (204) but the reload fails: the panel is already unusable and the failure notice stays', async () => {
    const w = world()
    const { user, writeText, panel } = await issueFirst(w)
    writeText.mockRejectedValueOnce(new Error('denied'))
    await user.click(within(panel).getByRole('button', { name: '웹 링크 복사' }))
    expect(await screen.findByLabelText('링크')).toBeInTheDocument()
    w.backend.failBefore('list')
    await revokeRow(user)
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('초대를 철회했어요. 다만 목록을 새로 불러오지 못했어요.')
    const after = screen.getByRole('region', panelName)
    expect(after).toHaveTextContent('철회돼서 더 이상 복사하거나 사용할 수 없어요')
    expect(within(after).queryByRole('button', { name: '웹 링크 복사' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('링크')).not.toBeInTheDocument()
    expect(after.textContent).not.toContain('24시간')
  })

  it('revoke response lost and the reload succeeds: the listed state decides', async () => {
    const w = world()
    const { user } = await issueFirst(w)
    w.backend.loseResponseAfter('revoke') // the server did revoke it
    await revokeRow(user)
    expect(await screen.findByRole('alert')).toHaveTextContent('철회 결과를 확인하지 못했어요.')
    expect(screen.getByRole('region', panelName)).toHaveTextContent('철회돼서')
    expect(within(screen.getByRole('region', panelName)).queryByRole('button', { name: '웹 링크 복사' })).not.toBeInTheDocument()
  })

  it('revoke response lost and the reload succeeds but the server never revoked it: the panel stays usable', async () => {
    const w = world()
    const { user } = await issueFirst(w)
    w.backend.failBefore('revoke')
    await revokeRow(user)
    expect(await screen.findByRole('alert')).toHaveTextContent('철회 결과를 확인하지 못했어요.')
    expect(within(screen.getByRole('region', panelName)).getByRole('button', { name: '웹 링크 복사' })).toBeInTheDocument()
  })

  it('revoke response lost and the reload fails: the result is unknown and nothing is claimed', async () => {
    const w = world()
    const { user } = await issueFirst(w)
    w.backend.loseResponseAfter('revoke')
    w.backend.failBefore('list')
    await revokeRow(user)
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('철회 결과를 확인하지 못했어요.')
    expect(alert).toHaveTextContent('목록도 불러오지 못했어요.')
    expect(alert.textContent).not.toContain('목록을 새로 불러왔어요')
    const panel = screen.getByRole('region', panelName)
    expect(panel.textContent).not.toContain('철회돼서')
    expect(within(panel).getByRole('button', { name: '웹 링크 복사' })).toBeInTheDocument()
  })

  it('a confirmed revoke is not undone by a stale list that still shows the invitation as pending', async () => {
    const w = world()
    const { user } = await issueFirst(w)
    const staleRelease = w.backend.hold('list')
    // A refresh started earlier is answered late, after the revoke was confirmed.
    await user.click(screen.getByRole('button', { name: '새로고침' }))
    await waitFor(() => expect(w.backend.count('list')).toBeGreaterThan(0))
    await revokeRow(user)
    await within(screen.getByRole('region', panelName)).findByText(/철회돼서/)
    await act(async () => {
      staleRelease()
      await Promise.resolve()
    })
    await waitFor(() => expect(screen.getByRole('button', { name: '초대 링크 만들기' })).toBeEnabled())
    expect(within(screen.getByRole('region', panelName)).queryByRole('button', { name: '웹 링크 복사' })).not.toBeInTheDocument()
  })
})
