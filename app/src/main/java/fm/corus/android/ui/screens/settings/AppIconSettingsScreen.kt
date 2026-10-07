package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fm.corus.android.R
import fm.corus.android.localization.CorusStrings
import fm.corus.android.service.AppIcon
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun AppIconSettingsScreen(
    onBack: () -> Unit,
    onUnlock: () -> Unit,
    viewModel: AppIconSettingsViewModel = hiltViewModel(),
) {
    val selected by viewModel.selectedIcon.collectAsState()
    val hasFullAccess by viewModel.hasFullAccess.collectAsState()
    val isChanging by viewModel.isChanging.collectAsState()
    val changeFailed by viewModel.changeFailed.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        viewModel.refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AppIconSettingsContent(selected, hasFullAccess, isChanging, onBack, onUnlock, viewModel::changeIcon)
    if (changeFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(CorusStrings.app_icon_error_title), style = CorusFont.songTitleLarge) },
            text = { Text(stringResource(CorusStrings.app_icon_error_message), style = CorusFont.body) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text(stringResource(CorusStrings.common_ok)) }
            },
        )
    }
}

@Composable
internal fun AppIconSettingsContent(
    selected: AppIcon,
    hasFullAccess: Boolean,
    isChanging: Boolean,
    onBack: () -> Unit,
    onUnlock: () -> Unit,
    onSelect: (AppIcon) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(CorusColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CorusHeaderIconButton(
                onClick = onBack,
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(CorusStrings.common_back),
            )
            Text(stringResource(CorusStrings.app_icon_title), style = CorusFont.screenTitle, color = CorusColors.Text)
        }
        HorizontalDivider(color = CorusColors.Divider)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(CorusSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CorusColors.CardBackground)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    AppIcon.entries.forEach { icon ->
                        val label = stringResource(
                            if (icon == AppIcon.DEFAULT) CorusStrings.app_icon_default else CorusStrings.app_icon_corus_blue,
                        )
                        val isSelected = selected == icon
                        Column(
                            Modifier.weight(1f)
                                .selectable(isSelected, enabled = hasFullAccess && !isChanging, role = Role.RadioButton) { onSelect(icon) }
                                .alpha(if (hasFullAccess) 1f else 0.55f).padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Image(
                                painterResource(if (icon == AppIcon.DEFAULT) R.drawable.app_icon_default_preview else R.drawable.app_icon_blue_preview),
                                contentDescription = null,
                                modifier = Modifier.size(76.dp).clip(CircleShape),
                            )
                            Text(label, style = CorusFont.bodyMedium, color = CorusColors.Text)
                            Icon(
                                if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) CorusColors.Accent else CorusColors.Tertiary,
                            )
                        }
                    }
                }
                if (isChanging) {
                    CircularProgressIndicator(Modifier.padding(top = 12.dp).size(24.dp), color = CorusColors.Accent)
                }
            }
            if (!hasFullAccess) {
                Text(stringResource(CorusStrings.app_icon_club_required), style = CorusFont.caption, color = CorusColors.Secondary)
                Button(
                    onClick = onUnlock,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent, contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) { Text(stringResource(CorusStrings.gift_club_offer_cta_standard), style = CorusFont.button) }
            }
            Text(stringResource(CorusStrings.app_icon_launcher_hint), style = CorusFont.caption, color = CorusColors.Secondary)
        }
    }
}
