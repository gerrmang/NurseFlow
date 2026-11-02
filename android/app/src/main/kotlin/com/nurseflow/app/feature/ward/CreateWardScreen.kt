package com.nurseflow.app.feature.ward

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.network.MembershipDto
import com.nurseflow.app.core.ui.NfError
import com.nurseflow.app.core.ui.NfInfo
import com.nurseflow.app.core.ui.NfPrimaryButton
import com.nurseflow.app.core.ui.NfScreen
import com.nurseflow.app.core.ui.NfText
import com.nurseflow.app.ui.theme.NurseFlowColors as C

/**
 * Ward administrator onboarding: create the ward, become its WARD_ADMIN, then invite nurses.
 * The server only allows this with its ward-creation code (NURSEFLOW_BOOTSTRAP_CODE).
 */
@Composable
fun CreateWardScreen(viewModel: CreateWardViewModel, onBack: () -> Unit, onCreated: (MembershipDto) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.created) { state.created?.let(onCreated) }

    NfScreen(title = "병동 만들기", subtitle = "만든 사람이 이 병동의 관리자가 돼요", onBack = onBack) {
        NfText("병동 이름", 13, C.secondary, true)
        OutlinedTextField(
            value = state.name, onValueChange = viewModel::onName, singleLine = true,
            placeholder = { NfText("예: 7병동 · 내과", 14, C.muted) },
            modifier = Modifier.fillMaxWidth(),
        )
        NfText("병동 생성 코드", 13, C.secondary, true)
        OutlinedTextField(
            value = state.code, onValueChange = viewModel::onCode, singleLine = true,
            placeholder = { NfText("서버 관리자에게 받은 코드", 14, C.muted) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        NfInfo("병동을 만든 뒤 간호사를 초대할 수 있어요. 관리자 계정은 근무 편성 대상에서 빠져요.")
        NfError(state.error)
        NfPrimaryButton("병동 만들기", viewModel::submit, enabled = state.canSubmit, loading = state.busy)
    }
}
