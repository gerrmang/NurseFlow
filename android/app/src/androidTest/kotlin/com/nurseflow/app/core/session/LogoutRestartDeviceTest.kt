package com.nurseflow.app.core.session

import android.os.Process
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nurseflow.app.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.security.KeyStore

/** Real DataStore/Keystore, injected file-removal and server-revocation failures. */
@RunWith(AndroidJUnit4::class)
class LogoutRestartDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val tokenKey = stringPreferencesKey("refresh_token")
    private val phase get() = InstrumentationRegistry.getArguments().getString("logoutRestartPhase")

    @Test fun failedFileDeletionLeavesOnlyUnreadableCiphertextAndReloginStillWorks() = runBlocking {
        val fixture = Fixture("logout_crypto_erasure")
        try {
            val api = FakeAuth()
            val manager = SessionManager(api, fixture.secure())
            manager.loginWithKakao("fake-id", "fake-nonce")
            val ciphertext = fixture.raw.data.first()[tokenKey]
            assertNotNull(ciphertext)
            fixture.faults.rejectUpdates = true
            manager.logout()
            assertEquals(SessionState.LogoutFailed, manager.state.value)
            assertNull(manager.accessToken)
            assertEquals(1, api.logoutCalls)
            assertFalse(fixture.hasKey())
            assertEquals(ciphertext, fixture.raw.data.first()[tokenKey])
            assertNull(fixture.secure().read())
            assertFalse("A read must not recreate the revoked key", fixture.hasKey())

            val restarted = SessionManager(api, fixture.secure())
            restarted.restore()
            assertEquals(SessionState.LogoutFailed, restarted.state.value)
            assertNull(restarted.accessToken)
            assertEquals(0, api.refreshCalls)
            fixture.faults.rejectUpdates = false
            restarted.logout()
            assertEquals(SessionState.SignedOut, restarted.state.value)
            assertNull(fixture.raw.data.first()[tokenKey])
            restarted.loginWithKakao("fake-id", "fake-nonce")
            assertTrue(restarted.state.value is SessionState.SignedIn)
            assertEquals("fake-restart-refresh", fixture.secure().read())
            // Replaying the old ciphertext under the fresh key cannot recover a credential.
            fixture.raw.edit { it[tokenKey] = requireNotNull(ciphertext) }
            assertNull(fixture.secure().read())
            assertFalse(fixture.hasKey())
        } finally { fixture.cleanup() }
    }

    @Test fun absentKeyIsNeverRecreatedByReadingResidualCiphertext() = runBlocking {
        val fixture = Fixture("logout_read_without_key")
        try {
            fixture.secure().write("fake-read-refresh")
            fixture.keys().deleteEntry(fixture.alias)
            assertNull(fixture.secure().read())
            assertFalse(fixture.hasKey())
            assertNotNull(fixture.raw.data.first()[tokenKey])
        } finally { fixture.cleanup() }
    }

    @Test fun prepareFailedDeletionAndOfflineRevocation() = runBlocking {
        assumeTrue("Run the prepare phase before force-stopping the app", phase == "prepare")
        val fixture = Fixture("logout_process_restart")
        var prepared = false
        try {
            val api = FakeAuth()
            val manager = SessionManager(api, fixture.secure())
            manager.loginWithKakao("fake-id", "fake-nonce")
            fixture.faults.rejectUpdates = true
            manager.logout()
            assertEquals(SessionState.LogoutFailed, manager.state.value)
            assertNull(manager.accessToken)
            assertEquals(1, api.logoutCalls)
            assertFalse(fixture.hasKey())
            assertNotNull(fixture.raw.data.first()[tokenKey])
            fixture.marker.writeText(Process.myPid().toString())
            prepared = true
        } finally {
            if (prepared) fixture.close() else fixture.cleanup()
        }
    }

    @Test fun verifyNewProcessCannotRestoreResidualCredential() = runBlocking {
        assumeTrue("Run the verify phase after force-stopping the prepared app", phase == "verify")
        val fixture = Fixture("logout_process_restart")
        try {
            assertTrue("Prepare phase must have completed", fixture.marker.exists())
            assertNotEquals("Verification must run in another OS process",
                fixture.marker.readText().toInt(), Process.myPid())
            assertNotNull("Keep the failed removal's ciphertext across process death", fixture.raw.data.first()[tokenKey])
            assertFalse(fixture.hasKey())
            fixture.faults.rejectUpdates = true // Removal is still broken after restarting.
            val api = FakeAuth()
            val manager = SessionManager(api, fixture.secure())
            manager.restore()
            assertEquals(SessionState.LogoutFailed, manager.state.value)
            assertNull(manager.accessToken)
            assertEquals(0, api.refreshCalls)
            assertFalse(fixture.hasKey())
            fixture.faults.rejectUpdates = false
            manager.logout()
            assertEquals(SessionState.SignedOut, manager.state.value)
            assertNull(fixture.raw.data.first()[tokenKey])
            assertEquals(0, api.logoutCalls) // No revoked credential is sent back to the server.
        } finally { fixture.cleanup() }
    }

    private inner class Fixture(name: String) {
        val alias = "nurseflow_test_$name"
        private val job = SupervisorJob()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
            File(context.noBackupFilesDir, "$name.preferences_pb")
        }
        val faults = FailingUpdates(raw)
        val marker = File(context.noBackupFilesDir, "$name.prepared")
        fun secure() = SecureTokenStore(context, faults, alias)
        fun keys() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        fun hasKey() = keys().containsAlias(alias)
        suspend fun close() { job.cancelAndJoin() }
        suspend fun cleanup() {
            try {
                faults.rejectUpdates = false
                secure().clear()
                marker.delete()
            } finally { close() }
        }
    }

    private class FailingUpdates(private val delegate: DataStore<Preferences>) : DataStore<Preferences> by delegate {
        @Volatile var rejectUpdates = false
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (rejectUpdates) throw IOException("fake token file removal failure")
            return delegate.updateData(transform)
        }
    }

    private class FakeAuth : AuthApi {
        var refreshCalls = 0
        var logoutCalls = 0
        override suspend fun kakaoLogin(body: KakaoLoginBody) = SessionDto(
            "fake-restart-access", "2099-01-01T00:00:00Z", "fake-restart-refresh",
            "2099-02-01T00:00:00Z", SessionUserDto("fake-restart-user", "가상 재시작 사용자"),
        )
        override suspend fun refresh(body: RefreshTokenBody): SessionDto {
            refreshCalls++
            return kakaoLogin(KakaoLoginBody("fake-id", "fake-nonce"))
        }
        override suspend fun logout(body: RefreshTokenBody) {
            logoutCalls++
            throw ApiException(0, "NETWORK", "fake offline revocation")
        }
    }
}
