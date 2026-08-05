package fm.corus.android.ui.screens.destination

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import fm.corus.android.R
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.OfflineRetryState
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * What a tapped public catalog link shows while the app works out which entity
 * it names. Replaced by that entity's own page the moment it is known, so this
 * is only ever seen for the length of one lookup — or when the lookup has an
 * answer the user needs: nothing owns this link, or we could not ask.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityLinkScreen(
    state: EntityLinkViewModel.State,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        containerColor = CorusColors.Background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    CorusHeaderIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is EntityLinkViewModel.State.Unreachable -> OfflineRetryState(
                    onRetry = onRetry,
                    title = stringResource(R.string.entity_link_unreachable_title),
                    subtitle = stringResource(R.string.entity_link_unreachable_subtitle),
                )
                is EntityLinkViewModel.State.Missing -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = CorusSpacing.lg),
                ) {
                    Text(
                        text = stringResource(R.string.entity_link_not_found),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Secondary,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> CircularProgressIndicator(color = CorusColors.Accent)
            }
        }
    }
}
