package com.nurseflow.app.feature.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.network.*
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun ScheduleSettingsScreen(wardName: String, model: ScheduleSettingsViewModel, onBack: () -> Unit, onForbidden: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("근무 유형") }
    var month by rememberSaveable(state.month) { mutableStateOf(state.month) }
    val activity = generateSequence(LocalContext.current) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()
    DisposableEffect(model) { model.activate(); onDispose { if (activity?.isChangingConfigurations != true) model.deactivate() } }
    LaunchedEffect(state.forbidden) { if (state.forbidden) onForbidden() }
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            TextButton(onClick = onBack) { Text("뒤로") }
            TextButton(onClick = model::reload, enabled = !state.busy && !state.loading) { Text("새로고침") }
        }
        Text("$wardName · 근무 설정", style = MaterialTheme.typography.titleLarge)
        Text("저장한 설정은 서버에서 다시 조회해요. 공개된 근무표는 재확정 전까지 유지돼요.")
        if (state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.notice?.let { Text(it, modifier = Modifier.testTag("schedule-notice")) }
        if (state.uncertain) {
            Text("변경 결과를 확인해야 계속할 수 있어요.")
            OutlinedButton(onClick = model::acknowledge, enabled = !state.stale && !state.busy && !state.loading) { Text("최신 결과 확인했어요") }
        }
        Select("설정 항목", tab, listOf("근무 유형", "팀별 필요 인원", "승인 부재", "근무 형태").map { it to it }, !state.busy) { tab = it; model.clearPreview() }
        if (tab == "팀별 필요 인원" || tab == "승인 부재") {
            Field("대상 월 (YYYY-MM)", month, !state.busy && !state.uncertain) { month = it }
            OutlinedButton(onClick = { model.setMonth(month) }, enabled = !state.busy && !state.uncertain && !state.loading) { Text("월 조회") }
            state.data?.draft?.value?.let { draft ->
                Text("${draft.month} 초안 · revision ${draft.revision} · ${draft.timeZone}")
                Text("필요 ${draft.staffing.required}명 / 미충원 ${draft.staffing.unfilled}명 / 선임 미충원 ${draft.staffing.seniorUnfilled}명")
                if (draft.needsRevalidation) Text("설정 변경으로 재검증이 필요한 초안이에요.")
            }
            if (state.data != null && state.data?.draft == null) {
                Text("이 달의 초안이 없어요. 설정을 입력하려면 초안을 만들어 주세요.")
                Button(onClick = model::createDraft, enabled = state.editable) { Text("선택한 월 초안 만들기") }
            }
        }
        state.data?.let { data ->
            // New server snapshots invalidate old forms/confirmations. Saveable fields survive rotation.
            key(state.version, tab) {
                when (tab) {
                    "근무 유형" -> ShiftPanel(data, state, model)
                    "팀별 필요 인원" -> if (data.draft != null) DemandPanel(data, state, model)
                    "승인 부재" -> if (data.draft != null) AbsencePanel(data, state, model)
                    "근무 형태" -> PatternPanel(data, state, model)
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, enabled: Boolean, change: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, enabled = enabled, modifier = Modifier.fillMaxWidth(), singleLine = true)
}
@Composable
private fun Select(label: String, selected: String, options: List<Pair<String, String>>, enabled: Boolean, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text("$label: ${options.firstOrNull { it.first == selected }?.second ?: "선택해 주세요"}") }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; change(id) }) }
        }
    }
}
private fun attempt(model: ScheduleSettingsViewModel, action: () -> Unit) {
    try { action() } catch (e: IllegalArgumentException) { model.message(e.message ?: "입력값을 확인해 주세요.") }
    catch (_: java.time.DateTimeException) { model.message("날짜·시각과 병동 시간대를 확인해 주세요.") }
}

@Composable
private fun ShiftPanel(data: ScheduleData, state: ScheduleState, model: ScheduleSettingsViewModel) {
    var selected by rememberSaveable { mutableStateOf("") }
    val shift = data.shifts.value.items.firstOrNull { it.shiftTypeId == selected }
    Select("근무 유형", selected, listOf("" to "새 근무 유형") + data.shifts.value.items.map { it.shiftTypeId to "${it.code} · ${it.displayName}${if (it.active) "" else " (비활성)"}" }, state.editable) { selected = it; model.clearPreview() }
    key(selected) {
        var code by rememberSaveable { mutableStateOf(shift?.code ?: "") }
        var name by rememberSaveable { mutableStateOf(shift?.displayName ?: "") }
        var kind by rememberSaveable { mutableStateOf(shift?.kind ?: "DAY") }
        var start by rememberSaveable { mutableStateOf(shift?.startTime ?: "07:00") }
        var end by rememberSaveable { mutableStateOf(shift?.endTime ?: "15:00") }
        var midnight by rememberSaveable { mutableStateOf(shift?.spansMidnight ?: false) }
        var rest by rememberSaveable { mutableStateOf((shift?.breakMinutes ?: 0).toString()) }
        var recognized by rememberSaveable { mutableStateOf((shift?.recognizedMinutes ?: 480).toString()) }
        var credit by rememberSaveable { mutableStateOf((shift?.targetCreditMinutes ?: 480).toString()) }
        var active by rememberSaveable { mutableStateOf(shift?.active ?: true) }
        var confirmDeactivate by rememberSaveable { mutableStateOf(false) }
        val editable = state.editable && kind != "OTHER"
        fun changed(action: () -> Unit) { action(); model.clearPreview() }
        if (kind == "OTHER") Text("기존 특수 근무는 조회·비활성화만 가능해요.")
        Field("코드 (영문 대문자·숫자·_)", code, editable) { changed { code = it } }
        Field("표시 이름", name, editable) { changed { name = it } }
        Select("종류", kind, listOf("DAY" to "Day", "EVENING" to "Evening", "NIGHT" to "Night"), editable) { changed { kind = it } }
        Field("시작 (HH:mm)", start, editable) { changed { start = it } }
        Field("종료 (HH:mm)", end, editable) { changed { end = it } }
        Row { Checkbox(midnight, onCheckedChange = { changed { midnight = it } }, enabled = editable); Text("종료는 다음 날") }
        Field("휴게 시간 (분)", rest, editable) { changed { rest = it } }
        Field("실제 인정 시간 (분)", recognized, editable) { changed { recognized = it } }
        Field("월 목표 환산 시간 (분)", credit, editable) { changed { credit = it } }
        if (shift != null) Row { Checkbox(active, onCheckedChange = { changed { active = it } }, enabled = editable); Text("활성") }
        fun input(): ScheduleShiftInput {
            require(Regex("[A-Z0-9_]{1,32}").matches(code)) { "코드는 영문 대문자·숫자·_ 1~32자예요." }
            require(name.isNotBlank() && name.length <= 64) { "이름은 1~64자로 입력해 주세요." }
            require(Regex("\\d{2}:\\d{2}").matches(start) && Regex("\\d{2}:\\d{2}").matches(end)) { "시각은 HH:mm으로 입력해 주세요." }
            val s = LocalTime.parse(start); val e = LocalTime.parse(end)
            require(s != e && midnight == (e < s)) { "시작·종료 시각과 자정 경과 여부를 확인해 주세요." }
            val duration = (e.toSecondOfDay() - s.toSecondOfDay()) / 60 + if (midnight) 1440 else 0
            val b = rest.toIntOrNull(); val r = recognized.toIntOrNull(); val c = credit.toIntOrNull()
            require(b != null && b in 0..240 && r != null && r in 1..(duration - b) && c != null && c in 0..1440) { "휴게·인정·환산 시간 범위를 확인해 주세요." }
            return ScheduleShiftInput(code, name, kind, start, end, midnight, b, r, c, active)
        }
        if (shift != null) OutlinedButton(onClick = { attempt(model) { model.preview(shift.shiftTypeId, input()) } }, enabled = editable) { Text("변경 영향 확인") }
        state.preview?.let { preview ->
            Text("영향 초안 ${preview.affectedDrafts.size}개 · 공개본 ${preview.publishedRostersUsingType}개는 유지돼요.")
            preview.affectedDrafts.forEach { Text("${it.month}: ${it.slots}칸 (배치 ${it.assignedSlots}칸)") }
            Text(if (preview.rewritesDraftSlotTimes) "초안의 근무 시각이 갱신돼요." else "초안의 근무 시각을 다시 쓰지 않아요.")
            if (preview.flagsDrafts) Text("영향받는 초안은 다시 검증해야 해요.")
        }
        Button(onClick = { attempt(model) { model.saveShift(shift?.shiftTypeId, input()) } }, enabled = editable && (shift == null || state.preview != null)) { Text(if (shift == null) "근무 유형 만들기" else "영향 확인 후 저장") }
        if (shift?.active == true) {
            OutlinedButton(onClick = { confirmDeactivate = true }, enabled = state.editable) { Text("비활성화") }
            if (confirmDeactivate) AlertDialog(onDismissRequest = { confirmDeactivate = false }, title = { Text("근무 유형 비활성화") }, text = { Text("기존 칸은 유지되고 관련 초안은 재검증 대상이 돼요. 공개본은 유지돼요.") }, confirmButton = { TextButton(enabled = state.editable, onClick = { confirmDeactivate = false; model.deactivateShift(shift.shiftTypeId) }) { Text("비활성화 확인") } }, dismissButton = { TextButton(onClick = { confirmDeactivate = false }) { Text("취소") } })
        }
    }
}

@Composable
private fun DemandPanel(data: ScheduleData, state: ScheduleState, model: ScheduleSettingsViewModel) {
    val draft = data.draft!!.value
    val assigned = draft.staffing.assigned > 0 || draft.slots.orEmpty().any { it.status == "FILLED" }
    Text("현재 수요는 조회용이에요. 기존 필요 자격을 복원할 수 없어 새 목록으로 전체 교체해요. 선임 칸은 서버가 만들어요.")
    draft.slots.orEmpty().groupBy { Triple(it.date, it.teamId, it.shiftCode) }.forEach { (group, slots) ->
        Text("${group.first} · ${data.teams.firstOrNull { it.teamId == group.second }?.name ?: "팀 미지정"} · ${group.third}: ${slots.size}명 / 선임 ${slots.count { it.seniorRequired }}명")
    }
    if (assigned) Text("기존 배치가 있어 교체할 수 없어요. 배치 편집에서 먼저 해제해 주세요.")
    var date by rememberSaveable { mutableStateOf("${state.month}-01") }
    var team by rememberSaveable { mutableStateOf("") }
    var shift by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableStateOf("1") }
    var qualifications by rememberSaveable { mutableStateOf("") }
    var encoded by rememberSaveable { mutableStateOf("[]") }
    val rows = ApiJson.json.decodeFromString<List<ScheduleRequirement>>(encoded)
    var checked by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val enabled = state.editable && !assigned
    Field("날짜 (YYYY-MM-DD)", date, enabled) { date = it }
    Select("팀", team, listOf("" to "팀 미지정") + data.teams.map { it.teamId to it.name }, enabled) { team = it }
    Select("근무", shift, data.shifts.value.items.filter { it.active && it.kind != "OTHER" }.map { it.shiftTypeId to it.code }, enabled) { shift = it }
    Field("필요 인원 (0~100)", count, enabled) { count = it }
    Field("필요 자격 (여러 개는 쉼표로 구분)", qualifications, enabled) { qualifications = it }
    OutlinedButton(onClick = { attempt(model) {
        val day = LocalDate.parse(date); val n = count.toIntOrNull()
        require(day.toString().startsWith(state.month + "-") && n != null && n in 0..100 && shift.isNotBlank()) { "날짜·근무·필요 인원을 확인해 주세요." }
        require(n == 0 || data.teams.isEmpty() || team.isNotBlank()) { "팀을 선택해 주세요." }
        require(rows.size < 1000 && rows.none { it.date == date && it.teamId == team.ifBlank { null } && it.shiftTypeId == shift }) { "중복 수요이거나 항목 수 상한을 넘었어요." }
        val q = qualifications.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(q.all { it.length <= 64 }) { "자격은 항목당 64자 이내예요." }
        encoded = ApiJson.json.encodeToString(rows + ScheduleRequirement(date, shift, n, q, team.ifBlank { null })); checked = false
    } }, enabled = enabled) { Text("새 수요 목록에 추가") }
    rows.forEachIndexed { index, row ->
        Text("${row.date} · ${data.teams.firstOrNull { it.teamId == row.teamId }?.name ?: "팀 미지정"} · ${data.shifts.value.items.firstOrNull { it.shiftTypeId == row.shiftTypeId }?.code}: ${row.requiredCount}명 / 자격 ${row.requiredQualifications.joinToString().ifEmpty { "없음" }}")
        TextButton(onClick = { encoded = ApiJson.json.encodeToString(rows.filterIndexed { i, _ -> i != index }); checked = false }, enabled = enabled) { Text("항목 제거") }
    }
    Row { Checkbox(checked, { checked = it }, enabled = enabled); Text("기존 수요 전체를 위 ${rows.size}개 항목으로 교체해요${if (rows.isEmpty()) " (모든 칸 제거)" else ""}") }
    Button(onClick = { confirm = true }, enabled = enabled && checked) { Text("수요 전체 교체") }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("기존 수요를 교체할까요?") }, text = { Text("선택한 월의 기존 칸을 제거하고 새 목록 ${rows.size}개로 교체해요.") }, confirmButton = { TextButton(onClick = { confirm = false; model.replaceRequirements(rows) }, enabled = enabled && checked) { Text("전체 교체 확인") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } })
}

@Composable
private fun AbsencePanel(data: ScheduleData, state: ScheduleState, model: ScheduleSettingsViewModel) {
    val zone = data.draft!!.value.timeZone
    var selected by rememberSaveable { mutableStateOf("") }
    Select("승인 부재", selected, listOf("" to "새 부재") + data.absences.map { it.absenceId to "${data.staff.firstOrNull { n -> n.nurseId == it.nurseId }?.displayName ?: "간호사"} · ${ScheduleTime.local(it.startsAt, zone)} · ${if (it.kind == "APPROVED_LEAVE") "VY" else "반차"}" }, state.editable) { selected = it }
    val absence = data.absences.firstOrNull { it.absenceId == selected }
    key(selected) {
        var nurse by rememberSaveable { mutableStateOf(absence?.nurseId ?: "") }
        var kind by rememberSaveable { mutableStateOf(absence?.kind ?: "APPROVED_LEAVE") }
        var start by rememberSaveable { mutableStateOf(absence?.let { ScheduleTime.local(it.startsAt, zone) } ?: "${state.month}-01T09:00") }
        var end by rememberSaveable { mutableStateOf(absence?.let { ScheduleTime.local(it.endsAt, zone) } ?: "${state.month}-01T13:00") }
        var confirmDelete by rememberSaveable { mutableStateOf(false) }
        Text("병동 시간대: $zone · 승인된 일정만 입력해 주세요.")
        Select("간호사", nurse, data.staff.map { it.nurseId to "${it.displayName}${if (it.active) "" else " (비활성)"}" }, state.editable) { nurse = it }
        Select("구분", kind, listOf("APPROVED_LEAVE" to "승인 VY", "APPROVED_HALF_DAY" to "승인 반차"), state.editable) { kind = it }
        Field("시작 (YYYY-MM-DDTHH:mm)", start, state.editable) { start = it }
        Field("종료 (YYYY-MM-DDTHH:mm)", end, state.editable) { end = it }
        Button(onClick = { attempt(model) {
            require(nurse.isNotEmpty()) { "간호사를 선택해 주세요." }
            val s = ScheduleTime.endpoint(start, absence?.startsAt, zone); val e = ScheduleTime.endpoint(end, absence?.endsAt, zone)
            require(Instant.parse(s) < Instant.parse(e)) { "종료는 시작보다 뒤여야 해요." }
            model.saveAbsence(absence?.absenceId, ScheduleAbsenceInput(nurse, kind, s, e))
        } }, enabled = state.editable) { Text("승인 부재 저장") }
        if (absence != null) {
            OutlinedButton(onClick = { confirmDelete = true }, enabled = state.editable) { Text("부재 삭제") }
            if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("승인 부재를 삭제할까요?") }, text = { Text("선택한 초안의 승인 부재가 삭제돼요.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; model.deleteAbsence(absence.absenceId) }, enabled = state.editable) { Text("삭제 확인") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } })
        }
    }
}

@Composable
private fun PatternPanel(data: ScheduleData, state: ScheduleState, model: ScheduleSettingsViewModel) {
    Select("간호사", state.nurseId ?: "", data.staff.map { it.nurseId to it.displayName }, state.editable) { model.selectNurse(it) }
    val labels = listOf("GENERAL" to "일반", "DAY_EVENING_ONLY" to "D/E 전담", "NIGHT_ONLY" to "N 전담")
    var selected by rememberSaveable(state.nurseId, state.pattern) { mutableStateOf(state.pattern ?: "") }
    if (state.nurseId != null && state.pattern == null) {
        Text("근무 형태를 확인하지 못했어요. 일반 근무로 추정하지 않아요.")
        TextButton(onClick = { model.selectNurse(state.nurseId) }, enabled = state.editable) { Text("근무 형태 다시 조회") }
    }
    if (state.pattern != null) {
        Text("현재 서버 값: ${labels.first { it.first == state.pattern }.second}")
        Select("근무 형태", selected, labels, state.editable) { selected = it }
        Text("같은 간호사의 근무 형태는 마지막 저장값이 적용돼요. 변경 시 초안은 재검증 대상이 돼요.")
        Button(onClick = { model.savePattern(selected) }, enabled = state.editable) { Text("근무 형태 저장") }
    }
}
