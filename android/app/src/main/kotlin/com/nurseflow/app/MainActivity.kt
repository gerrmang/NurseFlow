package com.nurseflow.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.nurseflow.app.feature.invite.InviteCode
import com.nurseflow.app.ui.NurseFlowRoot
import com.nurseflow.app.ui.theme.NurseFlowTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as NurseFlowApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        if (savedInstanceState == null) handleInviteLink(intent)
        setContent {
            NurseFlowTheme {
                NurseFlowRoot(container)
            }
        }
    }

    /** App already open (singleTask): a newly tapped invite link arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInviteLink(intent)
    }

    /** nurseflow://invite#token=… → remember the code; login (if needed) then opens the join screen. */
    private fun handleInviteLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val link = intent.data?.takeIf { it.scheme == "nurseflow" && it.host == "invite" } ?: return
        InviteCode.extract(link.toString())?.let { container.session.pendingInviteCode = it }
    }
}
