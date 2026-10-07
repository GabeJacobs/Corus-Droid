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
    FEED(fm.corus.android.localization.CorusStrings.nav_home, Icons.Filled.Headphones, Icons.Outlined.Headphones),
    EXPLORE(fm.corus.android.localization.CorusStrings.nav_search, Icons.Filled.Search, Icons.Outlined.Search),
    COMPOSE(fm.corus.android.localization.CorusStrings.nav_post, Icons.Filled.Search, Icons.Outlined.Search), // icons unused, compose is a custom button
    MESSAGES(fm.corus.android.localization.CorusStrings.nav_messages, Icons.AutoMirrored.Filled.Send, Icons.AutoMirrored.Outlined.Send),
    NOTIFICATIONS(fm.corus.android.localization.CorusStrings.nav_activity, Icons.Filled.Notifications, Icons.Outlined.Notifications),
    PROFILE(fm.corus.android.localization.CorusStrings.own_profile_tab, Icons.Filled.Person, Icons.Outlined.Person),
}
