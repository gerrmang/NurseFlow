package com.nurseflow.app.feature.profile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.network.CareerProfileDto
import com.nurseflow.app.core.ui.NfBadge
import com.nurseflow.app.core.ui.NfCard
import com.nurseflow.app.core.ui.NfError
import com.nurseflow.app.core.ui.NfInfo
import com.nurseflow.app.core.ui.NfLoading
import com.nurseflow.app.core.ui.NfOutlinedButton
import com.nurseflow.app.core.ui.NfPrimaryButton
import com.nurseflow.app.core.ui.NfScreen
import com.nurseflow.app.core.ui.NfText
import com.nurseflow.app.ui.theme.NurseFlowColors as C

/** Nurse's own career start and rank. Team is assigned by the admin and shown read-only. */
@Composable
fun MyProfileScreen(viewModel: MyProfileViewModel, wardName: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    NfScreen(title = "내 경력 정보", subtitle = wardName, onBack = onBack) {
        val member = state.member
        if (state.loading) { NfLoading(); return@NfScreen }
        if (member == null) {
            NfError(state.error)
            NfOutlinedButton("다시 시도", viewModel::load)
            return@NfScreen
        }

        NfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NfText(member.displayName, 17, C.text, true, Modifier.weight(1f))
                StatusBadge(member.profile.status)
            }
            Spacer(Modifier.height(8.dp))
            NfText("팀: ${member.team?.name ?: "미배정 (관리자가 지정해요)"}", 14, C.secondary)
            StageLine(member.profile)
        }

        if (member.profile.status == "PENDING") {
            NfInfo("관리자가 확인하기 전까지는 새로 입력한 정보가 팀 경력 구성에 반영되지 않아요.")
        }

        CareerStartPicker(state.careerStartedOn, viewModel::onCareerStart)
        RankPicker(state.ranks, state.rank, viewModel::onRank)
        NfError(state.error)
        if (state.saved) NfInfo("저장했어요. 관리자 확인을 기다리고 있어요.")
        NfPrimaryButton("저장", viewModel::save, enabled = state.canSave, loading = state.saving)
    }
}

@Composable
private fun StatusBadge(status: String) = when (status) {
    "CONFIRMED" -> NfBadge(profileStatusLabel(status), C.brandSoft, C.brandDeep)
    "PENDING" -> NfBadge(profileStatusLabel(status), C.warningSoft, C.warning)
    else -> NfBadge(profileStatusLabel(status), C.offBg, C.offFg)
}

@Composable
private fun StageLine(profile: CareerProfileDto) {
    val stage = profile.scoringStage ?: profile.submittedStage ?: return
    val note = if (profile.scoringStage != null) "확인됨" else "확인 전"
    NfText("경력 단계: ${stage}단계 ($note)", 14, C.secondary)
}
