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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.InlineYouTubePlayer
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
    val context = LocalContext.current
    val hapticView = androidx.compose.ui.platform.LocalView.current
    val prefs = remember { context.getSharedPreferences("map_onboarding", Context.MODE_PRIVATE) }
    var dialog by rememberSaveable { mutableStateOf(if (prefs.getBoolean("intro.${model.repository.currentUserId}", false)) "" else "intro") }
    var view by rememberSaveable { mutableStateOf("map") }
    LaunchedEffect(view) { model.repository.event("view_changed", view) }
    var browsingCityId by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var citySheetVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var audience by rememberSaveable { mutableStateOf("off") }
    var pendingMode by rememberSaveable { mutableStateOf("listen") }
    var countries by remember { mutableStateOf(model.selectedCountries) }
    LaunchedEffect(countries) { model.selectedCountries = countries }
    var query by rememberSaveable { mutableStateOf("") }
    var chosenCity by remember { mutableStateOf<MapCity?>(null) }
    var locationAction by remember { mutableStateOf<((Location) -> Unit)?>(null) }
    val listState = rememberLazyListState()
    val cities = state.cities.filter { (it.facets[state.filter]?.count ?: 0) > 0 }
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
        } catch (_: SecurityException) { model.error("Please allow location access or choose a city.") }
        catch (_: Exception) { model.error("Couldn’t get your location. Choose a city instead.") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> if (result.values.any { it }) locate() else { dialog = "city"; model.error("Please allow location access or choose a city.") } }
    fun requestLocation(action: (Location) -> Unit) {
        locationAction = action
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(listener) {
        val request = listener ?: return@LaunchedEffect
        delay(20_000)
        if (listener === request) { locationManager.removeUpdates(request); listener = null; locationAction = null; model.error("Couldn’t get your location. Choose a city instead.") }
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
    LaunchedEffect(state.paywall) { state.paywall?.let { model.dismissPaywall(); onPaywall(it) } }
    LaunchedEffect(state.selected?.cityId) { citySheetVisible = state.selected != null }
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
    BackHandler(dialog.isNotEmpty() || state.selected != null || state.mode != null) { when { dialog.isNotEmpty() -> { if (dialog != "intro" && dialog != "confirm" && !state.busy) dialog = "" }; state.selected != null -> dismissCitySheet(); else -> model.stop() } }
    if (!model.enabled) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = onBack) { Text(parityCopy("Back to Search")) } }; return }
    Box(Modifier.fillMaxSize().background(CorusColors.Background)) {
        CityMapView(state.cities, state.filter, state.selected, state.playing?.city, Modifier.fillMaxSize(), sessionKey = state.sessionKey, showArtwork = view == "map", playbackMode = state.mode, loadLatest = model.repository::latest, anchor = if (state.ownPresenceReady) mapFocusCity(cities, state.filter, state.ownCity)?.city else null, initialCamera = model.savedCamera, onCameraChanged = { model.savedCamera = it }, browsing = browsingCityId?.let { id -> cities.firstOrNull { it.city.cityId == id }?.city }) { browsingCityId = it.cityId; model.select(it) }
        // Mirror the map's existing iOS chrome: individual compact filter
        // pills, then a small sharing status and map/list control. This leaves
        // the map itself as the focus instead of turning the header into a
        // large segmented toolbar.
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                listOf("all" to "Everyone", "following" to "Following", "tasteMatches" to "Taste Matches").forEach { (value, label) ->
                    val selected = state.filter == value
                    TextButton(
                        onClick = {
                            val accepted = value != state.filter && (value != "tasteMatches" || fullAccess)
                            model.filter(value)
                            if (accepted) hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        },
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .then(if (!selected) Modifier.border(1.dp, CorusColors.Divider, CircleShape) else Modifier),
                        shape = CircleShape,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (selected) CorusColors.Accent else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (selected) androidx.compose.ui.graphics.Color.White else CorusColors.Secondary,
                        ),
                    ) {
                        Text(parityCopy(label), style = CorusFont.caption, maxLines = 1)
                    }
                }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val sharedCity = state.ownCity
                if (sharedCity == null) {
                    TextButton(onClick = { dialog = "audience" }) { Text(parityCopy("Share your city"), style = CorusFont.caption, color = CorusColors.Accent) }
                } else {
                    Text(parityCopy("Sharing: ${sharedCity.cityName}, ${sharedCity.regionName}"), style = CorusFont.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TextButton(onClick = { dialog = "audience" }) { Text(parityCopy("Change"), style = CorusFont.caption, color = CorusColors.Accent) }
                }
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = CorusColors.CardBackground) {
                    Row(Modifier.padding(2.dp)) {
                        listOf("map" to "Map", "list" to "List").forEach { (value, label) ->
                            val selected = view == value
                            TextButton(
                                onClick = { if (view != value) { view = value; hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) } },
                                shape = CircleShape,
                                colors = ButtonDefaults.textButtonColors(containerColor = if (selected) CorusColors.Accent else androidx.compose.ui.graphics.Color.Transparent, contentColor = if (selected) androidx.compose.ui.graphics.Color.White else CorusColors.Secondary),
                                modifier = Modifier.heightIn(min = 32.dp),
                            ) { Text(parityCopy(label), style = CorusFont.caption) }
                        }
                    }
                }
            }
        }
        if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (!state.loading && cities.isEmpty()) Surface(Modifier.align(Alignment.Center).padding(24.dp), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(20.dp)) { Text(parityCopy("No people to show for this filter.")); TextButton(onClick = { model.refresh() }) { Text(parityCopy("Retry")) } } }
        if (view == "list" && state.selected == null && state.playing == null) {
            Surface(Modifier.fillMaxWidth().padding(top = 90.dp, bottom = 90.dp, start = 12.dp, end = 12.dp), shape = RoundedCornerShape(20.dp)) {
                MapPeopleDirectory(cities, state, model, onUser) { city, chat -> if(chat.member) onChat(chat.threadId) else requestLocation { model.join(city,it,onChat) } }

            }
        }
        if (state.selected == null && state.playing == null && view == "map" && browsingCity != null) Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 76.dp), shape = CircleShape, color = CorusColors.Background, shadowElevation = 4.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                fun browse(delta: Int) { val i = cities.indexOfFirst { it.city.cityId == browsingCity.cityId }; browsingCityId = cities[(i + delta + cities.size) % cities.size].city.cityId }
                IconButton(onClick = { browse(-1) }, enabled = cities.size > 1) { Icon(Icons.Default.ChevronLeft, "Previous city") }
                TextButton(onClick = { model.select(browsingCity) }) { Text(browsingCity.cityName, maxLines = 1, modifier = Modifier.widthIn(max = 180.dp)) }
                IconButton(onClick = { browse(1) }, enabled = cities.size > 1) { Icon(Icons.Default.ChevronRight, "Next city") }
            }
        }
        if (state.selected == null && state.playing == null) Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { pendingMode = "listen"; dialog = "countries" }) { Icon(Icons.Default.Headphones, null); Text(parityCopy(" ") + parityCopy("Listen")) }
            FilledTonalButton(onClick = { pendingMode = "watch"; dialog = "countries" }) { Icon(Icons.Default.Movie, null); Text(parityCopy(" ") + parityCopy("Watch")) }
        }
        state.selected?.takeIf { state.playing == null }?.let { city ->
            // This must live in a Dialog window: MainTabScreen draws the
            // mini-player and tab bar after this NavHost, so an in-tree sheet
            // would incorrectly slip underneath both pieces of app chrome.
            Dialog(
                onDismissRequest = ::dismissCitySheet,
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                    dismissOnClickOutside = true,
                ),
            ) {
            AnimatedVisibility(
                visible = citySheetVisible,
                enter = slideInVertically(animationSpec = tween(220)) { it / 3 } + fadeIn(tween(180)),
                exit = slideOutVertically(animationSpec = tween(180)) { it / 3 } + fadeOut(tween(140)),
                modifier = Modifier.fillMaxSize(),
            ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(if (expanded) .9f else .52f), color = CorusColors.Background, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), shadowElevation = 12.dp) {
                Column {
                    Box(Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) { detectVerticalDragGestures { _, amount -> expanded = amount < 0 } }.clickable { expanded = !expanded }, contentAlignment = Alignment.Center) { Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(CorusColors.Secondary.copy(alpha = .4f))) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        fun step(delta: Int) { val i = cities.indexOfFirst { it.city.cityId == city.cityId }; if (cities.isNotEmpty()) model.select(cities[(i + delta + cities.size) % cities.size].city) }
                        IconButton(onClick = { step(-1) }) { Icon(Icons.Default.ChevronLeft, "Previous city") }
                        val peopleCount = cities.firstOrNull { it.city.cityId == city.cityId }?.facets?.get(state.filter)?.count ?: 0
                        val country = java.util.Locale("", city.countryCode).displayCountry.ifBlank { city.countryCode }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            MapCommunityPicker(state.cities, state.selectedCommunityId, model::community)
                            Text(city.cityName, style = CorusFont.bodyMedium, maxLines = 1)
                            Text("${city.regionName}, $country · $peopleCount ${if (peopleCount == 1) "person" else "people"}", style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { step(1) }) { Icon(Icons.Default.ChevronRight, "Next city") }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.chatLoading) Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp)) }
                        else {
                            FilledTonalButton(onClick = { model.start("listen", emptySet(), city.cityId) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text(parityCopy("Listen")) }
                            state.chat?.takeIf { showMapChat(city.cityId, state.currentDeviceCityId, it) }?.let { chat -> Button(onClick = { if (chat.member) onChat(chat.threadId) else requestLocation { model.join(city, it, onChat) } }, modifier = Modifier.weight(1f), enabled = !state.busy) { Text(if (chat.member) "Open chat" else "Join chat") } }
                        }
                    }
                    LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                        if (state.peopleLoading && state.people.isEmpty()) items(5) { Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(64.dp).clip(RoundedCornerShape(12.dp)).background(CorusColors.CardBackground)) }
                        items(state.people, key = { it.user.id }) { person ->
                            val post = state.posts[person.user.id]
                            Row(Modifier.fillMaxWidth().clickable { onUser(person.user) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AsyncImage(model = person.user.avatarThumbURL ?: person.user.avatarURL, contentDescription = null, modifier = Modifier.size(44.dp).clip(CircleShape))
                                Column(Modifier.weight(1f)) {
                                    UsernameWithFlair(username = person.user.username, isVerified = person.user.isVerified, isClubMember = person.user.isClubMember, flairStyle = person.user.flairStyle, isBot = person.user.isBot, showAtPrefix = true)
                                    Text(parityCopy("Latest post"), style = CorusFont.caption, color = CorusColors.Tertiary)
                                    Text(post?.let { if (it.isMovie) it.movieTitle.orEmpty() else it.track.name } ?: person.user.bio.ifBlank { person.user.displayName }, style = CorusFont.caption, color = CorusColors.Secondary, minLines = 1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                AsyncImage(model = post?.displayImageURL, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
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
            }
        }
        state.playing?.let { item ->
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 540.dp).padding(12.dp), shape = RoundedCornerShape(24.dp), color = CorusColors.Background, shadowElevation = 12.dp) {
                LazyColumn {
                    item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(item.city.cityName, Modifier.weight(1f), style = CorusFont.bodyMedium); IconButton(onClick = { model.stop() }) { Icon(Icons.Default.Close, "Stop") } } }
                    if (!fullAccess) item { TextButton(onClick = { onPaywall(if (state.mode == "listen") "MAP_LISTEN" else "MAP_WATCH") }, modifier = Modifier.fillMaxWidth().background(CorusColors.Accent)) { Text(if (state.preview.remaining(state.mode ?: "listen") == 0) parityCopy(if (state.mode == "listen") "Last free song · Explore Club" else "Last free trailer · Explore Club") else parityCopy(if (state.mode == "listen") "Listen preview · %lld songs left · Explore Club" else "Watch preview · %lld trailers left · Explore Club").replace("%lld", state.preview.remaining(state.mode ?: "listen").toString()), color = androidx.compose.ui.graphics.Color.White) } }
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
                        MapPostEngagement(item.post, onComments = { onComments(item.post.id) }, onRepost = onRepost, onPaywall = { onPaywall("SAVE_LIMIT") })
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { TextButton(onClick = { model.previous() }, enabled = state.historyIndex > 0) { Text(parityCopy("Previous")) }; Button(onClick = { model.next() }, enabled = !state.busy) { Text(if (state.busy) "Loading…" else "Next") } }
                    }
                }
            }
        }
        state.error?.let { message -> AlertDialog(onDismissRequest = { model.error(null) }, title = { Text("Please try again") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { model.error(null) }) { Text(parityCopy("OK")) } }) }
    }
    val sharingSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { value -> value != SheetValue.Hidden || (dialog != "intro" && dialog != "confirm" && !state.busy) })
    if (dialog.isNotEmpty()) ModalBottomSheet(sheetState = sharingSheet,onDismissRequest = { if (dialog != "intro" && dialog != "confirm" && !state.busy) dialog = "" }, containerColor = CorusColors.Background) {
        LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (dialog) {
                "intro" -> {
                    item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(CorusColors.Accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.People, null, tint = CorusColors.Accent, modifier = Modifier.size(28.dp)) }
                        Text(parityCopy("Explore the Corus Map"), style = CorusFont.songTitleLarge)
                    } }
                    listOf("Find people by city" to "Message, meet up, or go to concerts together.", "Share your city, not your exact location." to "People can find you in the city you choose.", "No one sees you until you choose." to "Change this anytime.").forEach { (title, body) -> item { Text(parityCopy(title), style = CorusFont.bodyMedium); Text(parityCopy(body), color = CorusColors.Secondary) } }
                    item { Button(onClick = { prefs.edit().putBoolean("intro.${model.repository.currentUserId}", true).apply(); dialog = "audience" }, modifier = Modifier.fillMaxWidth()) { Text(parityCopy("Next")) } }
                }
                "audience" -> {
                    item { Text(parityCopy("Who can see your city"), style = CorusFont.songTitleLarge); Text(parityCopy("If you share, people see your city — not your street or a live pin. You can change this anytime.")) }
                    listOf(Triple("everyone", "Everyone", "Anyone on Corus can see your city."), Triple("following", "Following", "Only accounts you follow."), Triple("off", "Off", "Don’t share. You can still explore.")).forEach { (value, title, subtitle) -> item {
                        Row(Modifier.fillMaxWidth().clickable { audience = value }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(CorusColors.Accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(if (value == "everyone") Icons.Default.Public else if (value == "following") Icons.Default.People else Icons.Default.LocationOff, null, tint = CorusColors.Accent) }
                            Column(Modifier.weight(1f)) { Text(parityCopy(title), style = CorusFont.bodyMedium); Text(parityCopy(subtitle), style = CorusFont.caption, color = CorusColors.Secondary) }
                            RadioButton(selected = audience == value, onClick = { audience = value })
                        }
                    } }
                    item { Button(onClick = { model.repository.rememberAudience(audience); if (audience == "off") model.stopSharing { dialog = "" } else {
                        dialog = "city"
                        requestLocation { location -> model.resolve(location) { city ->
                            chosenCity = city
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) model.share(city, audience, "device") { dialog = "" }
                            else dialog = "confirm"
                        } }
                    } }, modifier = Modifier.fillMaxWidth()) { Text(parityCopy("Done")) } }
                }
                "city" -> {
                    item { Text(parityCopy("Choose a city"), style = CorusFont.songTitleLarge); TextButton(onClick = { requestLocation { location -> model.resolve(location) { city -> chosenCity = city; dialog = "confirm" } } }) { Text("Use my city") }; OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(parityCopy("Search cities")) }, modifier = Modifier.fillMaxWidth()) }
                    items(state.searchResults, key = { it.cityId }) { city -> ListItem(headlineContent = { Text(city.cityName) }, supportingContent = { Text("${city.regionName} · ${city.countryCode}") }, modifier = Modifier.clickable { model.share(city, audience, "manual") { dialog = "" } }) }
                }
                "confirm" -> item { Text(chosenCity?.cityName.orEmpty(), style = CorusFont.songTitleLarge); Text(parityCopy("Share your city, not your exact location.")); Button(onClick = { chosenCity?.let { model.share(it, audience, "device") { dialog = "" } } }) { Text(parityCopy("Done")) }; TextButton(onClick = { dialog = "city" }) { Text(parityCopy("Choose a city")) } }
                "countries" -> {
                    item {
                        Text(parityCopy(if (pendingMode == "listen") "Listen Mode" else "Watch Mode"), style = CorusFont.songTitleLarge)
                        Text(
                            parityCopy(if (pendingMode == "listen") "Listen to music posted by people in the countries you choose." else "Watch films posted by people in the countries you choose."),
                            color = CorusColors.Secondary,
                            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                        )
                        FilterChip(selected = countries.isEmpty(), onClick = { countries = emptySet() }, label = { Text(parityCopy(if (pendingMode == "listen") "Anywhere · music posted by people around the world" else "Anywhere · films posted by people around the world")) })
                    }
                    items(cities.map { it.city.countryCode }.distinct().sorted()) { code -> Row(Modifier.fillMaxWidth().clickable { countries = if (code in countries) countries - code else countries + code }, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = code in countries, onCheckedChange = { countries = if (code in countries) countries - code else countries + code }); Text(java.util.Locale("", code).displayCountry) } }
                    item { Button(onClick = { model.start(pendingMode, countries); dialog = "" }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(parityCopy(if (pendingMode == "listen") "Listen" else "Watch")) } }
                }
            }
        }
    }
}
