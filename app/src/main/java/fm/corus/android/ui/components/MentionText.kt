package fm.corus.android.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.corus.android.R
import fm.corus.android.data.model.FlairStyle
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.NunitoFamily

private const val MENTION_TAG = "mention"
private const val HASHTAG_TAG = "hashtag"

/**
 * Builds an AnnotatedString with tappable @mentions and #hashtags.
 * Annotations are added so taps can be detected.
 */
fun buildMentionAnnotatedString(
    text: String,
    baseStyle: SpanStyle = SpanStyle(),
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
                pushStringAnnotation(tag = MENTION_TAG, annotation = token.removePrefix("@"))
                withStyle(
                    baseStyle.copy(
                        color = CorusColors.Accent,
                        fontWeight = FontWeight.ExtraBold,
                    )
                ) {
                    append(token)
                }
                pop()
            } else {
                pushStringAnnotation(tag = HASHTAG_TAG, annotation = token.removePrefix("#"))
                withStyle(baseStyle.copy(color = CorusColors.Accent)) {
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
        // Bold username
        pushStringAnnotation(tag = MENTION_TAG, annotation = username)
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
                pushStringAnnotation(tag = MENTION_TAG, annotation = token.removePrefix("@"))
                withStyle(baseStyle.copy(color = CorusColors.Accent, fontWeight = FontWeight.ExtraBold)) {
                    append(token)
                }
                pop()
            } else {
                pushStringAnnotation(tag = HASHTAG_TAG, annotation = token.removePrefix("#"))
                withStyle(baseStyle.copy(color = CorusColors.Accent)) {
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
    modifier: Modifier = Modifier,
) {
    ClickableText(
        text = text,
        style = style.copy(color = CorusColors.Text),
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
        onClick = { offset ->
            text.getStringAnnotations(tag = MENTION_TAG, start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onMentionTap(annotation.item)
                    return@ClickableText
                }
            text.getStringAnnotations(tag = HASHTAG_TAG, start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onHashtagTap(annotation.item)
                    return@ClickableText
                }
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
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    // null = not yet measured, false = fits, true = overflows
    var overflowState by remember(caption) { mutableStateOf<Boolean?>(null) }
    var trimmedText by remember(caption) { mutableStateOf<AnnotatedString?>(null) }

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

    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(durationMillis = 200, easing = EaseInOut),
        ),
    ) {
        if (overflowState == null) {
            // First render: measure with BasicText to detect overflow
            BasicText(
                text = fullText,
                style = CorusFont.body.copy(color = CorusColors.Text),
                maxLines = maxCollapsedLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!result.hasVisualOverflow) {
                        overflowState = false
                    } else {
                        val lastLine = result.lineCount - 1
                        val lineEnd = result.getLineEnd(lastLine, visibleEnd = true)
                        val reserveChars = 8
                        val truncEnd = (lineEnd - reserveChars).coerceAtLeast(0)
                        val plainFull = fullText.text
                        var cutoff = truncEnd
                        while (cutoff > 0 && plainFull[cutoff] != ' ') cutoff--
                        if (cutoff == 0) cutoff = truncEnd

                        val baseStyle = SpanStyle(
                            fontFamily = NunitoFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp,
                        )
                        trimmedText = buildAnnotatedString {
                            append(fullText, 0, cutoff)
                            withStyle(baseStyle) { append("... ") }
                            withStyle(baseStyle.copy(color = CorusColors.Secondary)) { append("more") }
                        }
                        overflowState = true
                    }
                },
            )
        } else {
            ClickableText(
                text = displayText,
                style = CorusFont.body.copy(color = CorusColors.Text),
                maxLines = displayMaxLines,
                onClick = { offset ->
                    displayText.getStringAnnotations(tag = MENTION_TAG, start = offset, end = offset)
                        .firstOrNull()?.let { onMentionTap(it.item); return@ClickableText }
                    displayText.getStringAnnotations(tag = HASHTAG_TAG, start = offset, end = offset)
                        .firstOrNull()?.let { onHashtagTap(it.item); return@ClickableText }
                    if (canExpand) isExpanded = true
                },
            )
        }
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
        )

        // Flair badge — club members and verified users see their selected flair (matching iOS hasClubAccess)
        if ((isClubMember || isVerified) && !isBot) {
            val flair = flairStyle
            if (flair != FlairStyle.NONE) {
                Spacer(modifier = Modifier.width(4.dp))
                if (flair.usesAssetImage) {
                    Image(
                        painter = painterResource(R.drawable.logo_no_background),
                        contentDescription = "Corus",
                        modifier = Modifier.size(14.dp),
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
            val gold = Color(1.0f, 0.76f, 0.03f)
            val capsule = RoundedCornerShape(50)
            Row(
                modifier = Modifier
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
                    text = "1ST",
                    style = CorusFont.caption.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 0.3.sp,
                    ),
                    color = gold,
                )
            }
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
                    text = "BOT",
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
 * Parse the current mention query from text by checking the last whitespace-separated word.
 * Returns the query (without @) if the user is typing a mention, or null otherwise.
 */
fun parseMentionQuery(text: String): String? {
    val lastWord = text.split(Regex("\\s+")).lastOrNull() ?: return null
    return if (lastWord.startsWith("@") && lastWord.length > 1) lastWord.drop(1) else null
}

/** Extract @mention usernames from text. */
fun extractMentions(text: String): List<String> {
    return Regex("@([\\w.]+)").findAll(text).map { it.groupValues[1] }.toList()
}

/** Extract #hashtag names from text. */
fun extractHashtags(text: String): List<String> {
    return Regex("#(\\w+)").findAll(text).map { it.groupValues[1] }.toList()
}
