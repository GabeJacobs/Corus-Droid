package fm.corus.android.data.model

/**
 * One row in the composer hashtag autocomplete. [count] is a this-week number
 * (weekly windowed for trending rows, `recentCount` for prefix matches),
 * rendered only when > 0; [trending] shows a "Trending" badge and sorts the
 * row to the top.
 */
data class HashtagSuggestion(
    val name: String,
    val count: Int,
    val trending: Boolean,
)
