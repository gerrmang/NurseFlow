package com.nurseflow.app.feature.ward

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WardListScreen(viewModel: WardsViewModel, onLogout: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("내 병동", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onLogout) { Text("로그아웃") }
        }
        TextButton(onClick = viewModel::reload) { Text("새로고침") }
        when (val current = state) {
            WardsUiState.Loading -> CircularProgressIndicator()
            is WardsUiState.Failed -> {
                Text(current.message)
                Button(onClick = viewModel::reload) { Text("다시 시도") }
            }
            is WardsUiState.Loaded -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("${current.displayName}님, 안녕하세요.") }
                if (current.wards.isEmpty()) item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("아직 소속된 병동이 없어요.")
                            Text("병동 만들기: 관리자는 병원을 선택하고 새 병동을 만들 수 있어요. 이 앱의 병동 생성 화면은 준비 중이에요.")
                            Text("초대 수락: 간호사는 병동 관리자에게 초대를 요청해 주세요. 이 앱의 초대 수락 화면은 준비 중이에요.")
                        }
                    }
                }
                items(current.wards, key = { it.wardId }) { ward ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(ward.wardName, style = MaterialTheme.typography.titleMedium)
                            Text(ward.roles.joinToString(" · ") {
                                when (it) { "WARD_ADMIN" -> "관리자"; "WARD_MEMBER" -> "간호사"; else -> "기타 역할" }
                            })
                            Text("서버 근무표 화면은 준비 중이에요.")
                        }
                    }
                }
            }
        }
    }
}
