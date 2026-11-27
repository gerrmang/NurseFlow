package com.nurseflow.app.feature.holiday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nurseflow.app.ui.theme.NurseFlowTheme

/** Non-exported debug-only host for Activity recreation tests without external network/settings. */
class HolidaySettingsTestActivity : ComponentActivity() {
    companion object { var createModel: (() -> HolidaySettingsViewModel)? = null }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model = viewModel { checkNotNull(createModel) { "Test factory must be supplied" }.invoke() }
            NurseFlowTheme { HolidaySettingsScreen("가상 회전 병동", model, onBack = { finish() }, onForbidden = { finish() }) }
        }
    }
}
