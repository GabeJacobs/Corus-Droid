package fm.corus.android.data.model

object FeedDecade {

    val OFFERED: List<Int> = listOf(2020, 2010, 2000, 1990, 1980, 1970, 1960)

    fun normalize(decade: Int?): Int? = decade?.takeIf { OFFERED.contains(it) }

    fun fromStored(raw: String?): Int? = normalize(raw?.trim()?.toIntOrNull())

    fun toStored(decade: Int?): String = decade?.toString() ?: ""

    fun label(decade: Int): String =
        if (decade >= 2000) "${decade}s" else "${decade.toString().drop(2)}s"

    fun analyticsValue(decade: Int?): String = decade?.toString() ?: "none"
}
