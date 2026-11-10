package com.nurseflow.app.feature.invite

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.network.InvitationPreviewDto
import com.nurseflow.app.core.network.InvitationRoles
import com.nurseflow.app.core.ui.NfError

internal fun roleLabel(role: String) = when (role) {
    InvitationRoles.ADMIN -> "관리자"
    InvitationRoles.MEMBER -> "간호사"
    else -> "알 수 없는 역할"
}

/**
 * Preview → (joined month for WARD_MEMBER) → join. [onEnterWard] is called once for a joined or
 * already-joined ward; the caller refreshes memberships and opens that ward.
 */
@Composable
fun JoinInvitationScreen(viewModel: JoinInvitationViewModel, onEnterWard: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Text field content is kept only for this composition: it is not the token and not persisted.
    var joinedOn by remember { mutableStateOf("") }
    LaunchedEffect(state) {
        (state as? JoinState.Joined)?.let { onEnterWard(it.wardId); viewModel.close() }
    }
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("병동 초대", style = MaterialTheme.typography.headlineSmall)
        when (val current = state) {
            JoinState.Idle, JoinState.Loading, is JoinState.Joined -> CircularProgressIndicator()
            is JoinState.Ready -> {
                PreviewCard(current.preview)
                current.notice?.let { Text(it) }
                if (current.preview.role == InvitationRoles.MEMBER) {
                    Text("이 병원에 입사한 연월을 입력해 주세요. 직급·연차는 관리자가 확인합니다.")
                    OutlinedTextField(
                        value = joinedOn,
                        onValueChange = { joinedOn = it.take(7) },
                        label = { Text("병원 입사 연월 (YYYY-MM)") },
                        placeholder = { Text("2020-03") },
                        singleLine = true,
                        enabled = !current.submitting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("관리자 초대는 입사 연월 입력 없이 가입합니다.")
                }
                NfError(current.error?.message)
                Button(
                    onClick = { viewModel.redeem(joinedOn) },
                    enabled = !current.submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (current.submitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("가입하기")
                }
                TextButton(onClick = viewModel::close, enabled = !current.submitting) { Text("초대 취소") }
            }
            is JoinState.Uncertain -> {
                PreviewCard(current.preview)
                Text("가입 결과를 확인하지 못했어요. 다시 보내기 전에 결과를 확인해 주세요.")
                NfError(current.error?.message)
                Button(onClick = viewModel::checkResult, enabled = !current.checking, modifier = Modifier.fillMaxWidth()) {
                    if (current.checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("결과 확인")
                }
                TextButton(onClick = viewModel::close, enabled = !current.checking) { Text("초대 닫기") }
            }
            is JoinState.AlreadyMember -> {
                Text(InviteFailure.ALREADY_MEMBER.message)
                Text("이 초대는 사용되지 않았어요.")
                Button(onClick = { onEnterWard(current.wardId); viewModel.close() }) { Text("병동으로 이동") }
                TextButton(onClick = viewModel::close) { Text("닫기") }
            }
            is JoinState.Failed -> {
                NfError(current.failure.message)
                if (current.failure.endsInvitation) {
                    Button(onClick = viewModel::close) { Text("닫기") }
                } else {
                    Button(onClick = viewModel::retryPreview) { Text("다시 시도") }
                    TextButton(onClick = viewModel::close) { Text("초대 취소") }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(preview: InvitationPreviewDto) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            preview.hospitalName?.takeIf { it.isNotBlank() }?.let { Text("병원: $it") }
            Text(preview.wardName, style = MaterialTheme.typography.titleMedium)
            Text("초대 역할: ${roleLabel(preview.role)}")
            Text("만료: ${formatInstant(preview.expiresAt)}")
            if (preview.linksExistingNurse) Text("관리자가 등록한 간호사 기록과 연결됩니다.")
        }
    }
}
