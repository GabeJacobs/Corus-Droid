@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package fm.corus.android.ui.screens.search

import fm.corus.android.ui.components.CorusModalBottomSheet
import android.content.Intent
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import fm.corus.android.R
import fm.corus.android.data.repository.ConcertShow
import fm.corus.android.domain.DestinationResolvingOverlay
import fm.corus.android.ui.navigation.ArtistPageRoute
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.dismissKeyboardOnDownwardDrag
import fm.corus.android.ui.components.hideKeyboardOnScroll
import fm.corus.android.ui.components.rememberGuardedSheetState
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.ImmersiveBarHeight
import fm.corus.android.ui.components.ImmersiveCollapsingBar
import fm.corus.android.ui.components.ImmersiveCoverBackdrop
import fm.corus.android.ui.components.ImmersiveExtendUnderStatusBar
import fm.corus.android.ui.components.ImmersiveStatusBarIcons
import fm.corus.android.ui.components.currentStatusBarTopPx
import fm.corus.android.ui.components.extendIntoStatusBar
import fm.corus.android.ui.components.immersiveCollapseProgress
import fm.corus.android.ui.components.contentHazeSource
import fm.corus.android.ui.components.ShareConcertSubject
import fm.corus.android.ui.components.ShareMediaSheet
import fm.corus.android.ui.components.ShareMediaSubject
import fm.corus.android.ui.theme.CorusSystemBars
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.bottomSheetMaxHeight
import dev.chrisbanes.haze.hazeSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import com.valentinilk.shimmer.shimmer


@Composable
fun ConcertPreview(
    onSeeAll: () -> Unit, onConcert: (String) -> Unit, onPostMusic: () -> Unit,
    vm: ConcertsViewModel = hiltViewModel(),
) {
    if (!vm.enabled) return
    val appLocale = LocalConfiguration.current.locales[0]
    val cityId by vm.cityId.collectAsState()
    val discovery by vm.discoveryFilter.collectAsState()
    // Capture one page value for this composition; LazyRow evaluates its content later.
    val page = vm.previewPage.collectAsState().value
    val loading by vm.previewLoading.collectAsState()
    val error by vm.previewError.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm.enabled, cityId, discovery) { vm.preview() }
    val browseAll = { vm.logPreviewEmptyAction("browse_all", filter = "all"); vm.rememberDiscoveryTab("all"); onSeeAll() }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        SectionHeader(
            icon = "ticket",
            title = stringResource(R.string.concerts_title).uppercase(appLocale),
            showSeeAll = true,
            onSeeAll = { vm.logPreviewSeeAll(); onSeeAll() },
        )
        when {
            page == null && !error -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(2) { ConcertPreviewSkeleton() }
            }
            error && page == null -> ConcertPreviewMessage(Icons.Default.Refresh, R.string.concert_load_error, null, R.string.concert_retry,
                { vm.logPreviewEmptyAction("retry"); scope.launch { vm.preview(true) } })
            page?.needsCity == true -> ConcertPreviewMessage(Icons.Default.LocationOn, R.string.concert_find_nearby, R.string.concert_choose_location_message, R.string.concert_choose_city,
                { vm.logPreviewEmptyAction("needs_city"); onSeeAll() })
            discovery == "forYou" && page?.hasPostedArtists == false -> ConcertPreviewMessage(
                Icons.Default.MusicNote, R.string.concert_make_personal, R.string.concert_personal_message, R.string.concert_post_music,
                { vm.logPreviewEmptyAction("post_music"); onPostMusic() },
                R.string.concert_view_all, browseAll)
            page?.shows.isNullOrEmpty() -> ConcertPreviewMessage(
                if ((page?.nearbyTotal ?: 0) == 0) Icons.Default.CalendarMonth else Icons.Default.AutoAwesome,
                if ((page?.nearbyTotal ?: 0) == 0) R.string.concert_no_shows else R.string.concert_no_matches,
                if ((page?.nearbyTotal ?: 0) == 0) null else R.string.concert_keep_posting_message,
                R.string.concert_view_all, browseAll)
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(page?.shows.orEmpty(), key = { it.id }) { show ->
                    val artist = show.matchedArtist ?: show.lineup.firstOrNull() ?: show.title
                    Surface(Modifier.width(218.dp).height(170.dp).clickable { vm.select(show, "search_music_preview"); onConcert(show.id) }, shape = RoundedCornerShape(18.dp)) {
                        Box(Modifier.fillMaxSize()) {
                            AsyncImage(show.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha = .32f), androidx.compose.ui.graphics.Color.Black.copy(alpha = .84f)))))
                            Column(Modifier.fillMaxSize().padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        runCatching { LocalDate.parse(show.date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale)).uppercase(appLocale) }.getOrDefault(show.date),
                                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = .78f),
                                        style = CorusFont.custom(700, 11),
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                if (show.suggestionSource == "tasteMatches") Text(stringResource(R.string.concert_you_might_like), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .86f), style = CorusFont.custom(600, 11))
                                Text(artist, maxLines = 2, overflow = TextOverflow.Ellipsis, color = androidx.compose.ui.graphics.Color.White, style = CorusFont.custom(700, 22))
                                if (artist != show.title && show.title.isNotBlank()) Text(show.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .76f), style = CorusFont.caption)
                                Spacer(Modifier.height(10.dp))
                                Text(show.venue, maxLines = 1, overflow = TextOverflow.Ellipsis, color = androidx.compose.ui.graphics.Color.White, style = CorusFont.custom(600, 13))
                                Text(show.city, maxLines = 1, overflow = TextOverflow.Ellipsis, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .72f), style = CorusFont.caption)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConcertPreviewMessage(
    icon: ImageVector,
    title: Int,
    message: Int?,
    actionTitle: Int,
    action: () -> Unit,
    secondaryTitle: Int? = null,
    secondary: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CorusSpacing.lg),
        shape = RoundedCornerShape(16.dp),
        color = CorusColors.CardBackground,
        contentColor = CorusColors.Text,
    ) {
        Row(
            modifier = Modifier.padding(CorusSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CorusColors.Text.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = CorusColors.Secondary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(stringResource(title), style = CorusFont.bodyMedium, color = CorusColors.Text)
                message?.let {
                    Text(
                        stringResource(it),
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                    )
                }
                FlowRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConcertPreviewAction(actionTitle, action)
                    secondaryTitle?.let { ConcertPreviewAction(it, secondary) }
                }
            }
        }
    }
}

@Composable
private fun ConcertPreviewAction(title: Int, action: () -> Unit) {
    Button(
        onClick = action,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = CorusColors.Text.copy(alpha = 0.08f),
            contentColor = CorusColors.Text,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
        modifier = Modifier.heightIn(min = 36.dp),
    ) {
        Text(stringResource(title), style = CorusFont.captionMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConcertListEmptyState(
    icon: ImageVector,
    title: Int,
    message: Int?,
    actionTitle: Int,
    action: () -> Unit,
    formattedMessage: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                stringResource(title),
                style = CorusFont.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            val body = formattedMessage ?: message?.let { stringResource(it) }
            body?.let {
                Text(
                    it,
                    style = CorusFont.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        ConcertPreviewAction(actionTitle, action)
    }
}

@Composable
private fun ConcertTopBar(onBack: () -> Unit, showFilters: Boolean, filtersActive: Boolean, onFilters: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart)) {
            CorusHeaderIconButton(
                onClick = onBack,
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
            )
        }
        Text(stringResource(R.string.concerts_title), style = CorusFont.screenTitle, fontWeight = FontWeight.ExtraBold)
        if (showFilters) {
            Box(Modifier.align(Alignment.CenterEnd)) {
                CorusHeaderIconButton(
                    onClick = onFilters,
                    imageVector = Icons.Default.FilterList,
                    contentDescription = stringResource(R.string.concert_filter_title),
                    tint = if (filtersActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ConcertsScreen(
    onBack: () -> Unit, onConcert: (String) -> Unit, onPostMusic: () -> Unit = {},
    vm: ConcertsViewModel = hiltViewModel(),
) {
    val tab by vm.tab.collectAsState(); val shows by vm.shows.collectAsState()
    val plans by vm.plans.collectAsState(); val plansSynced by vm.plansSynced.collectAsState(); val city by vm.cityName.collectAsState()
    val needsCity by vm.needsCity.collectAsState(); val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState(); val total by vm.total.collectAsState()
    val nearbyTotal by vm.nearbyTotal.collectAsState()
    val cursor by vm.cursor.collectAsState(); val genres by vm.genres.collectAsState()
    val range by vm.dateRange.collectAsState(); val genre by vm.genre.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val hasPostedArtists by vm.hasPostedArtists.collectAsState()
    val pullRefreshing by vm.pullRefreshing.collectAsState()
    val isDark = LocalCorusDarkTheme.current
    var citySheet by remember { mutableStateOf(false) }; var filterSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var locating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }
    fun locate() {
        vm.logLocationRequested()
        locating = true; locationError = false
        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .firstOrNull(manager::isProviderEnabled) ?: error("No location provider")
            fun accept(location: android.location.Location?) {
                if (location == null) { locating = false; locationError = true; vm.logLocationResolved("error"); return }
                vm.selectCurrentLocation(location) { success -> locating = false; locationError = !success; if (success) citySheet = false }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) manager.getCurrentLocation(provider, null, context.mainExecutor, ::accept)
            else manager.requestSingleUpdate(provider, object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) { accept(location) }
            }, Looper.getMainLooper())
        } catch (_: Exception) { locating = false; locationError = true; vm.logLocationResolved("error") }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) locate() else { locationError = true; vm.logLocationRequested(); vm.logLocationResolved("error") }
    }
    // Only the server knows whether a city is missing: with none picked it falls back to the
    // viewer's map city. Judging by the local pick alone flashed "Choose your city" on every open.
    val missingCity = tab != "myConcerts" && needsCity
    val filtersActive = range != "any" || genre != null || (tab == "forYou" && !suggestions)
    LaunchedEffect(vm.enabled) { if (vm.enabled) vm.refresh() }
    LaunchedEffect(vm.enabled, tab, loading, error, needsCity, shows, plans, range, genre, suggestions) { if (vm.enabled) vm.logListImpression() }
    if (!vm.enabled) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.concert_unavailable), style = CorusFont.body) }; return }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ConcertTopBar(
                onBack = onBack,
                showFilters = tab != "myConcerts",
                filtersActive = filtersActive,
                onFilters = { filterSheet = true; vm.logFiltersOpened() },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = pullRefreshing,
            onRefresh = vm::pullRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (tab == "myConcerts") {
                        Text(stringResource(R.string.concert_your_plans), style = CorusFont.bodyMedium)
                    } else {
                        Row(
                            Modifier.clickable { citySheet = true; vm.logCityPickerOpened() },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                if (missingCity) stringResource(R.string.concert_choose_your_city) else city.ifBlank { "…" },
                                style = CorusFont.bodyMedium,
                            )
                            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    val visibleTotal = if (tab == "myConcerts") plans.size else total
                    if (visibleTotal > 0) {
                        Text("$visibleTotal ${stringResource(R.string.concert_shows)}", style = CorusFont.captionMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(if (isDark) androidx.compose.ui.graphics.Color(0xFF1C1C1F) else CorusColors.Divider),
                ) {
                    listOf("all" to R.string.concert_tab_all, "forYou" to R.string.concert_tab_for_you, "myConcerts" to R.string.concert_tab_my).forEach { (value, label) ->
                        Surface(
                            onClick = { vm.selectTab(value) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = CircleShape,
                            color = if (tab == value) {
                                if (isDark) androidx.compose.ui.graphics.Color(0xFF5A5A5F) else MaterialTheme.colorScheme.surface
                            } else androidx.compose.ui.graphics.Color.Transparent,
                            border = if (tab == value) BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) .12f else .08f)) else null,
                            shadowElevation = if (tab == value && !isDark) 1.dp else 0.dp,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(label),
                                    style = CorusFont.bodyMedium,
                                    fontWeight = if (tab == value) FontWeight.Bold else FontWeight.Normal,
                                    color = if (tab == value) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (tab == "myConcerts") {
                val today = LocalDate.now().toString()
                val groups = listOf(
                    R.string.concert_going_section to plans.filter { it.status == "going" && it.date >= today },
                    R.string.concert_interested_section to plans.filter { it.status == "interested" && it.date >= today },
                    R.string.concert_past_section to plans.filter { it.date < today }.reversed(),
                )
                if (error && !loading) item {
                    ConcertListEmptyState(Icons.Default.Refresh, R.string.concert_load_error, null, R.string.concert_retry, { vm.logListEmptyAction("retry"); vm.refresh() })
                } else if (plans.isEmpty() && !loading) item {
                    ConcertListEmptyState(
                        Icons.Default.ConfirmationNumber,
                        R.string.concert_no_plans_title,
                        R.string.concert_my_empty,
                        R.string.concert_browse,
                        { vm.logListEmptyAction("browse_all"); vm.selectTab("all") },
                    )
                }
                groups.forEach { (label, sectionShows) ->
                    if (sectionShows.isNotEmpty()) { item { Text(stringResource(label).uppercase(), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = CorusFont.sectionHeader, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(sectionShows, key = { it.id }) { show -> ConcertRow(show, { vm.select(show, "my_concerts"); onConcert(show.id) }, showPlanStatus = true); Spacer(Modifier.height(10.dp)) }
                    }
                }
            } else {
                when {
                    missingCity -> item {
                        ConcertListEmptyState(
                            Icons.Default.LocationOn,
                            R.string.concert_find_nearby,
                            R.string.concert_choose_location_message,
                            R.string.concert_choose_location,
                            { vm.logListEmptyAction("needs_city"); citySheet = true },
                        )
                    }
                    error && !loading && shows.isEmpty() -> item {
                        ConcertListEmptyState(Icons.Default.Refresh, R.string.concert_load_error, null, R.string.concert_retry, { vm.logListEmptyAction("retry"); vm.refresh() })
                    }
                    shows.isEmpty() && !loading && tab == "forYou" && !hasPostedArtists -> item {
                        ConcertListEmptyState(Icons.Default.MusicNote, R.string.concert_post_personal_title, R.string.concert_personal_message, R.string.concert_post_music, { vm.logListEmptyAction("post_music"); onPostMusic() })
                    }
                    shows.isEmpty() && !loading && nearbyTotal == 0 -> item {
                        ConcertListEmptyState(
                            Icons.Default.CalendarMonth,
                            R.string.concert_no_shows,
                            null,
                            R.string.concert_choose_another_city,
                            { vm.logListEmptyAction("change_city"); citySheet = true },
                            formattedMessage = stringResource(R.string.concert_no_shows_around, city),
                        )
                    }
                    shows.isEmpty() && !loading && tab == "forYou" -> item {
                        ConcertListEmptyState(Icons.Default.AutoAwesome, R.string.concert_no_artist_shows, R.string.concert_keep_posting_message, R.string.concert_browse, { vm.logListEmptyAction("browse_all"); vm.selectTab("all") })
                    }
                }
                items(shows, key = { it.id }) { show -> ConcertRow(show, { vm.select(show, "concerts_list"); onConcert(show.id) }); Spacer(Modifier.height(10.dp)) }
                if (cursor != null) item {
                    LaunchedEffect(cursor) { if (!error) vm.refresh(cursor) }
                    if (loading) ConcertRowSkeleton()
                    else if (error) TextButton(onClick = { vm.refresh(cursor) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.concert_retry), style = CorusFont.button) }
                }
            }
            if (loading && !missingCity && (if (tab == "myConcerts") !plansSynced else shows.isEmpty())) items(if (tab == "myConcerts" && plans.isNotEmpty()) 3 else 5) { ConcertRowSkeleton(); Spacer(Modifier.height(10.dp)) }
            }
        }
    }
    if (citySheet) {
        val citySheetState = rememberGuardedSheetState()
        CorusModalBottomSheet(
            onDismissRequest = { citySheet = false },
            sheetState = citySheetState,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        ) {
            var cityQuery by remember { mutableStateOf("") }
            val cityResults by vm.cityResults.collectAsState()
            val citySearching by vm.citySearching.collectAsState()
            LaunchedEffect(Unit) { vm.log("city_picker_viewed") }
            LaunchedEffect(cityQuery) { vm.searchCities(cityQuery) }
            Column(Modifier.fillMaxWidth().heightIn(max = bottomSheetMaxHeight())) {
                Column(Modifier.fillMaxWidth().dismissKeyboardOnDownwardDrag()) {
                    Text(
                        stringResource(R.string.concert_choose_city),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
                        style = CorusFont.custom(700, 22),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !locating) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
                                else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (locating) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Navigation, null, Modifier.size(22.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(R.string.concert_use_location), style = CorusFont.bodyMedium)
                            Text(stringResource(R.string.concert_find_nearby), style = CorusFont.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (locationError) Text(stringResource(R.string.concert_location_error), Modifier.padding(horizontal = 20.dp), style = CorusFont.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextField(
                    cityQuery,
                    { cityQuery = it },
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    textStyle = CorusFont.body,
                    placeholder = { Text(stringResource(R.string.concert_city_search), style = CorusFont.body) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
                val popular = if (cityQuery.trim().length < 2) POPULAR_CONCERT_CITIES else POPULAR_CONCERT_CITIES.filter { it.second.contains(cityQuery.trim(), ignoreCase = true) }
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 500.dp).dismissKeyboardOnDownwardDrag().hideKeyboardOnScroll()) {
                    items(popular) { (id, name) ->
                        ListItem(
                            headlineContent = { Text(name, style = CorusFont.bodyMedium) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                            modifier = Modifier.clickable { vm.selectCity(id, name, "popular"); citySheet = false },
                        )
                    }
                    if (cityQuery.trim().length >= 2) {
                        items(cityResults.filter { result -> popular.none { it.first == result.cityId } }, key = { it.cityId }) { city ->
                            ListItem(
                                headlineContent = { Text(city.cityName, style = CorusFont.bodyMedium) },
                                supportingContent = { Text(listOf(city.regionName, city.countryCode).filter(String::isNotBlank).joinToString(", "), style = CorusFont.caption) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                                modifier = Modifier.clickable { vm.selectCity(city.cityId, city.cityName, "search"); citySheet = false },
                            )
                        }
                        if (citySearching) item { Text(stringResource(R.string.concert_city_searching), Modifier.padding(20.dp), style = CorusFont.body) }
                        else if (popular.isEmpty() && cityResults.isEmpty()) item { Text(stringResource(R.string.concert_no_cities), Modifier.padding(20.dp), style = CorusFont.body) }
                    }
            }
            }
        }
    }
    if (filterSheet) {
        val sheetState = rememberGuardedSheetState(skipPartiallyExpanded = true)
        CorusModalBottomSheet(
            onDismissRequest = { filterSheet = false },
            sheetState = sheetState,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            var localRange by remember(range) { mutableStateOf(range) }
            var localGenre by remember(genre) { mutableStateOf(genre) }
            var localSuggestions by remember(suggestions) { mutableStateOf(suggestions) }
            val filtersChanged = localRange != range || localGenre != genre || localSuggestions != suggestions
            val dateOptions = listOf(
                "any" to R.string.concert_any_date,
                "7d" to R.string.concert_next_7,
                "30d" to R.string.concert_next_30,
                "90d" to R.string.concert_next_90,
            )
            val genreOptions = listOf<String?>(null) + genres

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val safeInsets = WindowInsets.systemBars
                    .union(WindowInsets.displayCutout)
                    .asPaddingValues()
                val topGap = safeInsets.calculateTopPadding() + CorusSpacing.md + 32.dp
                Column(Modifier.fillMaxWidth().height((maxHeight - topGap).coerceAtLeast(0.dp))) {
                    Box(
                        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.concert_filter_title),
                            style = CorusFont.screenTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        if (tab == "forYou") {
                            item {
                                Row(
                                    Modifier.fillMaxWidth().clickable { localSuggestions = !localSuggestions }
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(R.string.concert_show_recommendations),
                                        Modifier.weight(1f),
                                        style = CorusFont.body,
                                    )
                                    Switch(localSuggestions, { localSuggestions = it })
                                }
                            }
                        }
                        item {
                            Text(
                                stringResource(R.string.concert_date_filter),
                                Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                style = CorusFont.custom(500, 14),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(dateOptions.size, key = { dateOptions[it].first }) { index ->
                            val (value, label) = dateOptions[index]
                            ConcertFilterOptionRow(
                                title = stringResource(label),
                                selected = localRange == value,
                                onClick = { localRange = value },
                            )
                        }
                        item {
                            Text(
                                stringResource(R.string.concert_genre_filter),
                                Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                style = CorusFont.custom(500, 14),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(genreOptions.size, key = { index -> genreOptions[index] ?: "__any_genre__" }) { index ->
                            val value = genreOptions[index]
                            ConcertFilterOptionRow(
                                title = value ?: stringResource(R.string.concert_any_genre),
                                selected = localGenre == value,
                                onClick = { localGenre = value },
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (filtersChanged) vm.setFilters(localRange, localGenre, localSuggestions)
                            filterSheet = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                            .padding(top = 8.dp, bottom = 16.dp + safeInsets.calculateBottomPadding())
                            .heightIn(min = 56.dp),
                        shape = CircleShape,
                    ) {
                        Text(stringResource(R.string.concert_show_concerts), style = CorusFont.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConcertFilterOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), style = CorusFont.body, color = MaterialTheme.colorScheme.onSurface)
            if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        )
    }
}

@Composable
fun ConcertRow(show: ConcertShow, onClick: () -> Unit, showPlanStatus: Boolean = false) {
    val appLocale = LocalConfiguration.current.locales[0]
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val date = runCatching { LocalDate.parse(show.date) }.getOrNull()
                Text(date?.month?.getDisplayName(java.time.format.TextStyle.SHORT, appLocale)?.uppercase(appLocale) ?: "", color = MaterialTheme.colorScheme.primary, style = CorusFont.custom(600, 11))
                Text(date?.dayOfMonth?.toString() ?: "", style = CorusFont.custom(700, 26))
            }
            VerticalDivider(Modifier.height(48.dp).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
            Column(Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (show.suggestionSource == "tasteMatches") Text(stringResource(R.string.concert_you_might_like), color = MaterialTheme.colorScheme.primary, style = CorusFont.custom(600, 11))
                Text(show.matchedArtist ?: show.lineup.firstOrNull() ?: show.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = CorusFont.custom(600, 17))
                if (show.matchedArtist != null && show.matchedArtist != show.title) Text(show.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = CorusFont.custom(400, 13))
                Text("${show.venue} · ${show.city}", maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = CorusFont.custom(400, 13))
            }
            if (!show.imageUrl.isNullOrBlank()) AsyncImage(show.imageUrl, null, Modifier.size(62.dp).clip(RoundedCornerShape(11.dp)), contentScale = ContentScale.Crop)
        }
        if (showPlanStatus && show.status != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (show.status == "going") Icons.Default.CheckCircle else Icons.Default.Star,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(if (show.status == "going") R.string.concert_going_section else R.string.concert_interested_section),
                    style = CorusFont.captionMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        }
    }
}

@Composable
fun ConcertDetailScreen(
    eventId: String, onBack: () -> Unit, onArtist: (ArtistPageRoute) -> Unit,
    onProfile: (String) -> Unit,
    vm: ConcertsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val show by vm.show.collectAsState(); val attendance by vm.attendance.collectAsState()
    val attendanceError by vm.attendanceError.collectAsState()
    val error by vm.error.collectAsState()
    val detailError by vm.detailError.collectAsState()
    var menu by remember { mutableStateOf(false) }; var peopleSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var venueSheet by remember { mutableStateOf(false) }
    var calendarSheet by remember { mutableStateOf(false) }; var expanded by remember(eventId) { mutableStateOf(false) }
    var resolvingArtist by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(eventId) { vm.open(eventId) }
    if (!vm.enabled) { Box(Modifier.fillMaxSize().background(CorusColors.Background), contentAlignment = Alignment.Center) { Text(stringResource(R.string.concert_unavailable), style = CorusFont.body) }; return }
    val concert = show
    if (concert == null) {
        Box(Modifier.fillMaxSize().background(CorusColors.Background)) {
            if (detailError) ConcertListEmptyState(Icons.Default.Refresh, R.string.concert_unavailable, null, R.string.concert_retry, { vm.open(eventId) })
            else ConcertDetailSkeleton(onBack)
        }
        return
    }
    val past = ConcertCalendarPolicy.hasStarted(concert.date, concert.time, concert.timezone)
    val unavailable = concert.eventStatus in listOf("canceled", "postponed")
    val immersive = !concert.imageUrl.isNullOrBlank()
    fun openArtist(name: String, source: String) {
        if (resolvingArtist) return
        resolvingArtist = true
        DestinationResolvingOverlay.arm()
        vm.resolveArtist(name, source) { route ->
            resolvingArtist = false
            DestinationResolvingOverlay.setResolving(false)
            if (route != null) onArtist(route)
            else android.widget.Toast.makeText(context, R.string.concert_artist_not_found, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (resolvingArtist) DestinationResolvingOverlay.setResolving(false) }
    }
    val hazeState = remember { HazeState() }
    val collapseDistancePx = with(LocalDensity.current) { (340.dp - ImmersiveBarHeight).toPx() }
    val collapseProgress by remember {
        derivedStateOf {
            immersiveCollapseProgress(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                collapseDistancePx = collapseDistancePx,
            )
        }
    }
    val statusBarTopPx = if (immersive) currentStatusBarTopPx() else 0
    val extendUnderStatusBar = immersive && ImmersiveExtendUnderStatusBar && statusBarTopPx > 0
    val statusBarPadding = if (extendUnderStatusBar) with(LocalDensity.current) { statusBarTopPx.toDp() } else 0.dp
    if (extendUnderStatusBar) ImmersiveStatusBarIcons(collapseProgress)
    LaunchedEffect(concert.id) { vm.share(concert) { } }

    Scaffold(
        modifier = if (extendUnderStatusBar) Modifier.extendIntoStatusBar(statusBarTopPx) else Modifier,
        containerColor = CorusColors.Background,
        topBar = {
            if (!immersive) {
                Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.align(Alignment.CenterStart)) {
                        CorusHeaderIconButton(onClick = onBack, imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Text(stringResource(R.string.concert_label), style = CorusFont.screenTitle)
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        CorusHeaderIconButton(onClick = { menu = true }, imageVector = Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.feed_cd_more_options))
                        ConcertDetailMenu(
                            expanded = menu,
                            onDismiss = { menu = false },
                            onArtist = { concert.lineup.firstOrNull()?.let { openArtist(it, "menu") } },
                            onShare = { showShareSheet = true; vm.log("share_opened", concert, "concert_detail_menu") },
                        )
                    }
                }
            }
        }, bottomBar = {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { showShareSheet = true; vm.log("invite_opened", concert, "concert_detail") },
                    Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Icon(Icons.Default.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.concert_invite), style = CorusFont.bodyMedium) }
                if (concert.eventStatus != "postponed") {
                    Button(
                        onClick = {
                            vm.log("ticket_tapped", concert, "concert_detail", provider = fm.corus.android.service.ConcertAnalytics.ticketProvider(concert.url))
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(concert.url)))
                        },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                    ) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.concert_tickets), style = CorusFont.bodyMedium)
                    }
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        if (immersive) {
            ImmersiveCoverBackdrop(
                artUrl = concert.imageUrl,
                height = 340.dp + statusBarPadding,
                listState = listState,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .then(if (immersive) Modifier.hazeSource(hazeState) else Modifier)
                .contentHazeSource(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
        item {
            if (immersive) Spacer(Modifier.height(statusBarPadding + ImmersiveBarHeight + CorusSpacing.md))
            Box(Modifier.fillMaxWidth().aspectRatio(5f / 3f).padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(concert.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = .7f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.concert_label).uppercase(Locale.getDefault()),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = .8f),
                    style = CorusFont.caption,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Row(
                    Modifier.clickable { (concert.matchedArtist ?: concert.lineup.firstOrNull())?.let { openArtist(it, "hero") } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(concert.matchedArtist ?: concert.lineup.firstOrNull() ?: concert.title, color = androidx.compose.ui.graphics.Color.White, style = CorusFont.songTitleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = .82f))
                }
            }
            }
        }
        item { Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if ((concert.lineup.firstOrNull() ?: concert.title) != concert.title) Text(concert.title, style = CorusFont.songTitleLarge)
            ConcertDetailInfoRow(Icons.Default.CalendarMonth, stringResource(R.string.concert_date_time), formatConcertDate(concert))
            ConcertDetailInfoRow(
                Icons.Default.LocationOn,
                stringResource(R.string.concert_venue),
                concert.venue,
                "${concert.city}, ${concert.region}",
                onClick = { venueSheet = true; vm.log("venue_options_opened", concert, "concert_detail") },
            )
            if (vm.calendarEnabled && ConcertCalendarPolicy.startMillis(concert.date, concert.time, concert.timezone) != null) {
                Row(
                    Modifier.fillMaxWidth().clickable { calendarSheet = true; vm.log("calendar_options_opened", concert, "concert_detail") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.CalendarMonth, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.concert_add_calendar), style = CorusFont.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } }
        if (concert.supportsAttendance) item { Box(Modifier.padding(horizontal = 20.dp)) { ConcertPlansCard(attendance, past, unavailable, onChoice = vm::updatePlan, onPeople = { peopleSheet = true; vm.log("attendees_opened", concert, "concert_detail", count = (attendance?.goingCount ?: 0) + (attendance?.interestedCount ?: 0)) }, boxed = false, loadError = attendanceError, onRetry = { vm.open(eventId) }) } }
        if (concert.lineup.isNotEmpty()) item { Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.concert_lineup), style = CorusFont.songTitleLarge)
            Spacer(Modifier.height(12.dp))
            concert.lineup.forEach { artist ->
                Row(
                    Modifier.fillMaxWidth().clickable { openArtist(artist, "lineup") }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Mic, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(artist, style = CorusFont.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.concert_lineup_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = CorusFont.caption)
        } }
        if (!concert.info.isNullOrBlank() || !concert.pleaseNote.isNullOrBlank()) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.concert_event_info), style = CorusFont.bodyMedium)
                val eventInfo = listOfNotNull(concert.info, concert.pleaseNote).joinToString("\n\n")
                var isTruncated by remember(eventInfo) { mutableStateOf(false) }
                Text(
                    eventInfo,
                    modifier = Modifier.animateContentSize(animationSpec = tween(durationMillis = 300)),
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    style = CorusFont.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    onTextLayout = { layout -> if (!expanded) isTruncated = layout.hasVisualOverflow },
                )
                if (isTruncated || expanded) {
                    TextButton(onClick = { vm.log("event_info_toggled", concert, "concert_detail", result = if (expanded) "collapsed" else "expanded"); expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(if (expanded) R.string.concert_show_less else R.string.concert_read_more), style = CorusFont.buttonSmall, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(6.dp))
                        Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        if (error) item { Text(stringResource(R.string.concert_update_error), Modifier.padding(horizontal = 20.dp), style = CorusFont.caption, color = MaterialTheme.colorScheme.error) }
        }
        if (immersive) {
            ImmersiveCollapsingBar(
                hazeState = hazeState,
                progress = collapseProgress,
                title = concert.matchedArtist ?: concert.lineup.firstOrNull() ?: concert.title,
                onBack = onBack,
                topInset = statusBarPadding,
                actions = { tint ->
                    Box {
                        CorusHeaderIconButton(
                            onClick = { menu = true },
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = stringResource(R.string.feed_cd_more_options),
                            tint = tint,
                        )
                        ConcertDetailMenu(
                            expanded = menu,
                            onDismiss = { menu = false },
                            onArtist = { concert.lineup.firstOrNull()?.let { openArtist(it, "menu") } },
                            onShare = { showShareSheet = true; vm.log("share_opened", concert, "concert_detail_menu") },
                        )
                    }
                },
            )
        }
        }
    }
    if (showShareSheet) {
        val shareSheetState = rememberGuardedSheetState(skipPartiallyExpanded = true)
        LaunchedEffect(Unit) { vm.loadRecentShareContacts() }
        val recentShareContacts by vm.recentShareContacts.collectAsState()
        val shareSearchResults by vm.shareSearchResults.collectAsState()
        val isShareSearching by vm.isShareSearching.collectAsState()
        val isLoadingShareContacts by vm.isLoadingShareContacts.collectAsState()
        CorusModalBottomSheet(
            onDismissRequest = { showShareSheet = false },
            sheetState = shareSheetState,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            contentWindowInsets = { WindowInsets.systemBars.only(WindowInsetsSides.Bottom) },
        ) {
            CorusSystemBars()
            BackHandler { showShareSheet = false }
            ShareMediaSheet(
                subject = ShareMediaSubject.Concert(
                    ShareConcertSubject(
                        id = concert.id,
                        title = concert.title,
                        artistName = concert.matchedArtist ?: concert.lineup.firstOrNull() ?: concert.title,
                        venue = concert.venue,
                        city = concert.city,
                        date = concert.date,
                        imageUrl = concert.imageUrl,
                    ),
                ),
                recentContacts = recentShareContacts,
                searchResults = shareSearchResults,
                isSearching = isShareSearching,
                isLoadingContacts = isLoadingShareContacts,
                onSearchQueryChange = vm::searchShareUsers,
                onSendToUser = { userId, message ->
                    ToastManager.show(context.getString(R.string.concert_sent))
                    vm.sendInvite(
                        userId = userId,
                        note = message,
                        onError = {
                            ToastManager.show(context.getString(R.string.concert_invite_error))
                        },
                    )
                    showShareSheet = false
                },
                onDismiss = { showShareSheet = false },
                onAnalyticsLog = { method ->
                    vm.log("share_attempted", concert, "share_sheet", method = method)
                },
            )
        }
    }
    if (peopleSheet) {
        val peopleSheetState = rememberGuardedSheetState(skipPartiallyExpanded = true)
        val peopleSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.72f
        LaunchedEffect(Unit) { vm.log("attendees_viewed", concert, "concert_detail", count = (attendance?.goingCount ?: 0) + (attendance?.interestedCount ?: 0)) }
        CorusModalBottomSheet(
            onDismissRequest = { peopleSheet = false },
            sheetState = peopleSheetState,
        ) {
            Column(Modifier.fillMaxWidth().height(peopleSheetHeight)) {
                ConcertSheetHeader(stringResource(R.string.concert_plans_title), showCloseButton = false) { peopleSheet = false }
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(attendance?.people.orEmpty(), key = { it.id }) { person ->
                        ListItem(
                            headlineContent = { Text(person.name, style = CorusFont.bodyMedium) },
                            supportingContent = { Text("@${person.username}", style = CorusFont.caption) },
                            trailingContent = { Text(stringResource(if (person.status == "going") { if (past) R.string.concert_went else R.string.concert_going_section } else { if (past) R.string.concert_was_interested else R.string.concert_interested_section }), style = CorusFont.captionMedium) },
                            leadingContent = { AsyncImage(person.avatarUrl, null, Modifier.size(42.dp).clip(CircleShape), contentScale = ContentScale.Crop) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                            modifier = Modifier.clickable { vm.log("attendee_profile_tapped", concert, "concert_attendees", result = person.status); peopleSheet = false; onProfile(person.id) },
                        )
                    }
                    if (attendance?.nextCursor != null) item {
                        TextButton(onClick = { vm.loadMorePeople() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.concert_see_more), style = CorusFont.button) }
                    }
                }
            }
        }
    }
    if (venueSheet) CorusModalBottomSheet(onDismissRequest = { venueSheet = false }) { LaunchedEffect(Unit) { vm.log("venue_sheet_viewed", concert, "concert_detail") }
        val address = listOfNotNull(concert.address ?: concert.venue, concert.city, concert.region).joinToString(", ")
        ConcertSheetHeader(concert.venue) { venueSheet = false }
        Text(address, Modifier.padding(horizontal = 20.dp), style = CorusFont.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ListItem(headlineContent = { Text(stringResource(R.string.concert_copy_address), style = CorusFont.body) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background), modifier = Modifier.clickable { vm.log("address_copied", concert, "venue_sheet"); (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Address", address)); venueSheet = false })
        ListItem(headlineContent = { Text(stringResource(R.string.concert_google_maps), style = CorusFont.body) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background), modifier = Modifier.clickable { vm.log("maps_tapped", concert, "venue_sheet", provider = "google"); val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}")).setPackage("com.google.android.apps.maps")
            runCatching { context.startActivity(mapIntent) }.recoverCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(address)}"))) }
            venueSheet = false })
    }
    if (calendarSheet && vm.calendarEnabled) CorusModalBottomSheet(onDismissRequest = { calendarSheet = false }) { ConcertSheetHeader(stringResource(R.string.concert_add_calendar)) { calendarSheet = false }
        if (runCatching { context.packageManager.getPackageInfo("com.google.android.calendar", 0) }.isSuccess) ListItem(headlineContent = { Text(stringResource(R.string.concert_google_calendar), style = CorusFont.body) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background), modifier = Modifier.clickable { vm.log("calendar_provider_tapped", concert, "concert_detail", provider = "google"); openCalendar(context, concert, "google"); calendarSheet = false })
        ListItem(headlineContent = { Text(stringResource(R.string.concert_device_calendar), style = CorusFont.body) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background), modifier = Modifier.clickable { vm.log("calendar_provider_tapped", concert, "concert_detail", provider = "device"); openCalendar(context, concert, "device"); calendarSheet = false })
    }
}

@Composable
private fun ConcertDetailMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onArtist: () -> Unit,
    onShare: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.concert_go_to_artist), style = CorusFont.body) },
            onClick = { onDismiss(); onArtist() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.concert_share), style = CorusFont.body) },
            onClick = { onDismiss(); onShare() },
        )
    }
}

@Composable
private fun ConcertDetailInfoRow(
    icon: ImageVector,
    title: String,
    detail: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = CorusFont.captionMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail, style = CorusFont.bodyMedium)
            subtitle?.let { Text(it, style = CorusFont.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun formatConcertDate(show: ConcertShow): String {
    val context = LocalContext.current
    val appLocale = LocalConfiguration.current.locales[0]
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return runCatching {
        LocalDate.parse(show.date).format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(appLocale)) +
            (show.time?.let { " · " + java.time.LocalTime.parse(it).format(DateTimeFormatter.ofPattern(pattern, appLocale)) } ?: "")
    }.getOrDefault(show.date)
}

private fun openCalendar(context: android.content.Context, show: ConcertShow, provider: String) {
    val start = ConcertCalendarPolicy.startMillis(show.date, show.time, show.timezone) ?: return
    val intent = Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
        .putExtra(android.provider.CalendarContract.Events.TITLE, show.title)
        .putExtra(android.provider.CalendarContract.Events.EVENT_TIMEZONE, show.timezone ?: java.time.ZoneId.systemDefault().id)
        .putExtra(android.provider.CalendarContract.Events.ALL_DAY, show.time == null)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, start + if (show.time == null) 86_400_000L else 10_800_000L)
        .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, listOfNotNull(show.address ?: show.venue, show.city, show.region).joinToString(", "))
    if (provider == "google") intent.setPackage("com.google.android.calendar")
    runCatching { context.startActivity(intent) }.recoverCatching { context.startActivity(intent.setPackage(null)) }.onFailure {
        android.widget.Toast.makeText(context, R.string.concert_calendar_error, android.widget.Toast.LENGTH_LONG).show()
    }
}
