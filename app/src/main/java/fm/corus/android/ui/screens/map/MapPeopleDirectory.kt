package fm.corus.android.ui.screens.map

import fm.corus.android.R
import fm.corus.android.ui.components.parityCopy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import com.valentinilk.shimmer.shimmer

@Composable
fun MapPeopleDirectory(cities: List<MapCitySummary>, state: MapScreenState, model: MapExploreViewModel, onUser: (CymbalUser) -> Unit, onChat: (MapCity, MapChatStatus) -> Unit) {
    var search by remember { mutableStateOf(model.directorySearch) }
    var collapsed by remember { mutableStateOf(ArrayList(model.directoryCollapsed)) }
    LaunchedEffect(search, collapsed) { model.directorySearch = search; model.directoryCollapsed = collapsed }
    LaunchedEffect(search, state.filter, state.selectedCommunityId) { model.searchDirectoryPeople(search) }
    val cityMatches = cities.filter { "${it.city.cityName} ${it.city.regionName} ${it.city.countryCode} ${java.util.Locale("", it.city.countryCode).displayCountry}".contains(search.trim(), ignoreCase = true) }
    val cityMatchIds = cityMatches.map { it.city.cityId }.toSet()
    val extraPeople = state.directorySearchPeople.filter { it.city.cityId !in cityMatchIds }.groupBy { it.city.cityId }
    val extraCities = extraPeople.map { (cityId, people) ->
        cities.firstOrNull { it.city.cityId == cityId } ?: MapCitySummary(
            people.first().city,
            mapOf(state.filter to MapFacet(people.size, emptyList())),
        )
    }
    val filtered = cityMatches + extraCities.filter { summary -> cityMatches.none { it.city.cityId == summary.city.cityId } }
    val listState = rememberLazyListState(model.directoryScrollIndex, model.directoryScrollOffset)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            model.directoryScrollIndex = index; model.directoryScrollOffset = offset
        }
    }
    LaunchedEffect(cities.map { it.city.cityId }, state.filter) {
        model.prepareListDirectory()
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CorusColors.CardBackground, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.map_cd_search),
                    tint = CorusColors.Secondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (search.isEmpty()) {
                        Text(
                            parityCopy("Search cities or people"),
                            style = CorusFont.body,
                            color = CorusColors.Tertiary,
                        )
                    }
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = CorusFont.body.copy(color = CorusColors.Text),
                        singleLine = true,
                        cursorBrush = SolidColor(CorusColors.Accent),
                    )
                }
                if (search.isNotEmpty()) {
                    IconButton(onClick = { search = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.map_cd_clear_search), tint = CorusColors.Tertiary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        filtered.forEach { summary ->
            val city = summary.city; val closed = city.cityId in collapsed
            val searchHits = extraPeople[city.cityId]
            val cityNameMatch = city.cityId in cityMatchIds
            item(key = "header:${city.cityId}") {
                Row(Modifier.fillMaxWidth().clickable { collapsed = ArrayList(if (closed) collapsed - city.cityId else collapsed + city.cityId) }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(city.cityName, style = CorusFont.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Surface(color = CorusColors.CardBackground, shape = CircleShape) {
                                Text(
                                    "${if (cityNameMatch) summary.facets[state.filter]?.count ?: 0 else searchHits?.size ?: 0}",
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    style = CorusFont.caption,
                                    color = CorusColors.Secondary,
                                )
                            }
                        }
                        Text("${city.regionName} · ${city.countryCode}", style = CorusFont.caption, color = CorusColors.Secondary)
                    }
                    run {
                        LaunchedEffect(city.cityId, state.currentDeviceCityId) { model.loadDirectoryChat(city.cityId) }
                        state.directoryChats[city.cityId]?.takeIf { it.available }?.let { value ->
                            Button(
                                onClick = { onChat(city, value) },
                                modifier = Modifier.padding(start = 8.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CorusColors.Accent,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(parityCopy(if (value.member) "Open chat" else "Join chat"), style = CorusFont.caption)
                            }
                        }
                    }
                    Icon(if (closed) Icons.Default.ChevronRight else Icons.Default.ExpandMore, stringResource(if (closed) R.string.map_cd_expand_city else R.string.map_cd_collapse_city))
                }
            }
            if (!closed) {
                val page = if (cityNameMatch) state.listPages[city.cityId] else MapPeoplePage(searchHits.orEmpty(), null, true)
                val showInitialSkeleton = cityNameMatch && state.listPreparing
                if (!showInitialSkeleton) items(page?.people.orEmpty(), key = { "${city.cityId}:${it.user.id}" }) { person ->
                    var appeared by rememberSaveable(city.cityId, person.user.id) { mutableStateOf(false) }
                    LaunchedEffect(Unit) { appeared = true }
                    val contentAlpha by animateFloatAsState(
                        targetValue = if (appeared) 1f else 0f,
                        animationSpec = tween(180),
                        label = "mapDirectoryPersonFade",
                    )
                    Row(Modifier.fillMaxWidth().alpha(contentAlpha).clickable { onUser(person.user) }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(person.user.avatarThumbURL ?: person.user.avatarURL, contentDescription = null, modifier = Modifier.size(44.dp).clip(CircleShape))
                        Column(Modifier.weight(1f)) { UsernameWithFlair(username = person.user.username, isVerified = person.user.isVerified, isClubMember = person.user.isClubMember, flairStyle = person.user.flairStyle, isBot = person.user.isBot, showAtPrefix = true); Text(person.user.displayName, style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1); if (person.user.bio.isNotBlank()) Text(person.user.bio, style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                if (showInitialSkeleton) {
                    val skeletonCount = (summary.facets[state.filter]?.count ?: 1).coerceIn(1, 3)
                    items(skeletonCount, key = { "initial-skeleton:${city.cityId}:$it" }) { MapDirectoryPersonSkeleton() }
                }
                else if (cityNameMatch && city.cityId in state.listErrors) item { TextButton(onClick = { model.prepareListDirectory() }) { Text(parityCopy("Retry loading people")) } }
                else if (cityNameMatch && city.cityId in state.listLoading) {
                    val skeletonCount = if (page == null) {
                        (summary.facets[state.filter]?.count ?: 1).coerceIn(1, 3)
                    } else 1
                    items(skeletonCount, key = { "skeleton:${city.cityId}:$it" }) { MapDirectoryPersonSkeleton() }
                }
                else if (cityNameMatch && page?.cursor != null) item(key = "next:${city.cityId}:${page.cursor}") { LaunchedEffect(page.cursor) { model.loadList(city, next = true) } }
                else if (cityNameMatch && canInviteMapCluster(state, city, page, city.cityId in state.listLoading,
                    city.cityId in state.listErrors, model.repository.currentUserId)) {
                    item(key = "invite:${city.cityId}") { MapClusterInviteFooter() }
                }
            }
        }
        if (filtered.isEmpty() && state.directorySearchLoading) item { MapDirectoryPersonSkeleton() }
        else if (filtered.isEmpty()) item { Text(parityCopy("No people to show for this filter."), Modifier.padding(vertical = 24.dp)) }
    }
}

/** Loading geometry matches a directory person row, so content can replace it without a jump. */
@Composable
private fun MapDirectoryPersonSkeleton() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).shimmer(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(CorusColors.Skeleton))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(.48f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(CorusColors.Skeleton))
            Box(Modifier.fillMaxWidth(.68f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(CorusColors.Skeleton))
            Box(Modifier.fillMaxWidth(.82f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(CorusColors.Skeleton))
        }
    }
}
