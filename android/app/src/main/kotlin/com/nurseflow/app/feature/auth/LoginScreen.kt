package com.nurseflow.app.feature.auth
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.ui.KakaoButton
import com.nurseflow.app.core.ui.NfError

/**
 * [invitationWaiting]: a link was received before login. Ward and role are not shown here:
 * preview requires login, so nothing is guessed. Cancelling login keeps the invitation.
 */
@Composable
fun LoginScreen(viewModel: LoginViewModel, invitationWaiting: Boolean = false, onCancelInvitation: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("NurseFlow")
        Spacer(Modifier.height(16.dp))
        if (invitationWaiting) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("병동 초대를 받았어요. 초대 내용을 확인하려면 먼저 로그인해 주세요.")
                    TextButton(onClick = onCancelInvitation) { Text("초대 취소") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Text("카카오로 로그인하고 내 병동을 확인하세요.")
        Spacer(Modifier.height(24.dp))
        NfError(state.error)
        KakaoButton(onClick = { viewModel.login(context) }, loading = state.loading)
    }
}
