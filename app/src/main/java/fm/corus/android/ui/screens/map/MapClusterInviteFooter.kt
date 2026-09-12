package fm.corus.android.ui.screens.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.ui.components.shareCorusInvite
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont

internal fun canInviteMapCluster(
    state: MapScreenState, city: MapCity, page: MapPeoplePage?,
    loading: Boolean, failed: Boolean, viewerId: String?,
): Boolean {
    if (viewerId.isNullOrBlank() || state.user?.id != viewerId || !state.ownPresenceReady ||
        state.ownAudience !in listOf("everyone", "following") || state.ownCity?.cityId != city.cityId ||
        state.loading || state.error != null || loading || failed || page == null ||
        page.cursor != null || !page.reachedEnd || page.people.isEmpty()) return false
    val summary = state.cities.firstOrNull { it.city.cityId == city.cityId } ?: return false
    val expectedCount = summary.facets[state.filter]?.count ?: return false
    if (page.people.size < expectedCount) return false
    return summary.facets["all"]?.includesViewer == true || page.people.any { it.user.id == viewerId }
}

@Composable
internal fun MapClusterInviteFooter() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.map_cluster_invite_copy), style = CorusFont.caption,
            color = CorusColors.Secondary, textAlign = TextAlign.Center)
        Button(onClick = { context.shareCorusInvite() }, shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)) {
            Text(stringResource(R.string.map_cluster_invite_action), style = CorusFont.bodyMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClusterInvitePreview() { MapClusterInviteFooter() }
