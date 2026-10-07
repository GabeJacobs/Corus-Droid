package fm.corus.android.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.localization.CorusStrings
import fm.corus.android.ui.components.PopularUsersInfiniteGrid
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.TasteMatchCard
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.components.VennDiagramIcon
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import java.util.Locale

/** Onboarding uses the endpoint's ranking, including single-artist matches. */
internal object RevisedTasteSuggestionsPolicy {
    const val SECONDARY_LIMIT = 3
    fun secondary(matches: List<SuggestedUserMatch>) = matches.drop(1).take(SECONDARY_LIMIT)
    fun remaining(current: Int, required: Int) = (required.coerceAtLeast(0) - current).coerceAtLeast(0)
    fun sharedNames(match: SuggestedUserMatch): List<String> = match.matchData?.let {
        (it.sharedArtistNames + it.sharedDirectorNames).map(String::trim).filter(String::isNotEmpty)
            .distinctBy { name -> name.lowercase(Locale.ROOT) }
    } ?: emptyList()
    fun artwork(match: SuggestedUserMatch): List<String> = match.matchData?.let {
        (it.sharedTrackPreviews.mapNotNull { preview -> preview.displayImageURL } +
            it.sharedMoviePreviews.mapNotNull { preview -> preview.posterURL })
            .filter(String::isNotBlank).distinct().take(4)
    } ?: emptyList()
    fun discoveryExclusions(matches: List<SuggestedUserMatch>, contacts: List<CymbalUser>, viewerId: String?) =
        (matches.map { it.user.id } + contacts.map { it.id } + listOfNotNull(viewerId)).toSet()
}

@Composable
internal fun RevisedTasteSuggestionsScreen(viewModel: SocialSetupViewModel, onBack: () -> Unit, onContinue: () -> Unit) {
    val matches by viewModel.tasteMatches.collectAsState()
    val picks by viewModel.quizPicks.collectAsState()
    val contacts by viewModel.contactMatches.collectAsState()
    val contactsSynced by viewModel.contactsSynced.collectAsState()
    val followed by viewModel.followedIds.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val searching by viewModel.isSearching.collectAsState()
    val finishing by viewModel.isFinishing.collectAsState()
    val session by viewModel.onboardingFollowSession.collectAsState()
    val users = matches?.users.orEmpty()
    LaunchedEffect(matches) {
        if (picks.isNotEmpty() && matches != null && users.isEmpty()) viewModel.analyticsService.logOnboardingTasteMakerShown()
    }
    val follow: (CymbalUser) -> Unit = { viewModel.toggleFollow(it.id) }
    val tasteFollow: (CymbalUser) -> Unit = {
        if (it.id !in followed) viewModel.analyticsService.logOnboardingTasteMatchFollowed()
        follow(it)
    }
    val starsTitle = stringResource(CorusStrings.onboarding_corus_stars)
    val preview by viewModel.previewSheetUser.collectAsState()
    val posts by viewModel.previewSheetPosts.collectAsState()
    val loading by viewModel.previewSheetIsLoading.collectAsState()
    val loadingMore by viewModel.previewSheetIsLoadingMore.collectAsState()
    val hasMore by viewModel.previewSheetHasMore.collectAsState()

    RevisedTasteSuggestionsContent(
        matches = users, quizTaken = picks.isNotEmpty(), contacts = if (contactsSynced) contacts else emptyList(),
        followedIds = followed, query = query, searchResults = results, isSearching = searching,
        minimumFollows = session?.minimumFollows ?: 0, isFinishing = finishing,
        onSearch = viewModel::searchUsers, onPreview = viewModel::openUserPreview,
        onFollow = follow, onTasteFollow = tasteFollow, onBack = onBack, onContinue = onContinue,
        discoveryContent = { topContent ->
            PopularUsersInfiniteGrid(
                excludeIds = RevisedTasteSuggestionsPolicy.discoveryExclusions(users, contacts, viewModel.onboardingViewerId),
                followedIds = followed, onUserTap = viewModel::openUserPreview, onFollowTap = follow,
                modifier = Modifier.fillMaxSize(), topContent = topContent,
                headerVerticalPadding = 0.dp, bottomContentPadding = 108.dp,
                headerTitle = starsTitle, headerIcon = Icons.Filled.Star,
            )
        },
    )
    preview?.let { user ->
        UserPreviewSheet(
            user = user, usesRevisedDesign = true, matchData = users.firstOrNull { it.user.id == user.id }?.matchData,
            posts = posts, isLoading = loading, isLoadingMore = loadingMore, hasMore = hasMore,
            isFollowed = user.id in followed, nowPlaying = viewModel.nowPlayingManagerInstance,
            onFollow = { follow(user) }, onLoadMore = viewModel::loadMorePreviewPosts, onDismiss = viewModel::closeUserPreview,
        )
    }
}

/** Data and callbacks let the same production layout be rendered without auth/network in UI tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RevisedTasteSuggestionsContent(
    matches: List<SuggestedUserMatch>, quizTaken: Boolean, contacts: List<CymbalUser>, followedIds: Set<String>,
    query: String, searchResults: List<CymbalUser>, isSearching: Boolean, minimumFollows: Int, isFinishing: Boolean,
    onSearch: (String) -> Unit, onPreview: (CymbalUser) -> Unit, onFollow: (CymbalUser) -> Unit,
    onTasteFollow: (CymbalUser) -> Unit, onBack: () -> Unit, onContinue: () -> Unit,
    discoveryContent: @Composable (@Composable () -> Unit) -> Unit,
) {
    var showAll by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val remaining = RevisedTasteSuggestionsPolicy.remaining(followedIds.size, minimumFollows)
    Column(Modifier.fillMaxSize().background(CorusColors.Background).statusBarsPadding().navigationBarsPadding()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = CorusSpacing.sm, top = CorusSpacing.lg)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(CorusStrings.onboarding_taste_back_to_quiz_aria), tint = CorusColors.Secondary)
        }
        Column(Modifier.padding(horizontal = CorusSpacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(CorusStrings.social_setup_curate_title), style = CorusFont.appTitle,
                color = CorusColors.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(CorusSpacing.sm))
            Text(if (minimumFollows > 0) pluralStringResource(CorusStrings.onboarding_follow_requirement_subtitle, minimumFollows, minimumFollows)
                else stringResource(CorusStrings.onboarding_suggestions_subtitle), style = CorusFont.body,
                color = CorusColors.Secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(CorusSpacing.xl))
        OnboardingSearchBar(query, onSearch, onSearch = { keyboard?.hide() }, modifier = Modifier.padding(horizontal = CorusSpacing.xxl))
        Spacer(Modifier.height(CorusSpacing.lg))
        Box(Modifier.weight(1f)) {
            if (query.isNotBlank()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 108.dp)) {
                    if (isSearching) items(4) { SkeletonUserRow() }
                    else if (searchResults.isEmpty()) item {
                        Text(stringResource(CorusStrings.parity_612eb3c64c41), style = CorusFont.bodyMedium,
                            color = CorusColors.Secondary, modifier = Modifier.padding(CorusSpacing.xxl))
                    } else items(searchResults, key = { it.id }) { user ->
                        RevisedOnboardingPersonRow(user, "${user.cymbalCount} ${stringResource(if (user.cymbalCount == 1) CorusStrings.post_noun else CorusStrings.profile_stat_coruses)}", user.id in followedIds,
                            { onPreview(user) }, { onFollow(user) }, modifier = Modifier.padding(horizontal = CorusSpacing.lg))
                    }
                }
            } else {
                discoveryContent {
                    Column(verticalArrangement = Arrangement.spacedBy(CorusSpacing.lg)) {
                        if (quizTaken) {
                            if (matches.isEmpty()) {
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(CorusSpacing.cornerRadiusLarge))
                                    .background(CorusColors.CardBackground).padding(CorusSpacing.xl)) {
                                    Text(stringResource(CorusStrings.onboarding_taste_maker_title), style = CorusFont.displayName, color = CorusColors.Text)
                                    Text(stringResource(CorusStrings.onboarding_taste_maker_body), style = CorusFont.caption, color = CorusColors.Secondary)
                                }
                            } else {
                                RevisedFeaturedTasteMatchCard(matches.first(), matches.first().user.id in followedIds,
                                    { onPreview(matches.first().user) }, { onTasteFollow(matches.first().user) })
                                val secondary = RevisedTasteSuggestionsPolicy.secondary(matches)
                                if (secondary.isNotEmpty()) {
                                    Column {
                                        RevisedOnboardingSectionHeader(stringResource(CorusStrings.onboarding_more_taste_matches))
                                        secondary.forEachIndexed { index, match ->
                                            RevisedOnboardingPersonRow(match.user, RevisedTasteSuggestionsPolicy.sharedNames(match).joinToString(" · "),
                                                match.user.id in followedIds, { onPreview(match.user) }, { onTasteFollow(match.user) })
                                            if (index < secondary.lastIndex) HorizontalDivider(Modifier.padding(start = 64.dp), color = CorusColors.Divider)
                                        }
                                    }
                                }
                                if (matches.size > 1 + RevisedTasteSuggestionsPolicy.SECONDARY_LIMIT) {
                                    OutlinedButton(onClick = { showAll = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)) {
                                        Text(stringResource(CorusStrings.onboarding_see_all_taste_matches, matches.size), style = CorusFont.buttonSmall, color = CorusColors.Text)
                                    }
                                }
                            }
                        }
                        if (contacts.isNotEmpty()) {
                            Column {
                                RevisedOnboardingSectionHeader(stringResource(CorusStrings.social_setup_section_friends), people = true)
                                contacts.forEach { user ->
                                    RevisedOnboardingPersonRow(user, stringResource(CorusStrings.search_subtitle_from_contacts), user.id in followedIds,
                                        { onPreview(user) }, { onFollow(user) })
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(
                0f to CorusColors.Background.copy(alpha = 0f), 0.4f to CorusColors.Background, 1f to CorusColors.Background,
            )).padding(top = 40.dp, start = CorusSpacing.xxl, end = CorusSpacing.xxl, bottom = CorusSpacing.lg)) {
                Button(onClick = onContinue, enabled = remaining == 0 && !isFinishing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("onboarding-continue"), shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent)) {
                    if (isFinishing) CircularProgressIndicator(Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                    else Text(if (remaining == 0) stringResource(CorusStrings.onboarding_cta_continue)
                        else pluralStringResource(if (followedIds.isEmpty()) CorusStrings.onboarding_follow_required else CorusStrings.onboarding_follow_remaining,
                            remaining, remaining), style = CorusFont.button)
                }
            }
        }
    }
    if (showAll) {
        ModalBottomSheet(onDismissRequest = { showAll = false }, containerColor = CorusColors.Background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = CorusSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(CorusStrings.feed_mode_taste_matches), style = CorusFont.displayName, color = CorusColors.Text, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAll = false }) { Text(stringResource(CorusStrings.done_finish)) }
            }
            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(CorusSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md), verticalArrangement = Arrangement.spacedBy(CorusSpacing.md)) {
                items(matches, key = { it.user.id }) { match ->
                    TasteMatchCard(match, match.user.id in followedIds, { onPreview(match.user) }, { onTasteFollow(match.user) })
                }
            }
        }
    }
}

@Composable
internal fun RevisedFeaturedTasteMatchCard(match: SuggestedUserMatch, isFollowed: Boolean, onPreview: () -> Unit, onFollow: () -> Unit) {
    val shape = RoundedCornerShape(CorusSpacing.cornerRadiusLarge)
    Column(Modifier.fillMaxWidth().clip(shape).background(CorusColors.CardBackground).padding(CorusSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm)) {
            Text("#1", style = CorusFont.sectionHeader, color = CorusColors.Accent,
                modifier = Modifier.background(CorusColors.Accent.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 3.dp))
            Text(stringResource(CorusStrings.native_400157a33bff), style = CorusFont.sectionHeader, color = CorusColors.Secondary)
        }
        Column(Modifier.fillMaxWidth().clickable(onClick = onPreview), verticalArrangement = Arrangement.spacedBy(CorusSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md)) {
                val user = match.user
                UserAvatarView(user.avatarURL, user.avatarThumbURL, user.displayName, user.username, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(displayName(user), style = CorusFont.displayName, color = CorusColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    UsernameWithFlair(username = user.username, showAtPrefix = true, isVerified = user.isVerified,
                        isClubMember = user.isClubMember, flairStyle = user.flairStyle, style = CorusFont.caption, color = CorusColors.Secondary)
                }
            }
            Row(Modifier.fillMaxWidth().aspectRatio(4f).clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium)).testTag("closest-match-artwork")) {
                val images = RevisedTasteSuggestionsPolicy.artwork(match)
                repeat(4) { index ->
                    Box(Modifier.weight(1f).fillMaxHeight().background(CorusColors.Skeleton), contentAlignment = Alignment.Center) {
                        images.getOrNull(index)?.let { url ->
                            AsyncImage(url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } ?: Image(painterResource(fm.corus.android.R.drawable.logo_no_background), null,
                            modifier = Modifier.fillMaxSize(0.36f), colorFilter = ColorFilter.tint(CorusColors.Tertiary))
                    }
                }
            }
            val names = RevisedTasteSuggestionsPolicy.sharedNames(match)
            if (names.isNotEmpty()) Column {
                Text(stringResource(CorusStrings.native_de09e9efb9a5), style = CorusFont.caption, color = CorusColors.Secondary)
                Text(names.joinToString(" · "), style = CorusFont.captionMedium, color = CorusColors.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Button(onClick = onFollow, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("closest-match-follow"),
            shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(
                containerColor = if (isFollowed) CorusColors.CardBackground else CorusColors.Accent,
                contentColor = if (isFollowed) CorusColors.Text else androidx.compose.ui.graphics.Color.White),
            border = if (isFollowed) androidx.compose.foundation.BorderStroke(1.dp, CorusColors.Divider) else null) {
            if (isFollowed) { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(CorusSpacing.sm)) }
            Text(stringResource(if (isFollowed) CorusStrings.following_status else CorusStrings.follow_action).uppercase(Locale.getDefault()), style = CorusFont.button)
        }
    }
}

@Composable
internal fun RevisedOnboardingPersonRow(user: CymbalUser, subtitle: String, isFollowed: Boolean, onPreview: () -> Unit, onFollow: () -> Unit,
    modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable(onClick = onPreview).padding(vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md)) {
        UserAvatarView(user.avatarURL, user.avatarThumbURL, user.displayName, user.username, size = 48.dp)
        Column(Modifier.weight(1f)) {
            Text(displayName(user), style = CorusFont.username, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            UsernameWithFlair(username = user.username, showAtPrefix = true, isVerified = user.isVerified,
                        isClubMember = user.isClubMember, flairStyle = user.flairStyle, style = CorusFont.caption, color = CorusColors.Secondary)
            if (subtitle.isNotBlank()) Text(subtitle, style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Keep the visual pill compact while retaining a 48dp accessible touch target.
        Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onFollow).testTag("follow-${user.id}"), contentAlignment = Alignment.Center) {
            Text(stringResource(if (isFollowed) CorusStrings.following_status else CorusStrings.follow_action), style = CorusFont.buttonSmall,
                color = if (isFollowed) CorusColors.Secondary else androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (isFollowed) CorusColors.CardBackground else CorusColors.Accent)
                    .border(1.dp, if (isFollowed) CorusColors.Divider else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(50))
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm))
        }
    }
}

@Composable
private fun RevisedOnboardingSectionHeader(title: String, people: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
        modifier = Modifier.padding(bottom = CorusSpacing.sm)) {
        if (people) Icon(Icons.Filled.People, null, Modifier.size(18.dp), tint = CorusColors.Accent)
        else VennDiagramIcon(size = 18.dp, color = CorusColors.Accent, shadedIntersection = true)
        Text(title.uppercase(Locale.getDefault()), style = CorusFont.sectionHeader, color = CorusColors.Secondary)
    }
}

private fun displayName(user: CymbalUser) = user.displayName.trim().ifEmpty { "@${user.username}" }
