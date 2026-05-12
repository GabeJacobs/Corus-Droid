package fm.corus.android.data.model

data class CymbalHashtag(
    val id: String,
    val name: String,
    val cymbalCount: Int = 0,
    val coverArtURLs: List<String> = emptyList(),
    val isFollowing: Boolean = false,
    val followerCount: Int = 0,
    val contributorCount: Int = 0,
    val recentCount: Int = 0,
)
