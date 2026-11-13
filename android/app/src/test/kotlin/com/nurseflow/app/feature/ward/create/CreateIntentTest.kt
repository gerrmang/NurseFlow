package com.nurseflow.app.feature.ward.create

import org.junit.Assert.*
import org.junit.Test

class CreateIntentTest {
    @Test fun notArmedDoesNothing() {
        for (phase in IntentPhase.values()) for (invite in listOf(true, false)) {
            assertEquals(IntentResolution.KEEP, resolveCreateIntent(false, phase, invite))
        }
    }

    @Test fun staysArmedWhileLoginIsStillPossible() {
        assertEquals(IntentResolution.KEEP, resolveCreateIntent(true, IntentPhase.WAITING, invitationPending = false))
        assertEquals(IntentResolution.KEEP, resolveCreateIntent(true, IntentPhase.WAITING, invitationPending = true))
    }

    @Test fun signInOpensTheCreateScreenAndConsumesTheIntent() {
        assertEquals(IntentResolution(open = true, clear = true), resolveCreateIntent(true, IntentPhase.SIGNED_IN, false))
    }

    @Test fun pendingInvitationWinsAndTheIntentIsDroppedNotDeferred() {
        val resolution = resolveCreateIntent(true, IntentPhase.SIGNED_IN, invitationPending = true)
        assertFalse(resolution.open)
        assertTrue(resolution.clear)
    }

    @Test fun failedLogoutOrRestoreDropsTheIntent() {
        assertEquals(IntentResolution(open = false, clear = true), resolveCreateIntent(true, IntentPhase.ENDED, false))
    }

    @Test fun afterConsumptionLogoutSessionInvalidAndAccountSwitchNeverRefire() {
        // The caller applies "clear" at the first sign-in; a later logout, invalid session or sign-in
        // of another account (different userId/generation) then resolves with armed = false.
        var armed = true
        val first = resolveCreateIntent(armed, IntentPhase.SIGNED_IN, false)
        if (first.clear) armed = false
        assertTrue(first.open)
        for (phase in listOf(IntentPhase.WAITING, IntentPhase.SIGNED_IN, IntentPhase.ENDED, IntentPhase.SIGNED_IN)) {
            assertEquals(IntentResolution.KEEP, resolveCreateIntent(armed, phase, false))
        }
    }

    @Test fun cancelledIntentNeverOpens() {
        val armed = false // the cancel button or the plain login button sets this
        assertFalse(resolveCreateIntent(armed, IntentPhase.SIGNED_IN, false).open)
    }
}
