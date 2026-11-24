package com.nurseflow.app.feature.invite

import androidx.lifecycle.ViewModel
import com.nurseflow.app.core.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

sealed interface JoinState {
    /** No invitation is being handled. */
    data object Idle : JoinState
    data object Loading : JoinState
    data class Ready(
        val preview: InvitationPreviewDto,
        val submitting: Boolean = false,
        val error: InviteFailure? = null,
        val notice: String? = null,
    ) : JoinState
    /** The join request was sent but its response was lost; the result must be checked before any resend. */
    data class Uncertain(val preview: InvitationPreviewDto, val checking: Boolean = false, val error: InviteFailure? = null) : JoinState
    data class Joined(val wardId: String) : JoinState
    /** ALREADY_WARD_MEMBER: nothing changed; the user can go to the ward they already belong to. */
    data class AlreadyMember(val wardId: String) : JoinState
    data class Failed(val failure: InviteFailure) : JoinState
}

private val joinMonthPattern = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
private val earliestJoinMonth = YearMonth.of(1950, 1)

/** Client-side precheck only; the server is the final judge of the joined month. */
fun validateJoinMonth(input: String, today: YearMonth): InviteFailure? {
    val value = input.trim()
    if (value.isEmpty()) return InviteFailure.JOIN_MONTH_REQUIRED
    if (!joinMonthPattern.matches(value)) return InviteFailure.INVALID_JOIN_MONTH
    val month = YearMonth.parse(value)
    return if (month < earliestJoinMonth || month > today) InviteFailure.INVALID_JOIN_MONTH else null
}

/**
 * Preview and join for the invitation held in [store]. Every response is applied only while the
 * invitation it was started for is still current, so a newer link, cancel or logout wins.
 * Joining is never resent automatically.
 */
class JoinInvitationViewModel(
    private val invitations: InvitationApi,
    private val api: NurseFlowApi,
    private val store: PendingInvitationStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val today: () -> YearMonth = { YearMonth.now() },
    /** Ends the local session (and with it this invitation) when the server no longer knows the account. */
    private val onSessionInvalid: () -> Unit = {},
) : ViewModel(scope) {
    private val scope = scope
    private val mutable = MutableStateFlow<JoinState>(JoinState.Idle)
    val state: StateFlow<JoinState> = mutable.asStateFlow()
    private var active: PendingInvitation? = null
    private var request: Job? = null

    init {
        scope.launch { store.current.collect(::onPending) }
    }

    private fun onPending(next: PendingInvitation?) {
        if (next?.seq == active?.seq) return
        request?.cancel()
        active = next
        if (next == null) mutable.value = JoinState.Idle else loadPreview(next, null)
    }

    private fun apply(owner: PendingInvitation, next: JoinState) {
        if (active?.seq != owner.seq) return
        mutable.value = next
        if ((next as? JoinState.Failed)?.failure?.endsSession == true) onSessionInvalid()
    }

    private fun loadPreview(owner: PendingInvitation, notice: String?) {
        mutable.value = JoinState.Loading
        request = scope.launch {
            val next = try {
                JoinState.Ready(apiCall { invitations.preview(InvitationTokenBody(owner.token.value)) }, notice = notice)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                JoinState.Failed(e.toInviteFailure())
            }
            apply(owner, next)
        }
    }

    fun retryPreview() {
        val owner = active ?: return
        val failed = mutable.value as? JoinState.Failed ?: return
        if (!failed.failure.endsInvitation && !failed.failure.endsSession) loadPreview(owner, null)
    }

    fun redeem(joinedOnInput: String) {
        val owner = active ?: return
        val ready = mutable.value as? JoinState.Ready ?: return
        if (ready.submitting) return // duplicate tap
        val month = if (ready.preview.role == InvitationRoles.MEMBER) {
            validateJoinMonth(joinedOnInput, today())?.let {
                mutable.value = ready.copy(error = it, notice = null)
                return
            }
            joinedOnInput.trim()
        } else null // WARD_ADMIN joins without a hospital joined month
        mutable.value = ready.copy(submitting = true, error = null, notice = null)
        val wardId = ready.preview.wardId
        request = scope.launch {
            val next = try {
                JoinState.Joined(apiCall { invitations.redeem(RedeemInvitationBody(owner.token.value, month)) }.wardId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = e.toInviteFailure()
                when {
                    failure.outcomeUnknown -> JoinState.Uncertain(ready.preview)
                    // An earlier request whose response was lost may have succeeded for this account.
                    failure == InviteFailure.ALREADY_REDEEMED -> membershipOrNull(wardId)?.let {
                        if (it) JoinState.Joined(wardId) else JoinState.Failed(failure)
                    } ?: JoinState.Uncertain(ready.preview)
                    failure == InviteFailure.ALREADY_MEMBER -> JoinState.AlreadyMember(wardId)
                    failure.endsInvitation || failure.endsSession -> JoinState.Failed(failure)
                    else -> ready.copy(submitting = false, error = failure)
                }
            }
            apply(owner, next)
        }
    }

    /** Resolves a lost join response by reading memberships, then the invitation, without resending. */
    fun checkResult() {
        val owner = active ?: return
        val uncertain = mutable.value as? JoinState.Uncertain ?: return
        if (uncertain.checking) return
        mutable.value = uncertain.copy(checking = true, error = null)
        val preview = uncertain.preview
        request = scope.launch {
            val next = try {
                if (apiCall { api.wards() }.items.any { it.wardId == preview.wardId }) {
                    JoinState.Joined(preview.wardId)
                } else {
                    JoinState.Ready(
                        apiCall { invitations.preview(InvitationTokenBody(owner.token.value)) },
                        notice = "가입이 완료되지 않았어요. 내용을 확인하고 다시 가입할 수 있어요.",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = e.toInviteFailure()
                if (failure.endsInvitation || failure.endsSession) JoinState.Failed(failure)
                else JoinState.Uncertain(preview, error = failure)
            }
            apply(owner, next)
        }
    }

    private suspend fun membershipOrNull(wardId: String): Boolean? = try {
        apiCall { api.wards() }.items.any { it.wardId == wardId }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Explicit cancel, terminal acknowledgement, or completion after navigating to the ward. */
    fun close() {
        active?.let { store.clear(it.seq) }
    }
}
