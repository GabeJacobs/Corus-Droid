package fm.corus.android.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import fm.corus.android.R

enum class CorusTab(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    FEED(R.string.tab_feed, Icons.Filled.Headphones, Icons.Outlined.Headphones),
    EXPLORE(R.string.tab_search, Icons.Filled.Search, Icons.Outlined.Search),
    COMPOSE(R.string.tab_post, Icons.Filled.Search, Icons.Outlined.Search), // icons unused, compose is a custom button
    MESSAGES(R.string.messaging_list_title, Icons.AutoMirrored.Filled.Send, Icons.AutoMirrored.Outlined.Send),
    NOTIFICATIONS(R.string.tab_activity, Icons.Filled.Notifications, Icons.Outlined.Notifications),
    PROFILE(R.string.tab_profile, Icons.Filled.Person, Icons.Outlined.Person),
}
