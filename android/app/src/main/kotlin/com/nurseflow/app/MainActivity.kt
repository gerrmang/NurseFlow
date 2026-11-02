package com.nurseflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.nurseflow.app.core.session.SessionSource
import com.nurseflow.app.core.session.createSessionSource
import com.nurseflow.app.ui.AppRoot
import com.nurseflow.app.ui.theme.NurseFlowTheme

/** 화면 회전 등으로 Activity가 다시 만들어져도 메모리 세션을 유지한다. 디스크에는 저장하지 않는다. */
private object AppSession {
    val source: SessionSource by lazy { createSessionSource() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        setContent {
            NurseFlowTheme {
                AppRoot(AppSession.source, BuildConfig.API_BASE_URL)
            }
        }
    }
}
