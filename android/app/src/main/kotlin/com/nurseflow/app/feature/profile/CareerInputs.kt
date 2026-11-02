package com.nurseflow.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nurseflow.app.core.ui.NfText
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import com.nurseflow.app.ui.theme.NurseFlowColors as C

/** 입사 년월: year and month dropdowns, never later than this month. */
@Composable
fun CareerStartPicker(value: YearMonth?, onChange: (YearMonth) -> Unit, today: YearMonth = YearMonth.now()) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NfText("입사 년월", 13, C.secondary, true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val years = (today.year downTo today.year - 45).map { it }
            Dropdown(
                label = value?.year?.let { "${it}년" } ?: "년도",
                options = years.map { "${it}년" },
                modifier = Modifier.weight(1f),
            ) { index ->
                val year = years[index]
                val month = (value?.monthValue ?: 1).coerceAtMost(if (year == today.year) today.monthValue else 12)
                onChange(YearMonth.of(year, month))
            }
            val maxMonth = if (value?.year == today.year) today.monthValue else 12
            Dropdown(
                label = value?.monthValue?.let { "${it}월" } ?: "월",
                options = (1..maxMonth).map { "${it}월" },
                enabled = value != null,
                modifier = Modifier.weight(1f),
            ) { index -> value?.let { onChange(YearMonth.of(it.year, index + 1)) } }
        }
        if (value != null) {
            val months = ChronoUnit.MONTHS.between(value, today).toInt().coerceAtLeast(0)
            NfText("경력 ${months / 12}년 ${months % 12}개월", 12, C.muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RankPicker(ranks: List<String>, selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NfText("직급", 13, C.secondary, true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ranks.forEach { rank ->
                FilterChip(
                    selected = rank == selected,
                    onClick = { onSelect(rank) },
                    label = { NfText(rank, 14, if (rank == selected) C.brandDeep else C.text, rank == selected) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.brandSoft),
                )
            }
        }
    }
}

@Composable
private fun Dropdown(
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(48.dp)
                .background(C.surface, RoundedCornerShape(12.dp))
                .border(1.dp, C.borderStrong, RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NfText(label, 15, if (enabled) C.text else C.muted, modifier = Modifier.weight(1f))
            NfText("▾", 14, C.muted)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(text = { NfText(option, 15) }, onClick = { open = false; onPick(index) })
            }
        }
    }
}

fun profileStatusLabel(status: String): String = when (status) {
    "CONFIRMED" -> "관리자 확인 완료"
    "PENDING" -> "관리자 확인 대기"
    else -> "미입력"
}
