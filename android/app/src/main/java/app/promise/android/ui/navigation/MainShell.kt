package app.promise.android.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import app.promise.android.ui.commitments.CommitmentDetailScreen
import app.promise.android.ui.commitments.CommitmentsListScreen
import app.promise.android.ui.goals.GoalDetailScreen
import app.promise.android.ui.goals.GoalsListScreen
import app.promise.android.ui.goals.chat.GoalChatScreen
import app.promise.android.ui.haptics.PromiseHaptics
import app.promise.android.ui.home.HomeScreen
import app.promise.android.ui.profile.ProfileScreen
import app.promise.android.ui.search.SearchScreen
import app.promise.android.ui.theme.Elevation
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.rememberReduceMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class MainShellViewModel @Inject constructor(
    val haptics: PromiseHaptics,
    val deepLinkRouter: DeepLinkRouter,
) : ViewModel()

@Composable
fun MainShell(
    onSignOut: () -> Unit,
    navController: NavHostController = rememberNavController(),
    viewModel: MainShellViewModel = hiltViewModel(),
) {
    val reduceMotion = rememberReduceMotion()
    val colors = PromiseThemeColors.current
    val density = LocalDensity.current
    val detailSlidePx = with(density) { 48.dp.roundToPx() }
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })

    // Observe and handle deep link events across cold start, background, and foreground
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.deepLinkRouter.destinations.collectLatest { destination ->
            when (destination) {
                is DeepLinkDestination.Commitment -> {
                    navController.navigate(CommitmentRoute(destination.id)) {
                        launchSingleTop = true
                    }
                }
                is DeepLinkDestination.Goal -> {
                    navController.navigate(GoalRoute(destination.id)) {
                        launchSingleTop = true
                    }
                }
                is DeepLinkDestination.GoalChat -> {
                    navController.navigate(GoalChatRoute(destination.id)) {
                        launchSingleTop = true
                    }
                }
                DeepLinkDestination.Home -> {
                    pagerState.scrollToPage(0)
                }
                DeepLinkDestination.Profile -> {
                    pagerState.scrollToPage(3)
                }
            }
        }
    }

    val swipeHost = remember(pagerState) {
        TabSwipeHost(
            currentIndex = pagerState.currentPage,
            tabCount = TabDestinations.items.size,
            enabled = true,
            onSwipe = { direction ->
                val next = TabSwipeClassifier.adjacentIndex(
                    currentIndex = pagerState.currentPage,
                    direction = direction,
                    tabCount = TabDestinations.items.size,
                ) ?: return@TabSwipeHost
                coroutineScope.launch {
                    if (reduceMotion) {
                        pagerState.scrollToPage(next)
                    } else {
                        pagerState.animateScrollToPage(next)
                    }
                }
            },
        )
    }

    CompositionLocalProvider(LocalTabSwipeHost provides swipeHost) {
        NavHost(
            navController = navController,
            startDestination = MainTabsRoute,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<MainTabsRoute>(
                deepLinks = listOf(navDeepLink { uriPattern = "promise://home" }),
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = colors.textSecondary,
                            tonalElevation = Elevation.none,
                        ) {
                            TabDestinations.items.forEachIndexed { index, tab ->
                                val selected = index == pagerState.currentPage
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (pagerState.currentPage != index) {
                                            coroutineScope.launch {
                                                if (reduceMotion) {
                                                    pagerState.scrollToPage(index)
                                                } else {
                                                    pagerState.animateScrollToPage(index)
                                                }
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.contentDescription,
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colors.accent,
                                        selectedTextColor = colors.accent,
                                        unselectedIconColor = colors.textSecondary,
                                        unselectedTextColor = colors.textSecondary,
                                        indicatorColor = MaterialTheme.colorScheme.background,
                                    ),
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !reduceMotion,
                        beyondViewportPageCount = 1,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) { page ->
                        when (page) {
                            0 -> HomeScreen(
                                onOpenProfile = {
                                    coroutineScope.launch {
                                        if (reduceMotion) pagerState.scrollToPage(3) else pagerState.animateScrollToPage(3)
                                    }
                                },
                                onOpenCommitment = { id ->
                                    navController.navigate(CommitmentRoute(id))
                                },
                                onOpenPractice = { id ->
                                    navController.navigate(GoalRoute(id))
                                },
                                onOpenSearch = {
                                    navController.navigate(SearchRoute)
                                },
                            )
                            1 -> CommitmentsListScreen(
                                onOpenDetail = { id ->
                                    navController.navigate(CommitmentRoute(id))
                                },
                            )
                            2 -> GoalsListScreen(
                                onOpenDetail = { id ->
                                    navController.navigate(GoalRoute(id))
                                },
                            )
                            3 -> ProfileScreen(onSignOut = onSignOut)
                        }
                    }
                }
            }

            composable<CommitmentRoute>(
                deepLinks = listOf(
                    navDeepLink { uriPattern = "promise://commitment/{commitmentId}" },
                ),
                enterTransition = { detailEnter(reduceMotion, detailSlidePx) },
                exitTransition = { detailExit(reduceMotion, detailSlidePx) },
                popEnterTransition = { detailEnter(reduceMotion, detailSlidePx) },
                popExitTransition = { detailExit(reduceMotion, detailSlidePx) },
            ) {
                CommitmentDetailScreen(
                    onBack = { navController.popBackStack() },
                    onNotFound = { navController.popBackStack() },
                )
            }
            composable<GoalRoute>(
                deepLinks = listOf(
                    navDeepLink { uriPattern = "promise://goal/{goalId}" },
                ),
                enterTransition = { detailEnter(reduceMotion, detailSlidePx) },
                exitTransition = { detailExit(reduceMotion, detailSlidePx) },
                popEnterTransition = { detailEnter(reduceMotion, detailSlidePx) },
                popExitTransition = { detailExit(reduceMotion, detailSlidePx) },
            ) {
                GoalDetailScreen(
                    onBack = { navController.popBackStack() },
                    onNotFound = { navController.popBackStack() },
                    onOpenChat = { goalId -> navController.navigate(GoalChatRoute(goalId)) },
                )
            }
            composable<GoalChatRoute>(
                deepLinks = listOf(
                    navDeepLink { uriPattern = "promise://goal/{goalId}/chat" },
                ),
                enterTransition = { detailEnter(reduceMotion, detailSlidePx) },
                exitTransition = { detailExit(reduceMotion, detailSlidePx) },
                popEnterTransition = { detailEnter(reduceMotion, detailSlidePx) },
                popExitTransition = { detailExit(reduceMotion, detailSlidePx) },
            ) {
                GoalChatScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable<SearchRoute>(
                enterTransition = { detailEnter(reduceMotion, detailSlidePx) },
                exitTransition = { detailExit(reduceMotion, detailSlidePx) },
                popEnterTransition = { detailEnter(reduceMotion, detailSlidePx) },
                popExitTransition = { detailExit(reduceMotion, detailSlidePx) },
            ) {
                SearchScreen(
                    onNavigateToCommitment = { id -> navController.navigate(CommitmentRoute(id)) },
                    onNavigateToGoal = { id -> navController.navigate(GoalRoute(id)) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun detailEnter(reduceMotion: Boolean, slidePx: Int): EnterTransition {
    val fadeMs = if (reduceMotion) Motion.ReducedMotionFadeMs else Motion.ScreenPushMs
    val fade = fadeIn(tween(fadeMs, easing = Motion.StandardEasing))
    if (reduceMotion) return fade
    return fade + slideInHorizontally(tween(Motion.ScreenPushMs, easing = Motion.StandardEasing)) {
        slidePx
    }
}

private fun detailExit(reduceMotion: Boolean, slidePx: Int): ExitTransition {
    val fadeMs = if (reduceMotion) Motion.ReducedMotionFadeMs else Motion.ScreenPushMs
    val fade = fadeOut(tween(fadeMs, easing = Motion.ExitEasing))
    if (reduceMotion) return fade
    return fade + slideOutHorizontally(tween(Motion.ScreenPushMs, easing = Motion.ExitEasing)) {
        -slidePx
    }
}
