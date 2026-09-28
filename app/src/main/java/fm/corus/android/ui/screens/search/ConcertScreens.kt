@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package fm.corus.android.ui.screens.search

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.repository.ConcertShow
import fm.corus.android.ui.navigation.ArtistPageRoute
import fm.corus.android.ui.theme.CorusFont
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    val page by vm.previewPage.collectAsState()
    val loading by vm.previewLoading.collectAsState()
    val error by vm.previewError.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm.enabled, cityId, discovery) { vm.preview() }
    val browseAll = { vm.rememberDiscoveryTab("all"); onSeeAll() }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ConfirmationNumber, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.concerts_title).uppercase(appLocale), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.log("preview_see_all_tapped", source = "search_music_preview"); onSeeAll() }) { Text(stringResource(R.string.concert_see_all)) }
        }
        when {
            page == null && !error -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(2) { ConcertPreviewSkeleton() }
            }
            error && page == null -> ConcertPreviewMessage(Icons.Default.Refresh, R.string.concert_load_error, null, R.string.concert_retry,
                { scope.launch { vm.preview(true) } })
            page?.needsCity == true -> ConcertPreviewMessage(Icons.Default.LocationOn, R.string.concert_find_nearby, R.string.concert_choose_location_message, R.string.concert_choose_city, onSeeAll)
            discovery == "forYou" && page?.hasPostedArtists == false -> ConcertPreviewMessage(
                Icons.Default.MusicNote, R.string.concert_make_personal, R.string.concert_personal_message, R.string.concert_post_music, onPostMusic,
                R.string.concert_view_all, browseAll)
            page?.shows.isNullOrEmpty() -> ConcertPreviewMessage(
                if ((page?.nearbyTotal ?: 0) == 0) Icons.Default.CalendarMonth else Icons.Default.AutoAwesome,
                if ((page?.nearbyTotal ?: 0) == 0) R.string.concert_no_shows else R.string.concert_no_matches,
                if ((page?.nearbyTotal ?: 0) == 0) null else R.string.concert_keep_posting_message,
                R.string.concert_view_all, browseAll)
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(page!!.shows, key = { it.id }) { show ->
                    Surface(Modifier.width(218.dp).height(170.dp).clickable { vm.select(show); onConcert(show.id) }, shape = RoundedCornerShape(18.dp)) {
                        Box(Modifier.fillMaxSize()) {
                            AsyncImage(show.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha = .32f), androidx.compose.ui.graphics.Color.Black.copy(alpha = .84f)))))
                            Column(Modifier.fillMaxSize().padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        runCatching { LocalDate.parse(show.date).format(DateTimeFormatter.ofPattern("MMM d", appLocale)).uppercase(appLocale) }.getOrDefault(show.date),
                                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = .78f),
                                        style = CorusFont.custom(700, 11),
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Default.OpenInNew, null, Modifier.size(14.dp), tint = androidx.compose.ui.graphics.Color.White.copy(alpha = .78f))
                                }
                                Spacer(Modifier.weight(1f))
                                if (show.suggestionSource == "tasteMatches") Text(stringResource(R.string.concert_you_might_like), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .86f), style = CorusFont.captionMedium)
                                Text(show.matchedArtist ?: show.lineup.firstOrNull() ?: show.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = androidx.compose.ui.graphics.Color.White, style = CorusFont.custom(700, 22))
                                if (show.matchedArtist != null && show.matchedArtist != show.title) Text(show.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .76f), style = CorusFont.caption)
                                Spacer(Modifier.height(10.dp))
                                Text(show.venue, maxLines = 1, overflow = TextOverflow.Ellipsis, color = androidx.compose.ui.graphics.Color.White, style = CorusFont.captionMedium)
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                message?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = Modifier.heightIn(min = 36.dp),
    ) {
        Text(stringResource(title), style = CorusFont.bodyMedium)
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
        modifier = Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        ConcertCircleButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.share_back)) }
        Text(stringResource(R.string.concerts_title), style = CorusFont.screenTitle, fontWeight = FontWeight.ExtraBold)
        if (showFilters) {
            ConcertCircleButton(
                onClick = onFilters,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    Icons.Default.FilterList,
                    stringResource(R.string.concert_filter_title),
                    tint = if (filtersActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConcertCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.shadow(16.dp, CircleShape, ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.10f)),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun ConcertsScreen(
    onBack: () -> Unit, onConcert: (String) -> Unit, onPostMusic: () -> Unit = {},
    vm: ConcertsViewModel = hiltViewModel(),
) {
    val tab by vm.tab.collectAsState(); val shows by vm.shows.collectAsState()
    val plans by vm.plans.collectAsState(); val city by vm.cityName.collectAsState()
    val cityId by vm.cityId.collectAsState()
    val needsCity by vm.needsCity.collectAsState(); val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState(); val total by vm.total.collectAsState()
    val nearbyTotal by vm.nearbyTotal.collectAsState()
    val cursor by vm.cursor.collectAsState(); val genres by vm.genres.collectAsState()
    val range by vm.dateRange.collectAsState(); val genre by vm.genre.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val hasPostedArtists by vm.hasPostedArtists.collectAsState()
    var citySheet by remember { mutableStateOf(false) }; var filterSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var locating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }
    fun locate() {
        locating = true; locationError = false
        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .firstOrNull(manager::isProviderEnabled) ?: error("No location provider")
            fun accept(location: android.location.Location?) {
                if (location == null) { locating = false; locationError = true; return }
                vm.selectCurrentLocation(location) { success -> locating = false; locationError = !success; if (success) citySheet = false }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) manager.getCurrentLocation(provider, null, context.mainExecutor, ::accept)
            else manager.requestSingleUpdate(provider, object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) { accept(location) }
            }, Looper.getMainLooper())
        } catch (_: Exception) { locating = false; locationError = true }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) locate() else locationError = true
    }
    val missingCity = tab != "myConcerts" && (cityId.isNullOrBlank() || needsCity)
    val filtersActive = range != "any" || genre != null || (tab == "forYou" && !suggestions)
    LaunchedEffect(vm.enabled) { if (vm.enabled) vm.refresh() }
    if (!vm.enabled) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.concert_unavailable)) }; return }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ConcertTopBar(
                onBack = onBack,
                showFilters = tab != "myConcerts",
                filtersActive = filtersActive,
                onFilters = { filterSheet = true; vm.log("filters_opened") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
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
                            Modifier.clickable { citySheet = true; vm.log("city_picker_opened") },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Outlined.Navigation, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                if (missingCity) stringResource(R.string.concert_choose_your_city) else city,
                                style = CorusFont.bodyMedium,
                            )
                            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (total > 0) {
                        Text("$total ${stringResource(R.string.concert_shows)}", style = CorusFont.captionMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp),
                ) {
                    listOf("all" to R.string.concert_tab_all, "forYou" to R.string.concert_tab_for_you, "myConcerts" to R.string.concert_tab_my).forEach { (value, label) ->
                        Surface(
                            onClick = { vm.selectTab(value) },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape,
                            color = if (tab == value) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                        ) {
                            Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(label), style = CorusFont.bodyMedium, fontWeight = if (tab == value) FontWeight.Bold else FontWeight.Normal)
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
                    ConcertListEmptyState(Icons.Default.Refresh, R.string.concert_load_error, null, R.string.concert_retry, { vm.refresh() })
                } else if (plans.isEmpty() && !loading) item {
                    ConcertListEmptyState(
                        Icons.Default.ConfirmationNumber,
                        R.string.concert_no_plans_title,
                        R.string.concert_my_empty,
                        R.string.concert_browse,
                        { vm.selectTab("all") },
                    )
                }
                groups.forEach { (label, sectionShows) ->
                    if (sectionShows.isNotEmpty()) { item { Text(stringResource(label).uppercase(), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = CorusFont.sectionHeader, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(sectionShows, key = { it.id }) { show -> ConcertRow(show, { vm.select(show); onConcert(show.id) }, showPlanStatus = true); Spacer(Modifier.height(10.dp)) }
                    }
                }
            } else {
                when {
                    missingCity -> item {
                        ConcertListEmptyState(
                            Icons.Outlined.Navigation,
                            R.string.concert_find_nearby,
                            R.string.concert_choose_location_message,
                            R.string.concert_choose_location,
                            { citySheet = true },
                        )
                    }
                    error && !loading && shows.isEmpty() -> item {
                        ConcertListEmptyState(Icons.Default.Refresh, R.string.concert_load_error, null, R.string.concert_retry, { vm.refresh() })
                    }
                    shows.isEmpty() && !loading && tab == "forYou" && !hasPostedArtists -> item {
                        ConcertListEmptyState(Icons.Default.MusicNote, R.string.concert_post_personal_title, R.string.concert_personal_message, R.string.concert_post_music, onPostMusic)
                    }
                    shows.isEmpty() && !loading && nearbyTotal == 0 -> item {
                        ConcertListEmptyState(
                            Icons.Default.CalendarMonth,
                            R.string.concert_no_shows,
                            null,
                            R.string.concert_choose_another_city,
                            { citySheet = true },
                            formattedMessage = stringResource(R.string.concert_no_shows_around, city),
                        )
                    }
                    shows.isEmpty() && !loading && tab == "forYou" -> item {
                        ConcertListEmptyState(Icons.Default.AutoAwesome, R.string.concert_no_artist_shows, R.string.concert_keep_posting_message, R.string.concert_browse, { vm.selectTab("all") })
                    }
                }
                items(shows, key = { it.id }) { show -> ConcertRow(show, { vm.select(show); onConcert(show.id) }); Spacer(Modifier.height(10.dp)) }
                if (cursor != null) item {
                    LaunchedEffect(cursor) { if (!error) vm.refresh(cursor) }
                    if (loading) ConcertRowSkeleton()
                    else if (error) TextButton(onClick = { vm.refresh(cursor) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.concert_retry)) }
                }
            }
            if (loading && !missingCity && (if (tab == "myConcerts") plans.isEmpty() else shows.isEmpty())) items(4) { ConcertRowSkeleton(); Spacer(Modifier.height(10.dp)) }
        }
    }
    if (citySheet) ModalBottomSheet(
        onDismissRequest = { citySheet = false },
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        var cityQuery by remember { mutableStateOf("") }
        val cityResults by vm.cityResults.collectAsState()
        val citySearching by vm.citySearching.collectAsState()
        LaunchedEffect(cityQuery) { vm.searchCities(cityQuery) }
        ConcertSheetHeader(stringResource(R.string.concert_choose_city)) { citySheet = false }
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
        if (locationError) Text(stringResource(R.string.concert_location_error), Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextField(
            cityQuery,
            { cityQuery = it },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.concert_city_search)) },
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
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
            items(popular) { (id, name) ->
                ListItem(
                    headlineContent = { Text(name, style = CorusFont.bodyMedium) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable { vm.selectCity(id, name); citySheet = false },
                )
            }
            if (cityQuery.trim().length >= 2) {
                items(cityResults.filter { result -> popular.none { it.first == result.cityId } }, key = { it.cityId }) { city ->
                    ListItem(
                        headlineContent = { Text(city.cityName, style = CorusFont.bodyMedium) },
                        supportingContent = { Text(listOf(city.regionName, city.countryCode).filter(String::isNotBlank).joinToString(", "), style = CorusFont.caption) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.clickable { vm.selectCity(city.cityId, city.cityName); citySheet = false },
                    )
                }
                if (citySearching) item { Text(stringResource(R.string.concert_city_searching), Modifier.padding(20.dp)) }
                else if (popular.isEmpty() && cityResults.isEmpty()) item { Text(stringResource(R.string.concert_no_cities), Modifier.padding(20.dp)) }
            }
        }
    }
    if (filterSheet) ModalBottomSheet(
        onDismissRequest = { filterSheet = false },
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        var localRange by remember { mutableStateOf(range) }; var localGenre by remember { mutableStateOf(genre) }; var localSuggestions by remember { mutableStateOf(suggestions) }
        ConcertSheetHeader(stringResource(R.string.concert_filter_title)) { filterSheet = false }
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        if (tab == "forYou") {
            Row(Modifier.fillMaxWidth().clickable { localSuggestions = !localSuggestions }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.concert_show_recommendations), Modifier.weight(1f), style = CorusFont.body)
                Switch(localSuggestions, { localSuggestions = it })
            }
        }
        Text(stringResource(R.string.concert_date_filter).uppercase(), Modifier.padding(horizontal = 20.dp, vertical = 10.dp), style = CorusFont.sectionHeader, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf("any" to R.string.concert_any_date, "7d" to R.string.concert_next_7, "30d" to R.string.concert_next_30, "90d" to R.string.concert_next_90).forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().clickable { localRange = value }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(label), Modifier.weight(1f), style = CorusFont.body)
                if (localRange == value) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(stringResource(R.string.concert_genre_filter).uppercase(), Modifier.padding(horizontal = 20.dp, vertical = 10.dp), style = CorusFont.sectionHeader, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (listOf<String?>(null) + genres).forEach { value ->
            Row(Modifier.fillMaxWidth().clickable { localGenre = value }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value ?: stringResource(R.string.concert_any_genre), Modifier.weight(1f), style = CorusFont.body)
                if (localGenre == value) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        }
        Button(
            onClick = { vm.setFilters(localRange, localGenre, localSuggestions); filterSheet = false },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            shape = CircleShape,
        ) { Text(stringResource(R.string.concert_show_concerts), style = CorusFont.bodyMedium, modifier = Modifier.padding(vertical = 4.dp)) }
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
                Text(date?.month?.getDisplayName(java.time.format.TextStyle.SHORT, appLocale)?.uppercase(appLocale) ?: "", color = MaterialTheme.colorScheme.primary, style = CorusFont.captionMedium)
                Text(date?.dayOfMonth?.toString() ?: "", style = CorusFont.custom(700, 26))
            }
            VerticalDivider(Modifier.height(48.dp).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (show.suggestionSource == "tasteMatches") Text(stringResource(R.string.concert_you_might_like), color = MaterialTheme.colorScheme.primary, style = CorusFont.captionMedium)
                Text(show.matchedArtist ?: show.lineup.firstOrNull() ?: show.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = CorusFont.custom(500, 17))
                if (show.matchedArtist != null && show.matchedArtist != show.title) Text(show.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = CorusFont.caption)
                Text("${show.venue} · ${show.city}", maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = CorusFont.caption)
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
    onThread: (String) -> Unit, onProfile: (String) -> Unit,
    vm: ConcertsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val show by vm.show.collectAsState(); val attendance by vm.attendance.collectAsState()
    val friends by vm.friends.collectAsState(); val error by vm.error.collectAsState()
    val detailLoading by vm.detailLoading.collectAsState(); val detailError by vm.detailError.collectAsState()
    var menu by remember { mutableStateOf(false) }; var peopleSheet by remember { mutableStateOf(false) }
    var inviteSheet by remember { mutableStateOf(false) }; var venueSheet by remember { mutableStateOf(false) }
    var calendarSheet by remember { mutableStateOf(false) }; var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    var inviteError by remember { mutableStateOf(false) }
    LaunchedEffect(eventId) { vm.open(eventId) }
    if (!vm.enabled) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.concert_unavailable)) }; return }
    val concert = show
    if (concert == null) {
        if (detailLoading) ConcertDetailSkeleton(onBack)
        else if (detailError) ConcertListEmptyState(Icons.Default.Refresh, R.string.concert_unavailable, null, R.string.concert_retry, { vm.open(eventId) })
        return
    }
    val past = ConcertCalendarPolicy.hasStarted(concert.date, concert.time, concert.timezone)
    val unavailable = concert.eventStatus in listOf("canceled", "postponed")
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { Box(Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        ConcertCircleButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.share_back)) }
        Text(stringResource(R.string.concert_label), style = CorusFont.screenTitle)
        Box(Modifier.align(Alignment.CenterEnd)) {
            ConcertCircleButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, null) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.concert_go_to_artist)) }, onClick = { menu = false; concert.lineup.firstOrNull()?.let { name -> vm.resolveArtist(name) { route -> if (route != null) onArtist(route) } } })
                DropdownMenuItem(text = { Text(stringResource(R.string.concert_share)) }, onClick = {
                    menu = false
                    vm.share(concert) { path ->
                        if (path == null) android.widget.Toast.makeText(context, R.string.concert_unavailable, android.widget.Toast.LENGTH_SHORT).show()
                        else {
                            val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://corus.fm$path")
                            context.startActivity(Intent.createChooser(intent, null))
                            vm.log("share_tapped", concert, "concert_detail")
                        }
                    }
                })
            }
        }
    } }, bottomBar = {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { inviteSheet = true; vm.log("invite_opened", concert, "concert_detail") },
                    Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Icon(Icons.Default.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.concert_invite), style = CorusFont.bodyMedium) }
                if (concert.eventStatus != "postponed") {
                    Button(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(concert.url))) },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                    ) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.concert_tickets), style = CorusFont.bodyMedium)
                    }
                }
            }
        }
    }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { Box(Modifier.fillMaxWidth().aspectRatio(5f / 3f).padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(concert.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = .7f)))))
            Row(
                Modifier.align(Alignment.BottomStart).clickable { concert.lineup.firstOrNull()?.let { name -> vm.resolveArtist(name) { route -> route?.let(onArtist) } } }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(concert.lineup.firstOrNull() ?: concert.title, color = androidx.compose.ui.graphics.Color.White, style = CorusFont.songTitleLarge, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = .82f))
            }
        } }
        item { Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if ((concert.lineup.firstOrNull() ?: concert.title) != concert.title) Text(concert.title, style = CorusFont.songTitleLarge)
            ConcertDetailInfoRow(Icons.Default.CalendarMonth, stringResource(R.string.concert_date_time), formatConcertDate(concert))
            ConcertDetailInfoRow(
                Icons.Default.LocationOn,
                stringResource(R.string.concert_venue),
                concert.venue,
                "${concert.city}, ${concert.region}",
                onClick = { venueSheet = true },
            )
            if (vm.calendarEnabled && ConcertCalendarPolicy.startMillis(concert.date, concert.time, concert.timezone) != null) {
                Row(
                    Modifier.fillMaxWidth().clickable { calendarSheet = true; vm.log("calendar_opened", concert, "concert_detail") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.CalendarMonth, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.concert_add_calendar), style = CorusFont.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } }
        item { Box(Modifier.padding(horizontal = 20.dp)) { ConcertPlansCard(attendance, past, unavailable, onChoice = vm::updatePlan, onPeople = { peopleSheet = true; vm.log("people_opened", concert, "concert_detail") }) } }
        if (concert.lineup.isNotEmpty()) item { Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.concert_lineup), style = CorusFont.songTitleLarge)
            Spacer(Modifier.height(12.dp))
            concert.lineup.forEach { artist ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.resolveArtist(artist) { route -> if (route != null) onArtist(route) } }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.MusicNote, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(artist, style = CorusFont.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.concert_lineup_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = CorusFont.caption)
        } }
        if (!concert.info.isNullOrBlank() || !concert.pleaseNote.isNullOrBlank()) item {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.concert_event_info), style = CorusFont.bodyMedium)
                    Text(listOfNotNull(concert.info, concert.pleaseNote).joinToString("\n\n"), maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis, style = CorusFont.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { expanded = !expanded; vm.log("event_info_toggled", concert, "concert_detail") }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(if (expanded) R.string.concert_show_less else R.string.concert_read_more), color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
        if (error) item { Text(stringResource(R.string.concert_update_error), Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
    } }
    if (peopleSheet) ModalBottomSheet(onDismissRequest = { peopleSheet = false }, containerColor = MaterialTheme.colorScheme.background) { ConcertSheetHeader(stringResource(R.string.concert_plans_title)) { peopleSheet = false }
        attendance?.people?.forEach { person -> ListItem(headlineContent = { Text(person.name) }, supportingContent = { Text("@${person.username}") }, trailingContent = { Text(stringResource(if (person.status == "going") { if (past) R.string.concert_went else R.string.concert_going_section } else { if (past) R.string.concert_was_interested else R.string.concert_interested_section })) }, leadingContent = { AsyncImage(person.avatarUrl, null, Modifier.size(42.dp).clip(CircleShape), contentScale = ContentScale.Crop) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background), modifier = Modifier.clickable { peopleSheet = false; onProfile(person.id) }) }
        if (attendance?.nextCursor != null) TextButton(onClick = { vm.loadMorePeople() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.concert_see_more)) }
    }
    if (venueSheet) ModalBottomSheet(onDismissRequest = { venueSheet = false }, containerColor = MaterialTheme.colorScheme.background) { val address = listOfNotNull(concert.address ?: concert.venue, concert.city, concert.region).joinToString(", ")
        ConcertSheetHeader(concert.venue) { venueSheet = false }
        Text(address, Modifier.padding(horizontal = 20.dp), style = CorusFont.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ListItem(headlineContent = { Text(stringResource(R.string.concert_copy_address)) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background), modifier = Modifier.clickable { (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Address", address)); venueSheet = false })
        ListItem(headlineContent = { Text(stringResource(R.string.concert_google_maps)) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background), modifier = Modifier.clickable { val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}")).setPackage("com.google.android.apps.maps")
            runCatching { context.startActivity(mapIntent) }.recoverCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(address)}"))) }
            venueSheet = false })
    }
    if (calendarSheet && vm.calendarEnabled) ModalBottomSheet(onDismissRequest = { calendarSheet = false }, containerColor = MaterialTheme.colorScheme.background) { ConcertSheetHeader(stringResource(R.string.concert_add_calendar)) { calendarSheet = false }
        if (runCatching { context.packageManager.getPackageInfo("com.google.android.calendar", 0) }.isSuccess) ListItem(headlineContent = { Text(stringResource(R.string.concert_google_calendar)) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background), modifier = Modifier.clickable { openCalendar(context, concert, "google"); calendarSheet = false })
        ListItem(headlineContent = { Text(stringResource(R.string.concert_device_calendar)) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background), modifier = Modifier.clickable { openCalendar(context, concert, "device"); calendarSheet = false })
    }
    if (inviteSheet) ModalBottomSheet(onDismissRequest = { inviteSheet = false }, containerColor = MaterialTheme.colorScheme.background) { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        ConcertSheetHeader(stringResource(R.string.concert_invite)) { inviteSheet = false }
        OutlinedTextField(query, { query = it; vm.searchFriends(it) }, label = { Text(stringResource(R.string.concert_search_people)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.concert_optional_message)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        if (inviteError) Text(stringResource(R.string.concert_invite_error), color = MaterialTheme.colorScheme.error)
        friends.forEach { friend -> ListItem(headlineContent = { Text(friend.displayName ?: friend.username) }, supportingContent = { Text("@${friend.username}") }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background), modifier = Modifier.clickable { vm.sendInvite(friend, note, { threadId -> inviteSheet = false; onThread(threadId) }, { inviteError = true }) }) }
    } }
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
