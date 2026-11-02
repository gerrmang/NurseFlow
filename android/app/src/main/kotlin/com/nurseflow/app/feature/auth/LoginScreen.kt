package com.nurseflow.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.ui.KakaoButton
import com.nurseflow.app.core.ui.NfError
import com.nurseflow.app.core.ui.NfInfo
import com.nurseflow.app.core.ui.NfText
import com.nurseflow.app.core.ui.NfTextLink
import com.nurseflow.app.feature.invite.InviteCodeDialog
import com.nurseflow.app.ui.theme.NurseFlowColors as C

/** Figma "00 로그인 (카카오)". Hospital-account login is outside the demo MVP and not shown. */
@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var inviteDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(C.canvas).padding(horizontal = 20.dp).navigationBarsPadding()) {
        Spacer(Modifier.height(72.dp))
        Box(Modifier.size(64.dp).background(C.brand, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            NfText("✚", 30, Color.White, true)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = C.text)) { append("Nurse") }
                withStyle(SpanStyle(color = C.brand)) { append("Flow") }
            },
            fontSize = 34.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        NfText("병동 근무표, 규칙 확인은\n시스템이 대신해요", 22, C.text, true)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("D", C.dayBg, C.dayFg), Triple("E", C.eveningBg, C.eveningFg),
                Triple("N", C.nightBg, C.nightFg), Triple("O", C.offBg, C.offFg)).forEach { (code, bg, fg) ->
                Box(Modifier.size(28.dp).background(bg, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    NfText(code, 13, fg, true)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.pendingInvite) NfInfo("초대 코드를 받았어요. 로그인하면 병동 가입을 이어서 진행해요.")
            NfError(state.error)
            KakaoButton(onClick = { viewModel.login(context) }, loading = state.loading)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            NfText("초대 코드가 있나요?", 14, C.secondary)
            NfTextLink("코드 입력") { inviteDialog = true }
        }
        NfText(
            "로그인하면 이용약관 및 개인정보처리방침에 동의하게 돼요.", 12, C.muted,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp),
        )
    }

    if (inviteDialog) {
        InviteCodeDialog(
            onDismiss = { inviteDialog = false },
            onSubmit = { input -> viewModel.rememberInvite(input).also { if (it) inviteDialog = false } },
        )
    }
}
