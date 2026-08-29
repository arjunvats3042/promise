package app.promise.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

data class TabDestination(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String,
)

object TabDestinations {
    val items: List<TabDestination> = listOf(
        TabDestination(
            route = HomeRoute,
            label = "Home",
            icon = Icons.Outlined.Home,
            contentDescription = "Home",
        ),
        TabDestination(
            route = CommitmentsRoute,
            label = "Commitments",
            icon = Icons.Outlined.TaskAlt,
            contentDescription = "Commitments",
        ),
        TabDestination(
            route = RoadmapRoute,
            label = "Roadmap",
            icon = Icons.Outlined.Timeline,
            contentDescription = "365 Roadmap",
        ),
        TabDestination(
            route = GoalsRoute,
            label = "Goals",
            icon = Icons.Outlined.Flag,
            contentDescription = "Goals",
        ),
        TabDestination(
            route = ProfileRoute,
            label = "Profile",
            icon = Icons.Outlined.Person,
            contentDescription = "Profile",
        ),
    )

    val startRoute: Any = HomeRoute
}
