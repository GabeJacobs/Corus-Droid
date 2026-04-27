package fm.corus.android.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.DiscoIntensity
import fm.corus.android.data.model.FlairStyle
import fm.corus.android.data.model.FrameStyle
import fm.corus.android.data.model.RainIntensity
import fm.corus.android.data.model.SnowIntensity
import fm.corus.android.data.model.VinylStyle
import fm.corus.android.ui.components.DiscoEffectView
import fm.corus.android.ui.components.RainEffectView
import fm.corus.android.ui.components.SnowEffectView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

data class StyleSelections(
    val vinylColor: VinylStyle = VinylStyle.BLACK,
    val vinylSpinning: Boolean = false,
    val frameColor: FrameStyle = FrameStyle.BLACK,
    val profileFlair: FlairStyle = FlairStyle.CHECKMARK,
    val rainEffect: RainIntensity = RainIntensity.OFF,
    val snowEffect: SnowIntensity = SnowIntensity.OFF,
    val discoEffect: DiscoIntensity = DiscoIntensity.OFF,
) {
    fun hasChanges(from: StyleSelections): Boolean =
        vinylColor != from.vinylColor ||
                vinylSpinning != from.vinylSpinning ||
                frameColor != from.frameColor ||
                profileFlair != from.profileFlair ||
                rainEffect != from.rainEffect ||
                snowEffect != from.snowEffect ||
                discoEffect != from.discoEffect

    fun changedFields(from: StyleSelections): Map<String, Any> {
        val fields = mutableMapOf<String, Any>()
        if (vinylColor != from.vinylColor) fields["vinylColor"] = vinylColor.value
        if (vinylSpinning != from.vinylSpinning) fields["vinylSpinning"] = vinylSpinning
        if (frameColor != from.frameColor) fields["frameColor"] = frameColor.value
        if (profileFlair != from.profileFlair) fields["profileFlair"] = profileFlair.value
        if (rainEffect != from.rainEffect) fields["rainEffect"] = rainEffect.value
        if (snowEffect != from.snowEffect) fields["snowEffect"] = snowEffect.value
        if (discoEffect != from.discoEffect) fields["discoEffect"] = discoEffect.value
        return fields
    }

    val hasNonDefaultValues: Boolean
        get() = vinylColor != VinylStyle.BLACK ||
                vinylSpinning ||
                frameColor != FrameStyle.BLACK ||
                profileFlair != FlairStyle.CHECKMARK ||
                rainEffect != RainIntensity.OFF ||
                snowEffect != SnowIntensity.OFF ||
                discoEffect != DiscoIntensity.OFF

    fun introducesPremiumValue(from: StyleSelections): Boolean =
        (vinylColor != from.vinylColor && vinylColor != VinylStyle.BLACK) ||
                (vinylSpinning != from.vinylSpinning && vinylSpinning) ||
                (frameColor != from.frameColor && frameColor != FrameStyle.BLACK) ||
                (profileFlair != from.profileFlair && profileFlair != FlairStyle.CHECKMARK) ||
                (rainEffect != from.rainEffect && rainEffect != RainIntensity.OFF) ||
                (snowEffect != from.snowEffect && snowEffect != SnowIntensity.OFF) ||
                (discoEffect != from.discoEffect && discoEffect != DiscoIntensity.OFF)
}

private enum class StylePage { VINYL, VINYL_SPIN, FRAME, FLAIR, RAIN, SNOW, DISCO }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StylePickerSheet(
    currentSelections: StyleSelections,
    username: String,
    latestTrackPost: CymbalPost?,
    latestMoviePost: CymbalPost?,
    hasTrackPosts: Boolean,
    hasMoviePosts: Boolean,
    isClubMember: Boolean,
    isSaving: Boolean,
    initialPage: Int = 0,
    onSave: (StyleSelections) -> Unit,
    onNavigateToClub: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(currentSelections) }

    val pages = remember(hasTrackPosts, hasMoviePosts) {
        buildList {
            if (hasTrackPosts) add(StylePage.VINYL)
            // VINYL_SPIN and DISCO hidden to match iOS
            if (hasMoviePosts) add(StylePage.FRAME)
            add(StylePage.FLAIR)
            if (hasTrackPosts) add(StylePage.RAIN)
            if (hasTrackPosts) add(StylePage.SNOW)
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
        pageCount = { pages.size },
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f),
    ) {
        // Top bar with close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.style_picker_cd_close),
                    tint = CorusColors.Secondary,
                )
            }
        }

        // Page indicator dots
        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CorusSpacing.sm),
                horizontalArrangement = Arrangement.Center,
            ) {
                pages.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) CorusColors.Text
                                else CorusColors.Secondary.copy(alpha = 0.3f)
                            ),
                    )
                }
            }
        }

        // Pager content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { pageIndex ->
            when (pages[pageIndex]) {
                StylePage.VINYL -> VinylColorPickerPage(
                    selected = draft.vinylColor,
                    onSelect = { draft = draft.copy(vinylColor = it) },
                    latestTrackPost = latestTrackPost,
                )
                StylePage.VINYL_SPIN -> VinylSpinTogglePage(
                    spinning = draft.vinylSpinning,
                    onSpinningChange = { draft = draft.copy(vinylSpinning = it) },
                    vinylStyle = draft.vinylColor,
                    latestTrackPost = latestTrackPost,
                )
                StylePage.FRAME -> FrameColorPickerPage(
                    selected = draft.frameColor,
                    onSelect = { draft = draft.copy(frameColor = it) },
                    latestMoviePost = latestMoviePost,
                )
                StylePage.FLAIR -> FlairPickerPage(
                    selected = draft.profileFlair,
                    onSelect = { draft = draft.copy(profileFlair = it) },
                    username = username,
                )
                StylePage.RAIN -> EffectTogglePage(
                    title = stringResource(R.string.style_picker_rain_effect),
                    entries = RainIntensity.entries,
                    selected = draft.rainEffect,
                    onSelect = { newValue ->
                        draft = if (newValue != RainIntensity.OFF) {
                            draft.copy(rainEffect = newValue, snowEffect = SnowIntensity.OFF)
                        } else {
                            draft.copy(rainEffect = newValue)
                        }
                    },
                    labelOf = { it.displayName },
                    vinylStyle = draft.vinylColor,
                    latestTrackPost = latestTrackPost,
                    effectOverlay = { intensity, modifier ->
                        if (intensity != RainIntensity.OFF) {
                            RainEffectView(intensity = intensity, modifier = modifier)
                        }
                    },
                )
                StylePage.SNOW -> EffectTogglePage(
                    title = stringResource(R.string.style_picker_snow_effect),
                    entries = SnowIntensity.entries,
                    selected = draft.snowEffect,
                    onSelect = { newValue ->
                        draft = if (newValue != SnowIntensity.OFF) {
                            draft.copy(snowEffect = newValue, rainEffect = RainIntensity.OFF)
                        } else {
                            draft.copy(snowEffect = newValue)
                        }
                    },
                    labelOf = { it.displayName },
                    vinylStyle = draft.vinylColor,
                    latestTrackPost = latestTrackPost,
                    effectOverlay = { intensity, modifier ->
                        if (intensity != SnowIntensity.OFF) {
                            SnowEffectView(intensity = intensity, modifier = modifier)
                        }
                    },
                )
                StylePage.DISCO -> EffectTogglePage(
                    title = stringResource(R.string.style_picker_disco_effect),
                    entries = DiscoIntensity.entries,
                    selected = draft.discoEffect,
                    onSelect = { draft = draft.copy(discoEffect = it) },
                    labelOf = { it.displayName },
                    vinylStyle = draft.vinylColor,
                    latestTrackPost = latestTrackPost,
                    effectOverlay = { intensity, modifier ->
                        if (intensity != DiscoIntensity.OFF) {
                            DiscoEffectView(intensity = intensity, modifier = modifier)
                        }
                    },
                )
            }
        }

        // Save button
        Button(
            onClick = {
                if (!draft.hasChanges(currentSelections)) {
                    onDismiss()
                    return@Button
                }
                if (!isClubMember && draft.introducesPremiumValue(currentSelections)) {
                    onNavigateToClub()
                    return@Button
                }
                onSave(draft)
            },
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.xl)
                .padding(top = CorusSpacing.md, bottom = CorusSpacing.lg)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CorusColors.Accent,
                contentColor = Color.White,
            ),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Text(stringResource(R.string.style_picker_save_changes), style = CorusFont.bodyMedium)
            }
        }
    }
}

// ── Vinyl Color Picker Page ──

@Composable
private fun VinylColorPickerPage(
    selected: VinylStyle,
    onSelect: (VinylStyle) -> Unit,
    latestTrackPost: CymbalPost?,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.style_picker_choose_vinyl),
            style = CorusFont.appTitle,
            color = CorusColors.Text,
            modifier = Modifier.padding(top = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        // Vinyl preview
        VinylPreview(
            style = selected,
            latestTrackPost = latestTrackPost,
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        // Color option cards
        Column(
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            VinylStyle.entries.forEach { style ->
                StyleOptionCard(
                    previewColor = style.previewColor,
                    previewShape = CircleShape,
                    label = style.displayName,
                    isSelected = selected == style,
                    onClick = { onSelect(style) },
                )
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.lg))
    }
}

@Composable
private fun VinylPreview(
    style: VinylStyle,
    latestTrackPost: CymbalPost?,
    modifier: Modifier = Modifier,
) {
    val vinylDrawable = remember(style) {
        when (style) {
            VinylStyle.BLACK -> R.drawable.vinyl_black
            VinylStyle.CLEAR -> R.drawable.vinyl_clear
            VinylStyle.RED_MATTE -> R.drawable.vinyl_red_matte
            VinylStyle.PURPLE -> R.drawable.vinyl_purple
            VinylStyle.WHITE -> R.drawable.vinyl_white
            VinylStyle.GOLD -> R.drawable.vinyl_gold
            VinylStyle.RED -> R.drawable.vinyl_red
            VinylStyle.BLUE -> R.drawable.vinyl_blue
            VinylStyle.GREEN -> R.drawable.vinyl_green
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val w = maxWidth
        val h = w * style.canvasRatio

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(h),
        ) {
            // Shadow
            Image(
                painter = painterResource(R.drawable.featured_shadow),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )

            // Vinyl
            Image(
                painter = painterResource(vinylDrawable),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )

            // Album art on label
            if (latestTrackPost != null) {
                val artUrl = latestTrackPost.displayImageLargeURL ?: latestTrackPost.displayImageURL
                if (artUrl != null) {
                    val labelW = w * style.labelWFrac
                    val labelH = h * style.labelHFrac
                    val labelX = w * style.labelXFrac
                    val labelY = h * style.labelYFrac

                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = labelX, y = labelY)
                            .size(width = labelW, height = labelH)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )

                    // Big album art overlay
                    val bigArtXFrac = 106f / 585f
                    val bigArtYFrac = 64f / 447f
                    val bigArtSizeFrac = 270f / 585f

                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = w * bigArtXFrac, y = h * bigArtYFrac)
                            .size(w * bigArtSizeFrac),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

// ── Vinyl Spin Toggle Page ──

@Composable
private fun VinylSpinTogglePage(
    spinning: Boolean,
    onSpinningChange: (Boolean) -> Unit,
    vinylStyle: VinylStyle,
    latestTrackPost: CymbalPost?,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.style_picker_vinyl_spin),
            style = CorusFont.appTitle,
            color = CorusColors.Text,
            modifier = Modifier.padding(top = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        // Vinyl preview (reuse existing)
        VinylPreview(
            style = vinylStyle,
            latestTrackPost = latestTrackPost,
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        // On/Off option cards
        Column(
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            SpinOptionCard(label = stringResource(R.string.style_picker_off), isSelected = !spinning) {
                onSpinningChange(false)
            }
            SpinOptionCard(label = stringResource(R.string.style_picker_on), isSelected = spinning) {
                onSpinningChange(true)
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.lg))
    }
}

@Composable
private fun SpinOptionCard(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = CorusColors.Background,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) CorusColors.Accent else CorusColors.Secondary.copy(alpha = 0.2f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (isSelected) CorusColors.Accent else CorusColors.Secondary.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ── Frame Color Picker Page ──

@Composable
private fun FrameColorPickerPage(
    selected: FrameStyle,
    onSelect: (FrameStyle) -> Unit,
    latestMoviePost: CymbalPost?,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.style_picker_choose_frame),
            style = CorusFont.appTitle,
            color = CorusColors.Text,
            modifier = Modifier.padding(top = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        // Frame preview
        FramePreview(
            style = selected,
            latestMoviePost = latestMoviePost,
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        // Color option cards
        Column(
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            FrameStyle.entries.forEach { style ->
                StyleOptionCard(
                    previewColor = style.previewColor,
                    previewShape = RoundedCornerShape(4.dp),
                    label = style.displayName,
                    isSelected = selected == style,
                    onClick = { onSelect(style) },
                )
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.lg))
    }
}

@Composable
private fun FramePreview(
    style: FrameStyle,
    latestMoviePost: CymbalPost?,
    modifier: Modifier = Modifier,
) {
    val frameDrawable = remember(style) {
        when (style) {
            FrameStyle.BLACK -> R.drawable.frame_black
            FrameStyle.WHITE -> R.drawable.frame_white
            FrameStyle.RED -> R.drawable.frame_red
            FrameStyle.BLUE -> R.drawable.frame_blue
            FrameStyle.GREEN -> R.drawable.frame_green
        }
    }

    val sectionAspect = 585f / 482f
    val posterXRatio = 207.28f / 585f
    val posterYRatio = 84.85f / 482f
    val posterWRatio = 184.98f / 585f
    val posterHRatio = 269.33f / 482f

    val glassOverlay = ImageBitmap.imageResource(R.drawable.frame_glass_overlay)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val w = maxWidth
        val h = w / sectionAspect

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(h),
        ) {
            // Frame
            Image(
                painter = painterResource(frameDrawable),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )

            // Poster
            if (latestMoviePost != null) {
                val posterUrl = latestMoviePost.displayImageLargeURL ?: latestMoviePost.displayImageURL
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = w * posterXRatio, y = h * posterYRatio)
                            .size(width = w * posterWRatio, height = h * posterHRatio),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            // Glass overlay (screen blend — matches iOS .blendMode(.screen))
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawImage(
                            image = glassOverlay,
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            blendMode = BlendMode.Screen,
                        )
                    },
            )
        }
    }
}

// ── Flair Picker Page ──

@Composable
private fun FlairPickerPage(
    selected: FlairStyle,
    onSelect: (FlairStyle) -> Unit,
    username: String,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.style_picker_choose_flair),
            style = CorusFont.appTitle,
            color = CorusColors.Text,
            modifier = Modifier.padding(top = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        // Flair preview
        FlairPreview(
            selected = selected,
            username = username,
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        // Flair option cards
        Column(
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            FlairStyle.entries.forEach { style ->
                FlairOptionCard(
                    style = style,
                    isSelected = selected == style,
                    onClick = { onSelect(style) },
                )
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.lg))
    }
}

@Composable
private fun FlairPreview(
    selected: FlairStyle,
    username: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CorusColors.Background)
            .border(1.dp, CorusColors.Divider, RoundedCornerShape(16.dp))
            .padding(vertical = CorusSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = username.ifEmpty { stringResource(R.string.style_picker_username_placeholder) },
                style = CorusFont.username,
                color = CorusColors.Text,
            )

            if (selected.usesAssetImage) {
                Spacer(modifier = Modifier.width(4.dp))
                Image(
                    painter = painterResource(R.drawable.logo_no_background),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(CorusColors.Accent),
                )
            } else if (selected.icon != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = selected.icon!!,
                    contentDescription = null,
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun FlairOptionCard(
    style: FlairStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) CorusColors.Accent else CorusColors.Secondary.copy(alpha = 0.2f)
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .background(CorusColors.Background)
            .border(borderWidth, borderColor, RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .clickable(onClick = onClick)
            .padding(CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        // Icon preview circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(CorusColors.Accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (style.usesAssetImage) {
                Image(
                    painter = painterResource(R.drawable.logo_no_background),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(CorusColors.Accent),
                )
            } else if (style.icon != null) {
                Icon(
                    imageVector = style.icon!!,
                    contentDescription = null,
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Icon(
                    imageVector = style.placeholderIcon,
                    contentDescription = null,
                    tint = CorusColors.Secondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Text(
            text = style.displayName,
            style = CorusFont.bodyMedium,
            color = CorusColors.Text,
            modifier = Modifier.weight(1f),
        )

        Icon(
            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) CorusColors.Accent else CorusColors.Secondary.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp),
        )
    }
}

// ── Shared Option Card (for vinyl + frame) ──

@Composable
private fun StyleOptionCard(
    previewColor: Color,
    previewShape: androidx.compose.ui.graphics.Shape,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) CorusColors.Accent else CorusColors.Secondary.copy(alpha = 0.2f)
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .background(CorusColors.Background)
            .border(borderWidth, borderColor, RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .clickable(onClick = onClick)
            .padding(CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        // Color swatch
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(previewShape)
                .background(previewColor)
                .border(1.dp, CorusColors.Divider, previewShape),
        )

        Text(
            text = label,
            style = CorusFont.bodyMedium,
            color = CorusColors.Text,
            modifier = Modifier.weight(1f),
        )

        Icon(
            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) CorusColors.Accent else CorusColors.Secondary.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp),
        )
    }
}

// ── Effect Toggle Page (generic for Rain/Snow/Disco) ──

@Composable
private fun <T : Enum<T>> EffectTogglePage(
    title: String,
    entries: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelOf: (T) -> String,
    vinylStyle: VinylStyle,
    latestTrackPost: CymbalPost?,
    effectOverlay: @Composable (T, Modifier) -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = CorusFont.appTitle,
            color = CorusColors.Text,
            modifier = Modifier.padding(top = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        // Vinyl preview with effect overlay
        EffectVinylPreview(
            vinylStyle = vinylStyle,
            latestTrackPost = latestTrackPost,
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
            effectOverlay = { mod -> effectOverlay(selected, mod) },
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        // Intensity option cards
        Column(
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            entries.forEach { level ->
                val isLevelSelected = selected == level
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                        .background(CorusColors.Background)
                        .border(
                            if (isLevelSelected) 2.dp else 1.dp,
                            if (isLevelSelected) CorusColors.Accent else CorusColors.Secondary.copy(alpha = 0.2f),
                            RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                        )
                        .clickable { onSelect(level) }
                        .padding(CorusSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    Text(
                        text = labelOf(level),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (isLevelSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isLevelSelected) CorusColors.Accent else CorusColors.Secondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.lg))
    }
}

@Composable
private fun EffectVinylPreview(
    vinylStyle: VinylStyle,
    latestTrackPost: CymbalPost?,
    modifier: Modifier = Modifier,
    effectOverlay: @Composable (Modifier) -> Unit,
) {
    val vinylDrawable = remember(vinylStyle) {
        when (vinylStyle) {
            VinylStyle.BLACK -> R.drawable.vinyl_black
            VinylStyle.CLEAR -> R.drawable.vinyl_clear
            VinylStyle.RED_MATTE -> R.drawable.vinyl_red_matte
            VinylStyle.PURPLE -> R.drawable.vinyl_purple
            VinylStyle.WHITE -> R.drawable.vinyl_white
            VinylStyle.GOLD -> R.drawable.vinyl_gold
            VinylStyle.RED -> R.drawable.vinyl_red
            VinylStyle.BLUE -> R.drawable.vinyl_blue
            VinylStyle.GREEN -> R.drawable.vinyl_green
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val w = maxWidth
        val h = w * vinylStyle.canvasRatio

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(h)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium)),
        ) {
            Image(
                painter = painterResource(R.drawable.featured_shadow),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            Image(
                painter = painterResource(vinylDrawable),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )

            if (latestTrackPost != null) {
                val artUrl = latestTrackPost.displayImageLargeURL ?: latestTrackPost.displayImageURL
                if (artUrl != null) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = w * vinylStyle.labelXFrac, y = h * vinylStyle.labelYFrac)
                            .size(width = w * vinylStyle.labelWFrac, height = h * vinylStyle.labelHFrac)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )

                    val bigArtXFrac = 106f / 585f
                    val bigArtYFrac = 64f / 447f
                    val bigArtSizeFrac = 270f / 585f
                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = w * bigArtXFrac, y = h * bigArtYFrac)
                            .size(w * bigArtSizeFrac),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            effectOverlay(Modifier.matchParentSize())
        }
    }
}
