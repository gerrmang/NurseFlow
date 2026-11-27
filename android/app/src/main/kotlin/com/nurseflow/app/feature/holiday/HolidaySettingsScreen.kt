package com.nurseflow.app.feature.holiday

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.network.HolidayDay
import java.time.DayOfWeek
import java.time.YearMonth

@Composable
fun HolidaySettingsScreen(wardName: String, model: HolidaySettingsViewModel, onBack: () -> Unit, onForbidden: () -> Unit) {
    val s by model.state.collectAsStateWithLifecycle()
    val activity = generateSequence(LocalContext.current) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()
    DisposableEffect(model) { model.activate(); onDispose { if (activity?.isChangingConfigurations != true) model.deactivate() } }
    LaunchedEffect(s.forbidden) { if (s.forbidden) onForbidden() }
    var month by rememberSaveable(s.month) { mutableStateOf(s.month) }
    var date by rememberSaveable(s.month) { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("WARD_REST_DAY") }
    var name by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshDetails by rememberSaveable { mutableStateOf(false) }
    val dirty = s.calendarDirty || s.policyDirty
    val enabled = s.editable && confirmation == null
    fun leave() { if (!s.busy) { if (dirty) confirmation = "leave" else onBack() } }
    BackHandler { leave() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            TextButton(onClick = { leave() }, enabled = !s.busy) { Text("뒤로") }
            TextButton(onClick = { if (dirty) confirmation = "reload" else { date = ""; model.reload() } }, enabled = !s.busy && !s.loading) { Text("최신 설정 조회") }
        }
        Text("$wardName · 휴일·목표시간", style = MaterialTheme.typography.titleLarge)
        Text("월 목표는 저장된 정책과 달력으로 서버가 계산해요. 미저장 입력은 화면을 떠나면 사라져요.")
        if (s.loading || s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        s.notice?.let { Text(it) }
        if (s.stale && s.calendar != null) Text("최신 설정 조회 전까지 저장할 수 없어요.")
        if (s.uncertain) OutlinedButton(onClick = model::acknowledge, enabled = !s.stale && !s.busy && !s.loading) { Text("최신 저장 결과를 확인했어요") }
        HolidayField("대상 월 (YYYY-MM)", month, !s.busy && !s.loading && !s.uncertain) { month = it }
        OutlinedButton(onClick = { if (dirty) confirmation = "month" else { date = ""; model.reload(month) } }, enabled = !s.busy && !s.loading && !s.uncertain) { Text("월 조회") }
        s.calendar?.let { c ->
            Text("저장된 월 목표", style = MaterialTheme.typography.titleMedium)
            Text(holidayMinutes(c.target.generalTargetMinutes), style = MaterialTheme.typography.headlineSmall)
            Text("목표일 ${c.target.targetDays}일 · 휴식일 ${c.target.restDays}일 · 하루 ${holidayMinutes(c.target.dailyStandardMinutes)}")
            Text("주말 ${c.target.weekendDays}일 · 평일 공휴일 ${c.target.holidayDays}일 · 병동 휴무 ${c.target.wardRestDays}일 · 근무일 전환 ${c.target.wardWorkdays}일")
            if (dirty) Text("미저장 변경이 있어요. 위 요약은 저장된 값이에요.")
            Text(if (c.holidaySource.configured) "국가 공휴일 자동 갱신 사용" else "자동 공휴일이 꺼져 있어 직접 입력이 필요해요.")
            Text("마지막 갱신: ${c.holidaySource.fetchedAt?.let { "$it (UTC)" } ?: "아직 가져오지 않음"}")
            TextButton(onClick = { refreshDetails = !refreshDetails }) { Text("국가 공휴일 갱신 안내") }
            if (refreshDetails) {
                Text("임시공휴일 확인 등에 사용해요. 작년~내년만 가능하며 10분 내 재요청은 저장값을 반환해요.")
                OutlinedButton(onClick = { model.save("refresh") }, enabled = enabled && c.holidaySource.configured) { Text("공휴일 지금 가져오기") }
            }
            Text("휴일 달력", style = MaterialTheme.typography.titleMedium)
            Text("병동 지정이 국가 공휴일·주말보다 우선해요. 날짜를 선택해 편집하세요.")
            val ym = YearMonth.parse(s.month)
            val offset = ym.atDay(1).dayOfWeek.value % 7
            Row { listOf("일", "월", "화", "수", "목", "금", "토").forEach { Text(it, Modifier.weight(1f)) } }
            val cells = List(offset) { 0 } + (1..ym.lengthOfMonth()).toList()
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    (week + List(7 - week.size) { 0 }).forEach { n ->
                        if (n == 0) Spacer(Modifier.weight(1f)) else {
                            val day = ym.atDay(n).toString()
                            val ward = s.days.find { it.date == day }
                            val national = c.publicHolidays.find { it.date == day }
                            val final = ward ?: national
                            val label = final?.let { holidayLabels[it.kind] } ?: if (ym.atDay(n).dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) "주말" else "평일"
                            TextButton(modifier = Modifier.weight(1f).semantics {
                                contentDescription = "$day · $label${ward?.let { " · 병동 지정 ${it.name.orEmpty()}" } ?: ""}${national?.let { " · 국가 공휴일 ${it.name.orEmpty()}" } ?: ""}"
                                selected = date == day
                            }, contentPadding = PaddingValues(2.dp), enabled = enabled, onClick = { date = day; kind = ward?.kind ?: "WARD_REST_DAY"; name = ward?.name ?: "" }) {
                                Text("${if (date == day) "✓" else ""}$n\n$label")
                            }
                        }
                    }
                }
            }
            if (date.isNotEmpty()) {
                Text("$date 병동 항목", style = MaterialTheme.typography.titleMedium)
                c.publicHolidays.find { it.date == date }?.let { Text("국가 공휴일: ${it.name ?: holidayLabels[it.kind]}") }
                s.days.find { it.date == date }?.let { Text("병동 지정: ${holidayLabels[it.kind]} ${it.name.orEmpty()}") }
                HolidaySelect(kind, enabled) { kind = it }
                HolidayField("공개 휴일 이름 (선택)", name, enabled) { if (it.length <= 50) name = it }
                Text("공휴일 이름 등 공개 정보만 입력해 주세요.")
                Row {
                    OutlinedButton(onClick = { model.editDay(HolidayDay(date, kind, name.trim().ifEmpty { null })) }, enabled = enabled) { Text("달력에 적용") }
                    TextButton(onClick = { model.editDay(HolidayDay(date, kind), remove = true) }, enabled = enabled && s.days.any { it.date == date }) { Text("병동 지정 삭제") }
                }
            }
            // Full list exposes holiday names and origins without squeezing text into small day cells.
            (s.days.map { it.date } + c.publicHolidays.map { it.date }).distinct().sorted().forEach { d ->
                val ward = s.days.find { it.date == d }; val national = c.publicHolidays.find { it.date == d }
                Text("$d · ${ward?.let { "병동 지정: ${holidayLabels[it.kind]} ${it.name.orEmpty()}" } ?: ""}${national?.let { " · 국가 공휴일: ${it.name ?: holidayLabels[it.kind]}" } ?: ""}")
            }
            Button(onClick = { confirmation = "calendar" }, enabled = enabled && s.calendarDirty) { Text("달력 저장") }
            HorizontalDivider()
            Text("목표시간 정책", style = MaterialTheme.typography.titleMedium)
            Text("분 단위로 입력하세요. Night 전담의 빈 값은 일반 월 목표 또는 근무 유형 환산시간을 사용해요.")
            s.policy.forEachIndexed { i, v ->
                HolidayField("${holidayPolicyLabels[i]} (분)", v, enabled, number = true) { model.editPolicy(i, it) }
                Text(v.toIntOrNull()?.let { holidayMinutes(it) } ?: if (i >= 4 && v.isEmpty()) "일반 값 사용" else "유효한 정수를 입력하세요")
            }
            if (parseHolidayPolicy(s.policy) == null) Text("하루 1~1440분, 환산시간 0~1440분, Night 전담 월 목표 0~44640분으로 입력해 주세요.")
            Button(onClick = { confirmation = "policy" }, enabled = enabled && s.policyDirty && parseHolidayPolicy(s.policy) != null) { Text("목표시간 정책 저장") }
        }
    }
    confirmation?.let { action ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("변경 확인") }, text = { Text(when (action) {
            "calendar" -> "${s.month} 병동 항목 전체를 ${s.days.size}건으로 교체할까요? ${if (s.days.isEmpty()) "모든 병동 지정이 삭제돼요." else ""} 국가 공휴일은 유지돼요."
            "policy" -> "병동 목표시간 정책을 저장할까요? 달력의 미저장 편집은 유지해요."
            "leave" -> "미저장 입력을 버리고 나갈까요?"
            else -> "미저장 달력·목표시간 입력을 버리고 최신 설정을 조회할까요?"
        }) }, confirmButton = { TextButton(enabled = !s.busy && !s.loading, onClick = {
            confirmation = null
            when (action) { "calendar", "policy" -> model.save(action); "leave" -> onBack(); else -> { date = ""; model.reload(if (action == "month") month else s.month) } }
        }) { Text("확인") } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("취소") } })
    }
}
@Composable private fun HolidayField(label: String, value: String, enabled: Boolean, number: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(value, change, label = { Text(label) }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text))
}
@Composable private fun HolidaySelect(value: String, enabled: Boolean, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text("날짜 구분: ${holidayLabels[value]}") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) { holidayLabels.forEach { (k, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; change(k) }) } }
    }
}
