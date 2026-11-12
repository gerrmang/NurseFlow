import { useId, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { INVITE_PATH, parseInviteInput } from './inviteLink'
import { restoreNoticeMessage } from './invitationMessages'
import { useInviteStore, usePendingInvite, useRestoreNotice } from './InviteContext'

/** Paste box for a link or bare token. The pasted text is cleared right after it is read. */
export function PasteInviteForm() {
  const store = useInviteStore()
  const navigate = useNavigate()
  const inputId = useId()
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)

  const submit = () => {
    const token = parseInviteInput(text, window.location.origin)
    setText('')
    if (token === null) {
      setError('올바른 초대 링크가 아니에요. 받은 링크를 그대로 붙여넣어 주세요.')
      return
    }
    if (store.offer(token) === 'busy') {
      setError('다른 초대를 처리하는 중이에요. 끝난 뒤 다시 시도해 주세요.')
      return
    }
    setError(null)
    navigate(INVITE_PATH)
  }

  return (
    <form
      className="paste-form"
      onSubmit={(event) => {
        event.preventDefault()
        submit()
      }}
    >
      <label htmlFor={inputId}>초대 링크 또는 코드</label>
      <input
        id={inputId}
        type="text"
        value={text}
        onChange={(event) => setText(event.target.value)}
        autoComplete="off"
        autoCapitalize="off"
        spellCheck={false}
        placeholder="받은 초대 링크를 붙여넣어 주세요"
        aria-describedby={error ? `${inputId}-error` : undefined}
      />
      {error && (
        <p id={`${inputId}-error`} role="alert" className="error">
          {error}
        </p>
      )}
      <div className="actions">
        <button type="submit" disabled={text.trim() === ''}>
          초대 확인
        </button>
      </div>
    </form>
  )
}

/** Shown when an invitation could not be carried through login automatically. */
export function RestoreNotice() {
  const store = useInviteStore()
  const reason = useRestoreNotice()
  if (reason === null) return null
  return (
    <p role="status" className="notice">
      {restoreNoticeMessage(reason)}{' '}
      <button type="button" className="link" onClick={() => store.dismissRestoreNotice()}>
        닫기
      </button>
    </p>
  )
}

/** Signed-in banner: an invitation is waiting (for example after leaving the invitation screen). */
export function PendingInviteBanner() {
  const store = useInviteStore()
  const invite = usePendingInvite()
  if (!invite) return null
  return (
    <section className="card" aria-label="확인 중인 초대">
      <p role="status">확인 중인 초대가 있어요.</p>
      <div className="actions">
        <Link className="button" to={INVITE_PATH}>
          이어서 하기
        </Link>
        <button type="button" className="secondary" onClick={() => store.discard()}>
          초대 취소
        </button>
      </div>
    </section>
  )
}
