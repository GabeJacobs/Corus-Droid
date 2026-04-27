package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.i18n.AppLanguage
import fm.corus.android.i18n.LanguageManager
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun LanguageSettingsScreen(
    onBack: () -> Unit = {},
) {
    var selected by remember { mutableStateOf(LanguageManager.current()) }

    Column(modifier = Modifier.fillMaxSize().background(CorusColors.Background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = CorusColors.Text,
                )
            }
            Text(
                stringResource(R.string.language_screen_title),
                style = CorusFont.screenTitle,
                color = CorusColors.Text,
            )
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
                    imageVector = Icons.Filled.Language,
                    contentDescription = null,
                    tint = CorusColors.Secondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(CorusSpacing.md))
                Column {
                    Text(
                        text = stringResource(R.string.language_section_title),
                        style = CorusFont.body,
                        color = CorusColors.Text,
                    )
                    Text(
                        text = stringResource(R.string.language_section_subtitle),
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                    )
                }
            }

            HorizontalDivider(
                color = CorusColors.Divider,
                modifier = Modifier.padding(horizontal = CorusSpacing.lg),
            )

            AppLanguage.values().forEach { option ->
                val labelRes = when (option) {
                    AppLanguage.SYSTEM -> R.string.language_option_system
                    AppLanguage.ENGLISH -> R.string.language_option_english
                    AppLanguage.PORTUGUESE_BR -> R.string.language_option_portuguese_br
                }
                LanguageOptionRow(
                    label = stringResource(labelRes),
                    isSelected = selected == option,
                    onClick = {
                        selected = option
                        LanguageManager.set(option)
                    },
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
private fun LanguageOptionRow(
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
