package fm.corus.android.ui.screens.map

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import fm.corus.android.ui.components.parityCopy
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.LocalMapCitySheetPresented
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
    initialCityId: String? = null, initialUserIds: List<String> = emptyList(),
    fromProfile: Boolean = false,
    onBack: () -> Unit, onUser: (CymbalUser) -> Unit, onPost: (CymbalPost) -> Unit,
    onChat: (String) -> Unit, onPaywall: (String) -> Unit,
    onComments: (String) -> Unit, onRepost: (CymbalPost) -> Unit,
    model: MapExploreViewModel = hiltViewModel(),
) {
    val openedAt = remember { android.os.SystemClock.elapsedRealtime() }
    DisposableEffect(model) {
        model.repository.event("opened")
        onDispose { model.repository.event("closed", durationMs = android.os.SystemClock.elapsedRealtime() - openedAt) }
    }
    LaunchedEffect(model) { model.beginMapSession() }
    val state by model.state.collectAsState()
    val fullAccess by model.subscription.hasFullAccessFlow.collectAsState()
    val revision by model.remote.revision.collectAsState()
    val mapKitToken = model.remote.mapKitJsToken
    val context = LocalContext.current
    val hapticView = androidx.compose.ui.platform.LocalView.current
    val prefs = remember { context.getSharedPreferences("map_onboarding", Context.MODE_PRIVATE) }

    var dialog by rememberSaveable { mutableStateOf(if (prefs.getBoolean("intro.${model.repository.currentUserId}", false)) "" else "intro") }
    var resolvingCity by remember { mutableStateOf(false) }
    var sharingAnchorFrozen by remember { mutableStateOf(false) }
    var sharingAnchor by remember { mutableStateOf<MapCity?>(null) }
    var view by rememberSaveable { mutableStateOf("map") }
    LaunchedEffect(view) { model.repository.event("view_changed", mode = view) }
    var introWasShown by remember { mutableStateOf(false) }
    LaunchedEffect(dialog) { if (dialog == "intro") { introWasShown = true; model.repository.event("intro_shown") } else if (introWasShown) { introWasShown = false; model.repository.event("intro_dismissed") } }
    var browsingCityId by rememberSaveable { mutableStateOf(initialCityId) }
    // A settled map gesture may change the highlighted city, but must not
    // turn that highlight back into a camera command.
    var preserveRoamingCamera by remember { mutableStateOf(false) }
    var citySheetVisible by remember { mutableStateOf(state.selected != null) }
    var citySheetDetent by rememberSaveable { mutableStateOf(MapCitySheetValue.Peek.name) }
    var pushDestinationApplied by rememberSaveable { mutableStateOf(false) }
    // Keep the transition alive before a selected city's content is mounted.
    // Otherwise selecting the city and making the sheet visible in one event can
    // compose its first frame already open, which reads as a jump rather than a
    // sheet entering from the bottom. Returning from a profile must start
    // already open so the sheet does not replay that enter.
    val citySheetTransition = remember { MutableTransitionState(state.selected != null) }
    val mapCitySheetPresented = LocalMapCitySheetPresented.current
    var citySheetFocusRevision by rememberSaveable { mutableIntStateOf(0) }
    var playbackFocusRevision by rememberSaveable { mutableIntStateOf(0) }
    var mapViewportHeightPx by remember { mutableIntStateOf(0) }
    var mapHeaderHeightPx by remember { mutableIntStateOf(0) }
    var playbackCardHeightPx by remember { mutableIntStateOf(0) }
    var citySheetHeightPx by remember { mutableIntStateOf(0) }
    // Every upgrade entry point owned by the map uses the standard Club bottom
    // sheet. Keeping its source here preserves the contextual copy while the
    // presentation, rounded top corners, and legal footer stay consistent.
    var mapPaywallSource by rememberSaveable { mutableStateOf<PaywallSource?>(null) }
    fun showMapPaywall(source: String, entryPoint: String? = null) {
        mapPaywallSource = when (source) {
            "MAP" -> PaywallSource.MAP
            else -> PaywallSource.entries.firstOrNull { it.name == source }
        }
        // Preserve the app-level route only for a genuinely unknown source.
        if (mapPaywallSource == null) onPaywall(source)
        else model.repository.event(
            "paywall_opened",
            mode = if (source == "MAP_WATCH") "watch" else if (source == "MAP_LISTEN") "listen" else "map",
            value = entryPoint ?: if (source == "MAP") "taste_matches" else "limit",
        )
    }
    val scope = rememberCoroutineScope()
    fun openUser(user: CymbalUser) { model.repository.event("profile_opened", state.mode ?: "map"); onUser(user) }
    fun openPost(post: CymbalPost) { model.repository.event("post_opened", state.mode ?: "map"); onPost(post) }
    fun openChat(threadId: String) { model.repository.event("message_opened", state.mode ?: "map"); onChat(threadId) }
    var audience by rememberSaveable { mutableStateOf("off") }
    var pendingMode by rememberSaveable { mutableStateOf("listen") }
    var countrySelection by remember { mutableStateOf(mapCountryPickerOpened(model.selectedCountries(pendingMode))) }
    var query by rememberSaveable { mutableStateOf("") }
    var countryQuery by rememberSaveable { mutableStateOf("") }
    var chosenCity by remember { mutableStateOf<MapCity?>(null) }
    var locationAction by remember { mutableStateOf<((Location) -> Unit)?>(null) }
    var locationAttempt by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val cities = sortedMapCitiesNear(
        state.cities.filter { (it.facets[state.filter]?.count ?: 0) > 0 },
        state.ownCity,
    )
    var sharedCityFocus by remember { mutableStateOf<MapCity?>(null) }
    var shareFocusRevision by rememberSaveable { mutableIntStateOf(0) }
    // Stopping Listen Mode clears [state.playing], but the map should remain
    // on the city the listener was viewing. iOS leaves its camera untouched on
    // exit; retain this focus until the user starts another mode or selects a
    // different city.
    var listeningExitFocus by remember { mutableStateOf<MapCity?>(null) }
    var previousPlaybackMode by remember { mutableStateOf<String?>(null) }
    fun openCountryPicker(mode: String) {
        pendingMode = mode
        countrySelection = mapCountryPickerOpened(model.selectedCountries(mode))
        countryQuery = ""
        model.repository.event("picker_opened", mode)
        dialog = "countries"
    }
    fun openCitySheet(city: MapCity) {
        preserveRoamingCamera = false
        // Same cluster tap as iOS: keep the open directory, camera, and
        // loaded people instead of pulsing a full reload.
        if (state.selected?.cityId == city.cityId && citySheetVisible && state.playing == null) return
        if (state.selected?.cityId != city.cityId) citySheetDetent = MapCitySheetValue.Peek.name
        listeningExitFocus = null
        // Keep sheet visibility and the selected city in the same Compose
        // transaction. MapKit then receives one sheet-aware camera target,
        // instead of first centering the overview and then zooming again when
        // the sheet becomes visible on the next frame.
        citySheetVisible = true
        citySheetFocusRevision++
        hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        model.select(city)
    }
    LaunchedEffect(cities, initialCityId, initialUserIds, pushDestinationApplied) {
        if (pushDestinationApplied || (initialCityId == null && initialUserIds.isEmpty())) return@LaunchedEffect
        var target = initialCityId?.let { id -> cities.firstOrNull { it.city.cityId == id }?.city }
        if (target == null && initialCityId != null && cities.isNotEmpty()) {
            val resolved = runCatching { model.repository.resolveCityId(initialCityId) }.getOrNull()
            target = resolved?.let { city -> cities.firstOrNull { it.city.cityId == city.cityId }?.city }
        }
        if (target != null) {
            if (!fromProfile) model.filter("following")
            browsingCityId = target.cityId
            openCitySheet(target)
            pushDestinationApplied = true
        } else if (cities.isNotEmpty()) {
            if (initialCityId == null && !fromProfile) model.filter("following")
            pushDestinationApplied = true
        }
    }
    fun focusSharedCity(city: MapCity) {
        preserveRoamingCamera = false
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
    fun applyLocationPresentation(presentation: CityLocationPresentation) {
        resolvingCity = presentation.resolving
        dialog = if (presentation.recoverySheet) "location" else ""
        model.error(presentation.genericError)
    }
    fun locate() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        fun fail(message: String, cause: Throwable? = null) {
            cause?.let { Log.w("MapLocation", message, it) } ?: Log.w("MapLocation", message)
            listener?.let(locationManager::removeUpdates)
            listener = null
            locationAction = null
            // The recovery sheet is the single error surface for a location
            // failure. A generic alert behind it is both redundant and
            // visually broken on Material's modal stack.
            applyLocationPresentation(cityLocationFailed())
        }
        if (!hasFine && !hasCoarse) {
            fail("City location requested without permission")
            return
        }
        try {
            listener?.let(locationManager::removeUpdates)
            // City-level sharing does not need GPS precision. On Samsung and
            // other devices an approximate grant cannot subscribe to GPS, even
            // when the GPS provider is enabled. Prefer the permitted network
            // provider and use GPS only as a precise-location fallback.
            val providers = cityLocationProviders(locationManager.getProviders(true).toSet(), hasFine)
            if (providers.isEmpty()) {
                fail("No permitted location provider enabled for city sharing")
                return
            }
            val recent = providers.mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }.maxByOrNull { it.time }
            if (recent != null && System.currentTimeMillis() - recent.time <= 10 * 60_000L) {
                Log.i("MapLocation", "Using recent ${recent.provider} fix for city sharing")
                locationAction?.invoke(recent)
                locationAction = null
                return
            }
            locationAttempt++
            Log.i("MapLocation", "Requesting city location from ${providers.joinToString()} (attempt=$locationAttempt, fine=$hasFine, coarse=$hasCoarse)")
            val next = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    listener = null
                    locationAction?.invoke(location)
                    locationAction = null
                }
            }
            listener = next
            // Subscribe to every permitted city-level provider and accept the
            // first fix. In particular, a newly granted network provider can
            // need a warm-up cycle on Samsung devices. GPS remains excluded
            // under an approximate-only grant.
            providers.forEach { provider ->
                locationManager.requestLocationUpdates(provider, 0L, 0f, next, Looper.getMainLooper())
            }
        } catch (error: SecurityException) { fail("City location provider rejected granted permission", error) }
        catch (error: Exception) { fail("City location request failed", error) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> if (result.values.any { it }) locate() else { locationAction = null; applyLocationPresentation(cityLocationFailed()) } }
    fun requestLocation(sharing: Boolean = false, action: (Location) -> Unit) {
        if (resolvingCity) return
        model.error(null)
        if (sharing) {
            sharingAnchor = mapFocusCity(cities, state.filter, state.ownCity)?.city
            sharingAnchorFrozen = true
            applyLocationPresentation(cityLocationStarted())
        }
        locationAttempt = 0
        locationAction = action
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(listener) {
        val request = listener ?: return@LaunchedEffect
        delay(CITY_LOCATION_ATTEMPT_TIMEOUT_MS)
        if (listener === request) {
            locationManager.removeUpdates(request)
            listener = null
            if (shouldWarmUpCityLocation(locationAttempt)) {
                Log.i("MapLocation", "City provider still warming; retrying without asking the user")
                locate()
            } else {
                Log.w("MapLocation", "Timed out waiting for city location after $locationAttempt attempts")
                locationAction = null
                applyLocationPresentation(cityLocationFailed())
            }
        }
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
    DisposableEffect(Unit) { onDispose { listener?.let(locationManager::removeUpdates); listener = null; locationAction = null; resolvingCity = false } }
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

    LaunchedEffect(state.ownAudience, state.ownCity) { audience = model.repository.sheetAudience(state.ownAudience, state.ownCity != null); chosenCity = state.ownCity }
    LaunchedEffect(listState, state.selected?.cityId, state.people) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { row -> state.people.getOrNull(row.index)?.user?.id } }
            .collect { ids -> model.visiblePeople(ids) }
    }
    LaunchedEffect(state.selected?.cityId, state.people, initialUserIds, pushDestinationApplied) {
        if (!pushDestinationApplied || initialUserIds.isEmpty()) return@LaunchedEffect
        val index = state.people.indexOfFirst { it.user.id in initialUserIds }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LaunchedEffect(query) { model.search(query) }
    LaunchedEffect(state.paywall) {
        state.paywall?.let { source ->
            model.dismissPaywall()
            showMapPaywall(source)
        }
    }
    LaunchedEffect(state.mode, state.playing?.city?.cityId) {
        if (state.mode == "listen" && previousPlaybackMode != "listen") listeningExitFocus = null
        if (state.mode == "listen") state.playing?.city?.let { listeningExitFocus = it }
        if (previousPlaybackMode == "listen" && state.mode == null) {
            listeningExitFocus?.let { browsingCityId = it.cityId }
        }
        if (state.mode != null && state.mode != "listen") listeningExitFocus = null
        previousPlaybackMode = state.mode
    }
    LaunchedEffect(state.playing?.post?.id) {
        if (state.playing != null) playbackFocusRevision++
        else playbackCardHeightPx = 0
    }
    LaunchedEffect(state.selected?.cityId) {
        // Covers state restoration or any future selection path that does not
        // pass through openCitySheet. Normal taps set this synchronously above.
        if (state.selected != null && !citySheetVisible) {
            citySheetVisible = true
            citySheetFocusRevision++
        } else if (state.selected == null) {
            // Starting Listen Mode clears the selected city. Clear the local
            // sheet flag too, otherwise it can keep the player and tab bar
            // suppressed after a failed Listen attempt.
            citySheetVisible = false
        }
    }
    LaunchedEffect(citySheetVisible, state.selected?.cityId) {
        // Keep the app's persistent player and tab bar behind the city sheet,
        // as with iOS's native sheet presentation — except when Map was pushed
        // from Profile. That stack keeps the Profile tab visible so a retap
        // pops back to the profile, matching iOS.
        mapCitySheetPresented.value = !fromProfile && (citySheetVisible || state.selected != null)
    }
    LaunchedEffect(citySheetVisible) {
        citySheetTransition.targetState = citySheetVisible
    }
    DisposableEffect(Unit) {
        onDispose { mapCitySheetPresented.value = false }
    }
    fun dismissCitySheet() {
        if (!citySheetVisible) return
        citySheetVisible = false
        citySheetDetent = MapCitySheetValue.Peek.name
        scope.launch {
            // Let the sheet finish its exit before clearing the selected pin so
            // the map and city marker keep their geometry during the motion.
            delay(220)
            model.closeCity()
        }
    }
    BackHandler(mapPaywallSource != null || dialog.isNotEmpty() || state.selected != null || state.mode != null) { when { mapPaywallSource != null -> mapPaywallSource = null; dialog.isNotEmpty() -> { if (dialog != "intro" && dialog != "confirm" && !state.busy) dialog = "" }; state.selected != null -> dismissCitySheet(); else -> model.stop() } }
    if (!model.enabled) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = onBack) { Text(parityCopy("Back to Search")) } }; return }
    Box(Modifier.fillMaxSize().background(CorusColors.Background).onSizeChanged { mapViewportHeightPx = it.height }) {
        if (view == "map") CityMapView(
            cities = state.cities,
            filter = state.filter,
            selected = state.selected,
            playing = state.playing?.city,
            modifier = Modifier.fillMaxSize(),
            sessionKey = state.sessionKey,
            browsing = browsingCityId?.let { id -> cities.firstOrNull { it.city.cityId == id }?.city },
            anchor = if (sharingAnchorFrozen) sharingAnchor else if (state.ownPresenceReady) mapFocusCity(cities, state.filter, state.ownCity)?.city else null,
            selectedPeople = state.people,
            initialCamera = if (fromProfile) null else model.savedCamera,
            onCameraChanged = { position ->
                if (!fromProfile) model.savedCamera = position
                if (state.selected == null && state.playing == null) {
                    preserveRoamingCamera = true
                    nearestMapCity(cities, state.filter, position.latitude, position.longitude)?.let { nearest ->
                        if (browsingCityId != nearest.cityId) {
                            browsingCityId = nearest.cityId
                            listeningExitFocus = null
                        }
                    }
                }
            },
            showArtwork = true,
            playbackMode = state.mode,
            playingUserId = state.playing?.post?.user?.id,
            playingPerson = state.playing?.let { MapPerson(it.city, it.post.user) },
            loadLatest = model.repository::latest,
            mapKitToken = mapKitToken,
            focusOverride = sharedCityFocus ?: listeningExitFocus.takeIf { state.mode == null },
            focusRevision = shareFocusRevision + citySheetFocusRevision + playbackFocusRevision,
            focusInVisibleMap = state.playing != null || (citySheetVisible && state.selected != null) || (fromProfile && initialCityId != null),
            preserveCameraOnFocus = preserveRoamingCamera && state.playing == null && state.selected == null && sharedCityFocus == null,
            citySheetOpen = citySheetVisible && state.selected != null && state.playing == null,
            mapTopInsetFraction = if (mapViewportHeightPx > 0 && mapHeaderHeightPx > 0) {
                (mapHeaderHeightPx.toFloat() / mapViewportHeightPx).also { model.savedMapTopInsetFraction = it }
            } else model.savedMapTopInsetFraction,
            mapBottomOcclusionFraction = run {
                val overlayHeightPx = when {
                    state.playing != null -> playbackCardHeightPx
                    citySheetVisible && state.selected != null -> citySheetHeightPx
                    else -> 0
                }
                if (mapViewportHeightPx > 0 && overlayHeightPx > 0) {
                    mapBottomOcclusionFraction(
                        viewportHeightPx = mapViewportHeightPx,
                        overlayHeightPx = overlayHeightPx,
                        fallback = MAP_CITY_SHEET_PEEK_FRACTION,
                    ).also { model.savedMapBottomOcclusionFraction = it }
                } else if (state.playing != null || (citySheetVisible && state.selected != null)) {
                    model.savedMapBottomOcclusionFraction
                } else {
                    mapBottomOcclusionFraction(
                        viewportHeightPx = mapViewportHeightPx,
                        overlayHeightPx = 0,
                        fallback = MAP_CITY_SHEET_PEEK_FRACTION,
                    )
                }
            },
            onCity = {
                browsingCityId = it.cityId
                listeningExitFocus = null
                openCitySheet(it)
            },
        )
        // iOS keeps all controls in one compact, opaque three-row header.
        Column(
            Modifier
                .zIndex(1f)
                .fillMaxWidth()
                .background(CorusColors.Background)
                .statusBarsPadding()
                // Keep the resting header's small breathing room, but let the
                // resolving banner meet the map edge-to-edge like iOS. The
                // banner owns its balanced internal vertical padding.
                .padding(top = 10.dp, bottom = if (resolvingCity) 0.dp else 4.dp)
                .onSizeChanged { mapHeaderHeightPx = it.height },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(48.dp), colors = IconButtonDefaults.iconButtonColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(fm.corus.android.R.string.map_cd_back), modifier = Modifier.size(24.dp)) }
                Text(parityCopy("Map"), style = CorusFont.songTitleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(
                    enabled = !resolvingCity,
                    onClick = { audience = model.repository.sheetAudience(state.ownAudience, state.ownCity != null); model.repository.event("audience_picker_opened"); dialog = "audience" },
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
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("all" to "All", "following" to "Following", "tasteMatches" to "Taste matches").forEach { (value, label) ->
                    val selected = state.filter == value
                    val selectFilter = {
                        if (value == "tasteMatches" && !fullAccess) {
                            // Open the shared bottom-sheet offer directly. This avoids
                            // navigating through the full-screen Club destination.
                            showMapPaywall("MAP")
                            model.filter(value)
                        } else {
                            val accepted = value != state.filter
                            model.filter(value)
                            if (accepted) hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                    // Foundation expands a small clickable target to Android's
                    // minimum touch size without making the visual pill/header
                    // tall. Keep explicit button and selection semantics.
                    Box(
                        Modifier.height((if (selected) MAP_FILTER_SELECTED_HEIGHT_DP else MAP_FILTER_UNSELECTED_HEIGHT_DP).dp)
                            .clip(CircleShape)
                            .background(if (selected) CorusColors.Accent else androidx.compose.ui.graphics.Color.Transparent)
                            .then(if (!selected) Modifier.border(1.dp, CorusColors.Divider, CircleShape) else Modifier)
                            .semantics { this.selected = selected }
                            .clickable(role = Role.Button, onClickLabel = parityCopy(label), onClick = selectFilter)
                            .padding(horizontal = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(parityCopy(label), style = CorusFont.caption, color = if (selected) androidx.compose.ui.graphics.Color.White else CorusColors.Secondary, maxLines = 1)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val sharedCity = state.ownCity?.takeIf { state.ownAudience != "off" }
                    if (sharedCity != null) {
                        Text("${stringResource(fm.corus.android.R.string.map_sharing_label)} ${sharedCity.cityName}", modifier = Modifier.weight(1f, fill = false), style = CorusFont.captionMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(
                        onClick = { audience = model.repository.sheetAudience(state.ownAudience, sharedCity != null); model.repository.event("audience_picker_opened"); dialog = "audience" },
                        contentPadding = PaddingValues(horizontal = if (sharedCity == null) 0.dp else 6.dp),
                    ) {
                        Text(parityCopy(if (sharedCity == null) "Share your city" else "Change"), style = CorusFont.captionMedium, color = CorusColors.Accent, maxLines = 1)
                    }
                }
                Surface(shape = CircleShape, color = CorusColors.CardBackground) { Row(Modifier.padding(2.dp)) {
                    listOf("map" to "Map", "list" to "List").forEach { (value, label) -> val selected = view == value; TextButton(onClick = {
                        if (view != value) {
                            if (value == "list" && state.selected != null) {
                                // Match iOS: changing to List dismisses the
                                // selected-city sheet before mounting the
                                // directory. Mounting it during the exit would
                                // launch requests that closeCity invalidates.
                                if (citySheetVisible) {
                                    citySheetVisible = false
                                    scope.launch {
                                        delay(220)
                                        model.prepareListAndCloseCity()
                                        view = value
                                    }
                                } else {
                                    model.prepareListAndCloseCity()
                                    view = value
                                }
                            } else {
                                if (value == "list") model.prepareListAndCloseCity()
                                view = value
                            }
                            hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }, shape = CircleShape, modifier = Modifier.height(28.dp).width(62.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(containerColor = if (selected) CorusColors.Accent else androidx.compose.ui.graphics.Color.Transparent, contentColor = if (selected) androidx.compose.ui.graphics.Color.White else CorusColors.Secondary)) { Text(parityCopy(label), style = CorusFont.caption) } }
                } }
            }
            if (resolvingCity) {
                Row(Modifier.fillMaxWidth().background(CorusColors.CardBackground).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = CorusColors.Accent, strokeWidth = 2.dp)
                    Text(parityCopy("Finding your city"), style = CorusFont.caption)
                }
            }
        }
        if (state.loading && !resolvingCity) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (!state.loading && cities.isEmpty()) Surface(Modifier.align(Alignment.Center).padding(24.dp), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(20.dp)) { Text(parityCopy("No people to show for this filter.")); TextButton(onClick = { model.refresh() }) { Text(parityCopy("Retry")) } } }
        if (view == "list" && state.selected == null && state.playing == null) {
            // MainTabScreen already reserves its measured bottom chrome and
            // only adds mini-player height while it is actually visible. A
            // fixed extra inset here made the directory end above a phantom
            // player and clipped its footer action.
            Surface(Modifier.fillMaxSize().padding(top = if (resolvingCity) 200.dp else 166.dp), color = CorusColors.Background) {
                MapPeopleDirectory(cities, state, model, ::openUser) { city, chat -> if(chat.member) openChat(chat.threadId) else if (chat.clusterMember) model.join(city,null,::openChat) else requestLocation { model.join(city,it,::openChat) } }
            }
        }
        if (state.selected == null && state.playing == null && view == "map" && browsingCity != null) Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 76.dp), shape = CircleShape, color = CorusColors.CardBackground.copy(alpha = .88f), shadowElevation = 4.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                fun browse(delta: Int) {
                    val next = nextMapCitySummary(cities, browsingCity.cityId, delta) ?: return
                    preserveRoamingCamera = false
                    browsingCityId = next.city.cityId
                    citySheetFocusRevision++
                }
                IconButton(onClick = { browse(-1) }, enabled = cities.size > 1) { Icon(Icons.Default.ChevronLeft, stringResource(fm.corus.android.R.string.map_cd_previous_city)) }
                TextButton(onClick = { openCitySheet(browsingCity) }) { Text(browsingCity.cityName, color = CorusColors.Text, maxLines = 1, modifier = Modifier.widthIn(max = 180.dp)) }
                IconButton(onClick = { browse(1) }, enabled = cities.size > 1) { Icon(Icons.Default.ChevronRight, stringResource(fm.corus.android.R.string.map_cd_next_city)) }
            }
        }
        if (state.selected == null && state.playing == null && view == "map") Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MapGlassModeButton("Listen", Icons.Default.Headphones) { openCountryPicker("listen") }
            MapGlassModeButton("Watch", Icons.Default.Movie) { openCountryPicker("watch") }
            val sharedCity = state.ownCity?.takeIf { state.ownAudience != "off" }
            if (sharedCity != null && cities.isNotEmpty()) {
                MapGlassLocateButton(
                    onClick = {
                        hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        model.repository.event("own_city_located")
                        sharedCityFocus = sharedCity
                        preserveRoamingCamera = false
                        shareFocusRevision++
                        browsingCityId = sharedCity.cityId
                    },
                )
            }
        }
        state.selected?.takeIf { state.playing == null }?.let { city ->
            // Keep the directory in the map hierarchy. Unlike a Dialog, this
            // leaves the exposed map live for panning, zooming and tapping.
            AnimatedVisibility(
                visibleState = citySheetTransition,
                // Enter and leave from below the map. Keep this separate from
                // the map-camera transition, which may pan left or right.
                enter = slideInVertically(animationSpec = tween(280), initialOffsetY = { it }) + fadeIn(tween(180)),
                exit = slideOutVertically(animationSpec = tween(220), targetOffsetY = { it }) + fadeOut(tween(140)),
                // Paint above the opaque map header. iOS presents this as a
                // sheet over the chrome; zIndex 1 on the header was tucking
                // the grabber underneath when the sheet expanded.
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(2f),
            ) {
            MapCityPeopleSheet(
                onDismiss = { dismissCitySheet() },
                onHeightChanged = { citySheetHeightPx = it },
                initialValue = MapCitySheetValue.valueOf(citySheetDetent),
                onDetentChanged = { citySheetDetent = it.name },
            ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        fun step(delta: Int) {
                            val next = nextMapCitySummary(cities, city.cityId, delta) ?: return
                            openCitySheet(next.city)
                        }
                        IconButton(onClick = { step(-1) }, enabled = cities.size > 1) { Icon(Icons.Default.ChevronLeft, stringResource(fm.corus.android.R.string.map_cd_previous_city)) }
                        val peopleCount = cities.firstOrNull { it.city.cityId == city.cityId }?.facets?.get(state.filter)?.count ?: 0
                        val country = java.util.Locale("", city.countryCode).displayCountry.ifBlank { city.countryCode }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(city.cityName, style = CorusFont.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 1)
                            Text("${city.regionName}, $country · ${pluralStringResource(fm.corus.android.R.plurals.map_people_count, peopleCount, peopleCount)}", style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { step(1) }, enabled = cities.size > 1) { Icon(Icons.Default.ChevronRight, stringResource(fm.corus.android.R.string.map_cd_next_city)) }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Keep a confirmed chat action visible while a background
                        // eligibility refresh is in flight. The city did not change,
                        // so dropping it causes a distracting Open chat flicker.
                        FilledTonalButton(onClick = { model.start("listen", emptySet(), city.cityId) }, enabled = !state.busy, modifier = Modifier.weight(1f).height(44.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = CorusColors.Accent.copy(alpha = .12f), contentColor = CorusColors.Accent)) { Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(parityCopy("Listen"), style = CorusFont.bodyMedium) }
                        state.chat?.takeIf { showMapChat(city.cityId, state.currentDeviceCityId, it) }?.let { chat -> Button(onClick = { if (chat.member) openChat(chat.threadId) else if (chat.clusterMember) model.join(city,null,::openChat) else requestLocation { model.join(city, it, ::openChat) } }, modifier = Modifier.weight(1f).height(44.dp), enabled = !state.busy) { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(parityCopy(if (chat.member) "Open chat" else "Join chat"), style = CorusFont.bodyMedium) } }
                    }
                    LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                        if (state.peopleLoading && state.people.isEmpty()) {
                            val count = cities.firstOrNull { it.city.cityId == city.cityId }?.facets?.get(state.filter)?.count
                            items((count ?: 3).coerceIn(1, 5)) { MapPersonRowSkeleton() }
                        }
                        items(state.people, key = { it.user.id }) { person ->
                            val post = state.posts[person.user.id]
                            val rowLayout = mapPersonRowLayout(post != null)
                            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClickLabel = stringResource(fm.corus.android.R.string.map_cd_view_profile), role = androidx.compose.ui.semantics.Role.Button) { openUser(person.user) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AsyncImage(model = person.user.avatarThumbURL ?: person.user.avatarURL, contentDescription = null, modifier = Modifier.size(44.dp).clip(CircleShape))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    UsernameWithFlair(username = person.user.username, isVerified = person.user.isVerified, isClubMember = person.user.isClubMember, flairStyle = person.user.flairStyle, isBot = person.user.isBot, showAtPrefix = true, flairYOffset = (-1).dp, flairSpacing = 2.dp)
                                    if (rowLayout == MapPersonRowLayout.LATEST_POST && post != null) {
                                        Text(stringResource(fm.corus.android.R.string.map_latest_post), style = CorusFont.caption, color = CorusColors.Secondary)
                                        Text(if (post.isMovie) post.movieTitle.orEmpty() else listOf(post.track.name, post.track.artistName).filter { value -> value.isNotBlank() }.joinToString(" · "), style = CorusFont.captionMedium, color = CorusColors.Text, minLines = 1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (rowLayout == MapPersonRowLayout.LATEST_POST && post != null) AsyncImage(model = post.displayImageURL, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(fm.corus.android.R.string.map_cd_view_profile), tint = CorusColors.Tertiary)
                            }; HorizontalDivider(color = CorusColors.Divider)
                        }
                        if (state.peopleLoading && state.people.isNotEmpty()) {
                            items(2, key = { "more-skeleton:$it" }) { MapPersonRowSkeleton() }
                        }
                        if (state.more != null) item(key = "next:${city.cityId}:${state.more}") {
                            LaunchedEffect(state.more) { model.more() }
                        }
                        else if (canInviteMapCluster(state, city, MapPeoplePage(state.people, state.more, state.peopleReachedEnd),
                            state.peopleLoading, state.error != null, model.repository.currentUserId)) {
                            item(key = "invite:${city.cityId}") { MapClusterInviteFooter() }
                        }
                    }
            }
            }
        }
        state.playing?.let { item ->
            val density = androidx.compose.ui.platform.LocalDensity.current
            val watchCardMaxHeight = with(density) {
                mapWatchCardMaxHeight(mapViewportHeightPx.toDp().value.toInt(), mapHeaderHeightPx.toDp().value.toInt()).dp
            }
            val listenCardMaxHeight = with(density) {
                mapPlaybackCardMaxHeight(mapViewportHeightPx.toDp().value.toInt(), mapHeaderHeightPx.toDp().value.toInt(), MAP_LISTEN_CARD_MAX_HEIGHT_DP).dp
            }
            // Keep the close affordance in the card's rounded corner. It is a
            // plain muted icon, like iOS, rather than a floating circular control.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .heightIn(max = if (state.mode == "watch") watchCardMaxHeight else listenCardMaxHeight)
                    .padding(horizontal = MAP_PLAYBACK_CARD_HORIZONTAL_SCREEN_INSET_DP.dp, vertical = 20.dp)
                    .onSizeChanged { playbackCardHeightPx = it.height },
            ) {
            Surface(
                // Leave the entire 32dp close affordance above the trailer,
                // plus a small visual gap, rather than overlapping its corner.
                modifier = Modifier.fillMaxWidth().padding(top = MAP_PLAYBACK_CLOSE_RESERVE_DP.dp),
                shape = RoundedCornerShape(MAP_PLAYBACK_CARD_CORNER_RADIUS_DP.dp),
                color = CorusColors.CardBackground.copy(alpha = MAP_PLAYBACK_CARD_SURFACE_ALPHA),
                shadowElevation = 12.dp,
            ) {
                Column {
                if (!fullAccess) {
                    val watchMode = state.mode == "watch"
                    LaunchedEffect(watchMode) { model.repository.event("preview_banner_shown", if (watchMode) "watch" else "listen", count = state.preview.remaining(if (watchMode) "watch" else "listen")) }
                    Surface(onClick = { showMapPaywall(if (watchMode) "MAP_WATCH" else "MAP_LISTEN", entryPoint = "banner") }, modifier = Modifier.fillMaxWidth(), color = CorusColors.Accent) {
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(if (watchMode) Icons.Default.Movie else Icons.Default.Headphones, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
                            Text(stringResource(if (watchMode) fm.corus.android.R.string.map_watch_mode_preview else fm.corus.android.R.string.map_listen_mode_preview), style = CorusFont.caption, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, tint = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
                LazyColumn(modifier = if (state.mode == "watch") Modifier.weight(1f) else Modifier) {
                    if (state.mode == "watch" && !state.blocked) item { val id = fm.corus.android.ui.components.youTubeVideoID(item.post.trailerURL); if (id != null) key(item.post.id) { InlineYouTubePlayer(videoID = id, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), showControls = true, onEnded = { model.ended(item.post.id) }, onStarted = { model.started(item.post.id, "watch") }) } }
                    item {
                        AnimatedContent(targetState = item.post, contentKey = { it.id }, transitionSpec = {
                            val duration = if(android.animation.ValueAnimator.areAnimatorsEnabled()) 160 else 0
                            (fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = .985f)) togetherWith (fadeOut(tween(duration)) + scaleOut(tween(duration), targetScale = .985f))
                        }, label = "mapPostTransition") { animatedPost -> Column {
                        Row(Modifier.fillMaxWidth().clickable { openUser(animatedPost.user) }.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            UserAvatarView(
                                avatarURL = animatedPost.user.avatarURL,
                                avatarThumbURL = animatedPost.user.avatarThumbURL,
                                displayName = animatedPost.user.displayName,
                                username = animatedPost.user.username,
                                size = 36.dp,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                UsernameWithFlair(username = animatedPost.user.username, isVerified = animatedPost.user.isVerified, isClubMember = animatedPost.user.isClubMember, flairStyle = animatedPost.user.flairStyle, isBot = animatedPost.user.isBot, showAtPrefix = true)
                                animatedPost.user.displayName.takeIf { it.isNotBlank() }?.let { Text(it, style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1) }
                                Text("${item.city.cityName}, ${item.city.regionName}", style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1)
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = CorusColors.Divider)
                        Column(Modifier.fillMaxWidth().clickable { openPost(animatedPost) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (animatedPost.isTrack) AsyncImage(model = animatedPost.displayImageURL, contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)))
                                Column(Modifier.weight(1f)) {
                                    Text(if (animatedPost.isMovie) animatedPost.movieTitle.orEmpty() else animatedPost.track.name, style = CorusFont.bodyMedium, maxLines = 2)
                                    Text(if (animatedPost.isMovie) animatedPost.directorName.orEmpty() else animatedPost.track.artistName, color = CorusColors.Secondary, style = CorusFont.caption, maxLines = 1)
                                }
                            }
                            animatedPost.caption?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = if (state.mode == "watch") MAP_WATCH_CAPTION_MAX_LINES else 3, overflow = TextOverflow.Ellipsis, color = CorusColors.Secondary, style = CorusFont.caption) }
                        }
                        } }
                        MapPostEngagement(item.post, onComments = { model.repository.event("post_opened", state.mode ?: "map", "comments"); onComments(item.post.id) }, onRepost = onRepost, onPaywall = { mapPaywallSource = PaywallSource.SAVE_LIMIT }, onCatalog = { model.repository.event("post_opened", state.mode ?: "map", "catalog"); onPost(item.post) }, onAnalytics = { model.repository.event("engagement_tapped", state.mode ?: "map", it) })
                    }
                }
                if (state.mode == "watch") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = MAP_WATCH_NAVIGATION_TOP_PADDING_DP.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = { model.previous() }, enabled = state.historyIndex > 0 && !state.busy, modifier = Modifier.size(MAP_WATCH_NAVIGATION_TARGET_DP.dp)) {
                            Icon(Icons.Default.SkipPrevious, stringResource(fm.corus.android.R.string.map_cd_previous_trailer), modifier = Modifier.size(23.dp))
                        }
                        IconButton(onClick = { model.next(true) }, enabled = !state.busy, modifier = Modifier.size(MAP_WATCH_NAVIGATION_TARGET_DP.dp)) {
                            Icon(Icons.Default.SkipNext, stringResource(fm.corus.android.R.string.map_cd_next_trailer), modifier = Modifier.size(23.dp))
                        }
                    }
                }
                }
            }
            IconButton(
                onClick = { model.stop() },
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset(x = MAP_PLAYBACK_CLOSE_TARGET_TRAILING_OVERHANG_DP.dp)
                    .size(MAP_PLAYBACK_CLOSE_TARGET_DP.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = CorusColors.Secondary,
                ),
            ) {
                Box(
                    Modifier.align(Alignment.TopCenter).size(MAP_PLAYBACK_CLOSE_VISUAL_DP.dp)
                        .background(CorusColors.CardBackground.copy(alpha = .82f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        parityCopy("Close"),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            }
        }
        state.error?.let { message -> AlertDialog(onDismissRequest = { model.error(null) }, title = { Text(stringResource(fm.corus.android.R.string.map_please_try_again)) }, text = { Text(message) }, confirmButton = { TextButton(onClick = { model.error(null) }) { Text(parityCopy("OK")) } }) }
    }
    val sharingSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { value -> value != SheetValue.Hidden || (dialog != "intro" && dialog != "confirm" && !state.busy) })
    // Give the growing country directory enough initial room for roughly three
    // rows without turning it into a full-screen page. The capped LazyColumn is
    // the scrolling surface, and the remaining top gap protects the cutout.
    val countryPickerSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val countryPickerMaxHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * .65f
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
    if (dialog.isNotEmpty()) ModalBottomSheet(
        sheetState = if (dialog == "countries") countryPickerSheet else sharingSheet,
        onDismissRequest = { if (dialog != "intro" && dialog != "confirm" && !state.busy) dialog = "" },
        containerColor = CorusColors.Background,
        contentWindowInsets = {
            if (dialog == "countries") WindowInsets.systemBars.only(WindowInsetsSides.Bottom)
            else BottomSheetDefaults.windowInsets
        },
    ) {
        AnimatedContent(targetState = dialog, transitionSpec = {
            (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 12 }) togetherWith
                (fadeOut(tween(130)) + slideOutHorizontally(tween(130)) { -it / 16 })
        }, label = "mapSheetStep") { currentDialog ->
            LazyColumn(
                modifier = if (currentDialog == "countries") Modifier.heightIn(max = countryPickerMaxHeight) else Modifier,
                contentPadding = PaddingValues(start = 24.dp, top = if (currentDialog == "countries") 10.dp else 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
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
                        Text(parityCopy("If you share, people see your city — not your street or a live pin. You can change this anytime."), style = CorusFont.caption, color = CorusColors.Secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    } }
                    listOf(
                        Triple("everyone", "Everyone", "Anyone on Corus can see your city."),
                        Triple("following", "People I follow", "Only accounts you follow."),
                        Triple("off", "No one", "Don’t share. You can still explore."),
                    ).forEach { (value, title, subtitle) -> item {
                        MapAudienceOption(value, title, subtitle, audience == value) { audience = value; model.repository.rememberAudience(value); model.repository.event("audience_selected", value = value) }
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
                    Text(stringResource(fm.corus.android.R.string.map_location_needed), style = CorusFont.songTitleLarge)
                    Text(stringResource(fm.corus.android.R.string.map_privacy_location), style = CorusFont.caption, color = CorusColors.Secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    MapSheetPrimaryButton(onClick = { dialog = ""; requestLocation(sharing = true) { location -> model.resolveAndShare(location, audience, onShared = ::focusSharedCity) { resolvingCity = false } } }) { Text(stringResource(fm.corus.android.R.string.map_try_again)) }
                    TextButton(onClick = { model.repository.rememberAudience("off"); model.stopSharing { dialog = "" } }) { Text(stringResource(fm.corus.android.R.string.map_dont_share)) }
                } }
                "countries" -> {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(parityCopy(if (pendingMode == "listen") "Listen Mode" else "Watch Mode"), style = CorusFont.songTitleLarge)
                            Surface(onClick = { dialog = "" }, modifier = Modifier.align(Alignment.CenterStart).size(46.dp), shape = CircleShape, color = CorusColors.CardBackground) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, parityCopy("Close")) } }
                            if (countrySelection.isSelectingMultiple) TextButton(
                                onClick = {
                                    val selected = countrySelection.pendingCountryCodes
                                    if (selected.isNotEmpty()) {
                                        dialog = ""
                                        model.start(pendingMode, selected)
                                    }
                                },
                                enabled = countrySelection.canStart && !state.busy,
                                modifier = Modifier.align(Alignment.CenterEnd).semantics {
                                    contentDescription = context.getString(fm.corus.android.R.string.map_cd_start_selected_countries)
                                },
                            ) { Text(stringResource(fm.corus.android.R.string.map_start), style = CorusFont.bodyMedium) }
                        }
                        Text(parityCopy(if (pendingMode == "listen") "Listen to music posted by people in the countries you choose." else "Watch films posted by people in the countries you choose."), style = CorusFont.bodyMedium, color = CorusColors.Secondary, modifier = Modifier.padding(top = 20.dp, bottom = 16.dp))
                        // Match iOS: Anywhere is a complete selection, not a
                        // filter reset. It starts Listen/Watch Mode globally.
                        Surface(onClick = {
                            model.start(pendingMode, emptySet())
                            dialog = ""
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = CorusColors.CardBackground) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("🌍", style = CorusFont.bodyMedium)
                                Column(Modifier.weight(1f)) { Text(parityCopy("Anywhere"), style = CorusFont.bodyMedium); Text(parityCopy(if (pendingMode == "listen") "Music posted by people around the world." else "Films posted by people around the world."), style = CorusFont.caption, color = CorusColors.Secondary) }
                                Icon(Icons.Default.ChevronRight, null, tint = CorusColors.Tertiary)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(stringResource(fm.corus.android.R.string.map_countries), style = CorusFont.bodyMedium, color = CorusColors.Secondary); TextButton(onClick = { countrySelection = if (countrySelection.isSelectingMultiple) countrySelection.cancelMultiple() else countrySelection.beginMultiple() }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(if (countrySelection.isSelectingMultiple) parityCopy("Cancel") else stringResource(fm.corus.android.R.string.map_select_multiple), style = CorusFont.caption, color = CorusColors.Accent) } }
                    }
                    items(cities.map { it.city.countryCode }.distinct().sorted().filter { java.util.Locale("", it).displayCountry.contains(countryQuery, true) }) { code ->
                        val count = cities.filter { it.city.countryCode == code }.sumOf { it.facets[state.filter]?.count ?: 0 }
                        Row(Modifier.fillMaxWidth().clickable { if (!countrySelection.isSelectingMultiple) { model.start(pendingMode, setOf(code)); dialog = "" } else countrySelection = countrySelection.toggleCountry(code) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(countryFlag(code), style = CorusFont.bodyMedium)
                            Text(java.util.Locale("", code).displayCountry, style = CorusFont.bodyMedium, modifier = Modifier.weight(1f))
                            Text(count.toString(), style = CorusFont.caption, color = CorusColors.Secondary)
                            if (countrySelection.isSelectingMultiple) Checkbox(checked = code.uppercase() in countrySelection.pendingCountryCodes, onCheckedChange = null) else if (code.uppercase() in countrySelection.activeCountryCodes) Icon(Icons.Default.CheckCircle, null, tint = CorusColors.Accent) else Icon(Icons.Default.ChevronRight, null, tint = CorusColors.Tertiary)
                        }; HorizontalDivider(color = CorusColors.Divider)
                    }
                    item { OutlinedTextField(value = countryQuery, onValueChange = { countryQuery = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(stringResource(fm.corus.android.R.string.map_search_countries)) }, singleLine = true) }
                }
                }
            }
        }
    }
}

private fun countryFlag(countryCode: String): String = countryCode.uppercase().map {
    String(Character.toChars(it.code + 0x1F1A5))
}.joinToString("")

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
private fun MapGlassLocateButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = CircleShape
    Surface(
        onClick = onClick,
        shape = shape,
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = CorusColors.Text,
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .55f)),
        modifier = modifier.size(44.dp),
    ) {
        Box(
            Modifier.fillMaxSize().clip(shape).background(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
                    CorusColors.Background.copy(alpha = .88f),
                    CorusColors.Background.copy(alpha = .68f),
                ))
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.NearMe,
                contentDescription = parityCopy("Show your city on the map"),
                modifier = Modifier.size(15.dp),
            )
        }
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
