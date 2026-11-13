package com.nurseflow.app.feature.ward.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseflow.app.core.network.HospitalCandidateDto
import com.nurseflow.app.core.network.MembershipDto

/**
 * Search an official hospital, name the ward, then see the setup guide. The server decides what
 * is valid; this screen only reflects its answers. There is no way to type a hospital by hand.
 *
 * [onClose] and [onShowWardList] leave and reload memberships (including when
 * the ward already exists), [onOpenRoster]/[onOpenInvites] open the created ward.
 */
@Composable
fun WardCreateScreen(
    viewModel: WardCreateViewModel,
    onClose: () -> Unit,
    onShowWardList: () -> Unit,
    onOpenRoster: (MembershipDto) -> Unit,
    onOpenInvites: (MembershipDto) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val created = state.created
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("병동 만들기", style = MaterialTheme.typography.headlineSmall)
            if (created == null) TextButton(onClick = onClose, enabled = !state.creating) { Text("닫기") }
        }
        when {
            created != null -> WardSetupGuide(created, onClose, onOpenRoster, onOpenInvites, Modifier.weight(1f))
            state.step == CreateStep.SEARCH -> SearchStep(state, viewModel, Modifier.weight(1f))
            else -> NameStep(state, viewModel, onShowWardList, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SearchStep(state: WardCreateState, viewModel: WardCreateViewModel, modifier: Modifier = Modifier) {
    // One scroll container for the whole step (field, button, notice and at most one page of
    // results), so a short or landscape screen or the keyboard never hides the controls.
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("공식 병원 목록에서 병원을 찾아 선택해 주세요.")
    // The create call was refused for the hospital that was selected: say so where the user lands.
    if (state.createError == WardCreateFailure.HOSPITAL_NOT_FOUND) {
        Text(
            WardCreateFailure.HOSPITAL_NOT_FOUND.searchMessage,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(HOSPITAL_NOT_FOUND_NOTICE_TAG),
        )
    }
    OutlinedTextField(
        value = state.queryInput,
        onValueChange = viewModel::setQuery,
        label = { Text("병원 이름") },
        singleLine = true,
        isError = state.queryError != null,
        supportingText = { state.queryError?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = viewModel::search, enabled = state.search !is SearchState.Loading) { Text("검색") }
    when (val search = state.search) {
        SearchState.Idle -> Unit
        is SearchState.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text("'${search.query}' 검색 중이에요. 시간이 걸릴 수 있어요.")
        }
        is SearchState.Failed -> Text(search.failure.searchMessage, color = MaterialTheme.colorScheme.error)
        is SearchState.Empty -> {
            Text("검색 결과가 없어요. 공식 목록에 등록된 이름으로 다시 검색해 주세요.")
            Attribution(search.attribution)
        }
        is SearchState.Results -> {
            if (search.truncated) Text("결과가 많아 일부만 보여요. 검색어를 더 구체적으로 입력해 주세요.")
            search.items.forEach { hospital ->
                val refused = state.isInvalid(hospital)
                Card(onClick = { viewModel.select(hospital) }, enabled = !refused, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HospitalSummary(hospital)
                        if (refused) Text("서버에서 찾을 수 없다고 한 병원이에요. 선택할 수 없어요.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Attribution(search.attribution)
        }
    }
    }
}

const val HOSPITAL_NOT_FOUND_NOTICE_TAG = "hospitalNotFoundNotice"
const val CREATE_BUTTON_TAG = "createWardButton"

@Composable
private fun HospitalSummary(hospital: HospitalCandidateDto, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(hospital.name, style = MaterialTheme.typography.titleMedium)
        Text(hospital.address)
        hospital.category?.takeIf { it.isNotBlank() }?.let { Text(it) }
    }
}

@Composable
private fun Attribution(text: String?) {
    text?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun NameStep(state: WardCreateState, viewModel: WardCreateViewModel, onShowWardList: () -> Unit, modifier: Modifier = Modifier) {
    // Scrolls as a whole: a long lost-response notice, a big font or the keyboard must not push
    // the create / retry / "check ward list" buttons out of reach. Locks while creating stay.
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    val hospital = state.selected
    if (hospital != null) Card(Modifier.fillMaxWidth()) { HospitalSummary(hospital, Modifier.padding(16.dp)) }
    TextButton(onClick = viewModel::backToSearch, enabled = !state.creating) { Text("병원 다시 고르기") }
    OutlinedTextField(
        value = state.wardName,
        onValueChange = viewModel::setWardName,
        label = { Text("병동 이름") },
        singleLine = true,
        enabled = !state.creating,
        modifier = Modifier.fillMaxWidth(),
    )
    val error = state.createError
    if (state.lostResponse) {
        Text(
            if (state.inputChangedSinceLoss) {
                "이전 요청의 결과를 확인하지 못했어요. 그 요청으로 병동이 이미 만들어졌을 수 있어요. 내용을 바꿨으니 새 요청으로 보내요. 중복이 걱정되면 병동 목록을 먼저 확인해 주세요."
            } else {
                "결과를 확인하지 못했어요. 같은 내용으로 다시 시도하면 중복으로 만들어지지 않아요."
            },
            color = MaterialTheme.colorScheme.error,
        )
        if (state.inputChangedSinceLoss) TextButton(onClick = onShowWardList) { Text("병동 목록 확인") }
    }
    if (error != null) {
        Text(error.createMessage, color = MaterialTheme.colorScheme.error)
        if (error == WardCreateFailure.ALREADY_CREATED) TextButton(onClick = onShowWardList) { Text("병동 목록 보기") }
    }
    Button(
        onClick = viewModel::create,
        modifier = Modifier.testTag(CREATE_BUTTON_TAG),
        enabled = !state.creating && state.wardName.isNotBlank() && error != WardCreateFailure.LIMIT_REACHED,
    ) {
        Text(
            when {
                state.creating -> "만드는 중…"
                state.lostResponse && !state.inputChangedSinceLoss -> "다시 시도"
                else -> "병동 만들기"
            },
        )
    }
    }
}

/** Setup skeleton: shows only which steps the app offers; it does not infer server-side progress. */
@Composable
private fun WardSetupGuide(
    created: MembershipDto,
    onClose: () -> Unit,
    onOpenRoster: (MembershipDto) -> Unit,
    onOpenInvites: (MembershipDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("병동을 만들었어요", style = MaterialTheme.typography.titleMedium)
                    Text(created.wardName)
                    created.hospitalName?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    Text("내가 이 병동의 관리자예요.")
                }
            }
        }
        item { Text("설정 안내", style = MaterialTheme.typography.titleSmall) }
        item {
            GuideItem("구성원 초대", "지금 가능해요") {
                OutlinedButton(onClick = { onOpenInvites(created) }) { Text("초대 관리 열기") }
            }
        }
        item { GuideItem("팀 구성", "앱에서 곧 설정할 수 있어요") }
        item { GuideItem("간호사 경력·편성 단계", "앱에서 곧 설정할 수 있어요") }
        item { GuideItem("근무 유형·필요 인원", "앱에서 곧 설정할 수 있어요") }
        item { GuideItem("휴가·반차 입력", "앱에서 곧 설정할 수 있어요") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onClose) { Text("병동 목록으로") }
                OutlinedButton(onClick = { onOpenRoster(created) }) { Text("이 병동 근무표 보기") }
            }
        }
    }
}

@Composable
private fun GuideItem(title: String, status: String, action: (@Composable () -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(status)
            action?.invoke()
        }
    }
}
