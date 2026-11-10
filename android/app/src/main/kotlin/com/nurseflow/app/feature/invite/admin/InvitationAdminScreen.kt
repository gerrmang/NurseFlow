package com.nurseflow.app.feature.invite.admin

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.network.InvitationRoles
import com.nurseflow.app.core.ui.NfError
import com.nurseflow.app.feature.invite.formatInstant
import com.nurseflow.app.feature.invite.roleLabel

private sealed interface Confirm {
    data class Reissue(val row: InvitationRow) : Confirm
    data class Revoke(val row: InvitationRow) : Confirm
}

@Composable
fun InvitationAdminScreen(
    wardName: String,
    viewModel: InvitationAdminViewModel,
    onBack: () -> Unit,
    onForbidden: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var issuing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    LaunchedEffect(state.forbidden) {
        if (state.forbidden) { viewModel.acknowledgeForbidden(); onForbidden() }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("‹ 병동 목록") }
        Text("$wardName 초대 관리", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { issuing = true }, enabled = !state.busy && !state.loading) { Text("새 초대 발급") }
            TextButton(onClick = viewModel::reload, enabled = !state.loading) { Text("새로고침") }
        }
        Text("초대 링크는 1회용이며 발급 후 24시간 동안 유효해요. 링크는 발급 직후에만 볼 수 있어요.")
        state.notice?.let { notice ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(notice, Modifier.weight(1f))
                    TextButton(onClick = viewModel::dismissNotice) { Text("확인") }
                }
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            state.loading && state.rows.isEmpty() -> CircularProgressIndicator()
            state.loadError != null -> {
                NfError(state.loadError?.message)
                Button(onClick = viewModel::reload) { Text("다시 시도") }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                if (state.unlinked.isNotEmpty()) {
                    item { Text("미가입 간호사", style = MaterialTheme.typography.titleMedium) }
                    items(state.unlinked, key = { "nurse-${it.nurseId}" }) { nurse ->
                        Text("${nurse.displayName} · ${nurse.latest?.label ?: "초대 없음"}")
                    }
                }
                item { Text("초대 목록", style = MaterialTheme.typography.titleMedium) }
                if (state.rows.isEmpty()) item { Text("발급한 초대가 없어요.") }
                items(state.rows, key = { it.invitation.invitationId }) { row ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${row.status.label} · ${roleLabel(row.invitation.role)} ${row.shortId}", style = MaterialTheme.typography.titleSmall)
                            Text(row.target)
                            Text("발급 ${formatInstant(row.invitation.createdAt)} · 만료 ${formatInstant(row.invitation.expiresAt)}")
                            if (row.status.manageable) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { confirm = Confirm.Reissue(row) }, enabled = !state.busy) { Text("재발급") }
                                OutlinedButton(onClick = { confirm = Confirm.Revoke(row) }, enabled = !state.busy) { Text("철회") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (issuing) IssueDialog(state, onDismiss = { issuing = false }) { role, nurseId ->
        issuing = false
        viewModel.create(role, nurseId)
    }
    confirm?.let { action ->
        val (title, body) = when (action) {
            is Confirm.Reissue -> "초대를 재발급할까요?" to "기존 링크는 철회되고 같은 역할·대상으로 새 24시간 링크가 발급돼요."
            is Confirm.Revoke -> "초대를 철회할까요?" to "철회한 링크로는 가입할 수 없어요."
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (action) {
                        is Confirm.Reissue -> viewModel.reissue(action.row.invitation.invitationId)
                        is Confirm.Revoke -> viewModel.revoke(action.row.invitation.invitationId)
                    }
                }) { Text(if (action is Confirm.Reissue) "재발급" else "철회") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("취소") } },
        )
    }
    state.link?.let { link ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = {}, // closed only by the explicit button so the one-time link is not lost by accident
            title = { Text("초대 링크") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("이 링크는 지금만 표시돼요. 1회용이며 ${formatInstant(link.expiresAt)}까지 유효해요.")
                    Text("한 사람에게만 보내 주세요. 여러 명이 같은 링크로 가입할 수 없어요.")
                    Text(link.url, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { copyLink(context, link.url) }) { Text("복사") }
                        OutlinedButton(onClick = { shareLink(context, link.url) }) { Text("공유") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissLink) { Text("닫기") } },
        )
    }
}

@Composable
private fun IssueDialog(state: InvitationAdminState, onDismiss: () -> Unit, onIssue: (String, String?) -> Unit) {
    var role by remember { mutableStateOf(InvitationRoles.MEMBER) }
    var nurseId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 초대 발급") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("역할")
                listOf(InvitationRoles.MEMBER, InvitationRoles.ADMIN).forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().selectable(role == option, onClick = {
                            role = option
                            if (option == InvitationRoles.ADMIN) nurseId = null // admin invitations never link a nurse record
                        }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(role == option, onClick = null)
                        Text(roleLabel(option))
                    }
                }
                if (role == InvitationRoles.MEMBER) {
                    Text("연결할 간호사 기록 (선택)")
                    Row(Modifier.fillMaxWidth().selectable(nurseId == null, onClick = { nurseId = null }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(nurseId == null, onClick = null)
                        Text("연결하지 않음 · 가입 시 새 기록 생성")
                    }
                    state.invitableNurses.forEach { nurse ->
                        Row(Modifier.fillMaxWidth().selectable(nurseId == nurse.nurseId, onClick = { nurseId = nurse.nurseId }), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(nurseId == nurse.nurseId, onClick = null)
                            Text(nurse.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onIssue(role, if (role == InvitationRoles.MEMBER) nurseId else null) }) { Text("발급") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private fun copyLink(context: Context, url: String) {
    val clip = ClipData.newPlainText("NurseFlow 초대 링크", url)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Keeps the token out of clipboard previews and keyboard suggestions.
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

private fun shareLink(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, "초대 링크 공유"))
}
