package fm.corus.android.data.model

import androidx.compose.ui.graphics.Color

enum class DiscoMark {
    TILE,
    NONE,
}

enum class DiscoPalette {
    SILVER,
    WARM,
    RAINBOW,
}

enum class DiscoIntensity(val value: String) {
    OFF("off"),
    LIGHT("light"),
    DISCO_BALL("discoBall"),
    DANCE_PARTY("danceParty"),
    SPOTIFLIGHT("spotiflight");

    val displayName: String get() = recipe.name
    val rotationSpeed: Double get() = recipe.rotation
    val facetRows: Int get() = recipe.rows
    val equatorFacetCount: Int get() = recipe.equator
    val scrimOpacity: Float get() = recipe.scrim
    val spillStrength: Double get() = recipe.spill
    val beamShare: Double get() = recipe.beamShare
    val pulseRate: Double get() = recipe.pulse
    val dotScale: Double get() = recipe.dotScale
    val mark: DiscoMark get() = recipe.mark
    val extras: Int get() = recipe.extras
    val palette: DiscoPalette get() = recipe.palette

    val usesColoredSpotlights: Boolean
        get() = palette != DiscoPalette.SILVER && this != OFF

    /// Dance Party sits in a colored room so the rainbow spots have contrast.
    /// The gel slowly walks pink → blue → red → orange; other looks stay put.
    val scrimColor: Color get() = scrimColor(0.0)

    fun scrimColor(time: Double): Color {
        if (this == DANCE_PARTY) return dancePartyGel(time)
        return when (palette) {
            DiscoPalette.RAINBOW -> Color(0.18f, 0.05f, 0.32f)
            else -> Color.Black
        }
    }

    fun spillTint(time: Double): Color {
        if (this == DANCE_PARTY) return dancePartySpill(time)
        return when (palette) {
            DiscoPalette.RAINBOW -> Color(0.62f, 0.18f, 0.85f)
            DiscoPalette.WARM -> Color(1.0f, 0.86f, 0.78f)
            DiscoPalette.SILVER -> Color(0.90f, 0.93f, 1.0f)
        }
    }

    val hasSearchlights: Boolean
        get() = extras and SEARCHLIGHT != 0

    private data class Recipe(
        val name: String,
        val rotation: Double,
        val rows: Int,
        val equator: Int,
        val scrim: Float,
        val spill: Double,
        val beamShare: Double,
        val pulse: Double,
        val dotScale: Double,
        val mark: DiscoMark,
        val extras: Int,
        val palette: DiscoPalette,
    )

    private val recipe: Recipe
        get() = when (this) {
            OFF -> Recipe(
                name = "Off",
                rotation = 0.0, rows = 0, equator = 0,
                scrim = 0f, spill = 0.0, beamShare = 0.0, pulse = 0.0,
                dotScale = 0.0, mark = DiscoMark.NONE, extras = 0, palette = DiscoPalette.SILVER,
            )
            DANCE_PARTY -> Recipe(
                name = "Dance Party",
                rotation = 0.09, rows = 14, equator = 18,
                scrim = 0.14f, spill = 0.0, beamShare = 0.24, pulse = 0.0,
                dotScale = 0.32, mark = DiscoMark.TILE, extras = 0, palette = DiscoPalette.RAINBOW,
            )
            LIGHT -> Recipe(
                name = "Slow Dance",
                rotation = 0.11, rows = 17, equator = 22,
                scrim = 0.05f, spill = 0.05, beamShare = 0.08, pulse = 0.0,
                dotScale = 0.34, mark = DiscoMark.TILE, extras = 0, palette = DiscoPalette.SILVER,
            )
            DISCO_BALL -> Recipe(
                name = "Disco Ball",
                rotation = 0.14, rows = 21, equator = 27,
                scrim = 0.10f, spill = 0.08, beamShare = 0.28, pulse = 0.0,
                dotScale = 0.34, mark = DiscoMark.TILE, extras = 0, palette = DiscoPalette.WARM,
            )
            SPOTIFLIGHT -> Recipe(
                name = "Spotlight",
                rotation = 0.05, rows = 6, equator = 6,
                scrim = 0.12f, spill = 0.03, beamShare = 0.0, pulse = 0.0,
                dotScale = 0.0, mark = DiscoMark.NONE, extras = SEARCHLIGHT, palette = DiscoPalette.SILVER,
            )
        }

    companion object {
        const val SEARCHLIGHT = 1 shl 0
        private const val PARTY_LEG = 5.0
        private val PARTY_GEL = arrayOf(
            Triple(0.18, 0.05, 0.32),
            Triple(0.06, 0.12, 0.38),
            Triple(0.32, 0.05, 0.10),
            Triple(0.34, 0.14, 0.04),
        )
        private val PARTY_SPILL = arrayOf(
            Triple(0.62, 0.18, 0.85),
            Triple(0.22, 0.42, 0.98),
            Triple(0.95, 0.18, 0.22),
            Triple(1.00, 0.52, 0.14),
        )

        fun dancePartyGel(time: Double): Color = lerpPartyColor(PARTY_GEL, time)
        fun dancePartySpill(time: Double): Color = lerpPartyColor(PARTY_SPILL, time)

        private fun lerpPartyColor(stops: Array<Triple<Double, Double, Double>>, time: Double): Color {
            val phase = (time / PARTY_LEG) % stops.size
            if (!phase.isFinite()) {
                val first = stops[0]
                return Color(first.first.toFloat(), first.second.toFloat(), first.third.toFloat())
            }
            val index = phase.toInt().coerceIn(0, stops.lastIndex)
            val t = (phase - index).let { it * it * (3 - 2 * it) }
            val a = stops[index]
            val b = stops[(index + 1) % stops.size]
            return Color(
                (a.first + (b.first - a.first) * t).toFloat(),
                (a.second + (b.second - a.second) * t).toFloat(),
                (a.third + (b.third - a.third) * t).toFloat(),
            )
        }

        fun from(value: String?): DiscoIntensity = resolved(value)

        fun resolved(raw: String?): DiscoIntensity {
            if (raw == null) return OFF
            entries.firstOrNull { it.value == raw }?.let { return it }
            return when (raw) {
                "disco", "prism", "strobe" -> DANCE_PARTY
                "heavy" -> DISCO_BALL
                "searchlight" -> SPOTIFLIGHT
                "candlelight", "bonfire" -> OFF
                else -> OFF
            }
        }
    }

    /** When [darkModeOnly] is on, lights stay off in light appearance. */
    fun visible(darkModeOnly: Boolean, isDark: Boolean): DiscoIntensity =
        if (darkModeOnly && !isDark) OFF else this
}

object DiscoEffectGate {
    fun isPageVisible(stylePack1Enabled: Boolean, saved: DiscoIntensity): Boolean =
        stylePack1Enabled || saved != DiscoIntensity.OFF
}
