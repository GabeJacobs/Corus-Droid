package fm.corus.android.data.model

data class KlipyGif(
    val id: String,
    val slug: String,
    val title: String,
    val thumbnailURL: String,
    val fullURL: String,
    val thumbnailWidth: Int = 0,
    val thumbnailHeight: Int = 0,
)
