package fm.corus.android.data.model

import java.util.Date
import fm.corus.android.domain.PlaylistTrialUsed

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
    /** Staff flag (server-managed; absent = false). Gates the staff-only "Corus" flair option. */
    val isStaff: Boolean = false,
    val isBot: Boolean = false,
    val botType: String? = null,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val hashtagCount: Int = 0,
    val cymbalCount: Int = 0,
    val trackCount: Int? = null,
    val movieCount: Int? = null,
    val savesCount: Int = 0,
    /** Mirror of users_v2/{uid}.likesCount — server-maintained count of posts this
     *  user has liked (mirrors savesCount). Drives the Likes-tab "export all" gate. */
    val likesCount: Int? = null,
    /** Mirror of users_v2/{uid}.favoritesCount — drives the favorite-people cap. */
    val favoritesCount: Int = 0,
    val vinylColor: String = "black",
    val frameColor: String = "black",
    val profileFlair: String = "checkmark",
    val rainEffect: String = "off",
    val snowEffect: String = "off",
    val discoEffect: String = "off",
    val featuredTab: String = "music",
    val artistsInCommonCount: Int? = null,
    val deletionStatus: String? = null,
    val lastPostedAt: Date? = null,
    val createdAt: Date? = null,
    /**
     * First-activation timestamp for the Corus Club subscription. Set on
     * initial trial/purchase and only cleared on cancel — not bumped by
     * renewals — so it represents the user's *original* sign-up.
     */
    val clubMemberSince: Date? = null,
    /**
     * Mirror of users_v2/{uid}.tasteSeedCount — how many onboarding-quiz picks
     * the server persisted (private/tasteSeed). > 0 means taste matches can be
     * ranked from the quiz seed before this user has posted, so the search
     * rail's post-count pre-gate must not suppress the fetch. Absent (null) on
     * accounts that never took the quiz.
     */
    val tasteSeedCount: Int? = null,
    /** Mirror of users_v2/{uid}.playlistTrialUsed for client-side playlist gating. */
    val playlistTrialUsed: PlaylistTrialUsed = PlaylistTrialUsed(),
) {
    val hasClubAccess: Boolean get() = isClubMember || isVerified

    val rawVinylStyle: VinylStyle get() = VinylStyle.from(vinylColor)
    val rawFrameStyle: FrameStyle get() = FrameStyle.from(frameColor)
    val rawFlairStyle: FlairStyle get() = FlairStyle.from(profileFlair)
    val rawRainIntensity: RainIntensity get() = RainIntensity.from(rainEffect)
    val rawSnowIntensity: SnowIntensity get() = SnowIntensity.from(snowEffect)
    val rawDiscoIntensityLevel: DiscoIntensity get() = DiscoIntensity.from(discoEffect)

    val vinylStyle: VinylStyle get() = if (hasClubAccess) rawVinylStyle else VinylStyle.BLACK
    val frameStyle: FrameStyle get() = if (hasClubAccess) rawFrameStyle else FrameStyle.BLACK
    val flairStyle: FlairStyle get() = if (hasClubAccess) rawFlairStyle else FlairStyle.CHECKMARK
    val rainIntensity: RainIntensity get() = if (hasClubAccess) rawRainIntensity else RainIntensity.OFF
    val snowIntensity: SnowIntensity get() = if (hasClubAccess) rawSnowIntensity else SnowIntensity.OFF
    val discoIntensityLevel: DiscoIntensity get() = if (hasClubAccess) rawDiscoIntensityLevel else DiscoIntensity.OFF
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
            // Index is into the *displayed* tab order: slot 0 is the user's
            // featured side, slot 1 is the other primary side. Edge-case
            // fallback flips to slot 1 when the featured side has no posts
            // but the other side does.
            return if (featuredTab == "film") {
                if (movies == 0 && tracks > 0) 1 else 0
            } else {
                if (tracks == 0 && movies > 0) 1 else 0
            }
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
        return if (featuredTab == "film") {
            if (!hasMovie && hasTrack) 1 else 0
        } else {
            if (!hasTrack && hasMovie) 1 else 0
        }
    }

    fun withArtistsInCommonCount(count: Int?): CymbalUser =
        copy(artistsInCommonCount = count)

    companion object {
        /**
         * Synthesizes a [CymbalUser] from a post's denormalized `author` block.
         * Field set mirrors `buildAuthorPreview()` in the backend
         * (`Corus-Web/backend/functions/postCreate.js`). Counts /
         * customization not carried on the preview get safe defaults — feed
         * rendering doesn't read those, and surfaces that do (profile pages,
         * customization sheets) fetch the user doc directly.
         *
         * Returns null when the block is missing or empty (no username AND
         * no displayName), so callers can fall back to a per-uid fetch for
         * legacy posts not yet covered by the one-time backfill.
         */
        fun fromAuthorPreview(uid: String, data: Map<String, Any?>?): CymbalUser? {
            if (data == null) return null
            val username = data["username"] as? String ?: ""
            val displayName = data["displayName"] as? String ?: ""
            if (username.isEmpty() && displayName.isEmpty()) return null
            return CymbalUser(
                id = uid,
                username = username,
                displayName = displayName,
                avatarURL = data["avatarURL"] as? String,
                avatarThumbURL = data["avatarThumbURL"] as? String,
                isVerified = data["isVerified"] as? Boolean ?: false,
                isClubMember = data["isClubMember"] as? Boolean ?: false,
                isBot = data["isBot"] as? Boolean ?: false,
                botType = data["botType"] as? String,
                profileFlair = data["profileFlair"] as? String ?: "checkmark",
            )
        }

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
            isStaff = data["isStaff"] as? Boolean ?: false,
            isBot = data["isBot"] as? Boolean ?: false,
            botType = data["botType"] as? String,
            followerCount = (data["followerCount"] as? Number)?.toInt() ?: 0,
            followingCount = (data["followingCount"] as? Number)?.toInt() ?: 0,
            hashtagCount = (data["hashtagCount"] as? Number)?.toInt() ?: 0,
            cymbalCount = (data["cymbalCount"] as? Number)?.toInt() ?: 0,
            trackCount = (data["trackCount"] as? Number)?.toInt(),
            movieCount = (data["movieCount"] as? Number)?.toInt(),
            savesCount = (data["savesCount"] as? Number)?.toInt() ?: 0,
            likesCount = (data["likesCount"] as? Number)?.toInt(),
            favoritesCount = (data["favoritesCount"] as? Number)?.toInt() ?: 0,
            vinylColor = data["vinylColor"] as? String ?: "black",
            frameColor = data["frameColor"] as? String ?: "black",
            profileFlair = data["profileFlair"] as? String ?: "checkmark",
            rainEffect = data["rainEffect"] as? String ?: "off",
            snowEffect = data["snowEffect"] as? String ?: "off",
            discoEffect = data["discoEffect"] as? String ?: "off",
            featuredTab = (data["featuredTab"] as? String)?.takeIf { it == "film" || it == "music" } ?: "music",
            artistsInCommonCount = (data["artistsInCommonCount"] as? Number)?.toInt(),
            deletionStatus = data["deletionStatus"] as? String,
            lastPostedAt = (data["lastPostedAt"] as? com.google.firebase.Timestamp)?.toDate(),
            // Firestore direct reads give a Timestamp; the getNewUsers callable
            // sends millis (a Number). Accept either so "Joined Nh ago" renders.
            createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate()
                ?: (data["createdAt"] as? Number)?.let { java.util.Date(it.toLong()) },
            clubMemberSince = (data["clubMemberSince"] as? com.google.firebase.Timestamp)?.toDate(),
            tasteSeedCount = (data["tasteSeedCount"] as? Number)?.toInt(),
            playlistTrialUsed = PlaylistTrialUsed.fromFirestore(data),
        )
    }
}
