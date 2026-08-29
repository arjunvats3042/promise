package app.promise.android.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.promise.android.ui.components.PromiseHairlineDivider
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.bouncyClickable
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
                DeepLinkDestination.VoiceCapture -> {
                    pagerState.scrollToPage(0)
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
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                            color = MaterialTheme.colorScheme.background,
                            shadowElevation = Elevation.none,
                            tonalElevation = Elevation.none,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                PromiseHairlineDivider(
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .padding(horizontal = Spacing.sm),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TabDestinations.items.forEachIndexed { index, tab ->
                                        val selected = index == pagerState.currentPage
                                        val itemBg = if (selected) colors.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent
                                        val itemFg = if (selected) colors.accent else colors.textSecondary

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(Radius.md))
                                                .bouncyClickable(
                                                    targetScale = 0.92f,
                                                    onClickLabel = tab.label,
                                                ) {
                                                    if (pagerState.currentPage != index) {
                                                        viewModel.haptics.selection()
                                                        coroutineScope.launch {
                                                            if (reduceMotion) {
                                                                pagerState.scrollToPage(index)
                                                            } else {
                                                                pagerState.animateScrollToPage(
                                                                    page = index,
                                                                    animationSpec = Motion.snappySpring(),
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                .padding(vertical = 4.dp)
                                                .semantics {
                                                    this.contentDescription = tab.contentDescription
                                                    this.selected = selected
                                                },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(Radius.pill))
                                                    .background(itemBg)
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = null,
                                                    tint = itemFg,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = tab.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 11.sp,
                                                color = itemFg,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
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
                            .padding(bottom = innerPadding.calculateBottomPadding()),
                    ) { page ->
                        when (page) {
                            0 -> HomeScreen(
                                onOpenProfile = {
                                    coroutineScope.launch {
                                        if (reduceMotion) pagerState.scrollToPage(3) else pagerState.animateScrollToPage(3, animationSpec = Motion.snappySpring())
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
    val fade = fadeIn(tween(fadeMs, easing = Motion.EmphasizedEasing))
    if (reduceMotion) return fade
    return fade + slideInHorizontally(tween(Motion.ScreenPushMs, easing = Motion.EmphasizedEasing)) {
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
