package com.nurseflow.app.feature.organization.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.R
import com.nurseflow.app.core.network.ApiError
import com.nurseflow.app.core.network.outcomeUnknown
import com.nurseflow.app.feature.organization.domain.HospitalWard
import com.nurseflow.app.feature.organization.domain.MyWard
import com.nurseflow.app.ui.AppButton
import com.nurseflow.app.ui.Asset
import com.nurseflow.app.ui.CardBox
import com.nurseflow.app.ui.Label
import com.nurseflow.app.ui.theme.NurseFlowColors as C

/** 서버 기반 병원·병동 선택. 화면은 상태를 그리기만 하고 판단은 [WardSelectionViewModel]과 서버가 한다. */
@Composable
fun WardSelectionRoute(viewModel: WardSelectionViewModel, onSignOut: () -> Unit, onOpenDemo: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val chosen = state.chosenWard
    if (chosen != null) {
        ChosenWardScreen(chosen, onChangeWard = viewModel::changeWard)
    } else {
        WardSelectionScreen(state, viewModel, onSignOut, onOpenDemo)
    }
}

@Composable
private fun WardSelectionScreen(
    state: WardSelectionUiState,
    actions: WardSelectionViewModel,
    onSignOut: () -> Unit,
    onOpenDemo: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Label("서버 연결", 12, C.secondary)
            Label("병동 선택", 18, C.text, true)
            Spacer(Modifier.height(16.dp))
        }
        if (state.sessionExpired) {
            item { MessageCard(ApiError.SessionExpired.userMessage(), "다시 로그인", onSignOut, danger = true) }
            return@LazyColumn
        }
        when (val load = state.load) {
            LoadState.Loading -> item { LoadingRow("병동 목록을 불러오는 중…") }
            is LoadState.Failed -> item { MessageCard(load.error.userMessage(), "다시 시도", actions::refresh, danger = true) }
            LoadState.Ready -> {
                state.notice?.let { notice -> item { Notice(notice, error = state.noticeIsError); Spacer(Modifier.height(12.dp)) } }
                state.selectedHospital?.let { hospital ->
                    item {
                        HospitalCard(hospital.name, state.hospitals.size > 1) {
                            val index = state.hospitals.indexOf(hospital)
                            actions.selectHospital(state.hospitals[(index + 1) % state.hospitals.size].hospitalId)
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
                item {
                    Label("내 병동 ${state.myWards.size}", 13, C.secondary, true)
                    Spacer(Modifier.height(12.dp))
                }
                if (state.myWards.isEmpty()) {
                    item {
                        CardBox(Modifier.fillMaxWidth()) {
                            Label("소속된 병동이 없습니다", 15, C.text, true)
                            Spacer(Modifier.height(4.dp))
                            Label(
                                if (state.isHospitalAdmin) "아래에서 병동을 만들면 병동 관리자로 등록되어 여기에 표시됩니다."
                                else "병동 관리자의 초대를 받으면 병동이 표시됩니다. 초대 기능은 준비 중입니다.",
                                13, C.secondary,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                items(state.myWards, key = { it.wardId }) { ward ->
                    MyWardCard(ward, highlighted = ward.wardId == state.highlightedWardId) { actions.choose(ward.wardId) }
                }
                if (state.isHospitalAdmin) {
                    item { HospitalAdminSection(state, actions) }
                }
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
            Label("가상 시연 데이터로 둘러보기 ›", 13, C.brandDeep, true,
                Modifier.clickable(role = Role.Button, onClick = onOpenDemo).padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun HospitalCard(name: String, switchable: Boolean, onSwitch: () -> Unit) {
    CardBox(Modifier.fillMaxWidth().then(if (switchable) Modifier.clickable(role = Role.Button, onClick = onSwitch) else Modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Asset(R.drawable.figma_8_313_imgframe, 20)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Label(if (switchable) "병원 · 눌러서 다른 병원" else "병원", 11, C.secondary)
                Label(name, 15, C.text, true)
            }
            if (switchable) Asset(R.drawable.figma_8_313_imgframe1, 20, "다른 병원 선택")
        }
    }
}

@Composable
private fun MyWardCard(ward: MyWard, highlighted: Boolean, onChoose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp)
            .border(if (highlighted) 1.5.dp else 0.dp, if (highlighted) C.brand else C.surface, RoundedCornerShape(16.dp))
            .background(C.surface, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onChoose).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(ward.wardName, 16, C.text, true, Modifier.weight(1f))
            Asset(R.drawable.figma_8_313_imgframe4, 20)
        }
        Spacer(Modifier.height(4.dp))
        RolePill(ward.isWardAdmin)
        Spacer(Modifier.height(5.dp))
        Label(listOfNotNull(ward.hospitalName ?: "병원 미지정", ward.timeZone).joinToString(" · "), 12, C.secondary)
    }
}

@Composable
private fun RolePill(admin: Boolean) {
    Row(Modifier.background(if (admin) C.brandSoft else C.infoSoft, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Asset(if (admin) R.drawable.figma_8_313_imgframe3 else R.drawable.figma_8_313_imgframe5, 14)
        Spacer(Modifier.width(4.dp))
        Label(if (admin) "관리자 · 편집 가능" else "조회 전용", 12, if (admin) C.brandDeep else C.info, true)
    }
}

@Composable
private fun HospitalAdminSection(state: WardSelectionUiState, actions: WardSelectionViewModel) {
    val hospital = state.selectedHospital ?: return
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label("${hospital.name} 병동 ${state.hospitalWards.size}", 13, C.secondary, true, Modifier.weight(1f))
        if (state.form == null) {
            Label("+ 병동 추가", 13, C.brandDeep, true, Modifier.clickable(role = Role.Button, onClick = actions::openCreateForm).padding(8.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    state.form?.let { form ->
        CreateWardCard(form, hospital.timeZone, actions)
        Spacer(Modifier.height(12.dp))
    }
    when {
        state.hospitalWardsLoading -> LoadingRow("병원 병동을 불러오는 중…")
        state.hospitalWardsError != null -> MessageCard(state.hospitalWardsError.userMessage(), "다시 시도", actions::retryHospitalWards, danger = true)
        state.hospitalWards.isEmpty() -> CardBox(Modifier.fillMaxWidth()) { Label("아직 만든 병동이 없습니다.", 13, C.secondary) }
        else -> CardBox(Modifier.fillMaxWidth()) {
            state.hospitalWards.forEachIndexed { index, ward ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                HospitalWardRow(ward, member = state.isMember(ward.wardId), justCreated = ward.wardId == state.justCreatedWardId)
            }
        }
    }
}

@Composable
private fun HospitalWardRow(ward: HospitalWard, member: Boolean, justCreated: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Label(ward.name, 14, C.text, true)
            Label(ward.timeZone, 12, C.secondary)
        }
        val (text, bg, fg) = when {
            justCreated -> Triple("방금 생성", C.brandSoft, C.brandDeep)
            member -> Triple("내 병동", C.brandSoft, C.brandDeep)
            else -> Triple("소속 없음", C.subtle, C.secondary)
        }
        Text(text, Modifier.background(bg, RoundedCornerShape(99.dp)).padding(horizontal = 9.dp, vertical = 4.dp), color = fg,
            fontSize = 11.sp)
    }
}

@Composable
private fun CreateWardCard(form: CreateWardForm, hospitalZone: String, actions: WardSelectionViewModel) {
    CardBox(Modifier.fillMaxWidth().border(1.5.dp, C.brand, RoundedCornerShape(16.dp))) {
        Label("새 병동", 15, C.text, true)
        Label("만든 사람이 첫 병동 관리자가 됩니다.", 12, C.secondary)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = form.name, onValueChange = actions::editName, enabled = !form.submitting, singleLine = true,
            label = { Text("병동 이름") }, isError = form.nameError != null,
            supportingText = form.nameError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.timeZone, onValueChange = actions::editTimeZone, enabled = !form.submitting, singleLine = true,
            label = { Text("시간대 (선택)") }, placeholder = { Text("$hospitalZone (병원 기본값)") },
            isError = form.timeZoneError != null, supportingText = form.timeZoneError?.let { { Text(it) } },
            // IANA 이름(예: Asia/Seoul)은 자동 수정·대문자 변환이 값을 망가뜨린다.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { actions.submitCreate() }),
            colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        form.failure?.let { error ->
            Spacer(Modifier.height(6.dp))
            Label(
                error.userMessage() + if (error.outcomeUnknown) " 같은 내용으로 다시 시도하면 중복 없이 처리됩니다." else "",
                12, C.danger,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("취소", actions::closeCreateForm, Modifier.weight(1f), outlined = true, enabled = !form.submitting)
            AppButton(
                when {
                    form.submitting -> "만드는 중…"
                    form.failure?.outcomeUnknown == true -> "다시 시도"
                    else -> "만들기"
                },
                actions::submitCreate, Modifier.weight(1f), enabled = !form.submitting,
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.brand, unfocusedBorderColor = C.border, focusedLabelColor = C.brandDeep, cursorColor = C.brand,
)

@Composable
private fun ChosenWardScreen(ward: MyWard, onChangeWard: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Label(ward.hospitalName ?: "병원 미지정", 12, C.secondary, modifier = Modifier.clickable(onClick = onChangeWard))
        Label(ward.wardName, 18, C.text, true)
        Spacer(Modifier.height(12.dp))
        RolePill(ward.isWardAdmin)
        Spacer(Modifier.height(16.dp))
        CardBox(Modifier.fillMaxWidth()) {
            Label("근무표 서버 연동 준비 중", 15, C.text, true)
            Spacer(Modifier.height(4.dp))
            Label("이 병동의 근무표 화면은 후속 기능에서 서버 API와 연결합니다. 가상 시연 데이터는 서버 병동에 표시하지 않습니다.", 13, C.secondary)
        }
        Spacer(Modifier.height(16.dp))
        AppButton("병동 다시 선택", onChangeWard, Modifier.fillMaxWidth(), outlined = true)
    }
}

@Composable
internal fun LoadingRow(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), color = C.brand, strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Label(text, 13, C.secondary)
    }
}

@Composable
internal fun MessageCard(message: String, action: String, onAction: () -> Unit, danger: Boolean = false) {
    CardBox(Modifier.fillMaxWidth()) {
        Label(message, 14, if (danger) C.danger else C.text)
        Spacer(Modifier.height(12.dp))
        AppButton(action, onAction, Modifier.fillMaxWidth(), outlined = true)
    }
}

@Composable
private fun Notice(text: String, error: Boolean) {
    Row(Modifier.fillMaxWidth().background(if (error) C.dangerSoft else C.brandSoft, RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // 시연 화면의 "⚠ 미충원"과 같은 경고 표기를 쓴다.
        if (error) Label("⚠", 15, C.danger, true) else Asset(R.drawable.figma_11_370_imgframe, 18)
        Spacer(Modifier.width(8.dp))
        Label(text, 13, if (error) C.danger else C.brandDeep)
    }
}
