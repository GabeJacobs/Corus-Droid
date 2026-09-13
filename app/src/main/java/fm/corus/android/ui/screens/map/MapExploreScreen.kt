package fm.corus.android.ui.screens.map

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import fm.corus.android.ui.components.parityCopy
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.InlineYouTubePlayer
import fm.corus.android.ui.screens.subscription.CymbalClubOfferSheet
import fm.corus.android.ui.screens.subscription.PaywallSource
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapExploreScreen(
    onBack: () -> Unit, onUser: (CymbalUser) -> Unit, onPost: (CymbalPost) -> Unit,
    onChat: (String) -> Unit, onPaywall: (String) -> Unit,
    onComments: (String) -> Unit, onRepost: (CymbalPost) -> Unit,
    model: MapExploreViewModel = hiltViewModel(),
) {
    DisposableEffect(model) {
        model.repository.event("opened")
        onDispose { model.repository.event("closed") }
    }
    val state by model.state.collectAsState()
    val fullAccess by model.subscription.hasFullAccessFlow.collectAsState()
    val revision by model.remote.revision.collectAsState()
    val mapKitToken = model.remote.mapKitJsToken
    val context = LocalContext.current
    val hapticView = androidx.compose.ui.platform.LocalView.current
    val prefs = remember { context.getSharedPreferences("map_onboarding", Context.MODE_PRIVATE) }
    val listenHintKey = "listen_hint_dismissed.${model.repository.currentUserId}"
    var listenHintDismissed by remember(listenHintKey) { mutableStateOf(prefs.getBoolean(listenHintKey, false)) }
    fun dismissListenHint() {
        listenHintDismissed = true
        prefs.edit().putBoolean(listenHintKey, true).apply()
    }

    var dialog by rememberSaveable { mutableStateOf(if (prefs.getBoolean("intro.${model.repository.currentUserId}", false)) "" else "intro") }
    var resolvingCity by remember { mutableStateOf(false) }
    var sharingAnchorFrozen by remember { mutableStateOf(false) }
    var sharingAnchor by remember { mutableStateOf<MapCity?>(null) }
    var view by rememberSaveable { mutableStateOf("map") }
    LaunchedEffect(view) { model.repository.event("view_changed", view) }
    var browsingCityId by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var citySheetVisible by remember { mutableStateOf(false) }
    var citySheetFocusRevision by remember { mutableIntStateOf(0) }
    var mapViewportHeightPx by remember { mutableIntStateOf(0) }
    var mapHeaderHeightPx by remember { mutableIntStateOf(0) }
    // Every upgrade entry point owned by the map uses the standard Club bottom
    // sheet. Keeping its source here preserves the contextual copy while the
    // presentation, rounded top corners, and legal footer stay consistent.
    var mapPaywallSource by rememberSaveable { mutableStateOf<PaywallSource?>(null) }
    fun showMapPaywall(source: String) {
        mapPaywallSource = when (source) {
            "MAP" -> PaywallSource.MAP
            else -> PaywallSource.entries.firstOrNull { it.name == source }
        }
        // Preserve the app-level route only for a genuinely unknown source.
        if (mapPaywallSource == null) onPaywall(source)
    }
    val scope = rememberCoroutineScope()
    var audience by rememberSaveable { mutableStateOf("off") }
    var pendingMode by rememberSaveable { mutableStateOf("listen") }
    var countries by remember { mutableStateOf(model.selectedCountries) }
    LaunchedEffect(countries) { model.selectedCountries = countries }
    var query by rememberSaveable { mutableStateOf("") }
    var countryQuery by rememberSaveable { mutableStateOf("") }
    var chosenCity by remember { mutableStateOf<MapCity?>(null) }
    var locationAction by remember { mutableStateOf<((Location) -> Unit)?>(null) }
    val listState = rememberLazyListState()
    val cities = state.cities.filter { (it.facets[state.filter]?.count ?: 0) > 0 }
    var sharedCityFocus by remember { mutableStateOf<MapCity?>(null) }
    var shareFocusRevision by remember { mutableIntStateOf(0) }
    fun focusSharedCity(city: MapCity) {
        sharingAnchorFrozen = false
        sharingAnchor = null
        sharedCityFocus = city
        shareFocusRevision++
        browsingCityId = city.cityId
        model.closeCity()
        model.filter("all")
        view = "map"
    }
    LaunchedEffect(browsingCityId, state.selected?.cityId, state.playing?.city?.cityId) {
        val focus = sharedCityFocus ?: return@LaunchedEffect
        if ((browsingCityId != null && browsingCityId != focus.cityId) ||
            (state.selected != null && state.selected?.cityId != focus.cityId) ||
            (state.playing != null && state.playing?.city?.cityId != focus.cityId)) sharedCityFocus = null
    }
    val browsingCity = cities.firstOrNull { it.city.cityId == browsingCityId }?.city ?: mapFocusCity(cities, state.filter, state.ownCity)?.city
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    var listener by remember { mutableStateOf<LocationListener?>(null) }
    fun locate() {
        try {
            listener?.let(locationManager::removeUpdates)
            val next = object : LocationListener { override fun onLocationChanged(location: Location) { locationManager.removeUpdates(this); listener = null; locationAction?.invoke(location); locationAction = null } }
            listener = next
            val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            locationManager.requestSingleUpdate(provider, next, Looper.getMainLooper())
        } catch (_: SecurityException) { resolvingCity = false; dialog = "location"; model.error("Allow location to share your city.") }
        catch (_: Exception) { resolvingCity = false; dialog = "location"; model.error("We couldn’t determine your city. Try again when location is available.") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> if (result.values.any { it }) locate() else { resolvingCity = false; dialog = "location"; model.error("Allow location to share your city.") } }
    fun requestLocation(sharing: Boolean = false, action: (Location) -> Unit) {
        if (resolvingCity) return
        model.error(null)
        if (sharing) {
            sharingAnchor = mapFocusCity(cities, state.filter, state.ownCity)?.city
            sharingAnchorFrozen = true
            resolvingCity = true
        }
        locationAction = action
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(listener) {
        val request = listener ?: return@LaunchedEffect
        delay(20_000)
        if (listener === request) { locationManager.removeUpdates(request); listener = null; locationAction = null; resolvingCity = false; dialog = "location"; model.error("We couldn’t determine your city. Try again when location is available.") }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var left = false
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) left = true
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && left) model.returnedFromPaywall()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) { onDispose { listener?.let(locationManager::removeUpdates) } }
    LaunchedEffect(Unit) { while (true) { delay(30000); model.expireDeviceCity() } }
    DisposableEffect(lifecycleOwner, locationManager) {
        val observerLocation = object : LocationListener {
            override fun onLocationChanged(location: Location) { model.updateDeviceCity(location) }
            override fun onProviderDisabled(provider: String) { model.clearDeviceCity() }
        }
        fun stopLocation() { locationManager.removeUpdates(observerLocation); model.clearDeviceCity() }
        fun startLocation() {
            stopLocation()
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
            try {
                val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                locationManager.requestLocationUpdates(provider, 60000L, 0f, observerLocation, Looper.getMainLooper())
            } catch (_: SecurityException) { model.clearDeviceCity() }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) startLocation()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) stopLocation()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) startLocation()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); stopLocation() }
    }

    LaunchedEffect(state.ownAudience, state.ownCity) { audience = if (state.ownCity != null) state.ownAudience else model.repository.savedAudience(); chosenCity = state.ownCity }
    LaunchedEffect(listState, state.selected?.cityId, state.people) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { row -> state.people.getOrNull(row.index)?.user?.id } }
            .collect { ids -> model.visiblePeople(ids) }
    }
    LaunchedEffect(query) { model.search(query) }
    LaunchedEffect(state.paywall) {
        state.paywall?.let { source ->
            model.dismissPaywall()
            showMapPaywall(source)
        }
    }
    LaunchedEffect(state.selected?.cityId) {
        citySheetVisible = state.selected != null
        citySheetFocusRevision++
    }
    fun dismissCitySheet() {
        if (!citySheetVisible) return
        citySheetVisible = false
        scope.launch {
            // Let the sheet finish its exit before clearing the selected pin so
            // the map and city marker keep their geometry during the motion.
            delay(180)
            model.closeCity()
        }
    }
    BackHandler(mapPaywallSource != null || dialog.isNotEmpty() || state.selected != null || state.mode != null) { when { mapPaywallSource != null -> mapPaywallSource = null; dialog.isNotEmpty() -> { if (dialog != "intro" && dialog != "confirm" && !state.busy) dialog = "" }; state.selected != null -> dismissCitySheet(); else -> model.stop() } }
    if (!model.enabled) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = onBack) { Text(parityCopy("Back to Search")) } }; return }
    Box(Modifier.fillMaxSize().background(CorusColors.Background).onSizeChanged { mapViewportHeightPx = it.height }) {
        if (view == "map") CityMapView(state.cities, state.filter, state.selected, state.playing?.city, Modifier.fillMaxSize(), sessionKey = state.sessionKey, showArtwork = true, playbackMode = state.mode, loadLatest = model.repository::latest, mapKitToken = mapKitToken, focusOverride = sharedCityFocus, focusRevision = shareFocusRevision + citySheetFocusRevision, citySheetOpen = citySheetVisible && state.selected != null && state.playing == null, mapTopInsetFraction = if (mapViewportHeightPx > 0) mapHeaderHeightPx.toFloat() / mapViewportHeightPx else 0f, citySheetHeightFraction = if (expanded) .9f else .52f, anchor = if (sharingAnchorFrozen) sharingAnchor else if (state.ownPresenceReady) mapFocusCity(cities, state.filter, state.ownCity)?.city else null, initialCamera = model.savedCamera, onCameraChanged = { model.savedCamera = it }, browsing = browsingCityId?.let { id -> cities.firstOrNull { it.city.cityId == id }?.city }) { browsingCityId = it.cityId; model.select(it) }
        // iOS keeps all controls in one compact, opaque three-row header.
        Column(Modifier.fillMaxWidth().background(CorusColors.Background).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).onSizeChanged { mapHeaderHeightPx = it.height }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(48.dp), colors = IconButtonDefaults.iconButtonColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(28.dp)) }
                Text(parityCopy("Map"), style = CorusFont.songTitleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(
                    enabled = !resolvingCity,
                    onClick = { audience = if (state.ownCity != null) state.ownAudience else model.repository.savedAudience(); dialog = "audience" },
                    modifier = Modifier.align(Alignment.CenterEnd).size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                ) {
                    val sharing = state.ownCity != null && state.ownAudience != "off"
                    Icon(
                        when {
                            !sharing -> Icons.Default.LocationOff
                            state.ownAudience == "following" -> Icons.Default.People
                            else -> Icons.Default.Public
                        },
                        contentDescription = parityCopy(when { !sharing -> "No one"; state.ownAudience == "following" -> "People I follow"; else -> "Everyone" }),
                        modifier = Modifier.size(24.dp),
                        tint = if (sharing) CorusColors.Accent else CorusColors.Secondary,
                    )
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("all" to "All", "following" to "Following", "tasteMatches" to "Taste matches").forEach { (value, label) ->
                    val selected = state.filter == value
                    TextButton(onClick = {
                        if (value == "tasteMatches" && !fullAccess) {
                            // Open the shared bottom-sheet offer directly. This avoids
                            // navigating through the full-screen Club destination.
                            mapPaywallSource = PaywallSource.MAP
                            model.filter(value)
                        } else {
                            val accepted = value != state.filter
                            model.filter(value)
                            if (accepted) hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }, modifier = Modifier.then(if (!selected) Modifier.border(1.dp, CorusColors.Divider, CircleShape) else Modifier).height(if (selected) 38.dp else 36.dp), shape = CircleShape, contentPadding = PaddingValues(horizontal = 13.dp), colors = ButtonDefaults.textButtonColors(containerColor = if (selected) CorusColors.Accent else androidx.compose.ui.graphics.Color.Transparent, contentColor = if (selected) androidx.compose.ui.graphics.Color.White else CorusColors.Secondary)) { Text(parityCopy(label), style = CorusFont.caption, maxLines = 1) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val sharedCity = state.ownCity?.takeIf { state.ownAudience != "off" }
                    if (sharedCity != null) {
                        Text("${parityCopy("Sharing:")} ${sharedCity.cityName}", modifier = Modifier.weight(1f, fill = false), style = CorusFont.captionMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(
                        onClick = { audience = if (sharedCity != null) state.ownAudience else model.repository.savedAudience(); dialog = "audience" },
                        contentPadding = PaddingValues(horizontal = if (sharedCity == null) 0.dp else 6.dp),
                    ) {
                        Text(parityCopy(if (sharedCity == null) "Share your city" else "Change"), style = CorusFont.captionMedium, color = CorusColors.Accent, maxLines = 1)
                    }
                }
                Surface(shape = CircleShape, color = CorusColors.CardBackground) { Row(Modifier.padding(2.dp)) {
                    listOf("map" to "Map", "list" to "List").forEach { (value, label) -> val selected = view == value; TextButton(onClick = { if (view != value) { view = value; hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) } }, shape = CircleShape, modifier = Modifier.height(34.dp).width(62.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(containerColor = if (selected) CorusColors.Accent else androidx.compose.ui.graphics.Color.Transparent, contentColor = if (selected) androidx.compose.ui.graphics.Color.White else CorusColors.Secondary)) { Text(parityCopy(label), style = CorusFont.caption) } }
                } }
            }
            if (resolvingCity) {
                Row(Modifier.fillMaxWidth().background(CorusColors.CardBackground, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = CorusColors.Accent, strokeWidth = 2.dp)
                    Text(parityCopy("Finding your city"), style = CorusFont.caption)
                }
            }
        }
        state.playing?.takeIf { !fullAccess && view == "map" && state.mode == "watch" }?.let {
            val mode = state.mode ?: "listen"
            val remaining = state.preview.remaining(mode)
            Surface(onClick = { mapPaywallSource = if (mode == "listen") PaywallSource.MAP_LISTEN else PaywallSource.MAP_WATCH }, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = if (resolvingCity) 210.dp else 158.dp).fillMaxWidth(), color = CorusColors.Accent) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (mode == "listen") Icons.Default.Headphones else Icons.Default.Movie, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
                    Text(if (remaining == 0) parityCopy(if (mode == "listen") "Last free song · Explore Club" else "Last free trailer · Explore Club") else parityCopy(if (mode == "listen") "Listen preview · %lld songs left · Explore Club" else "Watch preview · %lld trailers left · Explore Club").replace("%lld", remaining.toString()), style = CorusFont.caption, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (!state.loading && cities.isEmpty()) Surface(Modifier.align(Alignment.Center).padding(24.dp), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(20.dp)) { Text(parityCopy("No people to show for this filter.")); TextButton(onClick = { model.refresh() }) { Text(parityCopy("Retry")) } } }
        if (view == "list" && state.selected == null && state.playing == null) {
            Surface(Modifier.fillMaxSize().padding(top = if (resolvingCity) 218.dp else 166.dp, bottom = 80.dp), color = CorusColors.Background) {
                MapPeopleDirectory(cities, state, model, onUser) { city, chat -> if(chat.member) onChat(chat.threadId) else requestLocation { model.join(city,it,onChat) } }
            }
        }
        if (state.selected == null && state.playing == null && view == "map" && browsingCity != null) Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 76.dp), shape = CircleShape, color = CorusColors.CardBackground.copy(alpha = .88f), shadowElevation = 4.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                fun browse(delta: Int) { val i = cities.indexOfFirst { it.city.cityId == browsingCity.cityId }; browsingCityId = cities[(i + delta + cities.size) % cities.size].city.cityId }
                IconButton(onClick = { browse(-1) }, enabled = cities.size > 1) { Icon(Icons.Default.ChevronLeft, "Previous city") }
                TextButton(onClick = { model.select(browsingCity) }) { Text(browsingCity.cityName, color = CorusColors.Text, maxLines = 1, modifier = Modifier.widthIn(max = 180.dp)) }
                IconButton(onClick = { browse(1) }, enabled = cities.size > 1) { Icon(Icons.Default.ChevronRight, "Next city") }
            }
        }
        if (!listenHintDismissed && state.selected == null && state.playing == null && view == "map") {
            Surface(onClick = { dismissListenHint(); pendingMode = "listen"; dialog = "countries" }, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 210.dp), shape = CircleShape, color = CorusColors.Accent, shadowElevation = 4.dp) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Default.Headphones, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
                    Text(parityCopy("Try Listen Mode"), style = CorusFont.caption, color = androidx.compose.ui.graphics.Color.White)
                    Icon(Icons.Default.Close, contentDescription = "Dismiss Listen Mode hint", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp).clickable { dismissListenHint() })
                }
            }
        }
        if (state.selected == null && state.playing == null && view == "map") Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MapGlassModeButton("Listen", Icons.Default.Headphones) { pendingMode = "listen"; dialog = "countries" }
            MapGlassModeButton("Watch", Icons.Default.Movie) { pendingMode = "watch"; dialog = "countries" }
        }
        state.selected?.takeIf { state.playing == null }?.let { city ->
            // Keep the directory in the map hierarchy. Unlike a Dialog, this
            // leaves the exposed map live for panning, zooming and tapping.
            AnimatedVisibility(
                visible = citySheetVisible,
                // Enter and leave from below the map. Keep this separate from
                // the map-camera transition, which may pan left or right.
                enter = slideInVertically(animationSpec = tween(280), initialOffsetY = { it }) + fadeIn(tween(180)),
                exit = slideOutVertically(animationSpec = tween(220), targetOffsetY = { it }) + fadeOut(tween(140)),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (expanded) .9f else .52f)
                    // Consume sheet taps while leaving every exposed map pixel live.
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {},
                color = CorusColors.Background,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 12.dp,
            ) {
                Column {
                    var dragDistance by remember { mutableFloatStateOf(0f) }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { dragDistance = 0f },
                                    onVerticalDrag = { _, amount -> dragDistance += amount },
                                    onDragEnd = {
                                        when {
                                            dragDistance > 72f -> dismissCitySheet()
                                            dragDistance < -72f -> expanded = true
                                            else -> expanded = !expanded
                                        }
                                    },
                                )
                            }
                            .clickable { expanded = !expanded },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(CorusColors.Secondary.copy(alpha = .4f))) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        fun step(delta: Int) { val i = cities.indexOfFirst { it.city.cityId == city.cityId }; if (cities.isNotEmpty()) model.select(cities[(i + delta + cities.size) % cities.size].city) }
                        IconButton(onClick = { step(-1) }, enabled = cities.size > 1) { Icon(Icons.Default.ChevronLeft, "Previous city") }
                        val peopleCount = cities.firstOrNull { it.city.cityId == city.cityId }?.facets?.get(state.filter)?.count ?: 0
                        val country = java.util.Locale("", city.countryCode).displayCountry.ifBlank { city.countryCode }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(city.cityName, style = CorusFont.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 1)
                            Text("${city.regionName}, $country · $peopleCount ${if (peopleCount == 1) "person" else "people"}", style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { step(1) }, enabled = cities.size > 1) { Icon(Icons.Default.ChevronRight, "Next city") }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Chat membership loads independently. It must not replace
                        // the city’s primary action while that request is in flight.
                        FilledTonalButton(onClick = { model.start("listen", emptySet(), city.cityId) }, enabled = !state.busy, modifier = Modifier.weight(1f).height(44.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = CorusColors.Accent.copy(alpha = .12f), contentColor = CorusColors.Accent)) { Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(parityCopy("Listen"), style = CorusFont.bodyMedium) }
                        if (!state.chatLoading) {
                            state.chat?.takeIf { showMapChat(city.cityId, state.currentDeviceCityId, it) }?.let { chat -> Button(onClick = { if (chat.member) onChat(chat.threadId) else requestLocation { model.join(city, it, onChat) } }, modifier = Modifier.weight(1f), enabled = !state.busy) { Text(if (chat.member) "Open chat" else "Join chat") } }
                        }
                    }
                    LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                        if (state.peopleLoading && state.people.isEmpty()) {
                            val count = cities.firstOrNull { it.city.cityId == city.cityId }?.facets?.get(state.filter)?.count
                            items((count ?: 3).coerceIn(1, 5)) { MapPersonRowSkeleton() }
                        }
                        items(state.people, key = { it.user.id }) { person ->
                            val post = state.posts[person.user.id]
                            Row(Modifier.fillMaxWidth().clickable { onUser(person.user) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AsyncImage(model = person.user.avatarThumbURL ?: person.user.avatarURL, contentDescription = null, modifier = Modifier.size(44.dp).clip(CircleShape))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    UsernameWithFlair(username = person.user.username, isVerified = person.user.isVerified, isClubMember = person.user.isClubMember, flairStyle = person.user.flairStyle, isBot = person.user.isBot, showAtPrefix = true, flairYOffset = (-1).dp, flairSpacing = 2.dp)
                                    Text(parityCopy("Latest post"), style = CorusFont.caption, color = CorusColors.Secondary)
                                    Text(post?.let { if (it.isMovie) it.movieTitle.orEmpty() else listOf(it.track.name, it.track.artistName).filter { value -> value.isNotBlank() }.joinToString(" · ") } ?: person.user.displayName, style = CorusFont.captionMedium, color = if (post != null) CorusColors.Text else CorusColors.Secondary, minLines = 1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                AsyncImage(model = post?.displayImageURL, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                                Icon(Icons.Default.ChevronRight, contentDescription = "View profile", tint = CorusColors.Tertiary)
                            }; HorizontalDivider(color = CorusColors.Divider)
                        }
                        if (state.more != null) item { TextButton(onClick = { model.more() }, enabled = !state.peopleLoading) { Text(parityCopy("Load more people")) } }
                        else if (canInviteMapCluster(state, city, MapPeoplePage(state.people, state.more, state.peopleReachedEnd),
                            state.peopleLoading, state.error != null, model.repository.currentUserId)) {
                            item(key = "invite:${city.cityId}") { MapClusterInviteFooter() }
                        }
                    }
                }
            }
            }
        }
        state.playing?.let { item ->
            // Keep the active map post visually attached to the map rather than
            // presenting it as an opaque page: it sits above the app mini-player
            // as the translucent rounded iOS-style card.
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 480.dp).padding(horizontal = 12.dp, vertical = 20.dp), shape = RoundedCornerShape(26.dp), color = CorusColors.CardBackground.copy(alpha = .92f), shadowElevation = 12.dp) {
                LazyColumn {
                    item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(item.city.cityName, Modifier.weight(1f), style = CorusFont.bodyMedium); IconButton(onClick = { model.stop() }) { Icon(Icons.Default.Close, "Stop") } } }
                    if (!fullAccess && state.mode == "listen") item {
                        val remaining = state.preview.remaining("listen")
                        Surface(onClick = { mapPaywallSource = PaywallSource.MAP_LISTEN }, modifier = Modifier.fillMaxWidth(), color = CorusColors.Accent) { Row(Modifier.padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Default.Headphones, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp)); Text(if (remaining == 0) parityCopy("Last free song · Explore Club") else parityCopy("Listen preview · %lld songs left · Explore Club").replace("%lld", remaining.toString()), style = CorusFont.caption, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = androidx.compose.ui.graphics.Color.White) } }
                    }
                    if (state.mode == "watch" && !state.blocked) item { val id = fm.corus.android.ui.components.youTubeVideoID(item.post.trailerURL); if (id != null) key(item.post.id) { InlineYouTubePlayer(videoID = id, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), showControls = true, onEnded = { model.ended(item.post.id) }, onStarted = { model.started(item.post.id, "watch") }) } }
                    item {
                        AnimatedContent(targetState = item.post, contentKey = { it.id }, transitionSpec = {
                            val duration = if(android.animation.ValueAnimator.areAnimatorsEnabled()) 160 else 0
                            (fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = .985f)) togetherWith (fadeOut(tween(duration)) + scaleOut(tween(duration), targetScale = .985f))
                        }, label = "mapPostTransition") { animatedPost -> Column {
                        Row(Modifier.fillMaxWidth().clickable { onUser(animatedPost.user) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { AsyncImage(model = animatedPost.user.avatarURL, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape)); UsernameWithFlair(username = animatedPost.user.username, isVerified = animatedPost.user.isVerified, isClubMember = animatedPost.user.isClubMember, flairStyle = animatedPost.user.flairStyle, isBot = animatedPost.user.isBot, showAtPrefix = true) }
                        Column(Modifier.fillMaxWidth().clickable { onPost(animatedPost) }.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (animatedPost.isTrack) AsyncImage(model = animatedPost.displayImageURL, contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)))
                                Column(Modifier.weight(1f)) {
                                    Text(if (animatedPost.isMovie) animatedPost.movieTitle.orEmpty() else animatedPost.track.name, style = CorusFont.bodyMedium, maxLines = 2)
                                    Text(if (animatedPost.isMovie) animatedPost.directorName.orEmpty() else animatedPost.track.artistName, color = CorusColors.Secondary, style = CorusFont.caption, maxLines = 1)
                                }
                            }
                            animatedPost.caption?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 3, color = CorusColors.Secondary, style = CorusFont.caption) }
                        }
                        } }
                        MapPostEngagement(item.post, onComments = { onComments(item.post.id) }, onRepost = onRepost, onPaywall = { mapPaywallSource = PaywallSource.SAVE_LIMIT })
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { TextButton(onClick = { model.previous() }, enabled = state.historyIndex > 0) { Text(parityCopy("Previous")) }; Button(onClick = { model.next() }, enabled = !state.busy) { Text(if (state.busy) "Loading…" else "Next") } }
                    }
                }
            }
        }
        state.error?.let { message -> AlertDialog(onDismissRequest = { model.error(null) }, title = { Text("Please try again") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { model.error(null) }) { Text(parityCopy("OK")) } }) }
    }
    val sharingSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { value -> value != SheetValue.Hidden || (dialog != "intro" && dialog != "confirm" && !state.busy) })
    val clubOfferSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    mapPaywallSource?.let { source -> ModalBottomSheet(
        sheetState = clubOfferSheet,
        onDismissRequest = { mapPaywallSource = null },
        containerColor = CorusColors.Background,
    ) {
        CymbalClubOfferSheet(
            source = source,
            onDismiss = { mapPaywallSource = null },
            onPurchaseSuccess = { if (source == PaywallSource.MAP) model.filter("tasteMatches") },
        )
    } }
    if (dialog.isNotEmpty()) ModalBottomSheet(sheetState = sharingSheet,onDismissRequest = { if (dialog != "intro" && dialog != "confirm" && !state.busy) dialog = "" }, containerColor = CorusColors.Background) {
        AnimatedContent(targetState = dialog, transitionSpec = {
            (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 12 }) togetherWith
                (fadeOut(tween(130)) + slideOutHorizontally(tween(130)) { -it / 16 })
        }, label = "mapSheetStep") { currentDialog ->
            LazyColumn(contentPadding = PaddingValues(start = 24.dp, top = if (currentDialog == "countries") 10.dp else 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (currentDialog) {
                "intro" -> {
                    item { Column(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(68.dp).clip(CircleShape).background(CorusColors.Accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.People, null, tint = CorusColors.Accent, modifier = Modifier.size(32.dp)) }
                        Text(parityCopy("Explore the Corus Map"), style = CorusFont.songTitleLarge)
                    } }
                    listOf(
                        Triple(Icons.Default.People, "Find people by city", "Message, meet up, or go to concerts together."),
                        Triple(Icons.Default.LocationOff, "Share your city, not your exact location.", "People can find you in the city you choose."),
                        Triple(Icons.Default.Lock, "No one sees you until you choose.", "Change this anytime."),
                    ).forEach { (icon, title, body) -> item { MapIntroBenefitRow(icon, title, body) } }
                    item { MapSheetPrimaryButton(onClick = { prefs.edit().putBoolean("intro.${model.repository.currentUserId}", true).apply(); dialog = "audience" }) { Text(parityCopy("Next")) } }
                }
                "audience" -> {
                    item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(parityCopy("Who can see your city"), style = CorusFont.songTitleLarge)
                        Text(parityCopy("People see your city, never your street or a live pin. Change this anytime."), style = CorusFont.caption, color = CorusColors.Secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    } }
                    listOf(
                        Triple("everyone", "Everyone", "Anyone on Corus can see your city."),
                        Triple("following", "People I follow", "Only accounts you follow can see your city."),
                        Triple("off", "No one", "Stay private while you explore the map."),
                    ).forEach { (value, title, subtitle) -> item {
                        MapAudienceOption(value, title, subtitle, audience == value) { audience = value }
                    } }
                    item { MapSheetPrimaryButton(onClick = { model.repository.rememberAudience(audience); if (audience == "off") model.stopSharing { dialog = "" } else {
                        // Sharing is always device-derived: permission, visible resolving
                        // state, then placement in the resolved city.
                        dialog = ""
                        requestLocation(sharing = true) { location ->
                            model.resolveAndShare(location, audience, onShared = ::focusSharedCity) { resolvingCity = false }
                        }
                    } }) { Text(parityCopy("Done")) } }
                }
                "location" -> item { Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(CorusColors.Accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.LocationOn, null, tint = CorusColors.Accent, modifier = Modifier.size(30.dp)) }
                    Text(parityCopy("Location needed to share"), style = CorusFont.songTitleLarge)
                    Text(parityCopy("Corus uses your location only to determine your city. We never show your exact location."), style = CorusFont.caption, color = CorusColors.Secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    MapSheetPrimaryButton(onClick = { dialog = ""; requestLocation(sharing = true) { location -> model.resolveAndShare(location, audience, onShared = ::focusSharedCity) { resolvingCity = false } } }) { Text(parityCopy("Try again")) }
                    TextButton(onClick = { model.repository.rememberAudience("off"); model.stopSharing { dialog = "" } }) { Text(parityCopy("Don’t share my city")) }
                } }
                "countries" -> {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(parityCopy(if (pendingMode == "listen") "Listen Mode" else "Watch Mode"), style = CorusFont.songTitleLarge)
                            Surface(onClick = { dialog = "" }, modifier = Modifier.align(Alignment.CenterStart).size(46.dp), shape = CircleShape, color = CorusColors.CardBackground) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, "Close") } }
                        }
                        Text(parityCopy(if (pendingMode == "listen") "Listen to music posted by people in the countries you choose." else "Watch films posted by people in the countries you choose."), style = CorusFont.bodyMedium, color = CorusColors.Secondary, modifier = Modifier.padding(top = 20.dp, bottom = 16.dp))
                        // Match iOS: Anywhere is a complete selection, not a
                        // filter reset. It starts Listen/Watch Mode globally.
                        Surface(onClick = {
                            countries = emptySet()
                            model.start(pendingMode, emptySet())
                            dialog = ""
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = CorusColors.CardBackground) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("🌍", style = CorusFont.bodyMedium)
                                Column(Modifier.weight(1f)) { Text(parityCopy("Anywhere"), style = CorusFont.bodyMedium); Text(parityCopy(if (pendingMode == "listen") "Music posted by people around the world." else "Films posted by people around the world."), style = CorusFont.caption, color = CorusColors.Secondary) }
                                Text(cities.sumOf { it.facets[state.filter]?.count ?: 0 }.toString(), style = CorusFont.caption, color = CorusColors.Secondary)
                                Icon(Icons.Default.ChevronRight, null, tint = CorusColors.Tertiary)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(parityCopy("Countries"), style = CorusFont.bodyMedium, color = CorusColors.Secondary); TextButton(onClick = { countries = cities.map { it.city.countryCode }.toSet() }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(parityCopy("Select Multiple"), style = CorusFont.caption, color = CorusColors.Accent) } }
                    }
                    items(cities.map { it.city.countryCode }.distinct().sorted().filter { java.util.Locale("", it).displayCountry.contains(countryQuery, true) }) { code ->
                        val count = cities.filter { it.city.countryCode == code }.sumOf { it.facets[state.filter]?.count ?: 0 }
                        Row(Modifier.fillMaxWidth().clickable { if (countries.isEmpty()) { model.start(pendingMode, setOf(code)); dialog = "" } else countries = if (code in countries) countries - code else countries + code }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(countryFlag(code), style = CorusFont.bodyMedium)
                            Text(java.util.Locale("", code).displayCountry, style = CorusFont.bodyMedium, modifier = Modifier.weight(1f))
                            Text(count.toString(), style = CorusFont.caption, color = CorusColors.Secondary)
                            if (countries.isNotEmpty()) Checkbox(checked = code in countries, onCheckedChange = null) else Icon(Icons.Default.ChevronRight, null, tint = CorusColors.Tertiary)
                        }; HorizontalDivider(color = CorusColors.Divider)
                    }
                    item { OutlinedTextField(value = countryQuery, onValueChange = { countryQuery = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(parityCopy("Search countries")) }, singleLine = true) }
                }
                }
            }
        }
    }
}

@Composable
private fun MapIntroBenefitRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = CorusColors.Accent, modifier = Modifier.size(22.dp).padding(top = 1.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(parityCopy(title), style = CorusFont.bodyMedium)
            Text(parityCopy(body), style = CorusFont.caption, color = CorusColors.Secondary)
        }
    }
}

private fun countryFlag(countryCode: String): String = countryCode.uppercase().map {
    String(Character.toChars(it.code + 0x1F1A5))
}.joinToString("")

@Composable
private fun MapAudienceOption(value: String, title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (selected) CorusColors.Accent.copy(alpha = .10f) else CorusColors.CardBackground)
            .clickable(onClick = onSelect).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val icon = when (value) {
            "everyone" -> Icons.Default.Public
            "following" -> Icons.Default.People
            else -> Icons.Default.LocationOff
        }
        Box(Modifier.size(42.dp).clip(CircleShape).background(CorusColors.Accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = CorusColors.Accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(parityCopy(title), style = CorusFont.bodyMedium)
            Text(parityCopy(subtitle), style = CorusFont.caption, color = CorusColors.Secondary)
        }
        RadioButton(selected = selected, onClick = onSelect)
    }
}

/** Shared primary action geometry for the map's onboarding and picker sheets. */
@Composable
private fun MapSheetPrimaryButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp).navigationBarsPadding(),
        shape = RoundedCornerShape(28.dp),
        content = content,
    )
}

/** Mirrors the directory row so loading preserves avatar, text and artwork columns. */
@Composable
private fun MapPersonRowSkeleton() {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(CorusColors.Skeleton))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(.55f).height(20.dp).clip(RoundedCornerShape(4.dp)).background(CorusColors.Skeleton))
                Box(Modifier.fillMaxWidth(.4f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(CorusColors.Skeleton))
                Box(Modifier.fillMaxWidth(.9f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(CorusColors.Skeleton))
            }
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(CorusColors.Skeleton))
            Spacer(Modifier.size(24.dp))
        }
        HorizontalDivider(color = CorusColors.Divider)
    }
}

@Composable
private fun MapGlassModeButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = CorusColors.Text,
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .55f)),
    ) {
        Row(
            Modifier.height(48.dp)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
                    CorusColors.Background.copy(alpha = .88f),
                    CorusColors.Background.copy(alpha = .68f),
                )))
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            Text(parityCopy(label), style = CorusFont.bodyMedium)
        }
    }
}
