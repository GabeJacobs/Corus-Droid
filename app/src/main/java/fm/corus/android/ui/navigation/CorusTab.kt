package fm.corus.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

enum class CorusTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    FEED("Feed", Icons.Filled.Headphones, Icons.Outlined.Headphones),
    EXPLORE("Search", Icons.Filled.Search, Icons.Outlined.Search),
    COMPOSE("Post", Icons.Filled.Search, Icons.Outlined.Search), // icons unused, compose is a custom button
    NOTIFICATIONS("Activity", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person),
}
