import { buildAppPasteText, buildWebInviteLink, isLocalOrigin, parseInviteInput, readInviteFragment } from './inviteLink'

const TOKEN = 'A'.repeat(43)
const ORIGIN = 'https://nurseflow.example.test'

describe('readInviteFragment', () => {
  it('reads only an exact #token= fragment', () => {
    expect(readInviteFragment(`#token=${TOKEN}`)).toEqual({ kind: 'token', token: TOKEN })
    expect(readInviteFragment('')).toEqual({ kind: 'none' })
    expect(readInviteFragment('#')).toEqual({ kind: 'none' })
  })

  it.each([
    '#foo',
    '#token=',
    `#token=${'A'.repeat(42)}`,
    `#token=${'A'.repeat(44)}`,
    `#token=${TOKEN}&x=1`,
    `#x=1&token=${TOKEN}`,
    `#token=${'%'.repeat(43)}`,
    `#token=${'!'.repeat(43)}`,
  ])('rejects %s as invalid', (hash) => {
    expect(readInviteFragment(hash)).toEqual({ kind: 'invalid' })
  })

  it('is a pure read: calling it twice gives the same result', () => {
    expect(readInviteFragment(`#token=${TOKEN}`)).toEqual(readInviteFragment(`#token=${TOKEN}`))
  })
})

describe('links', () => {
  it('builds the web link from the given origin, never from a server inviteUrl', () => {
    expect(buildWebInviteLink(ORIGIN, TOKEN)).toBe(`${ORIGIN}/invite#token=${TOKEN}`)
  })

  it('builds the Android paste text with the development scheme', () => {
    expect(buildAppPasteText(TOKEN)).toBe(`nurseflow://invite#token=${TOKEN}`)
  })

  it('detects origins that other devices cannot open', () => {
    expect(isLocalOrigin('http://localhost:5173')).toBe(true)
    expect(isLocalOrigin('http://127.0.0.1:5173')).toBe(true)
    expect(isLocalOrigin(ORIGIN)).toBe(false)
  })
})

describe('parseInviteInput', () => {
  it('accepts this origin link, the app link and the bare token (with surrounding whitespace)', () => {
    expect(parseInviteInput(`  ${ORIGIN}/invite#token=${TOKEN}\n`, ORIGIN)).toBe(TOKEN)
    expect(parseInviteInput(`nurseflow://invite#token=${TOKEN}`, ORIGIN)).toBe(TOKEN)
    expect(parseInviteInput(` ${TOKEN} `, ORIGIN)).toBe(TOKEN)
  })

  it.each([
    ['another origin', `https://evil.example.test/invite#token=${TOKEN}`],
    ['other path', `${ORIGIN}/wards#token=${TOKEN}`],
    ['query string', `${ORIGIN}/invite?x=1#token=${TOKEN}`],
    ['extra fragment parameter', `${ORIGIN}/invite#token=${TOKEN}&x=1`],
    ['other app host', `nurseflow://other#token=${TOKEN}`],
    ['app link with path', `nurseflow://invite/extra#token=${TOKEN}`],
    ['javascript scheme', `javascript:alert(1)#token=${TOKEN}`],
    ['short token', 'A'.repeat(42)],
    ['text', '안녕하세요'],
    ['empty', ''],
    ['credentials in the URL', `https://user:pw@nurseflow.example.test/invite#token=${TOKEN}`],
  ])('rejects %s', (_name, text) => {
    expect(parseInviteInput(text, ORIGIN)).toBeNull()
  })
})
