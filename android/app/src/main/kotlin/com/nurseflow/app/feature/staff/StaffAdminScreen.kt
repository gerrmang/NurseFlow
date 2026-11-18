package com.nurseflow.app.feature.staff

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.network.*
import kotlin.math.abs

@Composable
fun StaffAdminScreen(wardName: String, model: StaffAdminViewModel, onBack: () -> Unit, onForbidden: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editTeamId by rememberSaveable { mutableStateOf<String?>(null) }
    val editTeam = state.data?.teams?.firstOrNull { it.teamId == editTeamId }
    var teamDialog by rememberSaveable { mutableStateOf(false) }
    var staffDialog by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val activity = generateSequence(LocalContext.current) { (it as? android.content.ContextWrapper)?.baseContext }
        .filterIsInstance<android.app.Activity>().firstOrNull()
    DisposableEffect(model) {
        model.activate()
        onDispose { if (activity?.isChangingConfigurations != true) model.deactivate() }
    }
    LaunchedEffect(state.forbidden) { if (state.forbidden) onForbidden() }
    // Snapshot replacement invalidates pending confirmations; never submit an old member/team form.
    var editorVersion by rememberSaveable { mutableStateOf(state.version) }
    LaunchedEffect(state.version) {
        if (state.version != editorVersion) { confirm = null; teamDialog = false; staffDialog = false; editorVersion = state.version }
    }
    val data = state.data
    val selected = data?.staff?.firstOrNull { it.nurseId == selectedId }
    val back = { if (selectedId != null) { selectedId = null; model.history(null) } else onBack() }
    BackHandler { back() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = back) { Text("뒤로") }
            TextButton(onClick = model::reload, enabled = !state.busy && !state.loading) { Text("새로고침") }
        }
        Text(wardName, style = MaterialTheme.typography.titleLarge)
        Text("팀·간호사·구성원 관리", style = MaterialTheme.typography.titleMedium)
        if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.notice?.let { Text(it, Modifier.testTag("staff-notice")) }
        if (state.uncertain) {
            Text("이미 처리됐을 수 있어요. 최신 목록을 확인한 뒤 작업을 재개해 주세요.")
            Button(onClick = model::acknowledge, enabled = !state.stale && !state.loading && !state.busy) { Text("목록 확인 완료") }
        }
        if (data == null) {
            if (!state.loading && !state.forbidden) Text("관리 목록을 불러오지 못했어요. 새로고침해 주세요.")
        } else if (selected != null) {
            key(selected.nurseId, state.version) {
                StaffDetail(selected, data, state, model, onEdit = { staffDialog = true },
                    onConfirm = { text, action -> confirm = text to action })
            }
        } else {
            Row {
                TextButton(onClick = { tab = 0 }) { Text("팀·간호사") }
                TextButton(onClick = { tab = 1 }) { Text("구성원") }
            }
            if (tab == 0) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("팀 ${data.teams.size}개 · 간호사 기록 ${data.staff.size}명")
                        Text("팀 안 위쪽이 더 선임이에요. 입사 연월과 별도로 저장해요. 일반 편집은 마지막 저장이 반영돼요.")
                        Row {
                            OutlinedButton(onClick = { editTeamId = null; teamDialog = true }, enabled = state.editable) { Text("팀 추가") }
                            TextButton(onClick = { selectedId = null; staffDialog = true }, enabled = state.editable) { Text("간호사 등록") }
                        }
                    }
                    items(data.teams.sortedBy { it.sortOrder }, key = { it.teamId }) { team ->
                        TeamCard(team, data.staff.filter { it.team?.teamId == team.teamId }, state.editable, state.version,
                            onEdit = { editTeamId = team.teamId; teamDialog = true },
                            onDelete = { confirm = "${team.name} 팀을 삭제할까요? 활성 간호사가 있으면 먼저 팀을 옮겨야 해요." to { model.deleteTeam(team) } },
                            onOpen = { selectedId = it; model.history(it) }, onSave = { model.saveOrder(team.teamId, it) })
                    }
                    item { Text("팀 미지정", style = MaterialTheme.typography.titleMedium) }
                    items(data.staff.filter { it.team == null }, key = { it.nurseId }) { nurse ->
                        NurseButton(nurse) { selectedId = nurse.nurseId; model.history(nurse.nurseId) }
                    }
                    if (data.staff.isEmpty()) item { Text("등록된 간호사 기록이 없어요.") }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text("구성원 접근 재활성화는 간호사 기록을 자동으로 활성화하지 않아요. 관리자 지정 전 활성 간호사 기록을 먼저 비활성화해 주세요.") }
                    items(data.members, key = { it.userId }) { member ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(member.displayName, style = MaterialTheme.typography.titleMedium)
                                val admin = "WARD_ADMIN" in member.roles
                                Text("${if (admin) "관리자" else "간호사"} · ${if (member.active) "접근 활성" else "접근 비활성"}")
                                TextButton(enabled = state.editable, onClick = {
                                    confirm = "${member.displayName}님의 관리자 역할을 ${if (admin) "해제" else "지정"}할까요? 본인 권한이 사라지면 관리 화면이 닫혀요." to { model.membership(member.userId, AdminMembershipInput(admin = !admin)) }
                                }) { Text(if (admin) "관리자 해제" else "관리자 지정") }
                                TextButton(enabled = state.editable, onClick = {
                                    confirm = (if (member.active) "접근을 비활성화할까요? 연결된 간호사 기록도 비활성화되고 팀 순서에서 빠져요." else "접근을 재활성화할까요? 간호사 기록은 별도로 활성화해야 해요.") to { model.membership(member.userId, AdminMembershipInput(active = !member.active)) }
                                }) { Text(if (member.active) "접근 비활성화" else "접근 재활성화") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (teamDialog) TeamEditor(editTeam, state.editable, onDismiss = { teamDialog = false }) { name, order ->
        model.saveTeam(editTeam?.teamId, name, order); teamDialog = false
    }
    if (staffDialog) StaffEditor(selected, state.editable, onDismiss = { staffDialog = false }) { name, qualifications, active ->
        if (selected != null && selected.active && !active) {
            confirm = "간호사 기록을 비활성화할까요? 편성 대상과 팀 순서에서 빠져요. 구성원 접근은 유지돼요." to { model.saveStaff(selected, name, qualifications, active) }
        } else model.saveStaff(selected, name, qualifications, active)
        staffDialog = false
    }
    confirm?.let { pending ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("변경 확인") }, text = { Text(pending.first) },
            confirmButton = { TextButton(enabled = state.editable, onClick = { confirm = null; pending.second() }) { Text("변경") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("취소") } })
    }
}

@Composable private fun NurseButton(nurse: AdminStaff, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("${nurse.displayName} · ${if (nurse.active) "활성" else "비활성"} · ${if (nurse.linkedAccount) "계정 연결" else "계정 미연결"}") }
}

@Composable
private fun TeamCard(team: AdminTeam, staff: List<AdminStaff>, enabled: Boolean, version: Int, onEdit: () -> Unit,
                     onDelete: () -> Unit, onOpen: (String) -> Unit, onSave: (List<String>) -> Unit) {
    // Preserve the server's fallback career order until an administrator saves a manual order.
    val original = staff.filter { it.active }.map { it.nurseId }
    var order by rememberSaveable(team.teamId, version) { mutableStateOf(original) }
    val latestOrder by rememberUpdatedState(order)
    val latestEnabled by rememberUpdatedState(enabled)
    fun move(id: String, delta: Int) {
        if (!latestEnabled) return
        val from = latestOrder.indexOf(id); val to = from + delta
        if (from < 0 || to !in latestOrder.indices) return
        order = latestOrder.toMutableList().apply { add(to, removeAt(from)) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${team.name} · 표시 순서 ${team.sortOrder}", style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(onClick = onEdit, enabled = enabled) { Text("팀 수정") }
                TextButton(onClick = onDelete, enabled = enabled) { Text("팀 삭제") }
            }
            Text("길게 눌러 드래그하거나 위·아래 버튼으로 경력순을 바꾼 뒤 저장하세요.")
            order.forEachIndexed { index, id ->
                val nurse = staff.firstOrNull { it.nurseId == id } ?: return@forEachIndexed
                key(id) {
                    var height by remember { mutableStateOf(1) }
                    Row(Modifier.fillMaxWidth().testTag("order-$id").onSizeChanged { height = it.height.coerceAtLeast(1) }
                        .pointerInput(id) {
                            var distance = 0f
                            detectDragGesturesAfterLongPress(onDragStart = { distance = 0f }, onDragEnd = { distance = 0f }, onDragCancel = { distance = 0f }) { change, amount ->
                                if (latestEnabled) {
                                    change.consume(); distance += amount.y
                                    if (abs(distance) >= height) { move(id, if (distance > 0) 1 else -1); distance = 0f }
                                }
                            }
                        }) {
                        TextButton(onClick = { onOpen(id) }, modifier = Modifier.weight(1f)) { Text("${index + 1}. ${nurse.displayName}") }
                        TextButton(onClick = { move(id, -1) }, enabled = enabled && index > 0, modifier = Modifier.semantics { contentDescription = "${nurse.displayName} 위로" }) { Text("↑") }
                        TextButton(onClick = { move(id, 1) }, enabled = enabled && index < order.lastIndex, modifier = Modifier.semantics { contentDescription = "${nurse.displayName} 아래로" }) { Text("↓") }
                    }
                }
            }
            Button(onClick = { onSave(order) }, enabled = enabled && order != original) { Text("${team.name} 경력순 저장") }
            if (order != original) Text("미저장 순서 · 새로고침하거나 화면을 나가면 초기화될 수 있어요.")
            staff.filter { !it.active }.forEach { nurse -> NurseButton(nurse) { onOpen(nurse.nurseId) } }
        }
    }
}

@Composable
private fun ColumnScope.StaffDetail(nurse: AdminStaff, data: StaffAdminData, state: StaffAdminState, model: StaffAdminViewModel,
                                   onEdit: () -> Unit, onConfirm: (String, () -> Unit) -> Unit) {
    var month by rememberSaveable { mutableStateOf(nurse.profile.careerStartedOn ?: "") }
    var rank by rememberSaveable { mutableStateOf(nurse.profile.rank ?: "") }
    var senior by rememberSaveable { mutableStateOf(nurse.seniorEligible) }
    var stage by rememberSaveable { mutableStateOf(nurse.stageOverride) }
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(nurse.displayName, style = MaterialTheme.typography.titleLarge)
            Text("${if (nurse.active) "활성" else "비활성"} · ${if (nurse.linkedAccount) "계정 연결" else "계정 미연결"}")
            Text("자격: ${nurse.qualifications.joinToString().ifEmpty { "없음" }}")
            TextButton(onClick = onEdit, enabled = state.editable) { Text("기록 수정·활성 상태") }
            Text("병원 입사 연월: ${nurse.profile.hospitalJoinedOn ?: "미입력"}")
            Text("현재 팀: ${nurse.team?.name ?: "미지정"}")
        }
        item { Text("팀 배치", style = MaterialTheme.typography.titleMedium) }
        item { TextButton(onClick = { model.assignTeam(nurse.nurseId, null) }, enabled = state.editable && nurse.team != null) { Text("팀 미지정으로 변경") } }
        items(data.teams, key = { "assign-${it.teamId}" }) { team -> TextButton(onClick = { model.assignTeam(nurse.nurseId, team.teamId) }, enabled = state.editable && nurse.team?.teamId != team.teamId) { Text("${team.name}에 배치") } }
        item {
            Text("실제 경력 정보", style = MaterialTheme.typography.titleMedium)
            Text("입사 연월·수동 경력순과 별도예요. 미저장 입력은 화면 전환·새로고침 시 초기화될 수 있어요.")
            Text("상태: ${when (nurse.profile.status) { "CONFIRMED" -> "확인됨"; "PENDING" -> "확인 대기"; else -> "미입력" }} · 계산 단계: ${nurse.profile.scoringStage ?: "없음"}")
            Text("확인 경력: ${nurse.profile.confirmedCareerStartedOn ?: "없음"} · ${nurse.profile.confirmedRank ?: "없음"}")
            OutlinedTextField(month, { month = it }, label = { Text("실제 경력 시작 연월 YYYY-MM") }, enabled = state.editable, singleLine = true)
            Text("직급 선택: ${rank.ifBlank { "미선택" }}")
        }
        items(data.policy.ranks, key = { "rank-$it" }) { value -> TextButton(onClick = { rank = value }, enabled = state.editable) { Text(value) } }
        item {
            Button(onClick = { model.saveProfile(nurse.nurseId, month, rank) }, enabled = state.editable && Regex("^(?!0000)\\d{4}-(0[1-9]|1[0-2])$").matches(month) && rank in data.policy.ranks) { Text("실제 경력 저장") }
            OutlinedButton(onClick = { onConfirm("현재 저장된 실제 경력 정보를 확인할까요? 입력 중인 미저장 값은 반영되지 않아요.") { model.confirmProfile(nurse.nurseId) } }, enabled = state.editable && nurse.profile.careerStartedOn != null && nurse.profile.rank != null) { Text("저장된 경력 확인") }
            Text("선임 자격·편성 단계", style = MaterialTheme.typography.titleMedium)
            Row { Checkbox(senior, { senior = it }, enabled = state.editable); Text("선임 자격") }
            Text("단계 조정: ${stage ?: "계산값 사용"} · 현재 편성 단계: ${nurse.rosterStage ?: "없음"}")
            Row {
                TextButton(onClick = { stage = null }, enabled = state.editable) { Text("자동") }
                (1..5).forEach { n -> TextButton(onClick = { stage = n }, enabled = state.editable, contentPadding = PaddingValues(2.dp), modifier = Modifier.widthIn(min = 32.dp)) { Text("$n") } }
            }
            Button(onClick = { model.saveSettings(nurse.nurseId, senior, stage) }, enabled = state.editable) { Text("선임·단계 저장") }
        }
        item {
            Text("조정 이력", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { model.history(nurse.nurseId) }, enabled = !state.busy && !state.stale && !state.loading) { Text("이력 새로고침") }
            if (state.historyLoading) CircularProgressIndicator()
            state.historyError?.let { Text(it) }
            if (state.historyNurseId == nurse.nurseId && !state.historyLoading && state.history.isEmpty() && state.historyError == null) Text("조정 이력이 없어요.")
        }
        if (state.historyNurseId == nurse.nurseId) items(state.history, key = { it.adjustmentId }) { change ->
            Text("${adjustmentName(change.kind)} · ${change.before ?: "없음"} → ${change.after ?: "없음"}\n${change.actorName ?: "관리자"} · ${change.at}")
        }
    }
}

private fun adjustmentName(kind: String) = when (kind) {
    "TEAM_ORDER" -> "팀 내 경력순"; "SENIOR_ELIGIBLE" -> "선임 자격"; "STAGE_OVERRIDE" -> "편성 단계"
    "TEAM_ASSIGNED" -> "팀 배치"; "PROFILE_SET_BY_ADMIN" -> "실제 경력 입력"; "PROFILE_CONFIRMED" -> "경력 확인"; else -> "정보 변경"
}

@Composable private fun TeamEditor(team: AdminTeam?, enabled: Boolean, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable(team?.teamId) { mutableStateOf(team?.name ?: "") }
    var order by rememberSaveable(team?.teamId) { mutableStateOf(team?.sortOrder?.toString() ?: "1") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (team == null) "팀 추가" else "팀 수정") }, text = {
        Column { OutlinedTextField(name, { name = it }, label = { Text("팀 이름 (1~50자)") }); OutlinedTextField(order, { order = it }, label = { Text("표시 순서 (1~1000)") }) }
    }, confirmButton = { TextButton(onClick = { onSave(name, order) }, enabled = enabled && name.trim().length in 1..50 && (order.toIntOrNull() ?: 0) in 1..1000) { Text("저장") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

@Composable private fun StaffEditor(nurse: AdminStaff?, enabled: Boolean, onDismiss: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    var name by rememberSaveable(nurse?.nurseId) { mutableStateOf(nurse?.displayName ?: "") }
    var qualifications by rememberSaveable(nurse?.nurseId) { mutableStateOf(nurse?.qualifications?.joinToString(", ") ?: "") }
    var active by rememberSaveable(nurse?.nurseId) { mutableStateOf(nurse?.active ?: true) }
    val qs = qualifications.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (nurse == null) "간호사 기록 등록" else "간호사 기록 수정") }, text = {
        Column {
            OutlinedTextField(name, { name = it }, label = { Text("이름 (1~100자)") })
            OutlinedTextField(qualifications, { qualifications = it }, label = { Text("자격 (쉼표로 구분)") })
            Row { Checkbox(active, { active = it }); Text("간호사 기록 활성") }
            Text("계정 연결은 초대 수락으로 진행해요. 기록 활성과 구성원 접근은 별도예요.")
        }
    }, confirmButton = { TextButton(onClick = { onSave(name, qualifications, active) }, enabled = enabled && name.trim().length in 1..100 && qs.size <= 50 && qs.all { it.length <= 64 }) { Text("저장") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}
