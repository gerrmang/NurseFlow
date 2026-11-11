package com.nurseflow.app.feature.ward.create

/** Where the session is, as far as the "create a ward after login" intent is concerned. */
enum class IntentPhase {
    /** Signed out or restoring: login may still be in progress. */
    WAITING,
    SIGNED_IN,
    /** Logout/restore failed: the login that the intent belonged to did not happen. */
    ENDED,
}

/** What to do with the intent: [open] the create screen and/or [clear] the intent. */
data class IntentResolution(val open: Boolean, val clear: Boolean) {
    companion object {
        val KEEP = IntentResolution(open = false, clear = false)
    }
}

/**
 * Decides the fate of the "log in, then create a ward" intent set from the login screen.
 *
 * - Only a sign-in consumes it, once: the caller clears it whenever [IntentResolution.clear] is set.
 * - A pending invitation always wins: the invitation flow runs and the intent is dropped, so the
 *   create screen never opens later behind it.
 * - Ended or failed sessions drop it. Because it is consumed at the first sign-in, a later logout,
 *   invalid session or switch to another account (different userId/generation) finds it already
 *   cleared and can never re-fire it.
 */
fun resolveCreateIntent(armed: Boolean, phase: IntentPhase, invitationPending: Boolean): IntentResolution = when {
    !armed -> IntentResolution.KEEP
    phase == IntentPhase.WAITING -> IntentResolution.KEEP
    phase == IntentPhase.ENDED -> IntentResolution(open = false, clear = true)
    invitationPending -> IntentResolution(open = false, clear = true)
    else -> IntentResolution(open = true, clear = true)
}
