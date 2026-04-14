package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: AppearanceSettingsViewModel = hiltViewModel(),
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(CorusColors.Background)) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Appearance", style = CorusFont.screenTitle, color = CorusColors.Text)
        }

        HorizontalDivider(color = CorusColors.Divider)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Dark Mode Toggle ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleDarkMode() }
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.DarkMode,
                    contentDescription = null,
                    tint = CorusColors.Secondary,
                    modifier = Modifier.size(20.dp),
                )

                Spacer(modifier = Modifier.width(CorusSpacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Dark Mode", style = CorusFont.body, color = CorusColors.Text)
                    Text(
                        text = "Use dark background throughout the app",
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                    )
                }

                Spacer(modifier = Modifier.width(CorusSpacing.sm))

                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { viewModel.toggleDarkMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CorusColors.Background,
                        checkedTrackColor = CorusColors.Accent,
                        uncheckedThumbColor = CorusColors.Background,
                        uncheckedTrackColor = CorusColors.Tertiary,
                        uncheckedBorderColor = CorusColors.Tertiary,
                    ),
                )
            }
            HorizontalDivider(
                color = CorusColors.Divider,
                modifier = Modifier.padding(horizontal = CorusSpacing.lg),
            )
        }
    }
}
