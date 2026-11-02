package com.nurseflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nurseflow.app.AppContainer
import com.nurseflow.app.core.network.MembershipDto
import com.nurseflow.app.core.session.SessionState
import com.nurseflow.app.feature.auth.LoginScreen
import com.nurseflow.app.feature.auth.LoginViewModel
import com.nurseflow.app.feature.invite.InvitesScreen
import com.nurseflow.app.feature.invite.InvitesViewModel
import com.nurseflow.app.feature.invite.JoinWardScreen
import com.nurseflow.app.feature.invite.JoinWardViewModel
import com.nurseflow.app.feature.profile.MyProfileScreen
import com.nurseflow.app.feature.profile.MyProfileViewModel
import com.nurseflow.app.feature.ward.CreateWardScreen
import com.nurseflow.app.feature.ward.CreateWardViewModel
import com.nurseflow.app.feature.ward.WardListScreen
import com.nurseflow.app.feature.ward.WardsViewModel
import kotlinx.coroutines.launch
import com.nurseflow.app.ui.theme.NurseFlowColors as C

/** Session gate: restoring → login → signed-in navigation. */
@Composable
fun NurseFlowRoot(container: AppContainer) {
    val session by container.session.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (container.session.state.value == SessionState.Restoring) container.session.restore()
    }

    Box(Modifier.fillMaxSize().background(C.canvas).statusBarsPadding()) {
        when (session) {
            SessionState.Restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = C.brand)
            }
            SessionState.SignedOut -> {
                val vm: LoginViewModel = viewModel(factory = viewModelFactory {
                    initializer { LoginViewModel(container.session, container.kakaoLogin) }
                })
                LoginScreen(vm)
            }
            is SessionState.SignedIn -> SignedInNavigation(container, (session as SessionState.SignedIn).userId)
        }
    }
}

private object Routes {
    const val WARDS = "wards"
    const val JOIN = "join"
    const val CREATE_WARD = "create-ward"
    const val INVITES = "invites"
    const val PROFILE = "profile"
    const val ROSTER = "roster"
}

@Composable
private fun SignedInNavigation(container: AppContainer, userId: String) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    // Ward chosen on the list; kept across configuration changes.
    var wardId by rememberSaveable { mutableStateOf<String?>(null) }
    var wardName by rememberSaveable { mutableStateOf("") }
    var wardAdmin by rememberSaveable { mutableStateOf(false) }
    fun select(ward: MembershipDto) { wardId = ward.wardId; wardName = ward.wardName; wardAdmin = ward.isAdmin }

    // Keyed by user so a different account after logout never sees the previous ward list.
    val wardsVm: WardsViewModel = viewModel(key = "wards-$userId", factory = viewModelFactory {
        initializer { WardsViewModel(container.wards) }
    })
    val startOnJoin = remember { container.session.pendingInviteCode != null }

    NavHost(nav, startDestination = if (startOnJoin) Routes.JOIN else Routes.WARDS) {
        composable(Routes.WARDS) {
            WardListScreen(
                viewModel = wardsVm,
                onOpenWard = { select(it); nav.navigate(Routes.ROSTER) },
                onInvites = { select(it); nav.navigate(Routes.INVITES) },
                onMyProfile = { select(it); nav.navigate(Routes.PROFILE) },
                onEnterInvite = { nav.navigate(Routes.JOIN) },
                onCreateWard = { nav.navigate(Routes.CREATE_WARD) },
                onLogout = {
                    container.kakaoLogin.logout()
                    scope.launch { container.session.logout() }
                },
            )
        }
        composable(Routes.JOIN) {
            val code = container.session.pendingInviteCode
            val vm: JoinWardViewModel = viewModel(factory = viewModelFactory {
                initializer { JoinWardViewModel(container.invitations, code).also { container.session.pendingInviteCode = null } }
            })
            JoinWardScreen(
                viewModel = vm,
                onBack = {
                    if (nav.previousBackStackEntry != null) nav.popBackStack()
                    else nav.navigate(Routes.WARDS) { popUpTo(nav.graph.id) { inclusive = true } }
                },
                onJoined = {
                    wardsVm.reload()
                    // Clear everything (JOIN may be the start destination) so Back cannot re-open it.
                    nav.navigate(Routes.WARDS) { popUpTo(nav.graph.id) { inclusive = true } }
                },
            )
        }
        composable(Routes.CREATE_WARD) {
            val vm: CreateWardViewModel = viewModel(factory = viewModelFactory {
                initializer { CreateWardViewModel(container.wards) }
            })
            CreateWardScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onCreated = { ward ->
                    // Admin flow continues straight to inviting nurses; Back returns to the ward list.
                    wardsVm.reload()
                    select(ward)
                    nav.navigate(Routes.INVITES) { popUpTo(Routes.WARDS) }
                },
            )
        }
        composable(Routes.INVITES) {
            val id = wardId ?: return@composable LaunchedEffect(Unit) { nav.popBackStack() }
            val vm: InvitesViewModel = viewModel(key = "invites-$id", factory = viewModelFactory {
                initializer { InvitesViewModel(container.invitations, id) }
            })
            InvitesScreen(vm, wardName, onBack = { nav.popBackStack() })
        }
        composable(Routes.PROFILE) {
            val id = wardId ?: return@composable LaunchedEffect(Unit) { nav.popBackStack() }
            val vm: MyProfileViewModel = viewModel(key = "profile-$id", factory = viewModelFactory {
                initializer { MyProfileViewModel(container.profiles, id) }
            })
            MyProfileScreen(vm, wardName, onBack = { nav.popBackStack() })
        }
        composable(Routes.ROSTER) {
            // Roster screens still use fictional DemoRoster data until the roster API step.
            NurseFlowApp(wardName = wardName, admin = wardAdmin, onSwitchWard = { nav.popBackStack() })
        }
    }
}
