package com.nurseflow.app.feature.invite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nurseflow.app.core.ui.NfText
import com.nurseflow.app.ui.theme.NurseFlowColors as C

/** Paste box for an invitation link, share message or raw code. [onSubmit] returns false when invalid. */
@Composable
fun InviteCodeDialog(onDismiss: () -> Unit, onSubmit: (String) -> Boolean) {
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { NfText("초대 코드 입력", 18, C.text, true) },
        text = {
            Column {
                NfText("관리자에게 받은 초대 링크나 코드를 붙여 넣어 주세요.", 14, C.secondary)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; invalid = false },
                    placeholder = { NfText("링크 또는 43자리 코드", 14, C.muted) },
                    isError = invalid,
                    minLines = 2,
                )
                if (invalid) NfText("초대 코드를 찾지 못했어요. 링크 전체를 다시 붙여 넣어 주세요.", 12, C.danger)
            }
        },
        confirmButton = { TextButton({ invalid = !onSubmit(input) }) { NfText("확인", 15, C.brandDeep, true) } },
        dismissButton = { TextButton(onDismiss) { NfText("취소", 15, C.secondary) } },
    )
}
