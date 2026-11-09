package com.nurseflow.app.feature.roster

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.YearMonth

private fun monthLabel(month: YearMonth) = "${month.year}년 ${month.monthValue}월"
private val weekdays = listOf("월", "화", "수", "목", "금", "토", "일")
private fun dateLabel(date: LocalDate) = "${date.monthValue}월 ${date.dayOfMonth}일 (${weekdays[date.dayOfWeek.value - 1]})"

@Composable
fun PublishedRosterScreen(wardName: String, entry: Int, viewModel: PublishedRosterViewModel, onBack: () -> Unit) {
    LaunchedEffect(entry) { viewModel.enter(entry) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 병동 목록") }
        }
        Text(wardName, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.changeMonth(-1) }) { Text("‹ 이전 달") }
            Text(monthLabel(state.month), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { viewModel.changeMonth(1) }) { Text("다음 달 ›") }
        }
        if (viewModel.canViewWard) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.mode == RosterMode.MINE, { viewModel.selectMode(RosterMode.MINE) }, { Text("내 근무") })
                FilterChip(state.mode == RosterMode.WARD, { viewModel.selectMode(RosterMode.WARD) }, { Text("병동 전체") })
            }
        }
        when (val content = state.content) {
            RosterContent.Loading -> CircularProgressIndicator()
            RosterContent.NotPublished -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("아직 확정된 근무표가 없습니다", style = MaterialTheme.typography.titleMedium)
                Text(monthLabel(state.month))
            }
            RosterContent.Forbidden -> Text("이 병동의 근무표를 볼 권한이 없어요.")
            is RosterContent.Retryable -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(content.message)
                Button(onClick = viewModel::retry) { Text("다시 시도") }
            }
            is RosterContent.Failed -> Text(content.message)
            is RosterContent.Mine ->
                if (content.days.isEmpty()) Text("이번 달 배정된 근무가 없습니다")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(content.days, key = { "${it.date}-${it.timeLabel}" }) { day ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(dateLabel(day.date), style = MaterialTheme.typography.titleSmall)
                                Text("${day.shiftCode} · ${day.timeLabel}")
                            }
                        }
                    }
                }
            is RosterContent.Ward ->
                if (content.days.isEmpty()) Text("이번 달 근무가 없습니다")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(content.days, key = { it.date.toString() }) { day ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(dateLabel(day.date), style = MaterialTheme.typography.titleSmall)
                                day.groups.forEach { g ->
                                    val names = if (g.names.isEmpty()) "배정 없음" else g.names.joinToString(", ")
                                    val gap = if (g.unfilled > 0) " (미배정 ${g.unfilled})" else ""
                                    Text("${g.timeLabel} · $names$gap")
                                }
                            }
                        }
                    }
                }
        }
    }
}
