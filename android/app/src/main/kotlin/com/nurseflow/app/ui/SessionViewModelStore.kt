package com.nurseflow.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.nurseflow.app.core.session.SessionState

/** Retained by the Activity; child models end with the session or the Activity's final destruction. */
class SessionViewModelStore : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private var identity: Pair<String, Long>? = null

    fun ownerFor(current: SessionState): ViewModelStoreOwner {
        val next = (current as? SessionState.SignedIn)?.let { it.userId to it.generation }
        if (identity != next) {
            viewModelStore.clear()
            identity = next
        }
        return this
    }

    override fun onCleared() {
        viewModelStore.clear()
    }
}
