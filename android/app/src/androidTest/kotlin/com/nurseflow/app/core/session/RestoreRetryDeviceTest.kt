package com.nurseflow.app.core.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nurseflow.app.AppContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Tests the root UI's retry entry point with an isolated store; no Kakao or HTTP calls. */
@RunWith(AndroidJUnit4::class)
class RestoreRetryDeviceTest {
    @Test fun retrySurvivesDestroyedUiScopeAndLeavesLoadingOnSuccess() = runBlocking {
        verifyRetry(failRetry = false)
    }

    @Test fun retrySurvivesDestroyedUiScopeAndCanBeRetriedAfterFailure() = runBlocking {
        verifyRetry(failRetry = true)
    }

    private suspend fun verifyRetry(failRetry: Boolean) = withTimeout(5_000) {
        val entered = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        val store = object : TokenStore {
            var reads = 0
            override suspend fun read(): String? {
                when (++reads) {
                    1 -> throw IOException("fake initial restore failure")
                    2 -> {
                        entered.complete(Unit)
                        response.await()
                        if (failRetry) throw IOException("fake retry failure")
                    }
                }
                return null
            }
            override suspend fun write(refreshToken: String) = error("No credentials expected")
            override suspend fun clear() = Unit
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = AppContainer(context, "https://example.invalid/", store)
        container.session.state.first { it is SessionState.RestoreFailed }
        val uiJob = SupervisorJob()
        val uiScope = CoroutineScope(uiJob + Dispatchers.Main.immediate)
        val started = CompletableDeferred<Job>()
        var retry: Job? = null
        try {
            uiScope.launch {
                // Same entry point as the retry button. Its work must not become a UI child.
                started.complete(container.restoreSession())
                awaitCancellation()
            }
            val activeRetry = started.await()
            retry = activeRetry
            entered.await()
            assertEquals(SessionState.Restoring, container.session.state.value)
            // Rotation destroys the old composition's scope before its replacement is created.
            uiJob.cancelAndJoin()
            assertTrue("Retry must survive disposal of the old UI", activeRetry.isActive)
            response.complete(Unit)
            activeRetry.join()
            assertFalse(activeRetry.isCancelled)
            if (failRetry) {
                assertTrue(container.session.state.value is SessionState.RestoreFailed)
                withContext(Dispatchers.Main) { container.restoreSession() }.join()
            }
            assertEquals(SessionState.SignedOut, container.session.state.value)
            assertNull(container.session.accessToken)
        } finally {
            uiJob.cancel()
            response.complete(Unit)
            // App-owned work is always allowed to finish, even if an assertion fails.
            withContext(NonCancellable) { retry?.join() }
        }
    }
}
