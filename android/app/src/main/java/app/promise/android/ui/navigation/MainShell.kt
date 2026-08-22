package app.promise.android.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import app.promise.android.ui.commitments.CommitmentDetailScreen
import app.promise.android.ui.commitments.CommitmentsListScreen
import app.promise.android.ui.goals.GoalDetailScreen
import app.promise.android.ui.goals.GoalsListScreen
import app.promise.android.ui.haptics.PromiseHaptics
import app.promise.android.ui.home.HomeScreen
import app.promise.android.ui.profile.ProfileScreen
import app.promise.android.ui.theme.Elevation
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.rememberReduceMotion
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainShellViewModel @Inject constructor(
    val haptics: PromiseHaptics,
) : ViewModel()

@Composable
fun MainShell(
    onSignOut: () -> Unit,
    navController: NavHostController = rememberNavController(),
    viewModel: MainShellViewModel = hiltViewModel(),
) {
    val reduceMotion = rememberReduceMotion()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val colors = PromiseThemeColors.current
    val density = LocalDensity.current
    val tabSlidePx = with(density) { Motion.TabSlideDp.roundToPx() }
    val detailSlidePx = with(density) { 48.dp.roundToPx() }
    val showBottomBar = currentDestination?.hierarchy?.any {
        it.hasRoute(HomeRoute::class) ||
            it.hasRoute(CommitmentsRoute::class) ||
            it.hasRoute(GoalsRoute::class) ||
            it.hasRoute(ProfileRoute::class)
    } == true &&
        currentDestination.hierarchy.none {
            it.hasRoute(CommitmentRoute::class) ||
                it.hasRoute(GoalRoute::class) ||
                it.hasRoute(GoalChatRoute::class) ||
                it.hasRoute(SearchRoute::class)
        }

    val currentTabIndex = remember(currentDestination) {
        when {
            currentDestination?.hierarchy?.any { it.hasRoute(HomeRoute::class) } == true -> 0
            currentDestination?.hierarchy?.any { it.hasRoute(CommitmentsRoute::class) } == true -> 1
            currentDestination?.hierarchy?.any { it.hasRoute(GoalsRoute::class) } == true -> 2
            currentDestination?.hierarchy?.any { it.hasRoute(ProfileRoute::class) } == true -> 3
            else -> -1
        }
    }

    fun navigateToTab(index: Int) {
        if (index !in TabDestinations.items.indices) return
        if (index == currentTabIndex) return
        val route = TabDestinations.items[index].route
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val swipeHost = if (showBottomBar && currentTabIndex >= 0) {
        TabSwipeHost(
            currentIndex = currentTabIndex,
            tabCount = TabDestinations.items.size,
            enabled = true,
            onSwipe = { direction ->
                val next = TabSwipeClassifier.adjacentIndex(
                    currentIndex = currentTabIndex,
                    direction = direction,
                    tabCount = TabDestinations.items.size,
                ) ?: return@TabSwipeHost
                navigateToTab(next)
            },
        )
    } else {
        null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!showBottomBar) return@Scaffold
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = colors.textSecondary,
                tonalElevation = Elevation.none,
            ) {
                TabDestinations.items.forEachIndexed { index, tab ->
                    val selected = index == currentTabIndex
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateToTab(index) },
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
        fun tabEnter(
            scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        ): EnterTransition {
            val from = tabIndexFor(scope.initialState)
            val to = tabIndexFor(scope.targetState)
            if (from == null || to == null) {
                return detailEnter(reduceMotion, detailSlidePx)
            }
            return directionEnter(reduceMotion, tabSlidePx, TabTransition.enterSlideSign(from, to))
        }

        fun tabExit(
            scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        ): ExitTransition {
            val from = tabIndexFor(scope.initialState)
            val to = tabIndexFor(scope.targetState)
            if (from == null || to == null) {
                return detailExit(reduceMotion, detailSlidePx)
            }
            return directionExit(reduceMotion, tabSlidePx, TabTransition.exitSlideSign(from, to))
        }

        CompositionLocalProvider(LocalTabSwipeHost provides swipeHost) {
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { tabEnter(this) },
                exitTransition = { tabExit(this) },
                popEnterTransition = { tabEnter(this) },
                popExitTransition = { tabExit(this) },
            ) {
                composable<HomeRoute>(
                    deepLinks = listOf(navDeepLink { uriPattern = "promise://home" }),
                ) {
                    HomeScreen(
                        onOpenProfile = {
                            navigateToTab(3)
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
                }
                composable<CommitmentsRoute> {
                    CommitmentsListScreen(
                        onOpenDetail = { id ->
                            navController.navigate(CommitmentRoute(id))
                        },
                    )
                }
                composable<GoalsRoute> {
                    GoalsListScreen(
                        onOpenDetail = { id ->
                            navController.navigate(GoalRoute(id))
                        },
                    )
                }
                composable<ProfileRoute> {
                    ProfileScreen(onSignOut = onSignOut)
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
                    app.promise.android.ui.goals.chat.GoalChatScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<SearchRoute>(
                    enterTransition = { detailEnter(reduceMotion, detailSlidePx) },
                    exitTransition = { detailExit(reduceMotion, detailSlidePx) },
                    popEnterTransition = { detailEnter(reduceMotion, detailSlidePx) },
                    popExitTransition = { detailExit(reduceMotion, detailSlidePx) },
                ) {
                    app.promise.android.ui.search.SearchScreen(
                        onNavigateToCommitment = { id -> navController.navigate(CommitmentRoute(id)) },
                        onNavigateToGoal = { id -> navController.navigate(GoalRoute(id)) },
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun tabIndexFor(entry: NavBackStackEntry): Int? {
    val dest = entry.destination
    return when {
        dest.hasRoute(HomeRoute::class) -> 0
        dest.hasRoute(CommitmentsRoute::class) -> 1
        dest.hasRoute(GoalsRoute::class) -> 2
        dest.hasRoute(ProfileRoute::class) -> 3
        else -> null
    }
}

private fun directionEnter(
    reduceMotion: Boolean,
    slidePx: Int,
    sign: Int,
): EnterTransition {
    val fadeMs = if (reduceMotion) Motion.ReducedMotionFadeMs else Motion.TabMs
    val fade = fadeIn(tween(fadeMs, easing = Motion.StandardEasing))
    if (reduceMotion) return fade
    return slideInHorizontally(tween(Motion.TabMs, easing = Motion.StandardEasing)) { fullWidth ->
        sign * fullWidth
    }
}

private fun directionExit(
    reduceMotion: Boolean,
    slidePx: Int,
    sign: Int,
): ExitTransition {
    val fadeMs = if (reduceMotion) Motion.ReducedMotionFadeMs else Motion.TabMs
    val fade = fadeOut(tween(fadeMs, easing = Motion.ExitEasing))
    if (reduceMotion) return fade
    return slideOutHorizontally(tween(Motion.TabMs, easing = Motion.StandardEasing)) { fullWidth ->
        sign * fullWidth
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
