package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.ui.theme.AppearanceMode
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: AppearanceSettingsViewModel = hiltViewModel(),
) {
    val mode by viewModel.appearanceMode.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(CorusColors.Background)) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = CorusColors.Text)
            }
            Text(stringResource(R.string.appearance_screen_title), style = CorusFont.screenTitle, color = CorusColors.Text)
        }

        HorizontalDivider(color = CorusColors.Divider)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = CorusSpacing.lg,
                        vertical = CorusSpacing.md,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.DarkMode,
                    contentDescription = null,
                    tint = CorusColors.Secondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(CorusSpacing.md))
                Column {
                    Text(text = stringResource(R.string.appearance_row_theme), style = CorusFont.body, color = CorusColors.Text)
                    Text(
                        text = stringResource(R.string.appearance_row_theme_subtitle),
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                    )
                }
            }

            HorizontalDivider(
                color = CorusColors.Divider,
                modifier = Modifier.padding(horizontal = CorusSpacing.lg),
            )

            AppearanceMode.values().forEach { option ->
                val label = when (option) {
                    AppearanceMode.LIGHT -> stringResource(R.string.appearance_option_light)
                    AppearanceMode.DARK -> stringResource(R.string.appearance_option_dark)
                    AppearanceMode.SYSTEM -> stringResource(R.string.appearance_option_system)
                }
                AppearanceOptionRow(
                    label = label,
                    isSelected = mode == option,
                    onClick = { viewModel.setAppearanceMode(option) },
                )
                HorizontalDivider(
                    color = CorusColors.Divider,
                    modifier = Modifier.padding(horizontal = CorusSpacing.lg),
                )
            }
        }
    }
}

@Composable
private fun AppearanceOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = CorusFont.body,
            color = CorusColors.Text,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.common_selected),
                tint = CorusColors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
