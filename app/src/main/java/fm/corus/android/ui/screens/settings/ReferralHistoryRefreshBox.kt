@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import fm.corus.android.ui.theme.CorusColors

@Composable
internal fun ReferralHistoryRefreshBox(
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        // One Corus indicator handles both initial fetch and pull-to-refresh.
        indicator = {},
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) CircularProgressIndicator(color = CorusColors.Accent)
            Box(Modifier.weight(1f)) { content() }
        }
    }
}
