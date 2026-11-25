package com.nurseflow.app.feature.offrequest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.network.OffRequest
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun OffRequestScreen(wardName: String, model: OffRequestViewModel, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    DisposableEffect(model) { model.activate(); onDispose { model.deactivate() } }
    BackHandler(onBack = onBack)
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(model) { while (true) { now = Instant.now(); delay(1_000) } }
    // Every authoritative reload drops old input. A rotation preserves only the current form.
    var date by rememberSaveable(state.version) { mutableStateOf("") }
    var target by rememberSaveable(state.version) { mutableStateOf<String?>(null) }
    var limit by rememberSaveable(state.version) { mutableStateOf(state.data?.policy?.value?.monthlyLimit?.toString() ?: "") }
    var deadlineDay by rememberSaveable(state.version) { mutableStateOf(state.data?.policy?.value?.deadlineDay?.toString() ?: "") }
    var nurseFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var exceptionsOnly by rememberSaveable { mutableStateOf(false) }
    var choosing by remember { mutableStateOf<String?>(null) }
    var decision by remember(state.version) { mutableStateOf<Pair<OffRequest, String>?>(null) }
    var submitConfirmation by remember(state.version) { mutableStateOf(false) }
    var policyConfirmation by remember(state.version) { mutableStateOf(false) }
    val data = state.data
    val late = data != null && !now.isBefore(Instant.parse(data.list.deadline))
    val eligible = data?.eligibility?.items.orEmpty()
    val names = eligible.associate { it.nurseId to it.displayName }
    fun name(id: String) = names[id] ?: "대상 간호사(목록에서 확인 불가)"

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).testTag("off-list"), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                TextButton(onClick = onBack) { Text("‹ 병동 목록") }
                Text("$wardName · 희망 오프", style = MaterialTheme.typography.titleLarge)
                Text(if (model.admin) "관리자 취합·판단" else "내 희망 오프")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { model.changeMonth(-1) }, enabled = state.canMoveMonth) { Text("‹ 이전 달") }
                    Text("${state.month.year}년 ${state.month.monthValue}월", Modifier.padding(top = 12.dp))
                    TextButton(onClick = { model.changeMonth(1) }, enabled = state.canMoveMonth) { Text("다음 달 ›") }
                }
                OutlinedButton(onClick = model::reload, enabled = !state.busy) { Text("새로고침") }
                if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (data != null) {
                item {
                    val zone = ZoneId.systemDefault()
                    Text("일반 신청 마감: ${DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss VV").withZone(zone).format(Instant.parse(data.list.deadline))}")
                    Text("표시 시간대: ${zone.id} · 서버가 마감 여부를 최종 판단해요.")
                    Text(if (late) "마감 후 예외 접수 중 · 관리자 확정 전 편성에 반영되지 않아요." else "마감 전 미확정 신청은 선호이며, 확정된 날짜는 배치가 금지돼요.")
                    Text(if (model.admin) "월 본인 신청 한도 ${data.list.monthlyLimit}일 · 관리자 대리 입력은 한도에서 제외돼요." else "본인 신청 ${ownCount(data.list)} / ${data.list.monthlyLimit}일 · 반려·철회·관리자 대리 입력 제외")
                    if (model.admin) Text(when (data.eligibility?.demoMode) { true -> "시연 모드 · 가상 간호사도 편성 대상에 포함돼요."; false -> "일반 모드 · 서버의 편성 대상을 사용해요."; null -> "편성 대상 확인 불가" })
                }
                item {
                    Text(if (model.admin) "희망 오프 대리 입력" else if (late) "예외 요청 제출" else "희망 오프 신청", style = MaterialTheme.typography.titleMedium)
                    if (model.admin) OutlinedButton(onClick = { choosing = "target" }, enabled = state.editable) { Text(target?.let(::name) ?: "대상 간호사 선택") }
                    Text("날짜를 골라 한 건씩 제출해 주세요. 날짜 변경은 철회 후 다시 신청해 주세요.")
                    val start = state.month.atDay(1).dayOfWeek.value - 1
                    Row(Modifier.fillMaxWidth()) {
                        listOf("월", "화", "수", "목", "금", "토", "일").forEachIndexed { index, label ->
                            Text(label, Modifier.weight(1f).testTag("off-weekday-${index + 1}"), textAlign = TextAlign.Center)
                        }
                    }
                    for (week in 0 until (start + state.month.lengthOfMonth() + 6) / 7) {
                        Row(Modifier.fillMaxWidth()) {
                            for (weekday in 0..6) {
                                val day = week * 7 + weekday - start + 1
                                Box(Modifier.weight(1f).then(if (day in 1..state.month.lengthOfMonth()) Modifier.testTag("off-day-$day") else Modifier)) {
                                    if (day in 1..state.month.lengthOfMonth()) {
                                        val value = state.month.atDay(day).toString()
                                        TextButton(onClick = { date = value }, enabled = state.editable, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) { Text(if (date == value) "[$day]" else "$day") }
                                    }
                                }
                            }
                        }
                    }
                    Text("선택 날짜: ${date.ifBlank { "없음" }}")
                    Button(onClick = { submitConfirmation = true }, enabled = state.editable && date.isNotBlank() && (!model.admin || target != null) && (model.admin || ownCount(data.list) < data.list.monthlyLimit)) { Text(if (model.admin) "대리 입력" else if (late) "예외 요청 제출" else "신청") }
                }
                if (model.admin) item {
                    Text("취합 필터", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { choosing = "filter" }) { Text(nurseFilter?.let(::name) ?: "전체 간호사") }
                    Row {
                        OutlinedButton(onClick = { choosing = "status" }) { Text(statusFilter?.let(::statusLabel) ?: "전체 상태") }
                        Checkbox(checked = exceptionsOnly, onCheckedChange = { exceptionsOnly = it })
                        Text("마감 후 예외만", Modifier.padding(top = 12.dp))
                    }
                }
                val filtered = data.list.items.filter { (!model.admin || nurseFilter == null || it.nurseId == nurseFilter) && (!model.admin || statusFilter == null || it.status == statusFilter) && (!model.admin || !exceptionsOnly || it.afterDeadline) }.sortedWith(compareBy<OffRequest> { it.date }.thenBy { it.submittedAt })
                item { Text("신청·상태·이력 ${filtered.size}건", style = MaterialTheme.typography.titleMedium); if (filtered.isEmpty()) Text("표시할 희망 오프가 없어요.") }
                items(filtered, key = { it.requestId }) { request ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${request.date} · ${statusLabel(request.status)}", style = MaterialTheme.typography.titleMedium)
                            if (model.admin) Text(name(request.nurseId))
                            Text("${if (request.source == "ADMIN") "관리자 대리 입력" else "본인 신청"}${if (request.afterDeadline) " · 마감 후 예외" else ""}")
                            Text("접수: ${displayInstant(request.submittedAt)}")
                            request.decidedAt?.let { Text("판단: ${displayInstant(it)}") }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (action in listOf("confirm", "reject", "withdraw")) {
                                    if (model.canDecide(request, action, now)) OutlinedButton(onClick = { decision = request to action }) { Text(actionLabel(action)) }
                                }
                            }
                        }
                    }
                }
                if (model.admin && data.policy != null) item {
                    Text("희망 오프 정책", style = MaterialTheme.typography.titleMedium)
                    val limitValue = limit.toIntOrNull()
                    val dayValue = deadlineDay.toIntOrNull()
                    val badLimit = limit.isNotEmpty() && (limitValue == null || limitValue !in 0..31)
                    val badDay = deadlineDay.isNotEmpty() && (dayValue == null || dayValue !in 1..28)
                    OutlinedTextField(value = limit, onValueChange = { limit = it }, label = { Text("월 본인 신청 한도 (0~31일)") }, enabled = state.editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = badLimit, supportingText = { if (badLimit) Text("월 한도는 0~31일로 입력해 주세요.") })
                    OutlinedTextField(value = deadlineDay, onValueChange = { deadlineDay = it }, label = { Text("대상 월 전달 마감일 (1~28일)") }, enabled = state.editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = badDay, supportingText = { if (badDay) Text("마감일은 1~28일로 입력해 주세요.") })
                    Text("마감일이 끝나는 병동 현지 자정부터 예외 요청이에요. 기존 신청과 공개본은 유지돼요.")
                    Button(onClick = { policyConfirmation = true }, enabled = state.editable) { Text("정책 저장") }
                }
            }
        }
        // Status and recovery stay outside the list so writes from the bottom cannot hide their outcome.
        if (state.notice != null || state.forbidden || state.uncertain || (state.stale && data != null)) {
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().testTag("off-status")) {
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.notice?.let { Text(it) }
                    if (state.forbidden) {
                        Text("병동 목록으로 돌아가 소속과 역할을 다시 확인해 주세요.")
                        TextButton(onClick = onBack) { Text("병동 목록으로 돌아가기") }
                    } else {
                        if (state.stale && data != null) Text("이전 조회 결과예요. 최신 조회 전에는 변경할 수 없어요.")
                        if (state.uncertain && state.notice == null) Text("처리 결과를 확인해야 해요. 최신 목록과 이력을 확인한 뒤 계속해 주세요.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.stale || state.uncertain) OutlinedButton(onClick = model::reload, enabled = !state.loading && !state.busy, modifier = Modifier.weight(1f)) { Text("최신 목록 다시 조회") }
                            if (state.uncertain) Button(onClick = model::acknowledge, enabled = !state.stale && !state.loading && !state.busy, modifier = Modifier.weight(1f)) { Text("최신 목록 확인 후 계속") }
                        }
                    }
                }
            }
        }
    }
    choosing?.let { kind ->
        AlertDialog(onDismissRequest = { choosing = null }, title = { Text(when (kind) { "target" -> "편성 대상 간호사"; "status" -> "상태 선택"; else -> "간호사 필터" }) }, text = {
            LazyColumn {
                if (kind != "target") item { TextButton(onClick = { if (kind == "status") statusFilter = null else nurseFilter = null; choosing = null }) { Text("전체") } }
                if (kind == "status") items(listOf("SUBMITTED", "CONFIRMED", "REJECTED", "WITHDRAWN")) { s -> TextButton(onClick = { statusFilter = s; choosing = null }) { Text(statusLabel(s)) } }
                else {
                    val choices = if (kind == "target") eligible.filter { it.eligible } else eligible
                    if (choices.isEmpty()) item { Text("선택할 간호사가 없어요. 구성원·편성 대상을 확인해 주세요.") }
                    items(choices, key = { it.nurseId }) { nurse -> TextButton(onClick = { if (kind == "target") target = nurse.nurseId else nurseFilter = nurse.nurseId; choosing = null }) { Text(nurse.displayName) } }
                }
            }
        }, confirmButton = { TextButton(onClick = { choosing = null }) { Text("닫기") } })
    }
    if (submitConfirmation) AlertDialog(onDismissRequest = { submitConfirmation = false }, title = { Text("${date} ${if (late) "예외 요청" else "신청"}") }, text = { Text("${if (model.admin) "${target?.let(::name)}의 희망 오프를 대리 입력해요." else "희망 오프를 제출해요."} 관리자 확정 전에는 배치 금지가 아니에요.${if (late) " 마감 후 요청은 확정 전 편성에 반영되지 않아요." else ""}") }, confirmButton = { TextButton(enabled = state.editable, onClick = { submitConfirmation = false; model.submit(date, if (model.admin) target else null) }) { Text("제출") } }, dismissButton = { TextButton(onClick = { submitConfirmation = false }) { Text("취소") } })
    decision?.let { (request, action) -> AlertDialog(onDismissRequest = { decision = null }, title = { Text("${request.date} ${actionLabel(action)}") }, text = { Text(if (action == "confirm" || request.status == "CONFIRMED") "확정 또는 확정분 철회는 초안 재검증이 필요해요. 기존 공개본은 유지돼요." else "선택한 희망 오프를 ${actionLabel(action)}할까요?") }, confirmButton = { TextButton(enabled = model.canDecide(request, action, now), onClick = { decision = null; model.decide(request.requestId, action) }) { Text(actionLabel(action)) } }, dismissButton = { TextButton(onClick = { decision = null }) { Text("취소") } }) }
    if (policyConfirmation) AlertDialog(onDismissRequest = { policyConfirmation = false }, title = { Text("정책 저장") }, text = { Text("월 한도 ${limit}일, 전달 ${deadlineDay}일 마감으로 저장할까요? 기존 신청은 유지돼요.") }, confirmButton = { TextButton(enabled = state.editable, onClick = { policyConfirmation = false; model.savePolicy(limit, deadlineDay) }) { Text("저장") } }, dismissButton = { TextButton(onClick = { policyConfirmation = false }) { Text("취소") } })
}

private fun displayInstant(value: String): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss VV").withZone(ZoneId.systemDefault()).format(Instant.parse(value))
internal fun statusLabel(value: String) = when (value) { "SUBMITTED" -> "신청"; "CONFIRMED" -> "확정"; "REJECTED" -> "반려"; "WITHDRAWN" -> "철회"; else -> value }
private fun actionLabel(value: String) = when (value) { "confirm" -> "확정"; "reject" -> "반려"; else -> "철회" }
