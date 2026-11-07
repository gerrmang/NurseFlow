package com.nurseflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nurseflow.app.AppContainer
import com.nurseflow.app.core.session.SessionState
import com.nurseflow.app.feature.auth.LoginScreen
import com.nurseflow.app.feature.auth.LoginViewModel
import com.nurseflow.app.feature.ward.WardListScreen
import com.nurseflow.app.feature.ward.WardsViewModel
import kotlinx.coroutines.launch

@Composable
fun NurseFlowRoot(container: AppContainer) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var demo by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (demo) {
            BackHandler { demo = false }
            Text("가상 데이터 시연 · 서버와 공유되지 않음", Modifier.padding(8.dp))
            TextButton(onClick = { demo = false }) { Text("시연 나가기") }
            Box(Modifier.weight(1f)) { NurseFlowApp() }
        } else {
            val owner = remember(session) {
                object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
            }
            DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                Box(Modifier.weight(1f)) {
                    when (val current = session) {
                        SessionState.Restoring -> CircularProgressIndicator()
                        SessionState.SignedOut -> LoginScreen(viewModel { LoginViewModel(container.session, container.kakao) })
                        is SessionState.RestoreFailed -> Column(Modifier.padding(24.dp)) {
                            Text(current.message)
                            Button(onClick = { scope.launch { container.session.restore() } }) { Text("세션 복원 재시도") }
                            TextButton(onClick = container::logout) { Text("로그아웃하고 다시 로그인") }
                        }
                        is SessionState.SignedIn -> WardListScreen(viewModel { WardsViewModel(container.api) }, container::logout)
                    }
                }
            }
            TextButton(onClick = { demo = true }) { Text("가상 근무표 시연 보기 (서버 미연동)") }
        }
    }
}
