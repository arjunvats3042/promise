package app.promise.android.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import app.promise.android.data.remote.LocalNetworkAccess
import app.promise.android.domain.SessionState
import app.promise.android.ui.auth.LocalNetworkAccessState
import app.promise.android.ui.auth.LoginScreen
import app.promise.android.ui.auth.LoginViewModel
import app.promise.android.ui.auth.RegisterScreen
import app.promise.android.ui.auth.RegisterViewModel
import app.promise.android.ui.auth.SessionRestoringScreen
import app.promise.android.ui.auth.SessionViewModel

@Composable
fun PromiseNavHost(
    navController: NavHostController = rememberNavController(),
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val restoreNeedsRetry by sessionViewModel.restoreNeedsRetry.collectAsStateWithLifecycle()
    val localNetwork by sessionViewModel.localNetwork.collectAsStateWithLifecycle()

    val localNetworkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        sessionViewModel.onLocalNetworkPermissionResult(granted)
    }

    LaunchedEffect(localNetwork) {
        if (localNetwork is LocalNetworkAccessState.NeedsRequest) {
            localNetworkLauncher.launch(LocalNetworkAccess.PERMISSION)
        }
    }

    LaunchedEffect(session) {
        when (session) {
            is SessionState.Authenticated -> {
                navController.navigate(MainGraphRoute) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
            SessionState.Unauthenticated -> {
                navController.navigate(LoginRoute) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
            SessionState.Restoring -> Unit
        }
    }

    NavHost(
        navController = navController,
        startDestination = AuthGraphRoute,
    ) {
        navigation<AuthGraphRoute>(startDestination = SessionRestoreRoute) {
            composable<SessionRestoreRoute> {
                SessionRestoringScreen(
                    onRetry = if (restoreNeedsRetry) sessionViewModel::retryRestore else null,
                    localNetworkDenied = localNetwork is LocalNetworkAccessState.Denied,
                    onAllowLocalNetwork = if (localNetwork is LocalNetworkAccessState.Denied) {
                        sessionViewModel::retryLocalNetworkPermission
                    } else {
                        null
                    },
                )
            }
            composable<LoginRoute> {
                val loginViewModel: LoginViewModel = hiltViewModel()
                LaunchedEffect(localNetwork) {
                    if (localNetwork is LocalNetworkAccessState.Granted ||
                        localNetwork is LocalNetworkAccessState.NotRequired
                    ) {
                        loginViewModel.onLocalNetworkGranted()
                    }
                }
                LoginScreen(
                    viewModel = loginViewModel,
                    onAllowLocalNetwork = sessionViewModel::retryLocalNetworkPermission,
                )
            }
        }
        composable<MainGraphRoute> {
            MainShell(onSignOut = sessionViewModel::logout)
        }
    }
}
