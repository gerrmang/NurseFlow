package com.nurseflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nurseflow.app.core.network.ApiClient
import com.nurseflow.app.core.network.UrlConnectionTransport
import com.nurseflow.app.core.session.SessionSource
import com.nurseflow.app.feature.organization.data.RemoteOrganizationRepository
import com.nurseflow.app.feature.organization.ui.WardSelectionRoute
import com.nurseflow.app.feature.organization.ui.WardSelectionViewModel
import com.nurseflow.app.ui.theme.NurseFlowColors as C

/**
 * 앱 진입점. 서버 연동 화면과 가상 시연(DemoRoster) 화면을 분리한다.
 * 시연 화면은 명시적으로 들어가며 서버 병동·사용자 데이터와 섞지 않는다.
 */
@Composable
fun AppRoot(session: SessionSource, apiBaseUrl: String) {
    var demo by rememberSaveable { mutableStateOf(false) }
    val token by session.token.collectAsStateWithLifecycle()
    val repository = remember(apiBaseUrl) {
        RemoteOrganizationRepository(ApiClient(apiBaseUrl, session, UrlConnectionTransport()))
    }

    Surface(color = C.canvas, modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        when {
            demo -> DemoFrame(onExit = { demo = false })
            apiBaseUrl.isBlank() -> GateScreen(
                title = "서버 연결이 설정되지 않았습니다",
                body = "이 빌드에는 NurseFlow 서버 주소와 로그인이 아직 없습니다. Kakao 로그인 연동 뒤 사용할 수 있습니다.",
                onOpenDemo = { demo = true },
            )
            token == null && session.acceptsDeveloperToken -> DeveloperTokenScreen(session::useDeveloperToken, onOpenDemo = { demo = true })
            token == null -> GateScreen(
                title = "로그인이 필요합니다",
                body = "Kakao 로그인 연동은 준비 중입니다. 로그인 후 초대받은 병동만 볼 수 있습니다.",
                onOpenDemo = { demo = true },
            )
            else -> {
                // 토큰이 바뀌면(다시 로그인) 이전 사용자의 화면 상태를 쓰지 않도록 새 ViewModel을 만든다.
                val vm = viewModel(key = "wards-${token.hashCode()}") { WardSelectionViewModel(repository) }
                WardSelectionRoute(vm, onSignOut = session::signOut, onOpenDemo = { demo = true })
            }
        }
    }
}

@Composable
private fun DemoFrame(onExit: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(C.warningSoft).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Label("가상 시연 데이터 · 서버와 공유되지 않음", 12, C.warning, true, Modifier.weight(1f))
            Label("나가기", 12, C.brandDeep, true, Modifier.clickable(role = Role.Button, onClick = onExit).padding(4.dp))
        }
        NurseFlowApp()
    }
}

@Composable
private fun GateScreen(title: String, body: String, onOpenDemo: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Label("NurseFlow", 12, C.secondary)
        Label(title, 18, C.text, true)
        Spacer(Modifier.height(16.dp))
        CardBox(Modifier.fillMaxWidth()) { Label(body, 14, C.secondary) }
        Spacer(Modifier.height(16.dp))
        AppButton("가상 시연 데이터로 둘러보기", onOpenDemo, Modifier.fillMaxWidth(), outlined = true)
    }
}

/** 디버그 빌드에서만 나타난다([SessionSource.acceptsDeveloperToken]). 입력값은 화면에 가려 표시한다. */
@Composable
private fun DeveloperTokenScreen(onSubmit: (String) -> Unit, onOpenDemo: () -> Unit) {
    var value by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Label("개발용 빌드", 12, C.warning, true)
        Label("개발 서버 access token 입력", 18, C.text, true)
        Spacer(Modifier.height(12.dp))
        CardBox(Modifier.fillMaxWidth()) {
            Label("Kakao 로그인 연동 전 개발 확인용입니다. 서버가 설정한 OIDC 발급자의 토큰만 통과합니다. 토큰은 저장하지 않습니다.", 13, C.secondary)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("access token") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        AppButton("연결", { onSubmit(value) }, Modifier.fillMaxWidth(), enabled = value.isNotBlank())
        Spacer(Modifier.height(8.dp))
        AppButton("가상 시연 데이터로 둘러보기", onOpenDemo, Modifier.fillMaxWidth(), outlined = true)
    }
}
