package com.nurseflow.app.feature.invite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One received invitation. [seq] identifies it so late responses for an older link are dropped. */
data class PendingInvitation(val seq: Long, val token: InviteToken)

/**
 * Process-memory holder for the invitation being handled. It is deliberately not persisted
 * (no SavedState, Bundle, DataStore or log): rotation and Activity recreation keep it, while a
 * process restart drops it and the user opens the link again.
 *
 * Cleared on join, explicit cancel, terminal error acknowledgement and when the signed-in
 * account ends (see AppContainer).
 */
class PendingInvitationStore {
    private val mutable = MutableStateFlow<PendingInvitation?>(null)
    val current: StateFlow<PendingInvitation?> = mutable.asStateFlow()
    private val invalid = MutableStateFlow(false)
    /** A link with the invitation scheme arrived but could not be parsed. */
    val invalidLink: StateFlow<Boolean> = invalid.asStateFlow()
    private var seq = 0L

    /** The same token again (duplicate tap or link) keeps the in-flight flow instead of restarting it. */
    @Synchronized
    fun offer(token: InviteToken): PendingInvitation {
        invalid.value = false
        mutable.value?.takeIf { it.token == token }?.let { return it }
        return PendingInvitation(++seq, token).also { mutable.value = it }
    }

    fun reportInvalidLink() { invalid.value = true }
    fun dismissInvalidLink() { invalid.value = false }

    /** Clears only if [onlySeq] is still current (or always when null). */
    @Synchronized
    fun clear(onlySeq: Long? = null) {
        if (onlySeq == null || mutable.value?.seq == onlySeq) mutable.value = null
        if (onlySeq == null) invalid.value = false
    }
}
