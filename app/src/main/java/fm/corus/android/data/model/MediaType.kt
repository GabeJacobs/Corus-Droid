package fm.corus.android.data.model

enum class MediaType(val value: String) {
    TRACK("track"),
    MOVIE("movie");

    companion object {
        fun from(value: String?): MediaType =
            entries.firstOrNull { it.value == value } ?: TRACK
    }
}
