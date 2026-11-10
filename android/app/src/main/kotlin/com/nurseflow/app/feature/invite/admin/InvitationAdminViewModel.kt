package com.nurseflow.app.feature.invite.admin

import androidx.lifecycle.ViewModel
import com.nurseflow.app.core.network.*
import com.nurseflow.app.feature.invite.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InvitationRow(
    val invitation: InvitationDto,
    val status: InvitationStatus,
    val target: String,
    val shortId: String,
)

/** An active nurse record not yet joined by any account, with its newest invitation status. */
data class UnlinkedNurse(val nurseId: String, val displayName: String, val latest: InvitationStatus?)

/** A link shown once right after issue/reissue. Held in memory only; [toString] is masked. */
class IssuedLink(val url: String, val expiresAt: String) {
    override fun toString() = "IssuedLink(***)"
}

data class InvitationAdminState(
    val loading: Boolean = true,
    val rows: List<InvitationRow> = emptyList(),
    val unlinked: List<UnlinkedNurse> = emptyList(),
    val busy: Boolean = false,
    val loadError: InviteFailure? = null,
    val notice: String? = null,
    val link: IssuedLink? = null,
    /** The server denied admin access for this ward; the screen must close. */
    val forbidden: Boolean = false,
) {
    /** Unlinked records with no usable PENDING invitation can be targeted by a new member invitation. */
    val invitableNurses: List<UnlinkedNurse> get() = unlinked.filter { it.latest != InvitationStatus.PENDING }
}

/**
 * Admin invitation management for one ward. The server decides permission and validity; this
 * model only reflects it. Issue, reissue and revoke are never retried automatically: after a
 * lost response the list is reloaded and the admin decides what to do.
 */
class InvitationAdminViewModel(
    private val api: InvitationApi,
    private val wardId: String,
    private val linkBase: String,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    /** Ends the local session when the server no longer knows the account (USER_NOT_FOUND). */
    private val onSessionInvalid: () -> Unit = {},
) : ViewModel(scope) {
    private val scope = scope
    private val mutable = MutableStateFlow(InvitationAdminState())
    val state: StateFlow<InvitationAdminState> = mutable.asStateFlow()
    private var load: Job? = null
    private var ticket = 0

    init { reload() }

    fun reload() {
        load?.cancel()
        val mine = ++ticket
        mutable.value = mutable.value.copy(loading = true, loadError = null)
        load = scope.launch {
            try {
                val invitations = apiCall { api.list(wardId) }.items
                val staff = apiCall { api.staff(wardId) }.items
                if (mine != ticket) return@launch
                val nurses = staff.associateBy { it.nurseId }
                val rows = invitations.map {
                    InvitationRow(it, InvitationStatus.of(it.status), targetLabel(it, nurses), invitationShortId(it))
                }
                val unlinked = staff.filter { it.active && !it.linkedAccount }.map { nurse ->
                    // The list is newest first, so the first match is the latest invitation.
                    UnlinkedNurse(nurse.nurseId, nurse.displayName, rows.firstOrNull { it.invitation.nurseId == nurse.nurseId }?.status)
                }
                mutable.value = mutable.value.copy(loading = false, rows = rows, unlinked = unlinked)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mine != ticket) return@launch
                val failure = e.toInviteFailure()
                mutable.value = mutable.value.copy(loading = false, loadError = failure, forbidden = failure == InviteFailure.NOT_ADMIN)
                if (failure.endsSession) onSessionInvalid()
            }
        }
    }

    /** WARD_ADMIN never carries a nurse record; a WARD_MEMBER may link an unlinked one. */
    fun create(role: String, nurseId: String?) {
        val body = when (role) {
            InvitationRoles.ADMIN -> CreateInvitationBody(InvitationRoles.ADMIN)
            InvitationRoles.MEMBER -> CreateInvitationBody(InvitationRoles.MEMBER, nurseId)
            else -> return
        }
        write(lostNotice = "발급 결과를 확인하지 못했어요. 목록에 새 초대가 보이지만 링크를 받지 못했다면 그 초대를 재발급해 주세요.") {
            issued(apiCall { api.create(wardId, body) })
        }
    }

    fun reissue(invitationId: String) {
        write(lostNotice = "재발급 결과를 확인하지 못했어요. 목록에서 새 초대를 확인하고, 링크를 받지 못했다면 그 초대를 다시 재발급해 주세요.") {
            issued(apiCall { api.reissue(wardId, invitationId) })
        }
    }

    fun revoke(invitationId: String) {
        write(lostNotice = "철회 결과를 확인하지 못했어요. 목록에서 상태를 확인하고 필요하면 다시 철회해 주세요.") {
            apiCall { api.revoke(wardId, invitationId) }
            mutable.value = mutable.value.copy(notice = "초대를 철회했어요.")
        }
    }

    /** The screen has closed for a denial; a later entry shows the error with a retry instead of closing again. */
    fun acknowledgeForbidden() { mutable.value = mutable.value.copy(forbidden = false, loadError = InviteFailure.NOT_ADMIN, loading = false) }

    fun dismissLink() { mutable.value = mutable.value.copy(link = null) }
    fun dismissNotice() { mutable.value = mutable.value.copy(notice = null) }

    private fun issued(created: CreatedInvitationDto) {
        val url = inviteLinkFor(created, linkBase)
        mutable.value = if (url != null) {
            mutable.value.copy(link = IssuedLink(url, created.invitation.expiresAt), notice = null)
        } else {
            mutable.value.copy(notice = "초대는 발급됐지만 링크 주소가 설정되지 않아 링크를 만들 수 없어요.")
        }
    }

    private fun write(lostNotice: String, block: suspend () -> Unit) {
        if (mutable.value.busy) return // one write at a time; duplicate taps are ignored
        mutable.value = mutable.value.copy(busy = true, notice = null)
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = e.toInviteFailure()
                mutable.value = mutable.value.copy(
                    notice = when {
                        failure.outcomeUnknown -> lostNotice
                        failure == InviteFailure.REVOKED -> "이미 철회된 초대예요. 목록을 새로 불러왔어요."
                        failure == InviteFailure.ALREADY_REDEEMED -> "이미 가입에 사용된 초대예요. 목록을 새로 불러왔어요."
                        else -> failure.message
                    },
                    forbidden = failure == InviteFailure.NOT_ADMIN,
                )
                if (failure.endsSession) {
                    mutable.value = mutable.value.copy(busy = false)
                    onSessionInvalid()
                    return@launch
                }
            } finally {
                mutable.value = mutable.value.copy(busy = false)
            }
            if (!mutable.value.forbidden) reload()
        }
    }
}
