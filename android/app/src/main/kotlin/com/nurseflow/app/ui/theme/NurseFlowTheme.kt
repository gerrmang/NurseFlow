package com.nurseflow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object NurseFlowColors {
    val canvas = Color(0xFFF4F6F8)
    val surface = Color(0xFFFFFFFF)
    val subtle = Color(0xFFEEF2F5)
    val border = Color(0xFFE3E8EE)
    val borderStrong = Color(0xFFC5CED8)
    val text = Color(0xFF18212B)
    val secondary = Color(0xFF5B6775)
    val muted = Color(0xFF6B7684)
    val brand = Color(0xFF0F8B7E)
    val brandDeep = Color(0xFF0B5F57)
    val brandSoft = Color(0xFFE0F3F0)
    val danger = Color(0xFFD1343A)
    val dangerSoft = Color(0xFFFDECEC)
    val warning = Color(0xFF8F5800)
    val warningSoft = Color(0xFFFFF4DB)
    val info = Color(0xFF2456B8)
    val infoSoft = Color(0xFFE7EEFB)
    val dayBg = Color(0xFFFFF1CC)
    val dayFg = Color(0xFF8A5100)
    val eveningBg = Color(0xFFDCEBFF)
    val eveningFg = Color(0xFF1F5FD1)
    val nightBg = Color(0xFFE9E3FF)
    val nightFg = Color(0xFF5B3CC4)
    val offBg = Color(0xFFEEF1F4)
    val offFg = Color(0xFF5B6775)
    val leaveBg = Color(0xFFF7E6EE)
    val leaveFg = Color(0xFF93285F)
}

@Composable
fun NurseFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = NurseFlowColors.brand,
            onPrimary = Color.White,
            background = NurseFlowColors.canvas,
            surface = NurseFlowColors.surface,
            onSurface = NurseFlowColors.text,
            outline = NurseFlowColors.border
        ),
        typography = Typography(),
        content = content
    )
}
