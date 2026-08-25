package fm.corus.android.ui.screens.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.DiscoEffectGate
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
import fm.corus.android.ui.theme.LocalCorusDarkTheme

data class StyleSelections(
    val vinylColor: VinylStyle = VinylStyle.BLACK,
    val frameColor: FrameStyle = FrameStyle.BLACK,
    val profileFlair: FlairStyle = FlairStyle.CHECKMARK,
    val rainEffect: RainIntensity = RainIntensity.OFF,
    val snowEffect: SnowIntensity = SnowIntensity.OFF,
    val discoEffect: DiscoIntensity = DiscoIntensity.OFF,
) {
    fun hasChanges(from: StyleSelections): Boolean =
        vinylColor != from.vinylColor ||
                frameColor != from.frameColor ||
                profileFlair != from.profileFlair ||
                rainEffect != from.rainEffect ||
                snowEffect != from.snowEffect ||
                discoEffect != from.discoEffect

    fun changedFields(from: StyleSelections): Map<String, Any> {
        val fields = mutableMapOf<String, Any>()
        if (vinylColor != from.vinylColor) fields["vinylColor"] = vinylColor.value
        if (frameColor != from.frameColor) fields["frameColor"] = frameColor.value
        if (profileFlair != from.profileFlair) fields["profileFlair"] = profileFlair.value
        if (rainEffect != from.rainEffect) fields["rainEffect"] = rainEffect.value
        if (snowEffect != from.snowEffect) fields["snowEffect"] = snowEffect.value
        if (discoEffect != from.discoEffect) fields["discoEffect"] = discoEffect.value
        return fields
    }

    val hasNonDefaultValues: Boolean
        get() = vinylColor != VinylStyle.BLACK ||
                frameColor != FrameStyle.BLACK ||
                profileFlair != FlairStyle.CHECKMARK ||
                rainEffect != RainIntensity.OFF ||
                snowEffect != SnowIntensity.OFF ||
                discoEffect != DiscoIntensity.OFF

    fun introducesPremiumValue(from: StyleSelections): Boolean =
        (vinylColor != from.vinylColor && vinylColor != VinylStyle.BLACK) ||
                (frameColor != from.frameColor && frameColor != FrameStyle.BLACK) ||
                (profileFlair != from.profileFlair && profileFlair != FlairStyle.CHECKMARK) ||
                (rainEffect != from.rainEffect && rainEffect != RainIntensity.OFF) ||
                (snowEffect != from.snowEffect && snowEffect != SnowIntensity.OFF) ||
                (discoEffect != from.discoEffect && discoEffect != DiscoIntensity.OFF)
}

private enum class StylePage { VINYL, FRAME, FLAIR, RAIN, SNOW, DISCO }

/**
 * Whether the staff-only "Corus" flair (`FlairStyle.CORUS_LOGO`) should appear
 * in the picker. Shown when the viewer is staff, when the open flag is on
 * (today's default), or when it's the current selection — the last clause keeps
 * existing holders from seeing an empty selection during the phase-out. Mirrors
 * the web implementation. Display/rendering of the flair is unaffected by this.
 */
internal fun shouldShowCorusFlairOption(
    isStaff: Boolean,
    corusFlairOpen: Boolean,
    selected: FlairStyle,
): Boolean = isStaff || corusFlairOpen || selected == FlairStyle.CORUS_LOGO

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
    stylePack1Enabled: Boolean,
    isStaff: Boolean,
    corusFlairOpen: Boolean,
    isSaving: Boolean,
    initialPage: Int = 0,
    onSave: (StyleSelections) -> Unit,
    onNavigateToClub: () -> Unit,
    onDismiss: () -> Unit,
    onPageChange: (Int) -> Unit = {},
) {
    var draft by remember { mutableStateOf(currentSelections) }

    val pages = remember(
        hasTrackPosts,
        hasMoviePosts,
        stylePack1Enabled,
        currentSelections.discoEffect,
    ) {
        buildList {
            if (hasTrackPosts) add(StylePage.VINYL)
            if (hasMoviePosts) add(StylePage.FRAME)
            add(StylePage.FLAIR)
            if (hasTrackPosts) {
                add(StylePage.RAIN)
                add(StylePage.SNOW)
                if (DiscoEffectGate.isPageVisible(stylePack1Enabled, currentSelections.discoEffect)) {
                    add(StylePage.DISCO)
                }
            }
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
        pageCount = { pages.size },
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChange(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            // ModalBottomSheet treats leftover vertical nested-scroll as
            // "drag the sheet." A slightly downward page-swipe then slides
            // the whole picker instead of changing pages. Eat that leftover
            // here so the pager and option lists keep the gesture. Dismiss
            // stays on the close button and the scrim.
            .nestedScroll(ConsumeSheetDragAfterChildScroll),
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
            beyondViewportPageCount = 1,
        ) { pageIndex ->
            when (pages[pageIndex]) {
                StylePage.VINYL -> VinylColorPickerPage(
                    selected = draft.vinylColor,
                    onSelect = { draft = draft.copy(vinylColor = it) },
                    latestTrackPost = latestTrackPost,
                    stylePack1Enabled = stylePack1Enabled,
                )
                StylePage.FRAME -> FrameColorPickerPage(
                    selected = draft.frameColor,
                    onSelect = { draft = draft.copy(frameColor = it) },
                    latestMoviePost = latestMoviePost,
                    stylePack1Enabled = stylePack1Enabled,
                )
                StylePage.FLAIR -> FlairPickerPage(
                    selected = draft.profileFlair,
                    onSelect = { draft = draft.copy(profileFlair = it) },
                    username = username,
                    isStaff = isStaff,
                    corusFlairOpen = corusFlairOpen,
                    stylePack1Enabled = stylePack1Enabled,
                )
                StylePage.RAIN -> EffectTogglePage(
                    title = stringResource(R.string.style_picker_rain_effect),
                    entries = RainIntensity.entries,
                    selected = draft.rainEffect,
                    onSelect = { newValue ->
                        draft = if (newValue != RainIntensity.OFF) {
                            draft.copy(
                                rainEffect = newValue,
                                snowEffect = SnowIntensity.OFF,
                                discoEffect = DiscoIntensity.OFF,
                            )
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
                            draft.copy(
                                snowEffect = newValue,
                                rainEffect = RainIntensity.OFF,
                                discoEffect = DiscoIntensity.OFF,
                            )
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
                    onSelect = { newValue ->
                        draft = if (newValue != DiscoIntensity.OFF) {
                            draft.copy(
                                discoEffect = newValue,
                                rainEffect = RainIntensity.OFF,
                                snowEffect = SnowIntensity.OFF,
                            )
                        } else {
                            draft.copy(discoEffect = newValue)
                        }
                    },
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
// Picker preview stays on the pack-1 canvas so the big cover never jumps
// when switching vinyls. The hole still follows each style.
/**
 * ModalBottomSheet's nested-scroll connection applies any leftover UserInput
 * delta to the sheet. HorizontalPager does not consume Y, so a normal
 * page-swipe (never perfectly level) becomes a sheet drag.
 */
private val ConsumeSheetDragAfterChildScroll = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return if (source == NestedScrollSource.UserInput) {
            Offset(0f, available.y)
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        return Velocity(0f, available.y)
    }
}

private const val PICKER_CANVAS_RATIO = 440f / 582f
private const val PICKER_ART_X_FRAC = 105f / 582f
private const val PICKER_ART_Y_FRAC = 57f / 440f
private const val PICKER_ART_SIZE_FRAC = 270f / 582f

@Composable
private fun VinylColorPickerPage(
    selected: VinylStyle,
    onSelect: (VinylStyle) -> Unit,
    latestTrackPost: CymbalPost?,
    stylePack1Enabled: Boolean,
) {
    val visibleStyles = remember(stylePack1Enabled) {
        VinylStyle.entries.filter { !it.requiresStylePack1 || stylePack1Enabled }
    }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.style_picker_choose_vinyl),
            style = CorusFont.appTitle,
            color = CorusColors.Text,
            modifier = Modifier.padding(top = CorusSpacing.xl, bottom = CorusSpacing.md),
        )

        VinylPreview(
            style = selected,
            latestTrackPost = latestTrackPost,
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.md))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = CorusSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            visibleStyles.forEach { style ->
                StyleOptionCard(
                    previewColor = style.previewColor,
                    previewShape = CircleShape,
                    label = style.displayName,
                    isSelected = selected == style,
                    onClick = { onSelect(style) },
                    previewImageRes = vinylDrawableRes(style),
                    previewCanvasW = style.canvasW,
                    previewCanvasH = style.canvasH,
                    previewCropX = style.swatchCropX,
                    previewCropY = style.swatchCropY,
                    previewCropS = style.swatchCropS,
                )
            }
            Spacer(modifier = Modifier.height(CorusSpacing.lg))
        }
    }
}

@Composable
private fun VinylPreview(
    style: VinylStyle,
    latestTrackPost: CymbalPost?,
    modifier: Modifier = Modifier,
    effectOverlay: @Composable (Modifier) -> Unit = {},
) {
    val vinylDrawable = remember(style) { vinylDrawableRes(style) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val w = maxWidth
        val h = w * PICKER_CANVAS_RATIO

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(h)
                .clipToBounds(),
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

                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = w * PICKER_ART_X_FRAC, y = h * PICKER_ART_Y_FRAC)
                            .size(w * PICKER_ART_SIZE_FRAC),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            effectOverlay(Modifier.matchParentSize())
        }
    }
}

// ── Frame Color Picker Page ──

@Composable
private fun FrameColorPickerPage(
    selected: FrameStyle,
    onSelect: (FrameStyle) -> Unit,
    latestMoviePost: CymbalPost?,
    stylePack1Enabled: Boolean,
) {
    val visibleStyles = remember(stylePack1Enabled) {
        FrameStyle.entries.filter { !it.requiresStylePack1 || stylePack1Enabled }
    }
    val scrollState = rememberScrollState()
    val isDark = LocalCorusDarkTheme.current

    // Pin the preview like the vinyl page. A full-page verticalScroll here
    // steals the HorizontalPager swipe (sheet + list both want the drag),
    // which is what made film → vinyl feel delayed.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CorusColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.style_picker_choose_frame),
            style = CorusFont.appTitle,
            color = CorusColors.Text,
            modifier = Modifier.padding(top = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        FramePreview(
            style = selected,
            latestMoviePost = latestMoviePost,
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = CorusSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            visibleStyles.forEach { style ->
                StyleOptionCard(
                    previewColor = style.previewColor,
                    previewShape = RoundedCornerShape(4.dp),
                    label = style.displayName,
                    isSelected = selected == style,
                    onClick = { onSelect(style) },
                    previewMarqueePattern = style.usesTextureSwatch,
                )
            }
            Spacer(modifier = Modifier.height(CorusSpacing.lg))
        }
    }
}

@Composable
private fun FramePreview(
    style: FrameStyle,
    latestMoviePost: CymbalPost?,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalCorusDarkTheme.current
    val frameDrawable = remember(style, isDark) {
        when (style) {
            FrameStyle.BLACK -> if (isDark) R.drawable.frame_black_dark else R.drawable.frame_black
            FrameStyle.WHITE -> if (isDark) R.drawable.frame_white_dark else R.drawable.frame_white
            FrameStyle.RED -> if (isDark) R.drawable.frame_red_dark else R.drawable.frame_red
            FrameStyle.BLUE -> if (isDark) R.drawable.frame_blue_dark else R.drawable.frame_blue
            FrameStyle.GREEN -> if (isDark) R.drawable.frame_green_dark else R.drawable.frame_green
            FrameStyle.THEATER -> if (isDark) R.drawable.frame_theater_dark else R.drawable.frame_theater
        }
    }

    val sectionAspect = 585f / 482f
    val posterXRatio = style.posterXFrac
    val posterYRatio = style.posterYFrac
    val posterWRatio = style.posterWFrac
    val posterHRatio = style.posterHFrac

    val glassOverlay = if (style.usesGlassOverlay) {
        ImageBitmap.imageResource(R.drawable.frame_glass_overlay)
    } else {
        null
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val w = maxWidth
        val h = w / sectionAspect

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(h)
                // Screen-blend glass composites against the destination. During
                // scroll the pager promotes this to an offscreen layer whose
                // default is black — the dark rectangle behind the frame.
                // Paint the sheet color first so the blend stays on white.
                .background(CorusColors.Background)
                .clipToBounds(),
        ) {
            // Frame
            Image(
                painter = painterResource(frameDrawable),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (style.usesStandingShadow) {
                            Modifier.drawBehind {
                                drawOval(
                                    color = Color.Black.copy(alpha = 0.22f),
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        size.width * 0.24f,
                                        size.height * 0.74f,
                                    ),
                                    size = androidx.compose.ui.geometry.Size(
                                        size.width * 0.52f,
                                        size.height * 0.16f,
                                    ),
                                )
                                drawOval(
                                    color = Color.Black.copy(alpha = 0.32f),
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        size.width * 0.30f,
                                        size.height * 0.70f,
                                    ),
                                    size = androidx.compose.ui.geometry.Size(
                                        size.width * 0.40f,
                                        size.height * 0.12f,
                                    ),
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
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

            if (glassOverlay != null) {
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
}

// ── Flair Picker Page ──

@Composable
private fun FlairPickerPage(
    selected: FlairStyle,
    onSelect: (FlairStyle) -> Unit,
    username: String,
    isStaff: Boolean,
    corusFlairOpen: Boolean,
    stylePack1Enabled: Boolean,
) {
    val scrollState = rememberScrollState()

    // Restrict the staff-only "Corus" flair (see shouldShowCorusFlairOption).
    // Recomputed on selection change so an existing holder who switches away
    // can't switch back once the option is otherwise gated off.
    // Flairs gated behind style_pack_1 are hidden until that flag is on — same
    // rule the vinyl/frame pickers use.
    val showCorus = shouldShowCorusFlairOption(isStaff, corusFlairOpen, selected)
    val visibleFlairs = FlairStyle.entries.filter { flair ->
        (flair != FlairStyle.CORUS_LOGO || showCorus) &&
            (!flair.requiresStylePack1 || stylePack1Enabled)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.style_picker_choose_flair),
            style = CorusFont.appTitle,
            color = CorusColors.Text,
            modifier = Modifier.padding(top = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        FlairPreview(
            selected = selected,
            username = username,
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = CorusSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            visibleFlairs.forEach { style ->
                FlairOptionCard(
                    style = style,
                    isSelected = selected == style,
                    onClick = { onSelect(style) },
                )
            }
            Spacer(modifier = Modifier.height(CorusSpacing.lg))
        }
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
    previewImageRes: Int? = null,
    previewCanvasW: Float = 582f,
    previewCanvasH: Float = 440f,
    previewCropX: Float = 422f,
    previewCropY: Float = 145f,
    previewCropS: Float = 70f,
    previewMarqueePattern: Boolean = false,
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
        // Color swatch. Vinyls crop the disc face (right of the inner circle)
        // instead of a flat hex, so grooves and finish show.
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(previewShape)
                .background(previewColor)
                .border(1.dp, CorusColors.Divider, previewShape),
        ) {
            if (previewMarqueePattern) {
                MarqueePatternSwatch()
            } else if (previewImageRes != null) {
                VinylSwatchImage(
                    resId = previewImageRes,
                    canvasW = previewCanvasW,
                    canvasH = previewCanvasH,
                    cropX = previewCropX,
                    cropY = previewCropY,
                    cropS = previewCropS,
                )
            }
        }

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

@Composable
private fun MarqueePatternSwatch() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        fun orb(radius: Float, stops: Array<Pair<Float, Color>>) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = stops,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        drawRect(color = Color.Black)
        orb(
            size.minDimension * 0.48f,
            arrayOf(
                0f to Color(1.0f, 0.45f, 0.06f).copy(alpha = 0.28f),
                1f to Color.Transparent,
            ),
        )
        orb(
            size.minDimension * 0.30f,
            arrayOf(
                0f to Color(1.0f, 0.78f, 0.28f),
                0.52f to Color(1.0f, 0.46f, 0.06f),
                1f to Color(0.45f, 0.12f, 0.0f, 0f),
            ),
        )
        orb(
            size.minDimension * 0.13f,
            arrayOf(
                0f to Color.White,
                0.4f to Color(1.0f, 0.96f, 0.72f),
                1f to Color.Transparent,
            ),
        )
    }
}

/** Disc-face crop, decoded once at ~256px so pager swipes don't redraw 2k vinyls. */
@Composable
private fun VinylSwatchImage(
    resId: Int,
    canvasW: Float,
    canvasH: Float,
    cropX: Float,
    cropY: Float,
    cropS: Float,
) {
    val resources = LocalContext.current.resources
    val bitmap = remember(resId, canvasW, canvasH, cropX, cropY, cropS) {
        cropVinylSwatch(resources, resId, canvasW, canvasH, cropX, cropY, cropS)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun cropVinylSwatch(
    resources: android.content.res.Resources,
    resId: Int,
    canvasW: Float,
    canvasH: Float,
    cropX: Float,
    cropY: Float,
    cropS: Float,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(resources, resId, bounds)
    val srcW = bounds.outWidth.coerceAtLeast(1)
    val sample = (srcW / 256).coerceAtLeast(1)
    val decoded = BitmapFactory.decodeResource(
        resources,
        resId,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    val x = ((cropX / canvasW) * decoded.width).toInt().coerceIn(0, decoded.width - 1)
    val y = ((cropY / canvasH) * decoded.height).toInt().coerceIn(0, decoded.height - 1)
    val side = ((cropS / canvasW) * decoded.width).toInt()
        .coerceAtLeast(1)
        .coerceAtMost(minOf(decoded.width - x, decoded.height - y))
    val cropped = Bitmap.createBitmap(decoded, x, y, side, side)
    if (cropped != decoded) decoded.recycle()
    return cropped
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
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = CorusFont.appTitle,
            color = CorusColors.Text,
            modifier = Modifier.padding(top = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        VinylPreview(
            style = vinylStyle,
            latestTrackPost = latestTrackPost,
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
            effectOverlay = { mod -> effectOverlay(selected, mod) },
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = CorusSpacing.xl),
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
            Spacer(modifier = Modifier.height(CorusSpacing.lg))
        }
    }
}

private fun vinylDrawableRes(style: VinylStyle): Int = when (style) {
    VinylStyle.BLACK -> R.drawable.vinyl_black
    VinylStyle.CLEAR -> R.drawable.vinyl_clear
    VinylStyle.RED_MATTE -> R.drawable.vinyl_red_matte
    VinylStyle.PURPLE -> R.drawable.vinyl_purple
    VinylStyle.WHITE -> R.drawable.vinyl_white
    VinylStyle.GOLD -> R.drawable.vinyl_gold
    VinylStyle.BLUE -> R.drawable.vinyl_blue
    VinylStyle.GREEN -> R.drawable.vinyl_green
    VinylStyle.PINK -> R.drawable.vinyl_pink
    VinylStyle.ORANGE -> R.drawable.vinyl_orange
    VinylStyle.YELLOW -> R.drawable.vinyl_yellow
    VinylStyle.PINK_MATTE -> R.drawable.vinyl_pink_matte
    VinylStyle.LIME -> R.drawable.vinyl_lime
    VinylStyle.PURPLE_TIE_DYE -> R.drawable.vinyl_purple_tie_dye
    VinylStyle.BLUE_TIE_DYE -> R.drawable.vinyl_blue_tie_dye
    VinylStyle.ORANGE_TIE_DYE -> R.drawable.vinyl_orange_tie_dye
    VinylStyle.ICY_BLUE -> R.drawable.vinyl_icy_blue
    VinylStyle.GALAXY -> R.drawable.vinyl_galaxy
    VinylStyle.PEACH -> R.drawable.vinyl_peach
    VinylStyle.LAVENDER -> R.drawable.vinyl_lavender
    VinylStyle.BLOOD_RED -> R.drawable.vinyl_blood_red
}
