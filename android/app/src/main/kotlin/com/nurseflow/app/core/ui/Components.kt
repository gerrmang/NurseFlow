package com.nurseflow.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nurseflow.app.ui.theme.NurseFlowColors as C

@Composable
fun NfText(text: String, size: Int = 14, color: Color = C.text, bold: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier, color = color, fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, lineHeight = (size * 1.45f).sp,
    )
}

@Composable
fun NfPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick, modifier.fillMaxWidth().height(52.dp), enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.brand, disabledContainerColor = C.subtle),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else NfText(text, 16, if (enabled) Color.White else C.muted, true)
    }
}

@Composable
fun NfOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick, modifier.fillMaxWidth().height(52.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, C.borderStrong),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = C.surface, contentColor = C.brandDeep),
    ) { NfText(text, 16, C.brandDeep, true) }
}

/** Kakao login button colours follow Kakao's design guide (yellow #FEE500, 85% black label). */
@Composable
fun KakaoButton(onClick: () -> Unit, loading: Boolean, modifier: Modifier = Modifier) {
    Button(
        onClick, modifier.fillMaxWidth().height(52.dp), enabled = !loading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE500), disabledContainerColor = Color(0xFFFEE500)),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xD9000000), strokeWidth = 2.dp)
        else NfText("카카오 로그인", 16, Color(0xD9000000), true)
    }
}

@Composable
fun NfTextLink(text: String, color: Color = C.brandDeep, onClick: () -> Unit) {
    TextButton(onClick) { NfText(text, 14, color, true) }
}

@Composable
fun NfCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .background(C.surface, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

/** Small rounded label, e.g. role or status. */
@Composable
fun NfBadge(text: String, bg: Color, fg: Color) {
    Box(Modifier.background(bg, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        NfText(text, 12, fg, true)
    }
}

@Composable
fun NfError(message: String?) {
    if (message == null) return
    Row(
        Modifier.fillMaxWidth().background(C.dangerSoft, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { NfText(message, 13, C.danger) }
}

@Composable
fun NfInfo(message: String) {
    Row(Modifier.fillMaxWidth().background(C.infoSoft, RoundedCornerShape(12.dp)).padding(12.dp)) {
        NfText(message, 13, C.info)
    }
}

/** Screen with a back header and scrollable content. */
@Composable
fun NfScreen(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(C.canvas)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextButton(onBack) { NfText("‹ 뒤로", 15, C.secondary, true) } else Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                NfText(title, 18, C.text, true, Modifier.semantics { heading() })
                if (subtitle != null) NfText(subtitle, 12, C.secondary)
            }
            action?.invoke()
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun NfLoading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = C.brand)
    }
}
