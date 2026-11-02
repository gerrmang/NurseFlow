package com.nurseflow.app.feature.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.network.MembershipDto
import com.nurseflow.app.core.ui.NfBadge
import com.nurseflow.app.core.ui.NfCard
import com.nurseflow.app.core.ui.NfError
import com.nurseflow.app.core.ui.NfLoading
import com.nurseflow.app.core.ui.NfOutlinedButton
import com.nurseflow.app.core.ui.NfPrimaryButton
import com.nurseflow.app.core.ui.NfScreen
import com.nurseflow.app.core.ui.NfText
import com.nurseflow.app.core.ui.NfTextLink
import com.nurseflow.app.ui.theme.NurseFlowColors as C

/**
 * Figma "01 병원·병동 선택" backed by GET /me. Only server memberships are listed; a user
 * without any sees the invitation-required state.
 */
@Composable
fun WardListScreen(
    viewModel: WardsViewModel,
    onOpenWard: (MembershipDto) -> Unit,
    onInvites: (MembershipDto) -> Unit,
    onMyProfile: (MembershipDto) -> Unit,
    onEnterInvite: () -> Unit,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val name = (state as? WardsUiState.Loaded)?.user?.displayName

    NfScreen(
        title = "병동 선택",
        subtitle = name?.let { "$it 님" },
        onBack = null,
        action = {
            TextButton({ menu = true }) { NfText("메뉴", 14, C.secondary, true) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { NfText("초대 코드 입력", 15) }, onClick = { menu = false; onEnterInvite() })
                DropdownMenuItem(text = { NfText("새로고침", 15) }, onClick = { menu = false; viewModel.reload() })
                DropdownMenuItem(text = { NfText("로그아웃", 15, C.danger) }, onClick = { menu = false; onLogout() })
            }
        },
    ) {
        when (val current = state) {
            WardsUiState.Loading -> NfLoading()
            is WardsUiState.Failed -> {
                NfError(current.message)
                NfOutlinedButton("다시 시도", viewModel::reload)
            }
            is WardsUiState.Loaded -> {
                val wards = current.user.memberships
                if (wards.isEmpty()) {
                    NoWardState(onEnterInvite)
                } else {
                    NfText("내 병동 ${wards.size}", 13, C.secondary, true)
                    wards.forEach { ward -> WardCard(ward, onOpenWard, onInvites, onMyProfile) }
                }
            }
        }
    }
}

@Composable
private fun WardCard(
    ward: MembershipDto,
    onOpen: (MembershipDto) -> Unit,
    onInvites: (MembershipDto) -> Unit,
    onMyProfile: (MembershipDto) -> Unit,
) {
    NfCard(onClick = { onOpen(ward) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NfText(ward.wardName, 16, C.text, true, Modifier.weight(1f))
            NfText("›", 20, C.muted)
        }
        Spacer(Modifier.height(6.dp))
        if (ward.isAdmin) NfBadge("관리자 · 편집 가능", C.brandSoft, C.brandDeep)
        else NfBadge("간호사 · 조회 전용", C.infoSoft, C.info)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (ward.isAdmin) NfTextLink("간호사 초대") { onInvites(ward) }
            else NfTextLink("내 경력 정보") { onMyProfile(ward) }
        }
    }
}

@Composable
private fun NoWardState(onEnterInvite: () -> Unit) {
    NfCard {
        NfText("아직 소속된 병동이 없어요", 17, C.text, true)
        Spacer(Modifier.height(6.dp))
        NfText("로그인만으로는 병동 근무표를 볼 수 없어요. 병동 관리자에게 초대 링크를 받아 가입해 주세요.", 14, C.secondary)
        Spacer(Modifier.height(16.dp))
        NfPrimaryButton("초대 코드 입력", onEnterInvite)
    }
    Column(horizontalAlignment = Alignment.Start) {
        Row { Spacer(Modifier.width(4.dp)); NfText("관리자라면 서버의 시연용 병동 생성 기능으로 첫 병동을 만들 수 있어요.", 12, C.muted) }
    }
}
