package com.nurseflow.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nurseflow.app.ui.theme.NurseFlowColors as C

/** Figma 프로토타입에서 쓰던 공통 표시 요소. 시연 화면과 서버 연동 화면이 같은 모양을 쓴다. */

@Composable
internal fun Label(text: String, size: Int = 14, color: Color = C.text, bold: Boolean = false, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, lineHeight = (size * 1.45f).sp)
}

@Composable
internal fun Asset(id: Int, size: Int, description: String? = null) {
    Image(painterResource(id), contentDescription = description, modifier = Modifier.size(size.dp))
}

@Composable
internal fun AppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, outlined: Boolean = false, enabled: Boolean = true) {
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
internal fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.background(C.surface, RoundedCornerShape(16.dp)).padding(16.dp)) { content() }
}
