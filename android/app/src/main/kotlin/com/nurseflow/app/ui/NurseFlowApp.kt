package com.nurseflow.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import com.nurseflow.app.R
import com.nurseflow.app.ui.theme.NurseFlowColors as C
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Page { WardPicker, AdminRoster, ManualAssign, Absences, Settings, AutoSetup, Review, Confirm, NurseRoster }
private enum class Shift(val short: String, val label: String, val hours: String) {
    DAY("D", "Day", "07:00 – 15:00"),
    EVENING("E", "Evening", "15:00 – 23:00"),
    NIGHT("N", "Night", "23:00 – 다음날 07:00"),
    OFF("O", "Off", "")
}

private data class Nurse(val id: String, val name: String, val title: String = "")
private data class Candidate(val nurse: Nurse, val eligible: Boolean, val detail: String)

private class DemoRoster {
    val sampleMonth: YearMonth = YearMonth.of(2026, 11)
    val nurses = listOf(
        Nurse("haneul", "김하늘", "책임"), Nurse("seoyeon", "이서연"),
        Nurse("jimin", "박지민"), Nurse("yuna", "최유나"),
        Nurse("daeun", "정다은"), Nurse("sohee", "한소희", "신규")
    )
    val draft = mutableStateMapOf<Pair<String, LocalDate>, Shift>()
    val published = mutableStateMapOf<Pair<String, LocalDate>, Shift>()
    val absence = mutableStateMapOf<Pair<String, LocalDate>, String>()
    val publishedAbsence = mutableStateMapOf<Pair<String, LocalDate>, String>()
    var draftVersion by mutableIntStateOf(4)
    var publishedVersion by mutableIntStateOf(3)
    var publishedAt by mutableStateOf(LocalDate.of(2026, 10, 25))

    init {
        val rows = listOf(
            "haneul" to "DDEENOO", "seoyeon" to "EEODDDN",
            "jimin" to "XXOOEEE", "yuna" to "DOAD EED".replace(" ", ""),
            "daeun" to "EENNOOD", "sohee" to "NNOODDE"
        )
        rows.forEach { (id, codes) ->
            codes.forEachIndexed { index, code ->
                val date = LocalDate.of(2026, 11, 2).plusDays(index.toLong())
                when (code) {
                    'D' -> draft[id to date] = Shift.DAY
                    'E' -> draft[id to date] = Shift.EVENING
                    'N' -> draft[id to date] = Shift.NIGHT
                    'O' -> draft[id to date] = Shift.OFF
                    'X' -> absence[id to date] = "휴가"
                    'A' -> absence[id to date] = "오전 반차"
                }
            }
        }
        absence["yuna" to LocalDate.of(2026, 11, 10)] = "오후 반차"
        absence["jimin" to LocalDate.of(2026, 11, 10)] = "휴가"
        draft["haneul" to LocalDate.of(2026, 11, 10)] = Shift.DAY
        draft["daeun" to LocalDate.of(2026, 11, 9)] = Shift.NIGHT
        published.putAll(draft)
        publishedAbsence.putAll(absence)
        published["jimin" to LocalDate.of(2026, 11, 23)] = Shift.OFF
        published["haneul" to LocalDate.of(2026, 11, 2)] = Shift.DAY
    }

    fun visibleShift(id: String, date: LocalDate, public: Boolean): String {
        val leave = (if (public) publishedAbsence else absence)[id to date]
        if (leave != null) return when (leave) {
            "휴가" -> "휴"
            "오전 반차" -> "AM"
            else -> "PM"
        }
        val shifts = if (public) published else draft
        return shifts[id to date]?.short ?: ""
    }

    fun candidates(date: LocalDate, shift: Shift): List<Candidate> = nurses.map { nurse ->
        val leave = absence[nurse.id to date]
        val current = draft[nurse.id to date]
        val prior = draft[nurse.id to date.minusDays(1)]
        val reason = when {
            leave == "휴가" -> "승인된 휴가"
            leave == "오후 반차" && shift != Shift.NIGHT -> "오후 반차와 시간이 겹침 (13:00~)"
            leave == "오전 반차" && shift == Shift.DAY -> "오전 반차와 시간이 겹침"
            prior == Shift.NIGHT && shift == Shift.DAY -> "전날 Night 근무 · Night 다음날 Day 금지"
            current != null && current != Shift.OFF -> "같은 날 ${current.label} 배치됨 (하루 1근무)"
            else -> "이번 달 ${shift.label} ${if (nurse.id == "sohee") 8 else 5}회"
        }
        Candidate(nurse, reason.startsWith("이번 달"), reason)
    }

    fun assign(date: LocalDate, shift: Shift, nurse: Nurse): Boolean {
        if (candidates(date, shift).firstOrNull { it.nurse.id == nurse.id }?.eligible != true) return false
        draft[nurse.id to date] = shift
        return true
    }

    fun publish() {
        published.clear()
        published.putAll(draft)
        publishedAbsence.clear()
        publishedAbsence.putAll(absence)
        publishedVersion = draftVersion
        publishedAt = LocalDate.now()
    }
}

@Composable
fun NurseFlowApp() {
    val roster = remember { DemoRoster() }
    var page by rememberSaveable { mutableStateOf(Page.WardPicker) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.of(2026, 11, 10).toString()) }
    var selectedShift by rememberSaveable { mutableStateOf(Shift.DAY) }
    var month by rememberSaveable { mutableStateOf(YearMonth.of(2026, 11).toString()) }
    var nurseWholeWard by rememberSaveable { mutableStateOf(false) }
    var selectedWard by rememberSaveable { mutableStateOf("7병동 · 내과") }
    val currentMonth = YearMonth.parse(month)

    Surface(color = C.canvas, modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        when (page) {
            Page.WardPicker -> WardPickerScreen { ward, admin ->
                selectedWard = ward
                page = if (admin) Page.AdminRoster else Page.NurseRoster
            }
            Page.AdminRoster -> AdminRosterScreen(
                roster = roster, ward = selectedWard, month = currentMonth,
                onMonthChange = { month = currentMonth.plusMonths(it.toLong()).toString() },
                onWard = { page = Page.WardPicker },
                onCell = { date, shift -> selectedDate = date.toString(); selectedShift = shift; page = Page.ManualAssign },
                onAbsences = { page = Page.Absences }, onSettings = { page = Page.Settings },
                onAuto = { page = Page.AutoSetup }, onReview = { page = Page.Review }
            )
            Page.ManualAssign -> ManualAssignScreen(roster, LocalDate.parse(selectedDate), selectedShift) { page = Page.AdminRoster }
            Page.Absences -> AbsencesScreen(roster) { page = Page.AdminRoster }
            Page.Settings -> SettingsScreen { page = Page.AdminRoster }
            Page.AutoSetup -> AutoSetupScreen(roster, currentMonth, onBack = { page = Page.AdminRoster }, onStart = {
                roster.draftVersion += 1
                page = Page.Review
            })
            Page.Review -> ReviewScreen(roster, onBack = { page = Page.AdminRoster }, onConfirm = { page = Page.Confirm })
            Page.Confirm -> ConfirmScreen(roster, onBack = { page = Page.Review }, onPublish = {
                roster.publish()
                nurseWholeWard = false
                page = Page.NurseRoster
            })
            Page.NurseRoster -> NurseRosterScreen(roster, selectedWard, currentMonth, nurseWholeWard,
                onToggle = { nurseWholeWard = it },
                onMonthChange = { month = currentMonth.plusMonths(it.toLong()).toString() },
                onWard = { page = Page.WardPicker })
        }
    }
}

@Composable
private fun Label(text: String, size: Int = 14, color: Color = C.text, bold: Boolean = false, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, lineHeight = (size * 1.45f).sp)
}

@Composable
private fun Asset(id: Int, size: Int, description: String? = null) {
    Image(painterResource(id), contentDescription = description, modifier = Modifier.size(size.dp))
}

@Composable
private fun AppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, outlined: Boolean = false, enabled: Boolean = true) {
    if (outlined) {
        OutlinedButton(onClick, modifier.height(48.dp), enabled = enabled,
            shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, C.borderStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = C.brandDeep)) {
            Label(text, 15, C.brandDeep, true)
        }
    } else {
        Button(onClick, modifier.height(48.dp), enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.brand, disabledContainerColor = C.subtle)) {
            Label(text, 15, if (enabled) Color.White else C.muted, true)
        }
    }
}

@Composable
private fun ShiftChip(code: String, size: Int = 22, modifier: Modifier = Modifier) {
    val bg = when (code) { "D" -> C.dayBg; "E" -> C.eveningBg; "N" -> C.nightBg; "O" -> C.offBg; "휴", "AM", "PM" -> C.leaveBg; else -> C.surface }
    val fg = when (code) { "D" -> C.dayFg; "E" -> C.eveningFg; "N" -> C.nightFg; "O" -> C.offFg; "휴", "AM", "PM" -> C.leaveFg; else -> C.muted }
    Box(modifier = modifier.size(size.dp).background(bg, RoundedCornerShape(if (size == 22) 6.dp else 8.dp))
        .then(if (code in listOf("휴", "AM", "PM")) Modifier.border(1.dp, C.leaveFg, RoundedCornerShape(6.dp)) else Modifier),
        contentAlignment = Alignment.Center) {
        Label(code, if (code.length > 1) 8 else if (size == 22) 11 else 13, fg, true)
    }
}

@Composable
private fun StatusBanner(message: String, published: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(39.dp).background(if (published) C.brandSoft else C.warningSoft, RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Asset(if (published) R.drawable.figma_11_370_imgframe else R.drawable.figma_8_382_imgframe, 18)
        Spacer(Modifier.width(8.dp))
        Label(message, 13, if (published) C.brandDeep else C.warning)
    }
}

@Composable
private fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.background(C.surface, RoundedCornerShape(16.dp)).padding(16.dp)) { content() }
}

@Composable
private fun WardPickerScreen(onSelect: (String, Boolean) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var lastWard by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(Modifier.height(0.dp))
        Label("이수진 님", 12, C.secondary)
        Label("병동 선택", 18, C.text, true)
        Spacer(Modifier.height(16.dp))
        CardBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Asset(R.drawable.figma_8_313_imgframe, 20)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Label("병원", 11, C.secondary); Label("서울한빛병원", 15, C.text, true) }
                Asset(R.drawable.figma_8_313_imgframe1, 20)
            }
        }
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth().height(44.dp)
            .background(C.surface, RoundedCornerShape(12.dp))
            .border(1.dp, C.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Asset(R.drawable.figma_8_313_imgframe2, 18)
            Spacer(Modifier.width(8.dp))
            BasicTextField(query, { query = it }, singleLine = true,
                textStyle = TextStyle(color = C.text, fontSize = 14.sp),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (query.isBlank()) Label("병동 이름 검색", 14, C.muted)
                        inner()
                    }
                })
        }
        Spacer(Modifier.height(20.dp))
        Label("내 병동 3", 13, C.secondary, true)
        Spacer(Modifier.height(15.dp))
        listOf(Triple("7병동 · 내과", true, "간호사 24명 · 11월 초안 편집 중"),
            Triple("8병동 · 외과", false, "11월 근무표 공개됨"),
            Triple("내과계 중환자실", false, "11월 근무표 준비 중"))
            .filter { it.first.contains(query, true) }
            .forEachIndexed { index, ward ->
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).height(if (index == 0) 118.dp else 115.dp)
                    .border(if (index == 0) 1.5.dp else 0.dp, if (index == 0) C.brand else C.surface, RoundedCornerShape(16.dp))
                    .background(C.surface, RoundedCornerShape(16.dp))
                    .clickable { onSelect(ward.first, ward.second) }.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label(ward.first, 16, C.text, true)
                        if (index == 0) { Spacer(Modifier.width(6.dp)); Label("최근", 11, C.brand, true) }
                        Spacer(Modifier.weight(1f))
                        Asset(R.drawable.figma_8_313_imgframe4, 20)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.background(if (ward.second) C.brandSoft else C.infoSoft, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Asset(if (ward.second) R.drawable.figma_8_313_imgframe3 else R.drawable.figma_8_313_imgframe5, 14)
                        Spacer(Modifier.width(4.dp))
                        Label(if (ward.second) "관리자 · 편집 가능" else "조회 전용", 12, if (ward.second) C.brandDeep else C.info, true)
                    }
                    Spacer(Modifier.height(5.dp))
                    Label(ward.third, 12, C.secondary)
                }
            }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("다음에도 마지막 병동으로 바로 시작", 13, C.secondary, modifier = Modifier.weight(1f))
            if (lastWard) {
                Image(painterResource(R.drawable.figma_8_313_imgframe6),
                    contentDescription = "마지막 병동으로 바로 시작 사용 중",
                    modifier = Modifier.width(44.dp).height(24.dp).clickable { lastWard = false })
            } else {
                Box(Modifier.width(44.dp).height(24.dp)
                    .background(C.borderStrong, RoundedCornerShape(99.dp)).clickable { lastWard = true }
                    .padding(3.dp), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.size(18.dp).background(Color.White, RoundedCornerShape(99.dp)))
                }
            }
        }
    }
}

@Composable
private fun AdminRosterScreen(
    roster: DemoRoster, ward: String, month: YearMonth, onMonthChange: (Int) -> Unit,
    onWard: () -> Unit, onCell: (LocalDate, Shift) -> Unit,
    onAbsences: () -> Unit, onSettings: () -> Unit, onAuto: () -> Unit, onReview: () -> Unit
) {
    var monthGrid by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(ward, 12, C.secondary, modifier = Modifier.clickable(onClick = onWard))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("‹", 21, C.text, true, Modifier.clickable { onMonthChange(-1) })
                        Spacer(Modifier.width(7.dp))
                        Label("${month.year}년 ${month.monthValue}월", 18, C.text, true)
                        Spacer(Modifier.width(7.dp))
                        Label("›", 21, C.text, true, Modifier.clickable { onMonthChange(1) })
                    }
                }
                Box(Modifier.size(44.dp).clickable { monthGrid = !monthGrid }, contentAlignment = Alignment.Center) { Asset(R.drawable.figma_8_382_imgframe1, 22, "필터") }
                Box(Modifier.size(44.dp).clickable(onClick = onSettings), contentAlignment = Alignment.Center) { Asset(R.drawable.figma_8_382_imgframe2, 22, "설정") }
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
                StatusBanner("v${roster.draftVersion} 초안 · 간호사에게 아직 보이지 않아요")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CounterPill("전체 24명", C.brand, Color.White)
                    CounterPill("⚠ 미충원 3칸", C.dangerSoft, C.danger)
                    CounterPill("휴가 5", C.leaveBg, C.leaveFg)
                    Spacer(Modifier.weight(1f))
                    Label("주", 11, if (monthGrid) C.secondary else C.brandDeep, true, Modifier.clickable { monthGrid = false })
                    Label("월", 11, if (monthGrid) C.brandDeep else C.secondary, true, Modifier.clickable { monthGrid = true })
                }
                Spacer(Modifier.height(15.dp))
                if (monthGrid) {
                    MonthSummary(month, roster, onCell)
                } else {
                    key(month) {
                        val firstMonday = month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong())
                        val weekStarts = (0..5).map { firstMonday.plusWeeks(it.toLong()) }
                            .filter { start -> YearMonth.from(start) == month || YearMonth.from(start.plusDays(6)) == month }
                        val startPage = if (month == YearMonth.of(2026, 11)) 1 else 0
                        val pager = rememberPagerState(initialPage = startPage, pageCount = { weekStarts.size })
                        HorizontalPager(pager, modifier = Modifier.fillMaxWidth().height(386.dp)) { page ->
                            AdminGrid(roster, (0..6).map { weekStarts[page].plusDays(it.toLong()) }, onCell)
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = C.border)
        Row(Modifier.fillMaxWidth().background(C.surface).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("자동 배치", onAuto, Modifier.weight(1f), outlined = true)
            AppButton("검토 · 확정", onReview, Modifier.weight(1f))
        }
        BottomTabs(selected = "근무표", onRoster = {}, onAbsences = onAbsences, onSettings = onSettings)
    }
}

@Composable
private fun CounterPill(text: String, bg: Color, fg: Color) {
    Box(Modifier.background(bg, RoundedCornerShape(99.dp)).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun AdminGrid(roster: DemoRoster, dates: List<LocalDate>, onCell: (LocalDate, Shift) -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.surface, RoundedCornerShape(16.dp))) {
        Row(Modifier.fillMaxWidth().background(C.subtle).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Label("이름", 11, C.secondary, true, Modifier.width(66.dp).padding(start = 10.dp))
            dates.forEach { date ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Label("${date.dayOfMonth}", 12, if (date.dayOfWeek.value >= 6) C.info else C.text, true)
                    Label(koreanWeekday(date), 10, if (date.dayOfWeek.value >= 6) C.info else C.muted)
                }
            }
        }
        listOf(Shift.DAY to 3, Shift.EVENING to 3, Shift.NIGHT to 2).forEach { (shift, required) ->
            Row(Modifier.fillMaxWidth().height(26.dp).background(C.canvas), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.width(66.dp).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShiftChip(shift.short)
                    Spacer(Modifier.width(3.dp))
                    Label("충원", 10, C.secondary)
                }
                dates.forEach { date ->
                    val shown = if (YearMonth.from(date) == YearMonth.of(2026, 11) && date.dayOfMonth in 2..8) {
                        if (shift == Shift.DAY && date.dayOfMonth == 7) 2 else if (shift == Shift.NIGHT && date.dayOfMonth == 4) 1 else required
                    } else roster.draft.entries.count { it.key.second == date && it.value == shift }
                    Box(Modifier.weight(1f).fillMaxHeight().clickable { onCell(date, shift) }, contentAlignment = Alignment.Center) {
                        Label("$shown/$required", 10, if (shown < required) C.danger else C.secondary, shown < required)
                    }
                }
            }
            HorizontalDivider(color = C.border, thickness = 0.5.dp)
        }
        roster.nurses.forEach { nurse ->
            Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(66.dp).padding(start = 10.dp)) {
                    Label(nurse.name, 11, C.text, true, Modifier.fillMaxWidth(),)
                    if (nurse.title.isNotBlank()) Label(nurse.title, 10, C.muted)
                }
                dates.forEach { date ->
                    Box(Modifier.weight(1f).fillMaxHeight().clickable {
                        onCell(date, roster.draft[nurse.id to date]?.takeIf { it != Shift.OFF } ?: Shift.DAY)
                    }, contentAlignment = Alignment.Center) {
                        val code = roster.visibleShift(nurse.id, date, false)
                        if (code.isNotEmpty()) ShiftChip(code)
                    }
                }
            }
            HorizontalDivider(color = C.border, thickness = 0.5.dp)
        }
        Box(Modifier.fillMaxWidth().height(35.dp), contentAlignment = Alignment.Center) { Label("외 18명 · 스크롤", 11, C.muted) }
    }
}

@Composable
private fun MonthSummary(month: YearMonth, roster: DemoRoster, onCell: (LocalDate, Shift) -> Unit) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value % 7
    val days = (0 until 42).map { it - offset + 1 }
    Column(Modifier.fillMaxWidth().background(C.surface, RoundedCornerShape(16.dp)).padding(8.dp)) {
        Row {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Label(it, 11, C.secondary, true) }
            }
        }
        Spacer(Modifier.height(6.dp))
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().height(48.dp)) {
                week.forEach { number ->
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        if (number in 1..month.lengthOfMonth()) {
                            val date = month.atDay(number)
                            Column(Modifier.fillMaxWidth().clickable { onCell(date, Shift.DAY) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Label("$number", 12, C.text, true)
                                val assigned = roster.draft.entries.count { it.key.second == date && it.value == Shift.DAY }
                                Label("D $assigned/3", 10, if (assigned < 3) C.danger else C.brandDeep)
                            }
                        }
                    }
                }
            }
        }
        Label("날짜를 누르면 Day 후보를 확인할 수 있어요", 11, C.secondary, modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun BottomTabs(selected: String, onRoster: () -> Unit, onAbsences: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).background(C.surface), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(Triple("근무표", R.drawable.figma_8_382_imgframe5, onRoster),
            Triple("휴가·반차", R.drawable.figma_8_382_imgframe6, onAbsences),
            Triple("설정", R.drawable.figma_8_382_imgframe7, onSettings)).forEach { (title, image, action) ->
            Column(Modifier.weight(1f).fillMaxHeight().clickable(onClick = action), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Asset(image, 20)
                Label(title, 11, if (title == selected) C.brandDeep else C.muted, title == selected)
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Label("‹", 30, C.text, modifier = Modifier.width(40.dp).clickable(onClick = onBack))
        Column {
            if (subtitle != null) Label(subtitle, 12, C.secondary)
            Label(title, 18, C.text, true)
        }
    }
}

@Composable
private fun ManualAssignScreen(roster: DemoRoster, date: LocalDate, shift: Shift, onBack: () -> Unit) {
    var availableOnly by rememberSaveable { mutableStateOf(false) }
    var assigned by rememberSaveable { mutableStateOf<String?>(null) }
    val candidates = roster.candidates(date, shift)
    val filtered = if (availableOnly) candidates.filter { it.eligible } else candidates
    Column(Modifier.fillMaxSize()) {
        Header("수동 배치", "${date.monthValue}월 ${date.dayOfMonth}일 (${koreanWeekday(date)}) · ${shift.label}", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            CardBox(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShiftChip(shift.short, 28)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Label("필요 3 · 배치 ${if (assigned == null) 1 else 2}", 15, C.text, true)
                        Text("${shift.label} ${shift.hours} · 배치됨: 김하늘", color = C.secondary,
                            fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    CounterPill("⚠ 2명 부족", C.dangerSoft, C.danger)
                }
            }
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("후보 ${candidates.size}명", 13, C.secondary, true, Modifier.weight(1f))
                Label("가능한 사람만", 12, C.secondary)
                Spacer(Modifier.width(5.dp))
                if (availableOnly) {
                    Box(Modifier.width(40.dp).height(22.dp).background(C.brand, RoundedCornerShape(99.dp))
                        .clickable { availableOnly = false }.padding(3.dp), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.size(16.dp).background(Color.White, RoundedCornerShape(99.dp)))
                    }
                } else {
                    Image(painterResource(R.drawable.figma_9_359_imgframe2),
                        contentDescription = "가능한 사람만 필터 꺼짐",
                        modifier = Modifier.width(40.dp).height(22.dp).clickable { availableOnly = true })
                }
            }
            LazyColumn(Modifier.fillMaxWidth().background(C.surface, RoundedCornerShape(16.dp)).padding(vertical = 8.dp)) {
                item { Label("배치 가능 ${candidates.count { it.eligible }}", 12, C.brandDeep, true, Modifier.padding(16.dp, 4.dp)) }
                items(filtered.filter { it.eligible }) { candidate ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Asset(R.drawable.figma_9_359_imgframe3, 20)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Label(candidate.nurse.name, 15, C.text, true); Label(candidate.detail, 12, C.secondary) }
                        Button(onClick = {
                            if (roster.assign(date, shift, candidate.nurse)) assigned = candidate.nurse.id
                        }, modifier = Modifier.width(54.dp).height(34.dp),
                            enabled = assigned != candidate.nurse.id,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = C.brand)) {
                            Label(if (assigned == candidate.nurse.id) "완료" else "배치", 13, Color.White, true)
                        }
                    }
                }
                if (!availableOnly) {
                    item { Label("배치 불가 ${candidates.count { !it.eligible }}", 12, C.danger, true, Modifier.padding(16.dp, 8.dp)) }
                    items(filtered.filter { !it.eligible }) { candidate ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                            Asset(R.drawable.figma_9_359_imgframe5, 20)
                            Spacer(Modifier.width(10.dp))
                            Column { Label(candidate.nurse.name, 15, C.secondary, true); Label(candidate.detail, 12, C.danger) }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(C.surface).padding(16.dp)) { AppButton("완료", onBack, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun AbsencesScreen(roster: DemoRoster, onBack: () -> Unit) {
    var selectedNurse by rememberSaveable { mutableStateOf(roster.nurses.first().id) }
    var selectedType by rememberSaveable { mutableStateOf("휴가") }
    var dateText by rememberSaveable { mutableStateOf("2026-11-12") }
    var feedback by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Header("휴가 · 반차", "7병동 · 2026년 11월", onBack)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            item { Label("승인된 일정만 등록합니다", 13, C.secondary, modifier = Modifier.padding(vertical = 12.dp)) }
            items(roster.absence.entries.sortedBy { it.key.second }) { entry ->
                CardBox(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShiftChip(if (entry.value == "휴가") "휴" else if (entry.value == "오전 반차") "AM" else "PM", 28)
                        Spacer(Modifier.width(10.dp))
                        Column { Label("${roster.nurses.firstOrNull { it.id == entry.key.first }?.name} · ${entry.value}", 15, C.text, true); Label(entry.key.second.toString(), 12, C.secondary) }
                    }
                }
            }
            item {
                CardBox(Modifier.fillMaxWidth()) {
                    Label("휴가 · 반차 등록", 18, C.text, true)
                    Spacer(Modifier.height(10.dp))
                    Label("간호사", 12, C.secondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        roster.nurses.take(3).forEach { nurse ->
                            CounterPill(nurse.name, if (selectedNurse == nurse.id) C.brandSoft else C.subtle, C.brandDeep)
                        }
                    }
                    roster.nurses.take(3).forEach { nurse ->
                        Label(if (selectedNurse == nurse.id) "✓ ${nurse.name}" else nurse.name, 12, C.brandDeep, modifier = Modifier.clickable { selectedNurse = nurse.id }.padding(5.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("휴가", "오전 반차", "오후 반차").forEach { type ->
                            Label(type, 12, if (type == selectedType) C.brandDeep else C.secondary, type == selectedType,
                                Modifier.clickable { selectedType = type }.background(if (type == selectedType) C.brandSoft else C.subtle, RoundedCornerShape(8.dp)).padding(8.dp))
                        }
                    }
                    OutlinedTextField(dateText, { dateText = it }, label = { Text("날짜 (YYYY-MM-DD)") }, singleLine = true)
                    if (feedback.isNotBlank()) Label(feedback, 12, C.warning, modifier = Modifier.padding(vertical = 6.dp))
                    AppButton("저장", {
                        val parsed = runCatching { LocalDate.parse(dateText) }.getOrNull()
                        if (parsed == null) feedback = "올바른 날짜를 입력해 주세요"
                        else { roster.absence[selectedNurse to parsed] = selectedType; feedback = "시연 데이터에 저장했습니다" }
                    }, Modifier.fillMaxWidth())
                }
            }
        }
        BottomTabs("휴가·반차", onBack, {}, onBack)
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header("근무 유형 · 필요 인원", onBack = onBack)
        Column(Modifier.weight(1f).padding(16.dp)) {
            Label("근무 유형", 14, C.secondary, true)
            Spacer(Modifier.height(10.dp))
            CardBox(Modifier.fillMaxWidth()) {
                listOf(Shift.DAY, Shift.EVENING, Shift.NIGHT).forEach { shift ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShiftChip(shift.short, 28); Spacer(Modifier.width(10.dp))
                        Column { Label(shift.label, 15, C.text, true); Label(shift.hours, 12, C.secondary) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Label("하루 필요 인원", 14, C.secondary, true)
            Spacer(Modifier.height(10.dp))
            CardBox(Modifier.fillMaxWidth()) {
                listOf(Shift.DAY to 3, Shift.EVENING to 3, Shift.NIGHT to 2).forEach { (shift, count) ->
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShiftChip(shift.short); Spacer(Modifier.width(8.dp))
                        Label("${shift.label} 최소 인원", 14, C.text, modifier = Modifier.weight(1f)); Label("$count", 16, C.text, true)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Label("설정은 다음 자동 배치부터 반영됩니다. 확정된 근무표는 바뀌지 않습니다.", 12, C.secondary)
        }
        BottomTabs("설정", onBack, onBack, {})
    }
}

@Composable
private fun AutoSetupScreen(roster: DemoRoster, month: YearMonth, onBack: () -> Unit, onStart: () -> Unit) {
    var scope by rememberSaveable { mutableStateOf("전체 다시 배치") }
    Column(Modifier.fillMaxSize()) {
        Header("자동 배치", "${month.year}년 ${month.monthValue}월", onBack)
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardBox(Modifier.fillMaxWidth()) {
                Label("범위", 13, C.secondary, true)
                listOf("전체 다시 배치", "빈 칸만 채우기").forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { scope = option }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Label(if (scope == option) "◉" else "○", 23, C.brand)
                        Spacer(Modifier.width(10.dp))
                        Column { Label(option, 14, C.text, true); Label(if (option == "전체 다시 배치") "직접 배치 · 고정한 칸 12개는 유지" else "현재 초안은 그대로 두고 미충원만", 12, C.secondary) }
                    }
                }
            }
            CardBox(Modifier.fillMaxWidth()) {
                Label("반드시 지키는 규칙", 13, C.secondary, true)
                listOf("승인된 휴가 시간에는 배치하지 않음", "반차 시간과 겹치는 근무는 배치하지 않음", "Night 다음날 Day는 배치하지 않음", "최대한: 간호사 간 근무 균등 배분").forEach {
                    Label("✓ $it", 13, C.text, modifier = Modifier.padding(top = 8.dp))
                }
            }
            CardBox(Modifier.fillMaxWidth()) {
                Label("입력 데이터 점검", 13, C.secondary, true)
                Label("✓ 간호사 24명", 13, C.brandDeep, modifier = Modifier.padding(top = 7.dp))
                Label("✓ 근무 유형 3개 · 필요 인원 설정됨", 13, C.brandDeep, modifier = Modifier.padding(top = 7.dp))
                Label("✓ 승인 휴가 · 반차 ${roster.absence.size}건 반영", 13, C.brandDeep, modifier = Modifier.padding(top = 7.dp))
            }
            Label("결과는 새 초안으로 저장됩니다. 현재 공개 근무표는 유지됩니다.", 12, C.secondary)
        }
        Box(Modifier.fillMaxWidth().background(C.surface).padding(16.dp)) { AppButton("자동 배치 시작", onStart, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun ReviewScreen(roster: DemoRoster, onBack: () -> Unit, onConfirm: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header("자동 배치 결과", "11월 · v${roster.draftVersion} 초안", onBack)
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusBanner("v${roster.draftVersion} 초안 · 간호사에게 아직 보이지 않아요")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReviewMetric("3칸", "미충원 · 부족 4명", C.dangerSoft, C.danger, Modifier.weight(1f))
                ReviewMetric("0건", "필수 규칙 위반", C.brandSoft, C.brandDeep, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReviewMetric("±2회", "균등 편차 최대", C.warningSoft, C.warning, Modifier.weight(1f))
                ReviewMetric("12칸", "직접 배치 유지", C.subtle, C.secondary, Modifier.weight(1f))
            }
            Label("문제 날짜 3", 13, C.secondary, true)
            CardBox(Modifier.fillMaxWidth()) {
                listOf("11/21 (토) · Day · 필요 3 · 배치 1 · 2명 부족", "11/4 (수) · Night · 필요 2 · 배치 1 · 1명 부족", "11/7 (토) · Day · 필요 3 · 배치 2 · 1명 부족").forEach {
                    Label(it, 13, C.text, true, Modifier.padding(vertical = 8.dp))
                    HorizontalDivider(color = C.border)
                }
            }
            CardBox(Modifier.fillMaxWidth()) {
                Label("균등 배분", 13, C.secondary, true)
                Label("한소희 · Night 5회 (+2)", 13, C.text, modifier = Modifier.padding(top = 8.dp))
                Label("이유: 휴가자 대체 가능 인원이 한소희뿐", 12, C.secondary)
            }
        }
        Row(Modifier.fillMaxWidth().background(C.surface).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("초안 편집", onBack, Modifier.weight(1f), true)
            AppButton("확정 단계로", onConfirm, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReviewMetric(value: String, label: String, bg: Color, fg: Color, modifier: Modifier) {
    Column(modifier.background(bg, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Label(value, 20, fg, true)
        Label(label, 12, fg)
    }
}

@Composable
private fun ConfirmScreen(roster: DemoRoster, onBack: () -> Unit, onPublish: () -> Unit) {
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Header("근무표 확정", "11월 · v${roster.draftVersion} 초안", onBack)
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardBox(Modifier.fillMaxWidth()) {
                Label("확정 전 마지막 확인", 16, C.text, true)
                Label("✓ 필수 규칙 위반 0건", 13, C.brandDeep, modifier = Modifier.padding(top = 8.dp))
                Label("⚠ 미충원 3칸 · 부족 4명", 13, C.danger, modifier = Modifier.padding(top = 8.dp))
                Label("⚖ 균등 편차 최대 2회 · 이유 2건 기록", 13, C.warning, modifier = Modifier.padding(top = 8.dp))
            }
            CardBox(Modifier.fillMaxWidth()) {
                Label("미충원 칸", 13, C.secondary, true)
                listOf("11/4 (수) · Night · 1명 부족", "11/7 (토) · Day · 1명 부족", "11/21 (토) · Day · 2명 부족").forEach {
                    Label(it, 13, C.danger, modifier = Modifier.padding(top = 10.dp))
                }
            }
            CardBox(Modifier.fillMaxWidth()) {
                Label("확정하면", 13, C.secondary, true)
                Label("7병동 간호사의 달력에 바로 적용돼요", 13, C.text, modifier = Modifier.padding(top = 8.dp))
                Label("현재 공개 중인 v${roster.publishedVersion}은 기록으로 보관", 12, C.secondary)
            }
            Row(Modifier.fillMaxWidth().border(1.dp, C.brand, RoundedCornerShape(12.dp)).clickable { acknowledged = !acknowledged }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(acknowledged, { acknowledged = it })
                Label("부족 인원 4명(3칸)을 확인했고, 이 상태로 확정합니다", 13, C.text, true)
            }
        }
        Box(Modifier.fillMaxWidth().background(C.surface).padding(16.dp)) {
            AppButton("11월 근무표 확정 · 공개", onPublish, Modifier.fillMaxWidth(), enabled = acknowledged)
        }
    }
}

@Composable
private fun NurseRosterScreen(
    roster: DemoRoster, ward: String, month: YearMonth, wholeWard: Boolean,
    onToggle: (Boolean) -> Unit, onMonthChange: (Int) -> Unit, onWard: () -> Unit
) {
    var selectedDay by rememberSaveable(month.toString()) { mutableIntStateOf(if (month == roster.sampleMonth) 4 else 1) }
    val hasPublishedRoster = month == roster.sampleMonth
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Label("$ward  ▾", 12, C.secondary, modifier = Modifier.clickable(onClick = onWard))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("‹", 24, C.text, true, Modifier.clickable { onMonthChange(-1) })
            Spacer(Modifier.width(8.dp))
            Label("${month.year}년 ${month.monthValue}월", 20, C.text, true)
            Spacer(Modifier.width(8.dp))
            Label("›", 24, C.text, true, Modifier.clickable { onMonthChange(1) })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().background(C.subtle, RoundedCornerShape(12.dp)).padding(4.dp)) {
            TabChoice("내 근무", !wholeWard, Modifier.weight(1f)) { onToggle(false) }
            TabChoice("병동 전체", wholeWard, Modifier.weight(1f)) { onToggle(true) }
        }
        Spacer(Modifier.height(12.dp))
        if (!hasPublishedRoster) {
            CardBox(Modifier.fillMaxWidth()) {
                Label("${month.monthValue}월에 공개된 근무표가 없어요", 16, C.text, true)
                Spacer(Modifier.height(5.dp))
                Label("관리자가 근무표를 확정하면 이곳에서 볼 수 있어요.", 13, C.secondary)
            }
        } else if (!wholeWard) {
            StatusBanner("${month.monthValue}월 근무표 · ${roster.publishedAt.monthValue}월 ${roster.publishedAt.dayOfMonth}일 공개 (v${roster.publishedVersion})", true)
            Spacer(Modifier.height(12.dp))
            val next = roster.published.entries.asSequence()
                .filter { (key, shift) -> key.first == "haneul" && YearMonth.from(key.second) == month &&
                    key.second >= LocalDate.now() && shift != Shift.OFF }
                .minByOrNull { it.key.second }
            if (next != null) {
                Column(Modifier.fillMaxWidth().background(C.brand, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Label("다음 근무", 12, Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val date = next.key.second
                        val shift = next.value
                        Column(Modifier.weight(1f)) {
                            Label("${date.monthValue}월 ${date.dayOfMonth}일 (${koreanWeekday(date)}) · ${shift.label}", 18, Color.White, true)
                            Label(shift.hours, 13, Color.White)
                        }
                        ShiftChip(shift.short, 28)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            OwnCalendar(roster, month)
        } else {
            val firstMonday = month.atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
            val weekDates = (0..6).map { firstMonday.plusDays(it.toLong()) }
            val selectedDate = month.atDay(selectedDay.coerceIn(1, month.lengthOfMonth()))
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                weekDates.forEach { date ->
                    Column(Modifier.weight(1f).background(if (date == selectedDate) C.brand else C.surface, RoundedCornerShape(12.dp))
                        .clickable { selectedDay = date.dayOfMonth }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Label(koreanWeekday(date), 11, if (date == selectedDate) Color.White else C.secondary)
                        Label("${date.dayOfMonth}", 16, if (date == selectedDate) Color.White else C.text, true)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Label("${selectedDate.monthValue}월 ${selectedDate.dayOfMonth}일 (${koreanWeekday(selectedDate)})", 16, C.text, true)
            Spacer(Modifier.height(10.dp))
            listOf(Shift.DAY, Shift.EVENING, Shift.NIGHT).forEach { shift ->
                val assignedNames = roster.nurses.filter { roster.published[it.id to selectedDate] == shift }
                    .map(Nurse::name)
                val compactHours = when (shift) {
                    Shift.DAY -> "07–15"
                    Shift.EVENING -> "15–23"
                    Shift.NIGHT -> "23–07"
                    Shift.OFF -> ""
                }
                CardBox(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShiftChip(shift.short, 28); Spacer(Modifier.width(8.dp)); Label("${shift.label} $compactHours", 14, C.text, true)
                        Spacer(Modifier.weight(1f)); Label("${assignedNames.size}명", 12, C.secondary)
                    }
                    if (assignedNames.isEmpty()) {
                        Label("배치된 근무자 없음", 13, C.secondary, modifier = Modifier.padding(top = 12.dp))
                    } else {
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            assignedNames.forEach { name ->
                                Box(Modifier.background(C.subtle, RoundedCornerShape(99.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)) {
                                    Label(name, 11, C.secondary)
                                }
                            }
                        }
                    }
                }
            }
            val publicAbsences = roster.publishedAbsence.entries.filter { it.key.second == selectedDate }
            if (publicAbsences.isNotEmpty()) {
                CardBox(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShiftChip("휴", 28); Spacer(Modifier.width(8.dp)); Label("휴가 · 반차", 14, C.text, true)
                        Spacer(Modifier.weight(1f)); Label("🔒 사유 비공개", 12, C.secondary)
                    }
                    publicAbsences.forEach { entry ->
                        val name = roster.nurses.firstOrNull { it.id == entry.key.first }?.name ?: return@forEach
                        Label("$name · ${entry.value}", 13, C.secondary, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChoice(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.background(if (selected) C.surface else C.subtle, RoundedCornerShape(9.dp)).clickable(onClick = onClick).padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
        Label(if (selected) "✓ $text" else text, 13, if (selected) C.brandDeep else C.secondary, selected)
    }
}

@Composable
private fun OwnCalendar(roster: DemoRoster, month: YearMonth) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value % 7
    val cellCount = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    val cells = (0 until cellCount).map { index -> index - offset + 1 }
        .map { day -> if (day in 1..month.lengthOfMonth()) month.atDay(day) else null }
    Column(Modifier.fillMaxWidth().background(C.surface, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Row { listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { index, label ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Label(label, 11, if (index == 0) C.danger else if (index == 6) C.info else C.secondary) }
        } }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().height(51.dp)) {
                week.forEach { date ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (date != null) {
                            Label("${date.dayOfMonth}", 11, if (date.dayOfWeek.value == 7) C.danger else if (date.dayOfWeek.value == 6) C.info else C.text)
                            Spacer(Modifier.height(4.dp))
                            val code = roster.visibleShift("haneul", date, true)
                            if (code.isNotBlank()) ShiftChip(code)
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = C.border)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            val codes = (1..month.lengthOfMonth()).map { roster.visibleShift("haneul", month.atDay(it), true) }
            listOf("D", "E", "N", "O", "휴").map { it to codes.count { code -> code == it } }.forEach { (code, count) ->
                Row(verticalAlignment = Alignment.CenterVertically) { ShiftChip(code); Spacer(Modifier.width(3.dp)); Label("$count", 12, C.text, true) }
            }
        }
    }
}

private fun koreanWeekday(date: LocalDate): String = when (date.dayOfWeek.value) {
    1 -> "월"; 2 -> "화"; 3 -> "수"; 4 -> "목"; 5 -> "금"; 6 -> "토"; else -> "일"
}
