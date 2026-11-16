package fm.corus.android.ui.screens.live

import androidx.compose.ui.res.stringResource
import fm.corus.android.localization.CorusStrings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fm.corus.android.ui.components.CorusModalBottomSheet
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.dismissKeyboardOnDownwardDrag
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont

@Composable
internal fun LiveText(
    text: String,
    style: TextStyle = CorusFont.body,
    color: Color = CorusColors.Text,
    modifier: Modifier = Modifier,
    lines: Int = Int.MAX_VALUE,
) = Text(text, modifier, color = color, style = style, maxLines = lines, overflow = TextOverflow.Ellipsis)

@Composable
private fun LiveHeader(
    title: String,
    done: (() -> Unit)? = null,
    sheetToolbar: Boolean = false,
    modifier: Modifier = Modifier,
    close: (() -> Unit)? = null,
) {
    val height = if (done != null || sheetToolbar || close != null) 56.dp else 48.dp
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Box(modifier.fillMaxWidth().height(height).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        LiveText(title, CorusFont.screenTitle, modifier = Modifier.padding(horizontal = 54.dp), lines = 1)
        if (done != null) {
            TextButton(
                onClick = {
                    keyboard?.hide()
                    focus.clearFocus()
                    done()
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                LiveText(stringResource(CorusStrings.corus_live_done), CorusFont.button, CorusColors.Secondary)
            }
        } else if (close != null) {
            IconButton(onClick = close, modifier = Modifier.align(Alignment.CenterEnd)) {
                Box(Modifier.size(44.dp).background(CorusColors.CardBackground, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, stringResource(CorusStrings.corus_live_close), tint = CorusColors.Secondary)
                }
            }
        }
    }
}

@Composable
internal fun LiveIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CorusColors.Secondary,
    circle: Boolean = false,
    enabled: Boolean = true,
    touchSize: Dp = 48.dp,
    glyphSize: Dp = 24.dp,
) {
    IconButton(onClick = onClick, modifier = modifier.size(touchSize), enabled = enabled) {
        Box(
            Modifier.size(touchSize - 5.dp).clip(CircleShape).then(
                if (circle) Modifier.background(CorusColors.SheetControlBackground) else Modifier,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = tint.copy(alpha = if (enabled) 1f else .35f), modifier = Modifier.size(glyphSize))
        }
    }
}

@Composable
internal fun LivePrimary(
    title: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val actionColor = if (destructive) LiveRed else CorusColors.Accent
    val labelColor = if (destructive) actionColor else Color.White
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = actionColor.copy(alpha = if (destructive) .12f else 1f),
            disabledContainerColor = actionColor.copy(alpha = if (destructive) .06f else .19f),
        ),
    ) {
        LiveText(
            title,
            CorusFont.body.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            labelColor.copy(alpha = if (enabled) 1f else .4f),
        )
    }
}

@Composable
internal fun LiveRow(label: String, icon: ImageVector, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .heightIn(min = 55.dp).padding(horizontal = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = CorusColors.Secondary, modifier = Modifier.size(25.dp))
        LiveText(label, modifier = Modifier.weight(1f))
        detail?.let { LiveText(it, CorusFont.caption, CorusColors.Secondary, lines = 1) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = CorusColors.Secondary)
    }
}

@Composable
internal fun LiveArtwork(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    neutralPlaceholder: Boolean = false,
    showPlaceholderWhileLoading: Boolean = false,
) {
    val background = if (neutralPlaceholder) CorusColors.SheetControlBackground else CorusColors.Accent.copy(alpha = .12f)
    Box(
        modifier.size(size).clip(RoundedCornerShape(7.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank() || showPlaceholderWhileLoading) {
            Icon(Icons.Default.MusicNote, null,
                tint = if (neutralPlaceholder) CorusColors.Tertiary else CorusColors.Accent,
                modifier = Modifier.size(size * .5f))
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
internal fun LiveAvatar(
    person: LivePerson,
    size: Dp,
    ring: Boolean = false,
    ringPadding: Dp = 4.dp,
    ringWidth: Dp = 2.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.then(if (ring) Modifier.border(ringWidth, CorusColors.Accent, CircleShape).padding(ringPadding) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        UserAvatarView(person.avatar, displayName = person.username, size = size, usesSolidLoadingPlaceholder = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LiveSheet(
    title: String,
    close: () -> Unit,
    large: Boolean = false,
    done: (() -> Unit)? = null,
    sheetToolbar: Boolean = false,
    startsHalfExpanded: Boolean = false,
    dismissKeyboardFromHeader: Boolean = false,
    horizontalContentPadding: Dp = 20.dp,
    fixedTopGap: Dp? = null,
    dismissEnabled: Boolean = true,
    showCloseButton: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val largeHeightFraction = when {
        fixedTopGap != null -> 1f
        sheetToolbar -> .90f
        else -> .96f
    }
    val canDismiss by rememberUpdatedState(dismissEnabled)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = !startsHalfExpanded) {
        it != SheetValue.Hidden || canDismiss
    }
    CorusModalBottomSheet(
        onDismissRequest = { if (canDismiss) close() },
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(top = fixedTopGap ?: 0.dp),
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = CorusColors.SheetBackground,
    ) {
        Column(Modifier.fillMaxWidth().then(if (large) Modifier.fillMaxHeight(largeHeightFraction) else Modifier)) {
            LiveHeader(
                title, done, sheetToolbar,
                close = if (showCloseButton) ({ if (canDismiss) close() }) else null,
                modifier = if (dismissKeyboardFromHeader) Modifier.dismissKeyboardOnDownwardDrag() else Modifier,
            )
            Column(
                Modifier.fillMaxWidth().then(if (large) Modifier.weight(1f) else Modifier)
                    .padding(horizontal = horizontalContentPadding).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun LiveSearchField(
    value: String, placeholder: String, modifier: Modifier = Modifier, showSearchIcon: Boolean = false, onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(),
        singleLine = true, placeholder = { LiveText(placeholder, color = CorusColors.Secondary) },
        leadingIcon = if (showSearchIcon) { { Icon(Icons.Default.Search, null, tint = CorusColors.Secondary) } } else null,
        textStyle = CorusFont.body, shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CorusColors.CardBackground, unfocusedContainerColor = CorusColors.CardBackground,
            focusedBorderColor = CorusColors.Accent, unfocusedBorderColor = Color.Transparent,
        ),
    )
}
