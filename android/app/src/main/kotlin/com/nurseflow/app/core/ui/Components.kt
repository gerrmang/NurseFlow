package com.nurseflow.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nurseflow.app.ui.theme.NurseFlowColors as C

@Composable
fun KakaoButton(onClick: () -> Unit, loading: Boolean) {
    Button(
        onClick, Modifier.fillMaxWidth().height(52.dp), enabled = !loading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFEE500), disabledContainerColor = Color(0xFFFEE500),
            contentColor = Color(0xD9000000),
        ),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xD9000000), strokeWidth = 2.dp)
        else Text("카카오 로그인")
    }
}

@Composable
fun NfError(message: String?) {
    if (message != null) Text(
        message, color = C.danger,
        modifier = Modifier.fillMaxWidth().background(C.dangerSoft, RoundedCornerShape(12.dp)).padding(12.dp),
    )
}
