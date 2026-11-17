package fm.corus.android.ui.screens.live

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.corus.android.R
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.launch

private val emojiCategories = listOf(
    "😀" to "Smileys and emotion", "👋" to "People and body", "🐻" to "Animals and nature",
    "🍕" to "Food and drink", "🚗" to "Travel and places", "⚽" to "Activities",
    "💡" to "Objects", "❤️" to "Symbols", "🏳️" to "Flags",
)

private data class LiveEmoji(val category: Int, val emoji: String, val name: String, val searchText: String, val base: String)

@Composable
internal fun LiveEmojiPicker(
    selected: Set<String>,
    pending: Set<String>,
    choose: (String) -> Unit,
) {
    val assets = LocalContext.current.assets
    val catalog = remember(assets) {
        assets.open("emoji/corus_live_emoji.tsv").bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val fields = line.split('\t', limit = 5)
                val category = fields.firstOrNull()?.toIntOrNull()
                if (fields.size == 5 && category != null) {
                    LiveEmoji(category, fields[1], fields[2], "${fields[1]} ${fields[2]} ${fields[3]}", fields[4])
                } else null
            }.toList()
        }
    }
    val defaults = remember(catalog) { catalog.filter { it.emoji == it.base } }
    val tones = remember(catalog) { catalog.filter { it.emoji != it.base }.groupBy(LiveEmoji::base) }
    var search by remember { mutableStateOf("") }
    val words = search.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    val browsing = words.isEmpty()
    val matches = remember(catalog, words) {
        if (words.isEmpty()) emptyList()
        else {
            val bases = catalog.filter { item -> words.all { item.searchText.contains(it, ignoreCase = true) } }
                .mapTo(mutableSetOf(), LiveEmoji::base)
            defaults.filter { it.emoji in bases }
        }
    }
    val groups = remember(defaults) {
        val grouped = defaults.groupBy(LiveEmoji::category)
        emojiCategories.indices.map { grouped[it].orEmpty() }
    }
    val groupOffsets = remember(groups) { groups.runningFold(0) { index, group -> index + 1 + group.size } }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(search) { grid.scrollToItem(0) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LiveSearchField(search, stringResource(R.string.likes_search_placeholder), showSearchIcon = true) { search = it }
        if (browsing) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                emojiCategories.forEachIndexed { index, (icon, name) ->
                    LiveEmojiCell(icon, name, active = false, size = 19) {
                        scope.launch { grid.scrollToItem(groupOffsets[index]) }
                    }
                }
            }
        }
        if (!browsing && matches.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                LiveText(stringResource(R.string.likes_empty_no_results), color = CorusColors.Secondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(44.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = grid,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (browsing) {
                    groups.forEachIndexed { index, group ->
                        item(key = "category-$index", span = { GridItemSpan(maxLineSpan) }) {
                            LiveText(
                                emojiCategories[index].second,
                                CorusFont.caption,
                                CorusColors.Secondary,
                                Modifier.padding(horizontal = 8.dp, vertical = 6.dp).semantics { heading() },
                            )
                        }
                        items(group, key = LiveEmoji::emoji) { item ->
                            LiveEmojiTile(item, tones[item.emoji].orEmpty(), selected, pending, choose)
                        }
                    }
                } else {
                    items(matches, key = LiveEmoji::emoji) { item ->
                        LiveEmojiTile(item, tones[item.emoji].orEmpty(), selected, pending, choose)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveEmojiTile(
    item: LiveEmoji, tones: List<LiveEmoji>, selected: Set<String>, pending: Set<String>, choose: (String) -> Unit,
) {
    var showingTones by remember { mutableStateOf(false) }
    Box {
        LiveEmojiCell(
            item.emoji, item.name, item.emoji in selected || tones.any { it.emoji in selected },
            enabled = item.emoji !in pending || tones.isNotEmpty(),
            onLongClick = if (tones.isEmpty()) null else ({ showingTones = true }),
        ) {
            if (item.emoji !in pending) choose(item.emoji)
        }
        if (showingTones) {
            DropdownMenu(expanded = true, onDismissRequest = { showingTones = false }, containerColor = CorusColors.SheetBackground) {
                (listOf(item) + tones).chunked(6).forEach { row ->
                    Row(Modifier.padding(horizontal = 4.dp)) {
                        row.forEach { variant ->
                            LiveEmojiCell(variant.emoji, variant.name, variant.emoji in selected, enabled = variant.emoji !in pending) {
                                showingTones = false
                                choose(variant.emoji)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveEmojiCell(
    emoji: String, name: String, active: Boolean, size: Int = 25, enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    Box(
        Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp).clip(CircleShape)
            .background(if (active) CorusColors.Accent.copy(alpha = .15f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(enabled = enabled, role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .semantics {
                contentDescription = name
                selected = active
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = size.sp)
    }
}
