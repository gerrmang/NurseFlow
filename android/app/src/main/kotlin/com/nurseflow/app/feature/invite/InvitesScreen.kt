package com.nurseflow.app.feature.invite

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.network.CreatedInvitationDto
import com.nurseflow.app.core.network.InvitationDto
import com.nurseflow.app.core.ui.NfBadge
import com.nurseflow.app.core.ui.NfCard
import com.nurseflow.app.core.ui.NfError
import com.nurseflow.app.core.ui.NfInfo
import com.nurseflow.app.core.ui.NfLoading
import com.nurseflow.app.core.ui.NfOutlinedButton
import com.nurseflow.app.core.ui.NfPrimaryButton
import com.nurseflow.app.core.ui.NfScreen
import com.nurseflow.app.core.ui.NfText
import com.nurseflow.app.core.ui.NfTextLink
import com.nurseflow.app.ui.theme.NurseFlowColors as C

/** Admin: issue a one-time invitation, share it, and revoke unused ones. */
@Composable
fun InvitesScreen(viewModel: InvitesViewModel, wardName: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    NfScreen(title = "간호사 초대", subtitle = wardName, onBack = onBack) {
        val created = state.created
        if (created != null) {
            CreatedCard(created, wardName, context, onDone = viewModel::dismissCreated)
        } else {
            NfCard {
                NfText("새 초대 만들기", 16, C.text, true)
                Spacer(Modifier.height(10.dp))
                NfText("역할", 13, C.secondary, true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("간호사", state.role == "WARD_MEMBER") { viewModel.onRole("WARD_MEMBER") }
                    Choice("관리자", state.role == "WARD_ADMIN") { viewModel.onRole("WARD_ADMIN") }
                }
                Spacer(Modifier.height(8.dp))
                NfText("유효 기간", 13, C.secondary, true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(24 to "1일", 72 to "3일", 168 to "7일").forEach { (hours, label) ->
                        Choice(label, state.expiresInHours == hours) { viewModel.onExpiry(hours) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                NfPrimaryButton("초대 만들기", viewModel::create, loading = state.creating)
            }
        }
        NfError(state.error)

        NfText("초대 기록", 13, C.secondary, true)
        when {
            state.loading -> NfLoading()
            state.invitations.isEmpty() -> NfText("아직 만든 초대가 없어요.", 14, C.muted)
            else -> state.invitations.forEach { InvitationRow(it, onRevoke = { viewModel.revoke(it.invitationId) }) }
        }
    }
}

@Composable
private fun CreatedCard(created: CreatedInvitationDto, wardName: String, context: Context, onDone: () -> Unit) {
    val link = created.inviteUrl
    val shareText = buildString {
        append("[NurseFlow] $wardName 병동 초대\n")
        if (link != null) append(link).append('\n')
        append("앱에서 ‘초대 코드 입력’에 붙여 넣어 주세요.\n코드: ${created.token}\n")
        append("만료: ${formatInstant(created.invitation.expiresAt)}")
    }
    NfCard {
        NfText("초대를 만들었어요", 16, C.text, true)
        Spacer(Modifier.height(6.dp))
        NfInfo("이 코드는 지금 한 번만 볼 수 있어요. 공유하거나 복사해 두세요. 한 사람만 사용할 수 있어요.")
        Spacer(Modifier.height(8.dp))
        NfText(created.token, 13, C.text)
        Spacer(Modifier.height(12.dp))
        NfPrimaryButton("공유하기", {
            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }
            context.startActivity(Intent.createChooser(send, "초대 공유"))
        })
        Spacer(Modifier.height(8.dp))
        NfOutlinedButton("복사", {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("NurseFlow 초대", shareText))
            Toast.makeText(context, "복사했어요", Toast.LENGTH_SHORT).show()
        })
        NfTextLink("새 초대 만들기", onClick = onDone)
    }
}

@Composable
private fun InvitationRow(invitation: InvitationDto, onRevoke: () -> Unit) {
    NfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NfText(if (invitation.role == "WARD_ADMIN") "관리자 초대" else "간호사 초대", 15, C.text, true, Modifier.weight(1f))
            when (invitation.status) {
                "PENDING" -> NfBadge("대기", C.warningSoft, C.warning)
                "REDEEMED" -> NfBadge("가입 완료", C.brandSoft, C.brandDeep)
                "REVOKED" -> NfBadge("철회됨", C.offBg, C.offFg)
                else -> NfBadge("만료", C.offBg, C.offFg)
            }
        }
        NfText("만료 ${formatInstant(invitation.expiresAt)}", 12, C.muted)
        if (invitation.status == "PENDING") NfTextLink("철회", C.danger, onRevoke)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { NfText(label, 14, if (selected) C.brandDeep else C.text, selected) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.brandSoft),
    )
}
