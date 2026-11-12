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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fm.corus.android.ui.components.CorusModalBottomSheet
import fm.corus.android.ui.components.UserAvatarView
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
internal fun LiveHeader(title: String, close: () -> Unit, done: (() -> Unit)? = null, sheetToolbar: Boolean = false) {
    val height = if (sheetToolbar) 56.dp else 66.dp
    Box(Modifier.fillMaxWidth().height(height).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        LiveText(title, CorusFont.screenTitle, modifier = Modifier.padding(horizontal = 54.dp), lines = 1)
        if (done == null) {
            LiveIconButton(Icons.Default.Close, stringResource(CorusStrings.corus_live_close), close, Modifier.align(Alignment.CenterEnd), circle = true)
        } else if (sheetToolbar) {
            Box(
                Modifier.align(Alignment.CenterEnd).height(44.dp).clip(CircleShape)
                    .background(CorusColors.SheetControlBackground).clickable(onClick = done).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                LiveText(stringResource(CorusStrings.corus_live_done), CorusFont.body.copy(fontWeight = FontWeight.Bold), CorusColors.Secondary)
            }
        } else {
            TextButton(onClick = done, modifier = Modifier.align(Alignment.CenterEnd)) {
                LiveText(stringResource(CorusStrings.corus_live_done), CorusFont.usernameLarge, CorusColors.Accent)
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
internal fun LivePrimary(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = CorusColors.Accent,
            disabledContainerColor = CorusColors.Accent.copy(alpha = .19f),
        ),
    ) {
        LiveText(title, CorusFont.body.copy(fontWeight = FontWeight.Bold), Color.White.copy(alpha = if (enabled) 1f else .4f))
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
internal fun LiveArtwork(url: String?, size: Dp, modifier: Modifier = Modifier, neutralPlaceholder: Boolean = false) {
    val background = if (neutralPlaceholder) CorusColors.SheetControlBackground else CorusColors.Accent.copy(alpha = .12f)
    Box(
        modifier.size(size).clip(RoundedCornerShape(7.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Default.MusicNote, null,
                tint = if (neutralPlaceholder) CorusColors.Tertiary else CorusColors.Accent,
                modifier = Modifier.size(size * .5f))
        } else {
            AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
internal fun LiveAvatar(person: LivePerson, size: Dp, ring: Boolean = false) {
    Box(
        Modifier.then(if (ring) Modifier.border(2.dp, CorusColors.Accent, CircleShape).padding(4.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        UserAvatarView(person.avatar, displayName = person.username, size = size, usesSolidLoadingPlaceholder = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveSheet(
    title: String,
    close: () -> Unit,
    large: Boolean = false,
    done: (() -> Unit)? = null,
    sheetToolbar: Boolean = false,
    startsHalfExpanded: Boolean = false,
    expand: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = !startsHalfExpanded)
    LaunchedEffect(expand) {
        if (expand && sheetState.hasExpandedState) sheetState.expand()
    }
    CorusModalBottomSheet(
        onDismissRequest = close,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = CorusColors.SheetBackground,
        dragHandle = if (sheetToolbar) {
            {
                Box(Modifier.fillMaxWidth().height(9.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.size(width = 36.dp, height = 5.dp).clip(CircleShape).background(CorusColors.Secondary.copy(alpha = .55f)))
                }
            }
        } else null,
    ) {
        val height = if (sheetToolbar) .90f else .96f
        Column(Modifier.fillMaxWidth().then(if (large) Modifier.fillMaxHeight(height) else Modifier)) {
            LiveHeader(title, close, done, sheetToolbar)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
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
