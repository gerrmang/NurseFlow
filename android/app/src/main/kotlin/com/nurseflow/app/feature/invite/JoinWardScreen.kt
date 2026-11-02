package com.nurseflow.app.feature.invite

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.network.MembershipDto
import com.nurseflow.app.core.ui.NfBadge
import com.nurseflow.app.core.ui.NfCard
import com.nurseflow.app.core.ui.NfError
import com.nurseflow.app.core.ui.NfInfo
import com.nurseflow.app.core.ui.NfOutlinedButton
import com.nurseflow.app.core.ui.NfPrimaryButton
import com.nurseflow.app.core.ui.NfScreen
import com.nurseflow.app.core.ui.NfText
import com.nurseflow.app.feature.profile.CareerStartPicker
import com.nurseflow.app.feature.profile.RankPicker
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import com.nurseflow.app.ui.theme.NurseFlowColors as C

@Composable
fun JoinWardScreen(viewModel: JoinWardViewModel, onBack: () -> Unit, onJoined: (MembershipDto) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.joined) { state.joined?.let(onJoined) }

    NfScreen(title = "병동 가입", subtitle = "초대받은 병동에만 가입할 수 있어요", onBack = onBack) {
        val preview = state.preview
        if (preview == null) {
            NfText("초대 링크 또는 코드", 13, C.secondary, true)
            OutlinedTextField(
                value = state.input, onValueChange = viewModel::onInput,
                placeholder = { NfText("관리자에게 받은 링크를 붙여 넣어 주세요", 14, C.muted) },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            NfError(state.error)
            NfPrimaryButton("초대 확인", viewModel::checkCode, enabled = state.input.isNotBlank(), loading = state.busy)
            return@NfScreen
        }

        NfCard {
            NfText(preview.wardName, 18, C.text, true)
            Spacer(Modifier.height(6.dp))
            if (preview.role == "WARD_ADMIN") NfBadge("관리자로 초대됨", C.brandSoft, C.brandDeep)
            else NfBadge("간호사로 초대됨", C.infoSoft, C.info)
            Spacer(Modifier.height(6.dp))
            NfText("초대 만료: ${formatInstant(preview.expiresAt)}", 12, C.muted)
        }

        if (state.isMemberInvite) {
            NfText("경력 정보", 16, C.text, true)
            NfText("입사 년월과 직급을 입력하면 관리자가 확인한 뒤 팀 경력 구성에 반영해요. 지금 건너뛰고 나중에 입력해도 돼요.", 13, C.secondary)
            CareerStartPicker(state.careerStartedOn, viewModel::onCareerStart)
            RankPicker(preview.ranks, state.rank, viewModel::onRank)
            if ((state.careerStartedOn == null) != (state.rank == null)) {
                NfInfo("입사 년월과 직급을 모두 선택하거나, 둘 다 비워 두세요.")
            }
        } else {
            NfInfo("관리자 계정은 근무 편성 대상에서 빠지고, 근무표 편집과 간호사 초대를 할 수 있어요.")
        }

        NfError(state.error)
        NfPrimaryButton("가입하기", viewModel::join, enabled = state.canJoin, loading = state.busy)
        NfOutlinedButton("다른 코드 입력", viewModel::reset)
    }
}

internal fun formatInstant(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
}.getOrDefault(iso)
