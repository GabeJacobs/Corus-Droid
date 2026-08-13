package fm.corus.android.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.HorizontalDivider
import fm.corus.android.R
import fm.corus.android.data.model.HashtagSuggestion
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.FlairStyle
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.NunitoFamily

private const val MENTION_TAG = "mention"
private const val HASHTAG_TAG = "hashtag"
private const val USERNAME_TAG = "username"

/**
 * Extracts the resolvable username from a whitespace-delimited `@mention` token
 * (e.g. "@epinephrine.auto," -> "epinephrine.auto"), or "" when the token holds
 * no valid handle. Mirrors web `mentionHandle` and iOS's punctuation trim on
 * tap — interior dots are preserved; a trailing "." is sentence punctuation.
 */
fun mentionHandle(token: String): String {
    val body = if (token.startsWith("@")) token.drop(1) else token
    val match = Regex("^[a-z0-9._]+", RegexOption.IGNORE_CASE).find(body) ?: return ""
    return match.value.lowercase().trimEnd('.')
}

/**
 * Builds an AnnotatedString with tappable @mentions and #hashtags.
 * Annotations are added so taps can be detected.
 */
fun buildMentionAnnotatedString(
    text: String,
    baseStyle: SpanStyle = SpanStyle(),
    linkColor: Color = CorusColors.Accent,
): AnnotatedString {
    val regex = Regex("(@[\\w.]+)|(#\\w+)")
    return buildAnnotatedString {
        var lastIndex = 0
        regex.findAll(text).forEach { match ->
            if (match.range.first > lastIndex) {
                withStyle(baseStyle) {
                    append(text.substring(lastIndex, match.range.first))
                }
            }
            val token = match.value
            if (token.startsWith("@")) {
                val handle = mentionHandle(token)
                if (handle.isNotEmpty()) {
                    pushStringAnnotation(tag = MENTION_TAG, annotation = handle)
                    withStyle(
                        baseStyle.copy(
                            color = linkColor,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    ) {
                        append(token)
                    }
                    pop()
                } else {
                    withStyle(baseStyle) { append(token) }
                }
            } else {
                pushStringAnnotation(tag = HASHTAG_TAG, annotation = token.removePrefix("#"))
                // Accent color only — regular weight so tags don't compete with
                // bold usernames / @mentions.
                withStyle(
                    baseStyle.copy(
                        color = linkColor,
                        fontWeight = FontWeight.Normal,
                    )
                ) {
                    append(token)
                }
                pop()
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) {
            withStyle(baseStyle) {
                append(text.substring(lastIndex))
            }
        }
    }
}

/**
 * Builds a caption AnnotatedString with bold username prefix + tappable mentions/hashtags.
 */
fun buildCaptionAnnotatedString(
    username: String,
    caption: String,
    onMentionTap: ((String) -> Unit)? = null,
    onHashtagTap: ((String) -> Unit)? = null,
): AnnotatedString {
    val baseStyle = SpanStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    )
    return buildAnnotatedString {
        // Bold username — tagged distinctly so taps route to profile, not to mention lookup
        pushStringAnnotation(tag = USERNAME_TAG, annotation = username)
        withStyle(
            baseStyle.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
            )
        ) {
            append(username)
        }
        pop()
        append(" ")

        // Caption with mentions/hashtags
        val regex = Regex("(@[\\w.]+)|(#\\w+)")
        var lastIndex = 0
        regex.findAll(caption).forEach { match ->
            if (match.range.first > lastIndex) {
                withStyle(baseStyle) {
                    append(caption.substring(lastIndex, match.range.first))
                }
            }
            val token = match.value
            if (token.startsWith("@")) {
                val handle = mentionHandle(token)
                if (handle.isNotEmpty()) {
                    pushStringAnnotation(tag = MENTION_TAG, annotation = handle)
                    withStyle(baseStyle.copy(color = CorusColors.Accent, fontWeight = FontWeight.ExtraBold)) {
                        append(token)
                    }
                    pop()
                } else {
                    withStyle(baseStyle) { append(token) }
                }
            } else {
                pushStringAnnotation(tag = HASHTAG_TAG, annotation = token.removePrefix("#"))
                withStyle(
                    baseStyle.copy(
                        color = CorusColors.Accent,
                        fontWeight = FontWeight.Normal,
                    )
                ) {
                    append(token)
                }
                pop()
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < caption.length) {
            withStyle(baseStyle) {
                append(caption.substring(lastIndex))
            }
        }
    }
}

/**
 * Tappable text with @mention and #hashtag navigation.
 * Matches iOS TappableMentionText.
 */
@Composable
fun TappableMentionText(
    text: AnnotatedString,
    style: TextStyle = CorusFont.body,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onMentionTap: (String) -> Unit = {},
    onHashtagTap: (String) -> Unit = {},
    // When set, a long-press anywhere on the text fires this (e.g. the comment
    // context menu). Was impossible with ClickableText, which swallowed the
    // press gesture so a long-press on the comment body never reached the row.
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Hand-rolled tap detection (replacing the deprecated ClickableText) so a
    // single gesture pass handles BOTH mention/hashtag taps and the long-press —
    // otherwise the two fight over the pointer and long-press never fires.
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style.copy(color = CorusColors.Text),
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layoutResult.value = it },
        modifier = modifier.pointerInput(text, onLongClick) {
            detectTapGestures(
                onLongPress = onLongClick?.let { cb -> { _ -> cb() } },
                onTap = { pos ->
                    val layout = layoutResult.value
                    if (layout != null) {
                        val offset = layout.getOffsetForPosition(pos)
                        val mention = text
                            .getStringAnnotations(tag = MENTION_TAG, start = offset, end = offset)
                            .firstOrNull()
                        val hashtag = text
                            .getStringAnnotations(tag = HASHTAG_TAG, start = offset, end = offset)
                            .firstOrNull()
                        when {
                            mention != null -> onMentionTap(mention.item)
                            hashtag != null -> onHashtagTap(hashtag.item)
                        }
                    }
                },
            )
        },
    )
}

/**
 * Expandable caption text with "... more" truncation, matching iOS TappableMentionText.
 * Shows up to [maxCollapsedLines] lines when collapsed. If text overflows, appends
 * "... more" (with "more" in secondary color). Tapping expands to full text.
 */
@Composable
fun ExpandableCaptionText(
    username: String,
    caption: String,
    maxCollapsedLines: Int = 3,
    onMentionTap: (String) -> Unit = {},
    onHashtagTap: (String) -> Unit = {},
    onUsernameTap: () -> Unit = {},
    onCommentTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    // null = not yet measured, false = fits, true = overflows
    var overflowState by remember(caption) { mutableStateOf<Boolean?>(null) }
    var trimmedText by remember(caption) { mutableStateOf<AnnotatedString?>(null) }

    val measurer = rememberTextMeasurer()
    val fullText = remember(username, caption) {
        buildCaptionAnnotatedString(username = username, caption = caption)
    }

    val displayText = when {
        isExpanded -> fullText
        overflowState == true && trimmedText != null -> trimmedText!!
        else -> fullText
    }
    val displayMaxLines = if (isExpanded) Int.MAX_VALUE else maxCollapsedLines
    val canExpand = !isExpanded && overflowState == true
    val secondaryColor = CorusColors.Secondary
    // The single base style used to measure AND render, so the two never disagree.
    val captionStyle = CorusFont.body.copy(color = CorusColors.Text)

    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(durationMillis = 200, easing = EaseInOut),
        ),
    ) {
        if (overflowState == null) {
            // First render: measure with BasicText to detect overflow.
            BasicText(
                text = fullText,
                style = captionStyle,
                maxLines = maxCollapsedLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!result.hasVisualOverflow) {
                        overflowState = false
                    } else {
                        // Binary-search the longest prefix whose collapsed candidate still fits
                        // the line budget, measuring the real candidate at each step. The old
                        // "back off 8 chars, then walk to the previous space" heuristic could
                        // walk back across a blank line and collapse the cutoff to ~the username:
                        // a caption like "#tag\n\nquote…" then rendered as "user… more" with the
                        // whole first line dropped. Mirrors ExpandableBioText / iOS CaptionTruncator.
                        val width = result.layoutInput.constraints.maxWidth
                        val cutoff = captionTruncationCutoff(fullText.length) { candidateEnd ->
                            measurer.measure(
                                text = buildCaptionCollapsedDisplay(fullText, candidateEnd, secondaryColor),
                                style = captionStyle,
                                constraints = Constraints(maxWidth = width),
                            ).lineCount <= maxCollapsedLines
                        }
                        trimmedText = buildCaptionCollapsedDisplay(fullText, cutoff, secondaryColor)
                        overflowState = true
                    }
                },
            )
        } else {
            ClickableText(
                text = displayText,
                style = captionStyle,
                maxLines = displayMaxLines,
                onClick = { offset ->
                    displayText.getStringAnnotations(tag = USERNAME_TAG, start = offset, end = offset)
                        .firstOrNull()?.let { onUsernameTap(); return@ClickableText }
                    displayText.getStringAnnotations(tag = MENTION_TAG, start = offset, end = offset)
                        .firstOrNull()?.let { onMentionTap(it.item); return@ClickableText }
                    displayText.getStringAnnotations(tag = HASHTAG_TAG, start = offset, end = offset)
                        .firstOrNull()?.let { onHashtagTap(it.item); return@ClickableText }
                    if (canExpand) {
                        isExpanded = true
                    } else {
                        onCommentTap()
                    }
                },
            )
        }
    }
}

internal const val CAPTION_ELLIPSIS = "... "
internal const val CAPTION_MORE = "more"

/**
 * Longest cutoff in 0..[maxLength] whose collapsed caption candidate still fits the line
 * budget, found by binary search over [fits] (which lays the candidate out and reports
 * whether it stays inside the budget). Mirrors [bioTruncationCutoff] and iOS
 * CaptionTruncator.fit().
 *
 * Measuring the real candidate is what fixes the "caption shows nothing" bug: the old
 * heuristic (back off a fixed number of chars, then walk to the previous space) could walk
 * back across a blank line and silently drop the caption's whole first line — e.g. a caption
 * like `#tag\n\nquote…` collapsed to just `username… more`. Pure + visible for testing.
 */
internal fun captionTruncationCutoff(maxLength: Int, fits: (cutoff: Int) -> Boolean): Int {
    var lo = 0
    var hi = maxLength
    var best = 0
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (fits(mid)) {
            best = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return best
}

/**
 * The collapsed caption for a given [cutoff] — the exact AnnotatedString that gets both
 * measured and rendered, so measurement and display can never disagree. Copies the
 * username/mention/hashtag spans from [fullText] for the visible prefix (trailing whitespace
 * trimmed so a cut landing right after a newline doesn't push "... more" onto its own line),
 * then appends "... more" ("more" in [secondaryColor]). Pure + visible for testing.
 */
internal fun buildCaptionCollapsedDisplay(
    fullText: AnnotatedString,
    cutoff: Int,
    secondaryColor: Color,
): AnnotatedString {
    val baseStyle = SpanStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    )
    var end = cutoff.coerceIn(0, fullText.length)
    while (end > 0 && fullText.text[end - 1].isWhitespace()) end--
    return buildAnnotatedString {
        append(fullText, 0, end)
        withStyle(baseStyle) { append(CAPTION_ELLIPSIS) }
        withStyle(baseStyle.copy(color = secondaryColor)) { append(CAPTION_MORE) }
    }
}

internal const val BIO_ELLIPSIS = "... "
internal const val BIO_MORE = "more"

/**
 * Longest prefix of [text] that still fits the collapsed line budget once "... more" is
 * appended, found by binary search over [fitsCollapsed] (which lays the candidate out and
 * reports whether it stays inside the budget). Mirrors iOS `TextTruncator.fit()`.
 *
 * The old character-count heuristic (back off N chars, walk to the previous space) could
 * walk back across a newline and silently drop a whole line — a bio of short hard-wrapped
 * lines rendered two lines where three fit. Measuring the real candidate can't do that.
 * Pure + visible for testing.
 */
internal fun bioTruncationCutoff(text: String, fitsCollapsed: (candidate: String) -> Boolean): Int {
    var lo = 0
    var hi = text.length
    var best = 0
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (fitsCollapsed(bioCollapsedDisplay(text, mid))) {
            best = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return best
}

/** The collapsed bio string for a given cutoff — what gets measured and what gets shown. */
internal fun bioCollapsedDisplay(text: String, cutoff: Int): String =
    text.take(cutoff).trimEnd() + BIO_ELLIPSIS + BIO_MORE

private const val LINK_TAG = "link"

/**
 * Emails and web addresses (with or without a scheme) inside a bio. Emails come first in
 * the alternation so `arielle@corus.fm` is one mailto link rather than a bare `corus.fm`
 * domain. Matches what iOS gets from NSDataDetector's link checking.
 */
private val BIO_LINK_REGEX = Regex(
    "[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}" +
        "|(https?://)?([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}(/[^\\s]*)?"
)

/** Sentence punctuation that trails a link rather than belonging to it. */
private const val LINK_TRAILING_PUNCTUATION = ".,;:!?)"

/**
 * Accent-colored, tappable emails and links inside an otherwise plain bio, tagged so
 * [ExpandableBioText] can route a tap to the browser or mail app. Pure + visible for testing.
 */
internal fun buildLinkifiedBio(bio: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    BIO_LINK_REGEX.findAll(bio).forEach { match ->
        val target = match.value.trimEnd { it in LINK_TRAILING_PUNCTUATION }
        if (target.isEmpty()) return@forEach
        append(bio.substring(lastIndex, match.range.first))
        pushStringAnnotation(tag = LINK_TAG, annotation = target)
        withStyle(SpanStyle(color = linkColor)) { append(target) }
        pop()
        lastIndex = match.range.first + target.length
    }
    if (lastIndex < bio.length) append(bio.substring(lastIndex))
}

/** `mailto:` for addresses, `https://` for scheme-less domains. Pure + visible for testing. */
internal fun bioLinkUri(target: String): String = when {
    target.contains("://") -> target
    target.contains("@") -> "mailto:$target"
    else -> "https://$target"
}

/**
 * Expandable profile bio with a [maxCollapsedLines] cap and a tap-to-reveal "... more"
 * affordance. Collapsed, it shows the longest prefix that still leaves room for "... more"
 * on the last line ("more" in primary text color so it reads against the secondary-colored
 * bio); tapping anywhere expands to the full bio. Emails and links are accent-colored and
 * tappable once the full text is showing. Bios carry no mentions or hashtags, so this is a
 * lighter sibling of [ExpandableCaptionText]. Mirrors the iOS ExpandableBioText in
 * ProfileHeaderView.
 */
@Composable
fun ExpandableBioText(
    bio: String,
    maxCollapsedLines: Int = 3,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember(bio) { mutableStateOf(false) }
    // null = not yet measured, false = fits, true = overflows
    var overflowState by remember(bio) { mutableStateOf<Boolean?>(null) }
    var trimmedText by remember(bio) { mutableStateOf<AnnotatedString?>(null) }

    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val moreColor = CorusColors.Text
    val linkColor = CorusColors.Accent
    val bioStyle = CorusFont.bio.copy(color = CorusColors.Secondary)
    val fullText = remember(bio, linkColor) { buildLinkifiedBio(bio, linkColor) }

    val displayText = when {
        isExpanded -> fullText
        overflowState == true && trimmedText != null -> trimmedText!!
        else -> fullText
    }
    val displayMaxLines = if (isExpanded) Int.MAX_VALUE else maxCollapsedLines
    val canExpand = !isExpanded && overflowState == true

    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(durationMillis = 200, easing = EaseInOut),
        ),
    ) {
        if (overflowState == null) {
            // First render: measure with BasicText to detect overflow.
            BasicText(
                text = fullText,
                style = bioStyle,
                maxLines = maxCollapsedLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!result.hasVisualOverflow) {
                        overflowState = false
                    } else {
                        val width = result.layoutInput.constraints.maxWidth
                        val cutoff = bioTruncationCutoff(bio) { candidate ->
                            measurer.measure(
                                text = candidate,
                                style = bioStyle,
                                constraints = Constraints(maxWidth = width),
                            ).lineCount <= maxCollapsedLines
                        }
                        trimmedText = buildAnnotatedString {
                            append(bio.take(cutoff).trimEnd())
                            append(BIO_ELLIPSIS)
                            withStyle(SpanStyle(color = moreColor)) { append(BIO_MORE) }
                        }
                        overflowState = true
                    }
                },
            )
        } else {
            ClickableText(
                text = displayText,
                style = bioStyle,
                maxLines = displayMaxLines,
                onClick = { offset ->
                    val link = displayText
                        .getStringAnnotations(tag = LINK_TAG, start = offset, end = offset)
                        .firstOrNull()
                    when {
                        link != null -> openBioLink(context, link.item)
                        canExpand -> isExpanded = true
                    }
                },
            )
        }
    }
}

private fun openBioLink(context: android.content.Context, target: String) {
    try {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(bioLinkUri(target)),
            )
        )
    } catch (_: Exception) { }
}

/**
 * Gold "1ST" trophy capsule for the first poster of a song/film, matching iOS.
 * Standalone so callers can place it next to the display name (detail screens)
 * rather than the username.
 */
@Composable
fun FirstPosterBadge(modifier: Modifier = Modifier) {
    val gold = Color(1.0f, 0.76f, 0.03f)
    val capsule = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .background(gold.copy(alpha = 0.14f), shape = capsule)
            .border(0.8.dp, gold.copy(alpha = 0.4f), shape = capsule)
            .padding(horizontal = 6.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = gold,
            modifier = Modifier.size(9.dp),
        )
        Text(
            text = stringResource(R.string.mention_badge_first),
            style = CorusFont.caption.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 0.3.sp,
            ),
            color = gold,
        )
    }
}

/**
 * Purple "NEW RELEASE" flame capsule for freshly released tracks/films,
 * matching iOS. Standalone so callers can place it next to the display name
 * (song/film detail headers) rather than inline with the username; the feed
 * badge (`UsernameWithFlair`) renders the same pill inline.
 */
@Composable
fun NewReleaseBadge(
    modifier: Modifier = Modifier,
    // The feed's inline badge uses the default 9; the song/film detail headers
    // pass a larger value (they have room to breathe). Icon, padding, and
    // spacing scale with it so the pill stays proportioned at any size.
    fontSize: TextUnit = 9.sp,
) {
    val newReleasePurple = Color(0.62f, 0.35f, 0.95f)
    val capsule = RoundedCornerShape(50)
    val scale = fontSize.value
    Row(
        modifier = modifier
            .background(newReleasePurple.copy(alpha = 0.14f), shape = capsule)
            .border(0.8.dp, newReleasePurple.copy(alpha = 0.4f), shape = capsule)
            .padding(horizontal = (scale / 1.5f).dp, vertical = (scale / 3.6f).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((scale / 3f).dp),
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = newReleasePurple,
            modifier = Modifier.size(scale.dp),
        )
        Text(
            text = stringResource(R.string.mention_badge_new_release),
            style = CorusFont.caption.copy(
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                letterSpacing = 0.3.sp,
            ),
            color = newReleasePurple,
        )
    }
}

/**
 * Username row with optional flair badge, matching iOS UsernameWithBotBadge.
 */
@Composable
fun UsernameWithFlair(
    username: String,
    isVerified: Boolean = false,
    isClubMember: Boolean = false,
    flairStyle: FlairStyle = FlairStyle.CHECKMARK,
    isBot: Boolean = false,
    isFirstPoster: Boolean = false,
    isNewRelease: Boolean = false,
    botType: String? = null,
    showAtPrefix: Boolean = false,
    style: TextStyle = CorusFont.username,
    color: Color = CorusColors.Text,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(
            text = if (showAtPrefix) "@$username" else username,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        // Flair badge — club members and verified users see their selected flair (matching iOS hasClubAccess)
        if ((isClubMember || isVerified) && !isBot) {
            val flair = flairStyle
            if (flair != FlairStyle.NONE) {
                Spacer(modifier = Modifier.width(4.dp))
                if (flair.usesAssetImage) {
                    // 18dp, not 14: the logo artwork carries its own transparent
                    // margin, so at the icon size it reads visibly smaller than the
                    // other flairs. Matches iOS UsernameWithBotBadge (18pt asset,
                    // 12pt SF Symbols).
                    Image(
                        painter = painterResource(R.drawable.logo_no_background),
                        contentDescription = stringResource(R.string.mention_cd_corus),
                        modifier = Modifier.size(18.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(CorusColors.Accent),
                    )
                } else if (flair.icon != null) {
                    Icon(
                        imageVector = flair.icon!!,
                        contentDescription = flair.displayName,
                        tint = CorusColors.Accent,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // Trophy / 1ST badge — gold capsule pill matching iOS
        if (isFirstPoster) {
            Spacer(modifier = Modifier.width(5.dp))
            FirstPosterBadge()
        }

        // New release — purple capsule pill with flame icon
        if (isNewRelease) {
            Spacer(modifier = Modifier.width(5.dp))
            NewReleaseBadge()
        }

        // Bot badge — purple capsule pill matching iOS
        if (isBot) {
            Spacer(modifier = Modifier.width(5.dp))
            val purple = Color(0xFF9B59B6)
            Box(
                modifier = Modifier
                    .alpha(0.8f)
                    .background(purple.copy(alpha = 0.14f), shape = RoundedCornerShape(50))
                    .border(0.8.dp, purple.copy(alpha = 0.35f), shape = RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.mention_badge_bot),
                    style = CorusFont.caption.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.3.sp,
                    ),
                    color = purple.copy(alpha = 0.9f),
                )
            }
        }
    }
}

/**
 * Parse the current mention query from text at the given caret position.
 * Returns the query (without @) if the word containing the caret is an active
 * @mention, or null otherwise. Defaults to the end of the string (legacy
 * behavior) when the caret is not supplied.
 */
fun parseMentionQuery(text: String, caret: Int = text.length): String? {
    val clamped = caret.coerceIn(0, text.length)
    var wordStart = clamped
    while (wordStart > 0) {
        val c = text[wordStart - 1]
        if (c == ' ' || c == '\n' || c == '\t' || c == '\r') break
        wordStart--
    }
    if (wordStart >= clamped) return null
    val word = text.substring(wordStart, clamped)
    return if (word.startsWith("@") && word.length > 1) word.drop(1) else null
}

/**
 * Parse the current #hashtag query from text at the given caret position.
 * Returns the query (without #) if the word containing the caret is an active
 * #hashtag, or null otherwise. Unlike [parseMentionQuery], a bare "#" (empty
 * query) is valid — it opens the suggestions list to trending tags. Stays
 * active only while every char after "#" is a valid tag char ([a-z0-9_], the
 * `#(\w+)` charset used at post time); trailing punctuation closes it.
 */
fun parseHashtagQuery(text: String, caret: Int = text.length): String? {
    val clamped = caret.coerceIn(0, text.length)
    var wordStart = clamped
    while (wordStart > 0) {
        val c = text[wordStart - 1]
        if (c == ' ' || c == '\n' || c == '\t' || c == '\r') break
        wordStart--
    }
    if (wordStart >= clamped || text[wordStart] != '#') return null
    val body = text.substring(wordStart + 1, clamped)
    if (!Regex("^[a-z0-9_]*$", RegexOption.IGNORE_CASE).matches(body)) return null
    return body.lowercase()
}

/** Extract @mention usernames from text. */
fun extractMentions(text: String): List<String> {
    return Regex("@[\\w.]+").findAll(text)
        .map { mentionHandle(it.value) }
        .filter { it.isNotEmpty() }
        .toList()
}

/** Extract #hashtag names from text. */
fun extractHashtags(text: String): List<String> {
    return Regex("#(\\w+)").findAll(text).map { it.groupValues[1] }.toList()
}

/**
 * Replace the in-progress `@query` at the end of [text] with `@username ` (trailing space).
 * If no in-progress mention is found, returns the text unchanged.
 */
fun applyMention(text: String, username: String): String {
    return applyMentionWithCaret(text, text.length, username).first
}

/**
 * [applyMention] for [TextFieldValue]: replaces the `@partial` token
 * containing the caret, preserves any text after it, and moves the cursor to
 * just after the inserted `@username` (and any trailing space) so the user
 * can keep typing.
 */
fun applyMention(value: TextFieldValue, username: String): TextFieldValue {
    val (newText, newCaret) = applyMentionWithCaret(value.text, value.selection.start, username)
    return TextFieldValue(newText, selection = TextRange(newCaret))
}

private fun applyMentionWithCaret(text: String, caret: Int, username: String): Pair<String, Int> {
    val clamped = caret.coerceIn(0, text.length)
    var wordStart = clamped
    while (wordStart > 0) {
        val c = text[wordStart - 1]
        if (c == ' ' || c == '\n' || c == '\t' || c == '\r') break
        wordStart--
    }
    if (wordStart >= clamped || text[wordStart] != '@') {
        return text to clamped
    }
    val hasTrailingSpace = clamped < text.length && text[clamped] == ' '
    val replacement = if (hasTrailingSpace) "@$username" else "@$username "
    val before = text.substring(0, wordStart)
    val after = text.substring(clamped)
    val newText = before + replacement + after
    val newCaret = wordStart + replacement.length + if (hasTrailingSpace) 1 else 0
    return newText to newCaret
}

/**
 * [applyHashtag] for [TextFieldValue]: replaces the `#partial` token containing
 * the caret with `#tag `, preserves any text after it, and moves the cursor to
 * just after the inserted tag (and any trailing space).
 */
fun applyHashtag(value: TextFieldValue, tag: String): TextFieldValue {
    val (newText, newCaret) = applyHashtagWithCaret(value.text, value.selection.start, tag)
    return TextFieldValue(newText, selection = TextRange(newCaret))
}

/** Replace the in-progress `#query` at the end of [text] with `#tag `. */
fun applyHashtag(text: String, tag: String): String =
    applyHashtagWithCaret(text, text.length, tag).first

private fun applyHashtagWithCaret(text: String, caret: Int, tag: String): Pair<String, Int> {
    val clamped = caret.coerceIn(0, text.length)
    var wordStart = clamped
    while (wordStart > 0) {
        val c = text[wordStart - 1]
        if (c == ' ' || c == '\n' || c == '\t' || c == '\r') break
        wordStart--
    }
    if (wordStart >= clamped || text[wordStart] != '#') {
        return text to clamped
    }
    val hasTrailingSpace = clamped < text.length && text[clamped] == ' '
    val replacement = if (hasTrailingSpace) "#$tag" else "#$tag "
    val before = text.substring(0, wordStart)
    val after = text.substring(clamped)
    val newText = before + replacement + after
    val newCaret = wordStart + replacement.length + if (hasTrailingSpace) 1 else 0
    return newText to newCaret
}

/**
 * A list of mention suggestions rendered as tappable rows. Used above keyboard
 * inputs in comments, captions, and compose.
 */
@Composable
fun MentionSuggestionsList(
    users: List<CymbalUser>,
    onSelect: (CymbalUser) -> Unit,
    modifier: Modifier = Modifier,
    isSearching: Boolean = false,
) {
    if (users.isEmpty() && !isSearching) return
    Column(modifier = modifier) {
        HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
        if (users.isEmpty() && isSearching) {
            MentionSearchingRow()
        } else {
            users.forEachIndexed { index, user ->
                MentionSuggestionRow(user = user, onClick = { onSelect(user) })
                if (index < users.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = CorusSpacing.lg + 28.dp + CorusSpacing.sm),
                        color = CorusColors.Divider,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MentionSearchingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = CorusColors.Accent,
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Text(
            text = stringResource(R.string.mention_searching),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
        )
    }
}

/**
 * A list of hashtag suggestions rendered as tappable rows. Mirrors
 * [MentionSuggestionsList] so "#" feels identical to "@" — same divider rhythm
 * and row height, with a "#" glyph where the avatar would be. Trending rows
 * carry a "Trending" badge and the count is a bounded this-week number.
 */
@Composable
fun HashtagSuggestionsList(
    hashtags: List<HashtagSuggestion>,
    onSelect: (HashtagSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hashtags.isEmpty()) return
    Column(modifier = modifier) {
        HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
        hashtags.forEachIndexed { index, tag ->
            HashtagSuggestionRow(tag = tag, onClick = { onSelect(tag) })
            if (index < hashtags.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = CorusSpacing.lg + 28.dp + CorusSpacing.sm),
                    color = CorusColors.Divider,
                    thickness = 0.5.dp,
                )
            }
        }
    }
}

@Composable
private fun HashtagSuggestionRow(
    tag: HashtagSuggestion,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(CorusColors.Accent.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "#", style = CorusFont.username, color = CorusColors.Accent)
        }
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Column {
            Text(
                text = "#${tag.name}",
                style = CorusFont.username,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tag.trending || tag.count > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tag.trending) {
                        Text(
                            text = stringResource(R.string.hashtag_suggestion_trending),
                            style = CorusFont.caption,
                            color = CorusColors.Accent,
                            maxLines = 1,
                        )
                    }
                    if (tag.trending && tag.count > 0) {
                        Text(
                            text = " · ",
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                        )
                    }
                    if (tag.count > 0) {
                        val velocityRes = if (tag.count == 1) {
                            R.string.hashtag_velocity_this_week_one
                        } else {
                            R.string.hashtag_velocity_this_week
                        }
                        Text(
                            text = stringResource(velocityRes, tag.count.toString()),
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MentionSuggestionRow(
    user: CymbalUser,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(avatarURL = user.avatarURL, displayName = user.displayName, size = 28.dp)
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Column {
            Text(
                text = user.username,
                style = CorusFont.username,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.displayName.isNotBlank() && user.displayName.lowercase() != user.username.lowercase()) {
                Text(
                    text = user.displayName,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
