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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.repository.ConcertShow
import fm.corus.android.ui.navigation.ArtistPageRoute
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ConcertPreview(
    onSeeAll: () -> Unit, onConcert: (String) -> Unit,
    vm: ConcertsViewModel = hiltViewModel(),
) {
    if (!vm.enabled) return
    val cityId by vm.cityId.collectAsState()
    var shows by remember { mutableStateOf<List<ConcertShow>>(emptyList()) }
    LaunchedEffect(vm.enabled, cityId) {
        runCatching { vmPreview(vm) }.onSuccess { shows = it }
    }
    if (shows.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.concerts_title).uppercase(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.concert_see_all)) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shows, key = { it.id }) { show ->
                Column(Modifier.width(144.dp).clickable { vm.select(show); onConcert(show.id) }) {
                    AsyncImage(show.imageUrl, null, Modifier.fillMaxWidth().height(108.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
                    Text(show.matchedArtist ?: show.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(formatConcertDate(show), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private suspend fun vmPreview(vm: ConcertsViewModel): List<ConcertShow> = vm.preview()

@Composable
fun ConcertsScreen(
    onBack: () -> Unit, onConcert: (String) -> Unit,
    vm: ConcertsViewModel = hiltViewModel(),
) {
    val tab by vm.tab.collectAsState(); val shows by vm.shows.collectAsState()
    val plans by vm.plans.collectAsState(); val city by vm.cityName.collectAsState()
    val needsCity by vm.needsCity.collectAsState(); val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState(); val total by vm.total.collectAsState()
    val cursor by vm.cursor.collectAsState(); val genres by vm.genres.collectAsState()
    val range by vm.dateRange.collectAsState(); val genre by vm.genre.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
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
    LaunchedEffect(vm.enabled) { if (vm.enabled) vm.refresh() }
    if (!vm.enabled) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.concert_unavailable)) }; return }
    Scaffold(topBar = { Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        Text(stringResource(R.string.concerts_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        IconButton(onClick = { filterSheet = true; vm.log("filters_opened") }) { Icon(Icons.Default.FilterList, stringResource(R.string.concert_filter_title)) }
    } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { citySheet = true; vm.log("city_picker_opened") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (tab == "myConcerts") stringResource(R.string.concert_your_plans) else city.ifBlank { if (needsCity) stringResource(R.string.concert_choose_city) else "…" })
                }
                if (total > 0) Text("$total ${stringResource(R.string.concert_shows)}", style = MaterialTheme.typography.bodySmall)
            } }
            item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                listOf("all" to R.string.concert_tab_all, "forYou" to R.string.concert_tab_for_you, "myConcerts" to R.string.concert_tab_my).forEach { (value, label) ->
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(if (tab == value) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant).clickable { vm.selectTab(value) }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, fontWeight = if (tab == value) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            } }
            if (error && !loading && shows.isEmpty() && plans.isEmpty()) item { EmptyConcertCard(stringResource(R.string.concert_load_error)) { vm.refresh() } }
            if (tab == "myConcerts") {
                val today = LocalDate.now().toString()
                val groups = listOf(
                    R.string.concert_going_section to plans.filter { it.status == "going" && it.date >= today },
                    R.string.concert_interested_section to plans.filter { it.status == "interested" && it.date >= today },
                    R.string.concert_past_section to plans.filter { it.date < today }.reversed(),
                )
                if (plans.isEmpty() && !loading && !error) item { EmptyConcertCard(stringResource(R.string.concert_my_empty)) }
                groups.forEach { (label, sectionShows) ->
                    if (sectionShows.isNotEmpty()) { item { Text(stringResource(label), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), fontWeight = FontWeight.SemiBold) }
                        items(sectionShows, key = { it.id }) { show -> ConcertRow(show, { vm.select(show); onConcert(show.id) }) }
                    }
                }
            } else {
                items(shows, key = { it.id }) { show -> ConcertRow(show, { vm.select(show); onConcert(show.id) }) }
                if (needsCity && !loading) item { EmptyConcertCard(stringResource(R.string.concert_choose_city)) { citySheet = true } }
                if (shows.isEmpty() && !loading && !needsCity && !error) item { EmptyConcertCard(stringResource(R.string.concert_no_shows)) }
                if (cursor != null) item { TextButton(onClick = { vm.refresh(cursor) }, Modifier.fillMaxWidth(), enabled = !loading) { Text(stringResource(R.string.concert_see_more)) } }
            }
            if (loading && (if (tab == "myConcerts") plans.isEmpty() else shows.isEmpty())) items(4) { Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(96.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) }
        }
    }
    if (citySheet) ModalBottomSheet(onDismissRequest = { citySheet = false }) {
        var cityQuery by remember { mutableStateOf("") }
        val cityResults by vm.cityResults.collectAsState()
        val citySearching by vm.citySearching.collectAsState()
        LaunchedEffect(cityQuery) { vm.searchCities(cityQuery) }
        Text(stringResource(R.string.concert_choose_city), Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
            else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }, enabled = !locating, modifier = Modifier.padding(horizontal = 12.dp)) {
            if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.LocationOn, null)
            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.concert_use_location))
        }
        if (locationError) Text(stringResource(R.string.concert_location_error), Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(cityQuery, { cityQuery = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text(stringResource(R.string.concert_city_search)) }, singleLine = true)
        val popular = if (cityQuery.trim().length < 2) POPULAR_CONCERT_CITIES else POPULAR_CONCERT_CITIES.filter { it.second.contains(cityQuery.trim(), ignoreCase = true) }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
            items(popular) { (id, name) ->
                ListItem(headlineContent = { Text(name) }, trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }, modifier = Modifier.clickable { vm.selectCity(id, name); citySheet = false })
            }
            if (cityQuery.trim().length >= 2) {
                items(cityResults.filter { result -> popular.none { it.first == result.cityId } }, key = { it.cityId }) { city ->
                    ListItem(headlineContent = { Text(city.cityName) }, supportingContent = { Text(listOf(city.regionName, city.countryCode).filter(String::isNotBlank).joinToString(", ")) }, trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }, modifier = Modifier.clickable { vm.selectCity(city.cityId, city.cityName); citySheet = false })
                }
                if (citySearching) item { Text(stringResource(R.string.concert_city_searching), Modifier.padding(20.dp)) }
                else if (popular.isEmpty() && cityResults.isEmpty()) item { Text(stringResource(R.string.concert_no_cities), Modifier.padding(20.dp)) }
            }
        }
    }
    if (filterSheet) ModalBottomSheet(onDismissRequest = { filterSheet = false }) {
        var localRange by remember { mutableStateOf(range) }; var localGenre by remember { mutableStateOf(genre) }; var localSuggestions by remember { mutableStateOf(suggestions) }
        Text(stringResource(R.string.concert_filter_title), Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.concert_date_filter), Modifier.padding(horizontal = 20.dp))
        listOf("any" to R.string.concert_any_date, "7d" to R.string.concert_next_7, "30d" to R.string.concert_next_30, "90d" to R.string.concert_next_90).forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().clickable { localRange = value }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(label), Modifier.weight(1f)); RadioButton(localRange == value, { localRange = value }) }
        }
        Text(stringResource(R.string.concert_genre_filter), Modifier.padding(horizontal = 20.dp))
        (listOf<String?>(null) + genres).forEach { value -> Row(Modifier.fillMaxWidth().clickable { localGenre = value }.padding(horizontal = 20.dp, vertical = 8.dp)) { Text(value ?: stringResource(R.string.concert_any_genre), Modifier.weight(1f)); RadioButton(localGenre == value, { localGenre = value }) } }
        if (tab == "forYou") Row(Modifier.fillMaxWidth().clickable { localSuggestions = !localSuggestions }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.concert_show_recommendations), Modifier.weight(1f)); Switch(localSuggestions, { localSuggestions = it }) }
        Button(onClick = { vm.setFilters(localRange, localGenre, localSuggestions); filterSheet = false }, Modifier.fillMaxWidth().padding(20.dp)) { Text(stringResource(R.string.concert_show_concerts)) }
    }
}

@Composable
fun ConcertRow(show: ConcertShow, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val date = runCatching { LocalDate.parse(show.date) }.getOrNull()
                Text(date?.month?.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())?.uppercase() ?: "", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                Text(date?.dayOfMonth?.toString() ?: "", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            VerticalDivider(Modifier.height(56.dp).padding(horizontal = 8.dp))
            Column(Modifier.weight(1f)) {
                if (show.suggestionSource == "tasteMatches") Text(stringResource(R.string.concert_you_might_like), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                Text(show.matchedArtist ?: show.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                if (show.matchedArtist != null && show.matchedArtist != show.title) Text(show.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("${show.venue} · ${show.city}", maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (!show.imageUrl.isNullOrBlank()) AsyncImage(show.imageUrl, null, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun EmptyConcertCard(message: String, action: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Column(Modifier.padding(20.dp)) { Text(message); if (action != null) TextButton(onClick = action) { Text(stringResource(R.string.concert_retry)) } } }
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
    if (!vm.enabled) { EmptyConcertCard(stringResource(R.string.concert_unavailable)); return }
    val concert = show
    if (concert == null) {
        if (detailLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (detailError) EmptyConcertCard(stringResource(R.string.concert_unavailable)) { vm.open(eventId) }
        return
    }
    val past = concert.date < LocalDate.now().toString()
    val unavailable = concert.eventStatus in listOf("canceled", "postponed")
    Scaffold(topBar = { Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        Spacer(Modifier.weight(1f))
        Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, null) }
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
    } }, bottomBar = { Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { inviteSheet = true; vm.log("invite_opened", concert, "concert_detail") }, Modifier.weight(1f)) { Icon(Icons.Default.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.concert_invite)) }
        OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(concert.url))) }) { Text(stringResource(R.string.concert_tickets)) }
    } }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Box(Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(concert.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text(concert.lineup.firstOrNull() ?: concert.title, Modifier.align(Alignment.BottomStart).padding(20.dp), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        } }
        item { Column(Modifier.padding(horizontal = 20.dp)) {
            Text(concert.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.concert_date_time), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatConcertDate(concert), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.concert_venue), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(concert.venue, Modifier.clickable { venueSheet = true }, style = MaterialTheme.typography.titleMedium)
            Text("${concert.city}, ${concert.region}", Modifier.clickable { venueSheet = true }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = { calendarSheet = true; vm.log("calendar_opened", concert, "concert_detail") }) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.concert_add_calendar)) }
        } }
        item { Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.concert_your_plans), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.updatePlan("interested") }, Modifier.weight(1f), enabled = attendance != null && ((!past && !unavailable) || attendance?.status == "interested")) { Text(stringResource(if (past && attendance?.status == "interested") R.string.concert_was_interested else R.string.concert_im_interested), maxLines = 1) }
                OutlinedButton(onClick = { vm.updatePlan("going") }, Modifier.weight(1f), enabled = attendance != null && ((!past && !unavailable) || attendance?.status == "going")) { Text(stringResource(if (past && attendance?.status == "going") R.string.concert_went else R.string.concert_im_going), maxLines = 1) }
            }
            if (attendance != null && (attendance!!.goingCount + attendance!!.interestedCount) > 0) {
                Spacer(Modifier.height(14.dp))
                Text("${attendance!!.goingCount} ${stringResource(R.string.concert_going_section)} · ${attendance!!.interestedCount} ${stringResource(R.string.concert_interested_section)}", Modifier.clickable { peopleSheet = true; vm.log("people_opened", concert, "concert_detail") })
            }
        } } }
        if (concert.lineup.isNotEmpty()) item { Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.concert_lineup), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            concert.lineup.forEach { artist -> ListItem(headlineContent = { Text(artist) }, trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }, modifier = Modifier.clickable { vm.resolveArtist(artist) { route -> if (route != null) onArtist(route) } }) }
            Text(stringResource(R.string.concert_lineup_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } }
        if (!concert.info.isNullOrBlank() || !concert.pleaseNote.isNullOrBlank()) item { Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.concert_event_info), style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(concert.info, concert.pleaseNote).joinToString("\n\n"), maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { expanded = !expanded; vm.log("event_info_toggled", concert, "concert_detail") }) { Text(stringResource(if (expanded) R.string.concert_show_less else R.string.concert_read_more)) }
        } } }
        if (error) item { Text(stringResource(R.string.concert_update_error), Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
    } }
    if (peopleSheet) ModalBottomSheet(onDismissRequest = { peopleSheet = false }) { Text(stringResource(R.string.concert_plans_title), Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
        attendance?.people?.forEach { person -> ListItem(headlineContent = { Text(person.name) }, supportingContent = { Text("@${person.username}") }, trailingContent = { Text(person.status) }, leadingContent = { AsyncImage(person.avatarUrl, null, Modifier.size(42.dp).clip(RoundedCornerShape(21.dp)), contentScale = ContentScale.Crop) }, modifier = Modifier.clickable { peopleSheet = false; onProfile(person.id) }) }
        if (attendance?.nextCursor != null) TextButton(onClick = { vm.loadMorePeople() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.concert_see_more)) }
    }
    if (venueSheet) ModalBottomSheet(onDismissRequest = { venueSheet = false }) { val address = listOfNotNull(concert.address ?: concert.venue, concert.city, concert.region).joinToString(", ")
        Text(concert.venue, Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
        Text(address, Modifier.padding(horizontal = 20.dp))
        ListItem(headlineContent = { Text(stringResource(R.string.concert_copy_address)) }, modifier = Modifier.clickable { (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Address", address)); venueSheet = false })
        ListItem(headlineContent = { Text(stringResource(R.string.concert_google_maps)) }, modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))); venueSheet = false })
    }
    if (calendarSheet) ModalBottomSheet(onDismissRequest = { calendarSheet = false }) { Text(stringResource(R.string.concert_add_calendar), Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
        ListItem(headlineContent = { Text(stringResource(R.string.concert_google_calendar)) }, modifier = Modifier.clickable { openCalendar(context, concert, "google"); calendarSheet = false })
        ListItem(headlineContent = { Text(stringResource(R.string.concert_device_calendar)) }, modifier = Modifier.clickable { openCalendar(context, concert, "device"); calendarSheet = false })
    }
    if (inviteSheet) ModalBottomSheet(onDismissRequest = { inviteSheet = false }) { Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(stringResource(R.string.concert_invite), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(query, { query = it; vm.searchFriends(it) }, label = { Text(stringResource(R.string.concert_search_people)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.concert_optional_message)) }, modifier = Modifier.fillMaxWidth())
        if (inviteError) Text(stringResource(R.string.concert_invite_error), color = MaterialTheme.colorScheme.error)
        friends.forEach { friend -> ListItem(headlineContent = { Text(friend.displayName ?: friend.username) }, supportingContent = { Text("@${friend.username}") }, modifier = Modifier.clickable { vm.sendInvite(friend, note, { threadId -> inviteSheet = false; onThread(threadId) }, { inviteError = true }) }) }
    } }
}

private fun formatConcertDate(show: ConcertShow): String = runCatching {
    LocalDate.parse(show.date).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())) + (show.time?.let { " · $it" } ?: "")
}.getOrDefault(show.date)

private fun openCalendar(context: android.content.Context, show: ConcertShow, provider: String) {
    val date = runCatching { LocalDate.parse(show.date) }.getOrNull() ?: return
    val time = runCatching { java.time.LocalTime.parse(show.time ?: "19:00:00") }.getOrDefault(java.time.LocalTime.of(19, 0))
    val start = date.atTime(time).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val intent = Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
        .putExtra(android.provider.CalendarContract.Events.TITLE, show.title)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, start + 3_600_000L)
        .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, listOfNotNull(show.address ?: show.venue, show.city, show.region).joinToString(", "))
    if (provider == "google") intent.setPackage("com.google.android.calendar")
    runCatching { context.startActivity(intent) }.onFailure { if (provider == "google") context.startActivity(intent.setPackage(null)) }
}
