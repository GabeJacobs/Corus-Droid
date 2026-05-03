package fm.corus.android.data.model

import java.util.Date

data class CymbalUser(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarURL: String? = null,
    val avatarThumbURL: String? = null,
    val bio: String = "",
    val website: String? = null,
    val isVerified: Boolean = false,
    val isClubMember: Boolean = false,
    val isBot: Boolean = false,
    val botType: String? = null,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val hashtagCount: Int = 0,
    val cymbalCount: Int = 0,
    val trackCount: Int? = null,
    val movieCount: Int? = null,
    val savesCount: Int = 0,
    val vinylColor: String = "black",
    val frameColor: String = "black",
    val profileFlair: String = "checkmark",
    val rainEffect: String = "off",
    val snowEffect: String = "off",
    val discoEffect: String = "off",
    val artistsInCommonCount: Int? = null,
    val deletionStatus: String? = null,
    val lastPostedAt: Date? = null,
    val createdAt: Date? = null,
) {
    val vinylStyle: VinylStyle get() = VinylStyle.from(vinylColor)
    val frameStyle: FrameStyle get() = FrameStyle.from(frameColor)
    val flairStyle: FlairStyle get() = FlairStyle.from(profileFlair)
    val rainIntensity: RainIntensity get() = RainIntensity.from(rainEffect)
    val snowIntensity: SnowIntensity get() = SnowIntensity.from(snowEffect)
    val discoIntensityLevel: DiscoIntensity get() = DiscoIntensity.from(discoEffect)
    val isMusicBot: Boolean get() = isBot && botType == "music"
    val isFilmBot: Boolean get() = isBot && botType == "film"

    /**
     * Initial profile-tab segment based purely on the per-medium counters on
     * the user document. Returns null when those counters aren't populated —
     * callers should fall back to deriving from loaded posts (see
     * [preferredProfileSegmentFromPosts]). Bots always default to 0.
     */
    val preferredProfileSegment: Int?
        get() {
            if (isBot) return 0
            val tracks = trackCount ?: return null
            val movies = movieCount ?: return null
            return if (tracks == 0 && movies > 0) 1 else 0
        }

    /**
     * Initial profile-tab segment derived from already-loaded posts, used
     * when the per-medium counters on the user doc aren't populated.
     *
     * @param loadedPosts the page of posts already fetched for this profile
     * @param hasMore whether more posts remain unfetched
     * @return 1 (FILM) when the loaded set is exhausted and contains films
     *   but no songs; 0 (MUSIC) when the loaded set is exhausted; null while
     *   we still don't have a complete picture.
     */
    fun preferredProfileSegmentFromPosts(
        loadedPosts: List<CymbalPost>,
        hasMore: Boolean,
    ): Int? {
        if (isBot) return 0
        // Trust the explicit counters first if we have them.
        preferredProfileSegment?.let { return it }
        // Otherwise we can only decide once we've seen every post — either the
        // page exhausted the user's content, or the loaded set already covers
        // every cymbal on their profile per the (still-trusted) total counter.
        val sawEverything = !hasMore || loadedPosts.size >= cymbalCount
        if (!sawEverything) return null
        val hasTrack = loadedPosts.any { it.mediaType == MediaType.TRACK }
        val hasMovie = loadedPosts.any { it.mediaType == MediaType.MOVIE }
        return if (!hasTrack && hasMovie) 1 else 0
    }

    fun withArtistsInCommonCount(count: Int?): CymbalUser =
        copy(artistsInCommonCount = count)

    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): CymbalUser = CymbalUser(
            id = id,
            username = data["username"] as? String ?: "",
            displayName = data["displayName"] as? String ?: "",
            avatarURL = data["avatarURL"] as? String,
            avatarThumbURL = data["avatarThumbURL"] as? String,
            bio = data["bio"] as? String ?: "",
            website = data["website"] as? String,
            isVerified = data["isVerified"] as? Boolean ?: false,
            isClubMember = data["isClubMember"] as? Boolean ?: false,
            isBot = data["isBot"] as? Boolean ?: false,
            botType = data["botType"] as? String,
            followerCount = (data["followerCount"] as? Number)?.toInt() ?: 0,
            followingCount = (data["followingCount"] as? Number)?.toInt() ?: 0,
            hashtagCount = (data["hashtagCount"] as? Number)?.toInt() ?: 0,
            cymbalCount = (data["cymbalCount"] as? Number)?.toInt() ?: 0,
            trackCount = (data["trackCount"] as? Number)?.toInt(),
            movieCount = (data["movieCount"] as? Number)?.toInt(),
            savesCount = (data["savesCount"] as? Number)?.toInt() ?: 0,
            vinylColor = data["vinylColor"] as? String ?: "black",
            frameColor = data["frameColor"] as? String ?: "black",
            profileFlair = data["profileFlair"] as? String ?: "checkmark",
            rainEffect = data["rainEffect"] as? String ?: "off",
            snowEffect = data["snowEffect"] as? String ?: "off",
            discoEffect = data["discoEffect"] as? String ?: "off",
            artistsInCommonCount = (data["artistsInCommonCount"] as? Number)?.toInt(),
            deletionStatus = data["deletionStatus"] as? String,
            lastPostedAt = (data["lastPostedAt"] as? com.google.firebase.Timestamp)?.toDate(),
            createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate(),
        )
    }
}
