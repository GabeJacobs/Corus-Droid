package fm.corus.android.data.model

data class TenorGif(
    val id: String,
    val tinyGifURL: String,
    val gifURL: String,
    val tinyGifWidth: Int = 0,
    val tinyGifHeight: Int = 0,
)
