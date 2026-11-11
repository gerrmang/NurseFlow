package com.nurseflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nurseflow.app.AppContainer
import com.nurseflow.app.BuildConfig
import com.nurseflow.app.core.session.SessionState
import com.nurseflow.app.feature.auth.LoginScreen
import com.nurseflow.app.feature.auth.LoginViewModel
import com.nurseflow.app.feature.invite.JoinInvitationScreen
import com.nurseflow.app.feature.invite.JoinInvitationViewModel
import com.nurseflow.app.feature.invite.PasteInviteDialog
import com.nurseflow.app.feature.invite.admin.InvitationAdminScreen
import com.nurseflow.app.feature.invite.admin.InvitationAdminViewModel
import com.nurseflow.app.feature.roster.PublishedRosterScreen
import com.nurseflow.app.feature.roster.PublishedRosterViewModel
import com.nurseflow.app.feature.ward.WardListScreen
import com.nurseflow.app.feature.ward.WardsUiState
import com.nurseflow.app.feature.ward.WardsViewModel
import com.nurseflow.app.feature.ward.create.IntentPhase
import com.nurseflow.app.feature.ward.create.WardCreateScreen
import com.nurseflow.app.feature.ward.create.WardCreateViewModel
import com.nurseflow.app.feature.ward.create.resolveCreateIntent

@Composable
fun NurseFlowRoot(container: AppContainer) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val pendingInvite by container.invites.current.collectAsStateWithLifecycle()
    val invalidInvite by container.invites.invalidLink.collectAsStateWithLifecycle()
    val sessionModels: SessionViewModelStore = viewModel()
    val owner = sessionModels.ownerFor(session)
    var demo by remember { mutableStateOf(false) }
    // Selection belongs to one signed-in session; a new session identity starts without one.
    val sessionKey = (session as? SessionState.SignedIn)?.let { "${it.userId}:${it.generation}" }
    var selectedWardId by rememberSaveable(sessionKey) { mutableStateOf<String?>(null) }
    var adminWardId by rememberSaveable(sessionKey) { mutableStateOf<String?>(null) }
    var wardEntry by rememberSaveable(sessionKey) { mutableStateOf(0) }
    var pasting by remember(sessionKey) { mutableStateOf(false) }
    var creatingWard by rememberSaveable(sessionKey) { mutableStateOf(false) }
    // "Log in, then create a ward": set only from the login screen and consumed (or dropped) on the
    // first sign-in, so it is deliberately independent of the session key. See resolveCreateIntent.
    var createAfterLogin by rememberSaveable { mutableStateOf(false) }
    val intentPhase = when (session) {
        SessionState.SignedOut, SessionState.Restoring -> IntentPhase.WAITING
        is SessionState.SignedIn -> IntentPhase.SIGNED_IN
        else -> IntentPhase.ENDED
    }
    LaunchedEffect(intentPhase, createAfterLogin) {
        // A failed logout/restore means the login the intent belonged to did not happen.
        if (resolveCreateIntent(createAfterLogin, intentPhase, invitationPending = false).clear && intentPhase != IntentPhase.SIGNED_IN) {
            createAfterLogin = false
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (demo) {
            BackHandler { demo = false }
            Text("가상 데이터 시연 · 서버와 공유되지 않음", Modifier.padding(8.dp))
            TextButton(onClick = { demo = false }) { Text("시연 나가기") }
            Box(Modifier.weight(1f)) { NurseFlowApp() }
        } else {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                Box(Modifier.weight(1f)) {
                    when (val current = session) {
                        SessionState.Restoring -> CircularProgressIndicator()
                        SessionState.SignedOut -> LoginScreen(
                            viewModel { LoginViewModel(container.session, container.kakao) },
                            invitationWaiting = pendingInvite != null,
                            onCancelInvitation = { container.invites.clear() },
                            createIntentArmed = createAfterLogin,
                            onCreateWard = { createAfterLogin = true },
                            onCancelCreateIntent = { createAfterLogin = false },
                        )
                        SessionState.LogoutFailed -> Column(Modifier.padding(24.dp)) {
                            Text("저장된 로그인 정보를 지우지 못했어요. 로그아웃을 다시 시도해 주세요.")
                            Button(onClick = container::logout) { Text("로그아웃 재시도") }
                        }
                        is SessionState.RestoreFailed -> Column(Modifier.padding(24.dp)) {
                            Text(current.message)
                            Button(onClick = { container.restoreSession() }) { Text("세션 복원 재시도") }
                            TextButton(onClick = container::logout) { Text("로그아웃하고 다시 로그인") }
                        }
                        is SessionState.SignedIn -> {
                            val wardsModel = viewModel { WardsViewModel(container.api) }
                            val wards by wardsModel.state.collectAsStateWithLifecycle()
                            val loaded = (wards as? WardsUiState.Loaded)?.wards
                            val ward = loaded?.firstOrNull { it.wardId == selectedWardId }
                            val adminWard = loaded?.firstOrNull { it.wardId == adminWardId && it.isAdmin }
                            // Created for the whole session so a newer link replaces, not duplicates, the flow.
                            // One fixed-key model; reset() on entry and exit means flows never pile up in the store.
                            val createModel = viewModel(key = "ward-create") {
                                WardCreateViewModel(
                                    container.wardCreationApi,
                                    onSessionInvalid = { container.logout() },
                                    onCreated = wardsModel::onCreated,
                                )
                            }
                            val createState by createModel.state.collectAsStateWithLifecycle()
                            val enterCreate = { createModel.reset(); creatingWard = true }
                            val leaveCreate = { createModel.reset(); creatingWard = false }
                            LaunchedEffect(createAfterLogin, pendingInvite != null) {
                                val resolution = resolveCreateIntent(createAfterLogin, IntentPhase.SIGNED_IN, pendingInvite != null)
                                if (resolution.clear) createAfterLogin = false
                                if (resolution.open) enterCreate()
                            }
                            val join = viewModel(key = "join") {
                                JoinInvitationViewModel(
                                    container.invitationApi, container.api, container.invites,
                                    onSessionInvalid = { container.logout() },
                                )
                            }
                            when {
                                // An invitation being handled takes precedence over the ward screens.
                                pendingInvite != null -> {
                                    BackHandler { join.close() }
                                    JoinInvitationScreen(join) { wardId ->
                                        wardsModel.reload()
                                        adminWardId = null
                                        selectedWardId = wardId
                                        wardEntry += 1
                                    }
                                }
                                creatingWard -> {
                                    BackHandler { if (!createState.creating) leaveCreate() }
                                    WardCreateScreen(
                                        createModel,
                                        onClose = { leaveCreate() },
                                        onShowWardList = { wardsModel.reload(); leaveCreate() },
                                        onOpenRoster = { leaveCreate(); adminWardId = null; selectedWardId = it.wardId; wardEntry += 1 },
                                        onOpenInvites = { leaveCreate(); adminWardId = it.wardId },
                                    )
                                }
                                adminWard != null -> {
                                    BackHandler { adminWardId = null }
                                    val admin = viewModel(key = "invites-${adminWard.wardId}") {
                                        InvitationAdminViewModel(
                                            container.invitationApi, adminWard.wardId, BuildConfig.INVITE_LINK_BASE,
                                            onSessionInvalid = { container.logout() },
                                        )
                                    }
                                    InvitationAdminScreen(
                                        adminWard.wardName, admin,
                                        onBack = { adminWardId = null },
                                        // The server no longer grants admin access: leave and refresh memberships.
                                        onForbidden = { adminWardId = null; wardsModel.reload() },
                                    )
                                }
                                ward == null -> WardListScreen(
                                    wardsModel,
                                    onSelect = { selectedWardId = it.wardId; wardEntry += 1 },
                                    onManageInvitations = { adminWardId = it.wardId },
                                    onPasteInvitation = { pasting = true },
                                    onCreateWard = enterCreate,
                                    onLogout = container::logout,
                                )
                                else -> {
                                    BackHandler { selectedWardId = null }
                                    val roster = viewModel(key = "roster-${ward.wardId}") {
                                        PublishedRosterViewModel(container.api, ward.wardId, ward.canViewWardCalendar)
                                    }
                                    PublishedRosterScreen(ward.wardName, wardEntry, roster) { selectedWardId = null }
                                }
                            }
                            if (pasting) PasteInviteDialog(onDismiss = { pasting = false }) {
                                pasting = false
                                container.invites.offer(it)
                            }
                        }
                    }
                }
            }
            if (invalidInvite) AlertDialog(
                onDismissRequest = container.invites::dismissInvalidLink,
                title = { Text("초대 링크 오류") },
                text = { Text("올바른 초대 링크가 아니에요. 관리자에게 받은 링크를 다시 열어 주세요.") },
                confirmButton = { TextButton(onClick = container.invites::dismissInvalidLink) { Text("확인") } },
            )
            // Demo data is reachable only outside a signed-in session and never substitutes for server data.
            if (session !is SessionState.SignedIn) {
                TextButton(onClick = { demo = true }) { Text("가상 근무표 시연 보기 (서버 미연동)") }
            }
        }
    }
}
