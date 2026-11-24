package com.nurseflow.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.nurseflow.app.feature.invite.parseInviteLink
import com.nurseflow.app.ui.NurseFlowRoot
import com.nurseflow.app.ui.theme.NurseFlowTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as NurseFlowApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        // A recreated Activity (rotation, or process restore that replays the launch intent) must
        // not re-handle the original link: the invitation lives in memory or was already finished.
        if (savedInstanceState == null) receiveInvite(intent)
        val container = container
        setContent {
            NurseFlowTheme {
                NurseFlowRoot(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveInvite(intent)
    }

    /** Takes the token from the link fragment and drops the link from the Activity's intent. Never logged. */
    private fun receiveInvite(source: Intent?) {
        if (source?.action != Intent.ACTION_VIEW) return
        val raw = source.dataString ?: return
        setIntent(Intent(source).setData(null))
        val token = parseInviteLink(raw)
        if (token != null) container.invites.offer(token) else container.invites.reportInvalidLink()
    }
}
