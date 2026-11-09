package com.nurseflow.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.nurseflow.app.core.session.SessionState
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SessionViewModelStoreTest {
    class ScreenModel : ViewModel(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    private fun models(store: ViewModelStore) = ViewModelProvider(
        object : ViewModelStoreOwner { override val viewModelStore = store },
        ViewModelProvider.NewInstanceFactory(),
    )[SessionViewModelStore::class.java]

    private fun screen(owner: ViewModelStoreOwner) =
        ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory())[ScreenModel::class.java]

    @Test fun recreatedActivityOwnerKeepsLoginModelUntilSessionEnds() = runBlocking {
        val retained = ViewModelStore()
        val original = models(retained)
        val login = screen(original.ownerFor(SessionState.SignedOut))
        val authentication = CompletableDeferred<Unit>()
        var completed = false
        val job = login.viewModelScope.launch { authentication.await(); completed = true }
        // Activity recreation supplies a new owner over the retained Activity store.
        val recreated = models(retained)
        assertSame(original, recreated)
        assertSame(login, screen(recreated.ownerFor(SessionState.SignedOut)))
        assertFalse(login.cleared)
        assertTrue(job.isActive)
        authentication.complete(Unit)
        job.join()
        assertTrue(completed)
        recreated.ownerFor(SessionState.SignedIn("demo-user", "가상 사용자", 1))
        assertTrue(login.cleared)
        retained.clear()
    }

    @Test fun refreshKeepsWardModelButLogoutAndReloginReplaceIt() {
        val retained = ViewModelStore()
        val models = models(retained)
        val session = SessionState.SignedIn("demo-user", "가상 사용자", 1)
        val wards = screen(models.ownerFor(session))
        assertSame(wards, screen(models.ownerFor(session.copy(displayName = "변경된 가상 이름"))))
        assertFalse(wards.cleared)
        val login = screen(models.ownerFor(SessionState.SignedOut))
        assertTrue(wards.cleared)
        val next = screen(models.ownerFor(session.copy(generation = 2)))
        assertTrue(login.cleared)
        assertNotSame(wards, next)
        retained.clear()
        assertTrue(next.cleared)
    }

    @Test fun sessionEndCancelsPendingWorkAndFinalDestructionClearsLogin() {
        val retained = ViewModelStore()
        val models = models(retained)
        val session = SessionState.SignedIn("demo-user", "가상 사용자", 1)
        val wards = screen(models.ownerFor(session))
        val pending = wards.viewModelScope.launch { awaitCancellation() }
        // Even if composition misses SignedOut, a new session generation clears old work.
        val next = screen(models.ownerFor(session.copy(generation = 2)))
        assertTrue(pending.isCancelled)
        assertTrue(wards.cleared)
        models.ownerFor(SessionState.SignedOut)
        assertTrue(next.cleared)
        val login = screen(models.ownerFor(SessionState.SignedOut))
        val authentication = login.viewModelScope.launch { awaitCancellation() }
        retained.clear()
        assertTrue(authentication.isCancelled)
        assertTrue(login.cleared)
    }
}
