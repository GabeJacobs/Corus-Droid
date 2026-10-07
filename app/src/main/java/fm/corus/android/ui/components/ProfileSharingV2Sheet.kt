package fm.corus.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.localization.CorusStrings
import fm.corus.android.service.ProfileShareAnalytics as V2Analytics
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun ProfileSharingV2Sheet(
    profile: ShareProfileSubject,
    instagramEnabled: Boolean,
    analytics: ProfileShareAnalytics?,
    renderPreview: suspend (Context, ShareProfileSubject, PreparedProfileShareArt, ProfileStoryGridSize, ProfileStoryBackground) -> Bitmap = ::renderProfileSharePreview,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { UUID.randomUUID().toString() }
    val openedAt = remember { SystemClock.elapsedRealtime() }
    var layout by remember(profile.id) { mutableStateOf(ProfileShareEligibility.defaultLayout(profile)) }
    var background by remember(profile.id) { mutableStateOf(ProfileStoryBackground.CORUS_BLUE) }
    var weather by remember { mutableStateOf(ProfileStoryWeather.NONE) }
    var art by remember { mutableStateOf<PreparedProfileShareArt?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var renderedLayout by remember { mutableStateOf<ProfileStoryGridSize?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var unlockHint by remember { mutableStateOf<String?>(null) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var errorVideo by remember { mutableStateOf<Boolean?>(null) }
    val film = profile.featuredMoviePosterUrl != null
    val count = ProfileShareEligibility.count(profile)
    val eventContext = V2Analytics.Context(session, analytics?.entryPoint ?: "unknown", if (film) "film" else layout.analyticsValue,
        background.analyticsValue, weather.analyticsValue, profile.postCount, profile.artworkUrls.size,
        !film && count >= 28, !film && count >= 16)
    val latestEventContext by rememberUpdatedState(eventContext)
    val latestAnalytics by rememberUpdatedState(analytics)
    fun log(action: String, snapshot: V2Analytics.Context = eventContext, extra: Map<String, Any> = emptyMap()) {
        analytics?.onV2Event?.invoke(V2Analytics.params(action, snapshot, extra))
    }
    DisposableEffect(session) {
        log("opened")
        log("preview_started")
        onDispose {
            latestAnalytics?.onV2Event?.invoke(V2Analytics.params("dismissed", latestEventContext,
                mapOf("duration_ms" to SystemClock.elapsedRealtime() - openedAt)))
        }
    }
    LaunchedEffect(profile.artworkUrls, profile.featuredMoviePosterUrl, retry) {
        art = null
        try { art = ProfileShareArtCache.prepare(context, profile); loadFailed = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadFailed = true; log("preview_failed", extra = mapOf("error_code" to "render_failed")) }
    }
    LaunchedEffect(art, layout, background, retry) {
        val snapshot = eventContext
        val start = if (preview == null) openedAt else SystemClock.elapsedRealtime()
        val assets = art ?: return@LaunchedEffect
        if (film && profile.featuredMoviePosterUrl.isNullOrBlank()) return@LaunchedEffect
        if (assets.slots.size < if (film) 1 else layout.artworkLimit) return@LaunchedEffect
        if (preview != null || retry > 0) log("preview_started", snapshot)
        try {
            val bitmap = renderPreview(context, profile, assets, layout, background)
            preview = bitmap; renderedLayout = layout; loadFailed = false
            log("preview_ready", snapshot, mapOf("duration_ms" to SystemClock.elapsedRealtime() - start))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadFailed = true; log("preview_failed", snapshot, mapOf("error_code" to "render_failed")) }
    }
    val ready = preview != null && renderedLayout == layout && art != null && !loadFailed
    fun shareMedia(x: Boolean) {
        if (!ready || exportJob?.isActive == true) return
        val snapshot = eventContext
        val assets = art ?: return
        val selectedLayout = layout; val selectedBackground = background; val selectedWeather = weather
        val destination = if (x) "x" else "instagram"
        val format = if (x) "x_image" else if (weather != ProfileStoryWeather.NONE) "story_video"
            else if (layout.artworkLimit == 28 && !film) "story_image" else "story_layers"
        val extra = mapOf("destination" to destination, "format" to format)
        log("share_tapped", snapshot, extra)
        analytics?.onShared?.invoke(if (x) "x" else "instagram_stories", null)
        exportJob = scope.launch {
            val start = SystemClock.elapsedRealtime()
            var exported = false
            log("export_started", snapshot, extra)
            try {
                val payload = exportProfileShare(context, profile, assets, selectedLayout, selectedBackground, selectedWeather, x)
                exported = true
                log("export_ready", snapshot, extra + ("duration_ms" to SystemClock.elapsedRealtime() - start))
                val link = profileV2ShareLink(profile, destination, background = selectedBackground)
                if (!x) {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText(context.getString(CorusStrings.share_post_clip_label), link))
                    Toast.makeText(context, context.getString(CorusStrings.share_profile_toast_instagram_link), Toast.LENGTH_LONG).show()
                }
                val accepted = handoffProfileShare(context, payload, link, x)
                log("handoff_result", snapshot, extra + ("result" to if (accepted) "accepted" else "rejected"))
                if (!accepted) errorVideo = format == "story_video"
            } catch (cancelled: CancellationException) {
                if (!exported) log("export_cancelled", snapshot, extra)
                throw cancelled
            } catch (_: Exception) {
                log(if (exported) "handoff_result" else "export_failed", snapshot,
                    extra + ("error_code" to if (exported) "handoff_failed" else if (format == "story_video") "encoding_failed" else "render_failed"))
                errorVideo = format == "story_video"
            } finally { exportJob = null }
        }
    }
    fun shareLink(destination: String) {
        val extra = mapOf("destination" to destination, "format" to "link")
        log("share_tapped", extra = extra); analytics?.onShared?.invoke(destination, null)
        val link = profileV2ShareLink(profile, destination, background = background)
        try {
            if (destination == "copy_link") {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Corus", link))
                Toast.makeText(context, context.getString(CorusStrings.thread_copied), Toast.LENGTH_SHORT).show()
                log("handoff_result", extra = extra + ("result" to "copied"))
            } else {
                val intent = if (destination == "whatsapp") Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/").buildUpon().appendQueryParameter("text", link).build())
                else Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), null)
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                log("handoff_result", extra = extra + ("result" to "accepted"))
            }
        } catch (_: Exception) { log("handoff_result", extra = extra + ("result" to "rejected")) }
    }
    val previewWidth = ((LocalConfiguration.current.screenHeightDp - 330) * 9f / 16)
        .coerceIn(144f, 260f).dp
    val showInstagram = instagramEnabled && isInstagramAvailable(context)
    val showWhatsApp = isWhatsAppAvailable(context)
    val destinationCount = 3 + (if (showInstagram) 1 else 0) + (if (showWhatsApp) 1 else 0)
    val destinationSpacing = ((LocalConfiguration.current.screenWidthDp - 32 - destinationCount * 64f) / (destinationCount - 1)).coerceIn(4f, 20f).dp
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ShareSheetDragIndicator()
        Box(Modifier.align(Alignment.CenterHorizontally).width(previewWidth).aspectRatio(9f / 16)
            .clip(RoundedCornerShape(14.dp)).background(if (preview != null) Color(background.color) else CorusColors.CardBackground)) {
            // Keep the existing composition mounted while the next layout renders.
            // `ready` only gates exports; it must not hide the last visible preview.
            if (preview != null) {
                Image(preview!!.asImageBitmap(), stringResource(CorusStrings.instagram_v2_preview), Modifier.fillMaxSize())
                ProfileStoryWeatherOverlay(weather)
                if (loadFailed) TextButton(onClick = { retry++ }, modifier = Modifier.align(Alignment.BottomCenter)) {
                    Text(stringResource(CorusStrings.common_retry))
                }
            } else Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!loadFailed) CircularProgressIndicator(Modifier.size(24.dp), color = CorusColors.Accent, strokeWidth = 2.dp)
                else TextButton(onClick = { retry++ }) { Text(stringResource(CorusStrings.common_retry)) }
                Text(stringResource(if (layout.artworkLimit == 28) CorusStrings.profile_share_loading_full else CorusStrings.profile_share_loading_grid),
                    style = CorusFont.caption, color = CorusColors.Secondary, modifier = Modifier.padding(12.dp))
            }
        }
        Text(stringResource(CorusStrings.instagram_v2_preview_label), style = CorusFont.caption, color = CorusColors.Secondary,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 10.dp))
        if (!film) {
            Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(40.dp).clip(CircleShape).background(CorusColors.CardBackground)) {
                ProfileStoryGridSize.entries.sortedByDescending { it.artworkLimit }.forEach { option ->
                    val available = ProfileShareEligibility.available(profile, option)
                    Row(Modifier.weight(1f).fillMaxHeight().padding(2.dp).clip(CircleShape)
                        .background(if (layout == option) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .semantics { selected = layout == option }
                        .clickable {
                            if (!available) {
                                val needed = option.artworkLimit - count
                                unlockHint = profileUnlockText(context, option, needed)
                                analytics?.onDiscovery?.invoke(V2Analytics.discoveryParams("locked_layout_tapped", session,
                                    analytics.entryPoint, option.analyticsValue, count, needed))
                            } else {
                                unlockHint = null
                                if (layout != option) { layout = option; log("grid_changed", eventContext.copy(layout = option.analyticsValue)) }
                            }
                        }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (!available) Icon(Icons.Default.Lock, null, Modifier.size(12.dp).padding(end = 2.dp), tint = CorusColors.Secondary)
                        Text(when (option) {
                            ProfileStoryGridSize.FULL -> stringResource(CorusStrings.profile_share_grid_full)
                            ProfileStoryGridSize.EXTRA_LARGE -> stringResource(CorusStrings.profile_share_grid_extra_large)
                            ProfileStoryGridSize.LARGE -> stringResource(CorusStrings.profile_share_grid_large)
                            ProfileStoryGridSize.STANDARD -> stringResource(CorusStrings.profile_share_grid_standard)
                        },
                            style = CorusFont.bodyMedium, color = if (available) CorusColors.Text else CorusColors.Secondary)
                    }
                }
            }
            unlockHint?.let { Text(it, style = CorusFont.caption, color = CorusColors.Secondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            profileShareBackgroundChoices.forEach { option ->
                val label = stringResource(profileBackgroundLabel(option))
                Column(Modifier.widthIn(min = 54.dp).semantics { selected = background == option }
                    .clickable {
                        if (background != option) {
                            background = option
                            log("background_changed", eventContext.copy(background = option.analyticsValue))
                            // Keep the established theme event for historical
                            // reports as well as the separate V2 control funnel.
                            when (option) {
                                ProfileStoryBackground.LIGHT -> analytics?.onThemeChanged?.invoke(ShareCardTheme.LIGHT)
                                ProfileStoryBackground.DARK -> analytics?.onThemeChanged?.invoke(ShareCardTheme.DARK)
                                else -> analytics?.onBackgroundChanged?.invoke(option.analyticsValue)
                            }
                        }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(40.dp).border(2.dp, if (background == option) CorusColors.Accent else Color.Transparent, CircleShape)
                        .padding(4.dp).clip(CircleShape).background(Color(option.color)).border(.5.dp, CorusColors.Divider, CircleShape))
                    Text(label, style = CorusFont.captionMedium, color = CorusColors.Text, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        Row(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ProfileStoryWeather.RAIN, ProfileStoryWeather.SNOW).forEach { option ->
                val selected = weather == option
                val label = stringResource(if (option == ProfileStoryWeather.RAIN) CorusStrings.profile_share_effect_rain else CorusStrings.profile_share_effect_snow)
                val state = stringResource(if (selected) CorusStrings.profile_share_effect_on else CorusStrings.profile_share_effect_off)
                Row(Modifier.clip(CircleShape).background(if (selected) CorusColors.Accent else CorusColors.CardBackground)
                    .semantics { this.selected = selected; stateDescription = state }
                    .clickable { weather = weather.toggling(option); log("effect_changed", eventContext.copy(effect = weather.analyticsValue)) }
                    .padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (option == ProfileStoryWeather.RAIN) Icons.Default.Grain else Icons.Default.AcUnit, null, Modifier.size(18.dp),
                        tint = if (selected) Color.White else CorusColors.Secondary)
                    Text(label, style = CorusFont.captionMedium, color = if (selected) Color.White else CorusColors.Secondary)
                }
            }
        }
        HorizontalDivider(color = CorusColors.Divider)
        LazyRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(destinationSpacing)) {
            if (showInstagram) item { InstagramShareButton(!ready || exportJob?.isActive == true) { shareMedia(false) } }
            if (showWhatsApp) item { ShareActionButton(stringResource(CorusStrings.share_post_whatsapp), painter = painterResource(R.drawable.whatsapp_logo),
                backgroundColor = Color(0xff25d366), iconTint = Color.White) { shareLink("whatsapp") } }
            item { XShareButton(enabled = ready && exportJob?.isActive != true) { shareMedia(true) } }
            item { ShareActionButton(stringResource(CorusStrings.share_post_share_link), icon = Icons.Default.Share) { shareLink("share_link") } }
            item { ShareActionButton(stringResource(CorusStrings.post_menu_copy_link), icon = Icons.Default.ContentCopy) { shareLink("copy_link") } }
        }
    }
    if (exportJob?.isActive == true) AlertDialog(onDismissRequest = { exportJob?.cancel() },
        text = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(CorusStrings.profile_share_preparing_preview), Modifier.padding(top = 12.dp))
        } }, confirmButton = { TextButton(onClick = { exportJob?.cancel() }) { Text(stringResource(CorusStrings.common_cancel)) } })
    errorVideo?.let { video -> AlertDialog(onDismissRequest = { errorVideo = null },
        title = { Text(stringResource(if (video) CorusStrings.profile_share_video_error_title else CorusStrings.profile_share_image_error_title)) },
        text = { Text(stringResource(if (video) CorusStrings.profile_share_video_error_body else CorusStrings.profile_share_image_error_body)) },
        confirmButton = { TextButton(onClick = { errorVideo = null }) { Text(stringResource(CorusStrings.common_ok)) } }) }
}

private suspend fun renderProfileSharePreview(context: Context, profile: ShareProfileSubject, assets: PreparedProfileShareArt,
    layout: ProfileStoryGridSize, background: ProfileStoryBackground): Bitmap = withContext(Dispatchers.Default) {
    renderProfileShareStory(context, profile, assets, layout, background,
        transparent = layout != ProfileStoryGridSize.FULL || profile.featuredMoviePosterUrl != null)
}

@Composable private fun ProfileStoryWeatherOverlay(weather: ProfileStoryWeather) {
    if (weather == ProfileStoryWeather.NONE) return
    val scene = remember(weather) { ProfileStoryWeatherScene(weather) }
    var revision by remember(weather) { mutableIntStateOf(0) }
    LaunchedEffect(scene) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L) scene.advance(((now - last) / 1_000_000_000f).coerceAtMost(.05f))
            last = now; revision++
        }
    }
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        revision
        scene.draw(drawContext.canvas.nativeCanvas, size.width, size.height)
    }
}

internal fun profileBackgroundLabel(option: ProfileStoryBackground) = when (option) {
    ProfileStoryBackground.CORUS_BLUE, ProfileStoryBackground.INVITATION_BLUE -> CorusStrings.profile_share_background_blue
    ProfileStoryBackground.LIGHT -> CorusStrings.profile_share_background_white
    ProfileStoryBackground.DARK -> CorusStrings.profile_share_background_black
    ProfileStoryBackground.PURPLE -> CorusStrings.profile_share_background_purple
    ProfileStoryBackground.ROSE -> CorusStrings.profile_share_background_rose
    ProfileStoryBackground.ORANGE -> CorusStrings.profile_share_background_orange
    ProfileStoryBackground.GREEN -> CorusStrings.profile_share_background_green
}

internal fun profileUnlockText(context: Context, layout: ProfileStoryGridSize?, needed: Int): String {
    val key = when (layout?.artworkLimit) {
        28 -> if (needed == 1) CorusStrings.profile_share_unlock_full_one else CorusStrings.profile_share_unlock_full_many
        25 -> if (needed == 1) CorusStrings.profile_share_unlock_extra_large_one else CorusStrings.profile_share_unlock_extra_large_many
        16 -> if (needed == 1) CorusStrings.profile_share_unlock_large_one else CorusStrings.profile_share_unlock_large_many
        else -> if (needed == 1) CorusStrings.profile_share_unlock_collage_one else CorusStrings.profile_share_unlock_collage_many
    }
    return context.getString(key, needed)
}

@Composable internal fun ProfileCollageTeaser(profile: ShareProfileSubject, analytics: ProfileShareAnalytics?) {
    val context = LocalContext.current
    val count = ProfileShareEligibility.count(profile).coerceIn(0, 8)
    val session = remember { UUID.randomUUID().toString() }
    LaunchedEffect(session) { analytics?.onDiscovery?.invoke(V2Analytics.discoveryParams("teaser_shown", session,
        analytics.entryPoint, "3x3", count, 9 - count)) }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), shape = RoundedCornerShape(12.dp), color = CorusColors.Accent.copy(alpha = .07f)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.GridOn, null, Modifier.size(26.dp), tint = CorusColors.Accent)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(CorusStrings.profile_share_collage_teaser_title), style = CorusFont.bodyMedium, color = CorusColors.Text)
                val progress = stringResource(CorusStrings.profile_share_collage_progress, count, 9)
                LinearProgressIndicator(progress = { count / 9f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                    .semantics { contentDescription = progress }, color = CorusColors.Accent,
                    trackColor = CorusColors.Accent.copy(alpha = .2f), drawStopIndicator = {})
                Text(profileUnlockText(context, null, 9 - count), style = CorusFont.caption, color = CorusColors.Secondary)
            }
        }
    }
}
