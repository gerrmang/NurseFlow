package com.nurseflow.app.feature.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nurseflow.app.core.ui.NfError

/**
 * Manual entry for messengers that do not linkify the custom scheme. The clipboard is never
 * read programmatically: the user pastes into the field. Input is not saved and not echoed in
 * errors.
 */
@Composable
fun PasteInviteDialog(onDismiss: () -> Unit, onAccepted: (InviteToken) -> Unit) {
    var text by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("초대 링크 붙여넣기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("관리자에게 받은 초대 링크 전체를 붙여넣어 주세요.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; invalid = false },
                    label = { Text("초대 링크") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (invalid) NfError("올바른 초대 링크가 아니에요.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val token = parseInviteLink(text)
                if (token == null) invalid = true else { text = ""; onAccepted(token) }
            }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
