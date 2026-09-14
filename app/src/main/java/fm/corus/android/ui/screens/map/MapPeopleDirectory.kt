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

@Composable
fun MapPeopleDirectory(cities: List<MapCitySummary>, state: MapScreenState, model: MapExploreViewModel, onUser: (CymbalUser) -> Unit, onChat: (MapCity, MapChatStatus) -> Unit) {
    var search by remember { mutableStateOf(model.directorySearch) }
    var collapsed by remember { mutableStateOf(ArrayList(model.directoryCollapsed)) }
    LaunchedEffect(search, collapsed) { model.directorySearch = search; model.directoryCollapsed = collapsed }
    val filtered = cities.filter { "${it.city.cityName} ${it.city.regionName} ${it.city.countryCode} ${java.util.Locale("", it.city.countryCode).displayCountry}".contains(search.trim(), ignoreCase = true) }
    val listState = rememberLazyListState(model.directoryScrollIndex, model.directoryScrollOffset)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            model.directoryScrollIndex = index; model.directoryScrollOffset = offset
        }
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
                            parityCopy("Search cities or countries"),
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
            item(key = "header:${city.cityId}") {
                Row(Modifier.fillMaxWidth().clickable { collapsed = ArrayList(if (closed) collapsed - city.cityId else collapsed + city.cityId) }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(city.cityName, style = CorusFont.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Surface(color = CorusColors.CardBackground, shape = CircleShape) {
                                Text(
                                    "${summary.facets[state.filter]?.count ?: 0}",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    style = CorusFont.caption,
                                    color = CorusColors.Secondary,
                                )
                            }
                        }
                        Text("${city.regionName} · ${city.countryCode}", style = CorusFont.caption, color = CorusColors.Secondary)
                    }
                    run {
                        var chat by remember(city.cityId, state.currentDeviceCityId) { mutableStateOf<MapChatStatus?>(null) }
                        var loading by remember(city.cityId) { mutableStateOf(true) }
                        LaunchedEffect(city.cityId, state.currentDeviceCityId) { loading=true; chat=null; try { chat = model.repository.chat(city.cityId, state.currentDeviceCityId) } catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException) throw e } finally { loading=false } }
                        if(loading) CircularProgressIndicator(Modifier.padding(horizontal=8.dp).size(20.dp))
                        else chat?.takeIf { it.member || it.canJoin }?.let { value ->
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
                if (!closed) LaunchedEffect(city.cityId, state.filter) { model.loadList(city) }
            }
            if (!closed) {
                val page = state.listPages[city.cityId]
                items(page?.people.orEmpty(), key = { "${city.cityId}:${it.user.id}" }) { person ->
                    Row(Modifier.fillMaxWidth().clickable { onUser(person.user) }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(person.user.avatarThumbURL ?: person.user.avatarURL, contentDescription = null, modifier = Modifier.size(44.dp).clip(CircleShape))
                        Column(Modifier.weight(1f)) { UsernameWithFlair(username = person.user.username, isVerified = person.user.isVerified, isClubMember = person.user.isClubMember, flairStyle = person.user.flairStyle, isBot = person.user.isBot, showAtPrefix = true); Text(person.user.displayName, style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1); if (person.user.bio.isNotBlank()) Text(person.user.bio, style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                if (city.cityId in state.listErrors) item { TextButton(onClick = { model.loadList(city, next = page?.cursor != null) }) { Text(parityCopy("Retry loading people")) } }
                else if (city.cityId in state.listLoading) item { Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp)) } }
                else if (page?.cursor != null) item(key = "next:${city.cityId}:${page.cursor}") { LaunchedEffect(page.cursor) { model.loadList(city, next = true) }; TextButton(onClick = { model.loadList(city, next = true) }) { Text(parityCopy("Load more people")) } }
                else if (canInviteMapCluster(state, city, page, city.cityId in state.listLoading,
                    city.cityId in state.listErrors, model.repository.currentUserId)) {
                    item(key = "invite:${city.cityId}") { MapClusterInviteFooter() }
                }
            }
        }
        if (filtered.isEmpty()) item { Text(parityCopy("No people to show for this filter."), Modifier.padding(vertical = 24.dp)) }
    }
}
