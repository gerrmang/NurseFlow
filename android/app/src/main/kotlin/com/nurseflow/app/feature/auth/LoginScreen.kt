package com.nurseflow.app.feature.auth
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.ui.KakaoButton
import com.nurseflow.app.core.ui.NfError

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("NurseFlow")
        Spacer(Modifier.height(16.dp))
        Text("카카오로 로그인하고 내 병동을 확인하세요.")
        Spacer(Modifier.height(24.dp))
        NfError(state.error)
        KakaoButton(onClick = { viewModel.login(context) }, loading = state.loading)
    }
}
