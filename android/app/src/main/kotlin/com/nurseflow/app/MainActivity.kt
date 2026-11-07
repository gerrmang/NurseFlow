package com.nurseflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.nurseflow.app.ui.NurseFlowRoot
import com.nurseflow.app.ui.theme.NurseFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        val container = (application as NurseFlowApplication).container
        setContent {
            NurseFlowTheme {
                NurseFlowRoot(container)
            }
        }
        val keyHash = com.kakao.sdk.common.util.Utility.getKeyHash(this)
        android.util.Log.d("KAKAO_HASH", keyHash)
    }
}
