package fm.corus.android.data.model

enum class ProfileMediaTab(val raw: String, val logicalIndex: Int) {
    MUSIC("music", 0),
    FILM("film", 1),
    BOOKS("books", 2);

    companion object {
        fun fromRaw(raw: String?): ProfileMediaTab? = entries.firstOrNull { it.raw == raw }

        fun featuredFromRaw(raw: String?): ProfileMediaTab = when (raw) {
            "film" -> FILM
            "books" -> BOOKS
            else -> MUSIC
        }
    }
}

data class ProfileTabPreferences(
    val order: List<ProfileMediaTab>,
    val hidden: Set<ProfileMediaTab>,
) {
    fun visibleMediaTabs(booksEnabled: Boolean): List<ProfileMediaTab> {
        val available = availableTabs(booksEnabled)
        val visible = order.filter { it in available && it !in hidden }
        return visible.ifEmpty { available }
    }

    fun visibleLogicalIndices(booksEnabled: Boolean): List<Int> =
        visibleMediaTabs(booksEnabled).map { it.logicalIndex }

    fun editorTabs(booksEnabled: Boolean): List<ProfileMediaTab> {
        val available = availableTabs(booksEnabled)
        return order.filter { it in available }
    }

    fun canHide(tab: ProfileMediaTab, booksEnabled: Boolean = false): Boolean =
        tab in hidden || visibleMediaTabs(booksEnabled).size > 1

    fun toggleHidden(tab: ProfileMediaTab, booksEnabled: Boolean = false): ProfileTabPreferences {
        if (tab in hidden) return copy(hidden = hidden - tab)
        if (!canHide(tab, booksEnabled)) return this
        return copy(hidden = hidden + tab)
    }

    fun move(from: ProfileMediaTab, to: ProfileMediaTab): ProfileTabPreferences {
        if (from == to) return this
        val fromIndex = order.indexOf(from)
        val toIndex = order.indexOf(to)
        if (fromIndex < 0 || toIndex < 0) return this
        val next = order.toMutableList()
        next.removeAt(fromIndex)
        next.add(toIndex, from)
        return copy(order = next)
    }

    fun persisted(
        existingOrder: List<String>,
        existingHidden: List<String>,
        booksEnabled: Boolean,
    ): PersistedProfileTabs {
        val editor = availableTabs(booksEnabled).toSet()
        val merged = order.filter { it in editor }.toMutableList()
        sanitize(existingOrder).forEachIndexed { index, tab ->
            if (tab !in editor && tab !in merged) {
                merged.add(index.coerceAtMost(merged.size), tab)
            }
        }
        DEFAULT_ORDER.forEach { tab ->
            if (tab !in merged) merged.add(tab)
        }

        val mergedHidden = hidden.filter { it in editor }.toMutableSet()
        sanitize(existingHidden).forEach { tab ->
            if (tab !in editor) mergedHidden.add(tab)
        }
        if (availableTabs(booksEnabled).all { it in mergedHidden }) {
            mergedHidden.clear()
        }

        val featured = merged.firstOrNull { it !in mergedHidden } ?: ProfileMediaTab.MUSIC
        return PersistedProfileTabs(
            order = merged.map { it.raw },
            hidden = DEFAULT_ORDER.filter { it in mergedHidden }.map { it.raw },
            featuredTab = featured.raw,
        )
    }

    fun preferredLogicalSegment(
        trackCount: Int?,
        movieCount: Int?,
        bookCount: Int?,
        booksEnabled: Boolean,
    ): Int? {
        val tracks = trackCount ?: return null
        val movies = movieCount ?: return null
        val books = bookCount ?: 0
        val counts = mapOf(
            ProfileMediaTab.MUSIC to tracks,
            ProfileMediaTab.FILM to movies,
            ProfileMediaTab.BOOKS to books,
        )
        val visible = visibleMediaTabs(booksEnabled)
        return visible.firstOrNull { (counts[it] ?: 0) > 0 }?.logicalIndex
            ?: visible.firstOrNull()?.logicalIndex
            ?: 0
    }

    fun preferredLogicalSegment(
        hasTrack: Boolean,
        hasMovie: Boolean,
        hasBook: Boolean,
        booksEnabled: Boolean,
    ): Int {
        val flags = mapOf(
            ProfileMediaTab.MUSIC to hasTrack,
            ProfileMediaTab.FILM to hasMovie,
            ProfileMediaTab.BOOKS to hasBook,
        )
        val visible = visibleMediaTabs(booksEnabled)
        return visible.firstOrNull { flags[it] == true }?.logicalIndex
            ?: visible.firstOrNull()?.logicalIndex
            ?: 0
    }

    data class PersistedProfileTabs(
        val order: List<String>,
        val hidden: List<String>,
        val featuredTab: String,
    )

    companion object {
        val DEFAULT_ORDER = listOf(
            ProfileMediaTab.MUSIC,
            ProfileMediaTab.FILM,
            ProfileMediaTab.BOOKS,
        )

        fun resolve(
            featuredTab: String,
            tabOrder: List<String>,
            hiddenTabs: List<String>,
            booksEnabled: Boolean,
        ): ProfileTabPreferences {
            val featured = ProfileMediaTab.featuredFromRaw(featuredTab)
            val parsed = sanitize(tabOrder)
            val order = if (parsed.isEmpty()) {
                listOf(featured) + DEFAULT_ORDER.filter { it != featured }
            } else {
                val next = parsed.toMutableList()
                DEFAULT_ORDER.forEach { tab ->
                    if (tab !in next) {
                        if (tab == featured) next.add(0, tab) else next.add(tab)
                    }
                }
                next
            }
            var hidden = sanitize(hiddenTabs).toSet()
            val available = availableTabs(booksEnabled)
            if (available.all { it in hidden }) hidden = emptySet()
            return ProfileTabPreferences(order, hidden)
        }

        fun parseStringList(value: Any?): List<String> {
            val list = value as? List<*> ?: return emptyList()
            return list.mapNotNull { it as? String }
        }

        private fun sanitize(raw: List<String>): List<ProfileMediaTab> {
            val seen = mutableSetOf<ProfileMediaTab>()
            val result = mutableListOf<ProfileMediaTab>()
            raw.forEach { value ->
                val tab = ProfileMediaTab.fromRaw(value) ?: return@forEach
                if (seen.add(tab)) result.add(tab)
            }
            return result
        }

        private fun availableTabs(booksEnabled: Boolean): List<ProfileMediaTab> =
            if (booksEnabled) DEFAULT_ORDER else listOf(ProfileMediaTab.MUSIC, ProfileMediaTab.FILM)
    }
}
