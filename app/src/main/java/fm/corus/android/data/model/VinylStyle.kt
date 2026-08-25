package fm.corus.android.data.model

import androidx.compose.ui.graphics.Color

enum class VinylStyle(val value: String) {
    BLACK("black"),
    CLEAR("clear"),
    LIME("lime"),
    BLOOD_RED("bloodRed"),
    ORANGE("orange"),
    PINK_MATTE("pinkMatte"),
    RED_MATTE("redMatte"),
    PURPLE_TIE_DYE("purpleTieDye"),
    BLUE_TIE_DYE("blueTieDye"),
    ORANGE_TIE_DYE("orangeTieDye"),
    ICY_BLUE("icyBlue"),
    GALAXY("galaxy"),
    PEACH("peach"),
    YELLOW("yellow"),
    PURPLE("purple"),
    LAVENDER("lavender"),
    PINK("pink"),
    WHITE("white"),
    GOLD("gold"),
    BLUE("blue"),
    GREEN("green");

    /// True for vinyl colors gated behind the `style_pack_1_enabled`
    /// Remote Config flag.
    val requiresStylePack1: Boolean
        get() = when (this) {
            PINK, ORANGE, YELLOW, PINK_MATTE, LIME, PURPLE_TIE_DYE, BLUE_TIE_DYE, ORANGE_TIE_DYE, ICY_BLUE, GALAXY, PEACH, LAVENDER, BLOOD_RED -> true
            BLACK, CLEAR, RED_MATTE, PURPLE, WHITE, GOLD, BLUE, GREEN -> false
        }

    val displayName: String
        get() = when (this) {
            BLACK -> "Black"
            BLUE -> "Blue"
            WHITE -> "White"
            RED_MATTE -> "Red"
            CLEAR -> "Clear"
            PURPLE -> "Purple Rain"
            GREEN -> "Green"
            GOLD -> "Gold"
            PINK -> "Cotton Candy"
            ORANGE -> "Channel Orange"
            YELLOW -> "Yellow"
            PINK_MATTE -> "Pink"
            LIME -> "Brat Green"
            PURPLE_TIE_DYE -> "Purple Tie-Dye"
            BLUE_TIE_DYE -> "Blue Tie-Dye"
            ORANGE_TIE_DYE -> "Orange Tie-Dye"
            ICY_BLUE -> "Icy Blue"
            GALAXY -> "Galaxy"
            PEACH -> "Peach"
            LAVENDER -> "Lavender"
            BLOOD_RED -> "Depression Cherry"
        }

    val previewColor: Color
        get() = when (this) {
            BLACK -> Color(0xFF1A1A1A)
            BLUE -> Color(0xFF12368E)
            WHITE -> Color(0xFFE8E8E8)
            RED_MATTE -> Color(0xFFD24235)
            CLEAR -> Color(0xFFD0D0D0)
            PURPLE -> Color(0xFF591968)
            GREEN -> Color(0xFF2D8B4E)
            GOLD -> Color(0xFFC5A835)
            PINK -> Color(0xFFF9469F)
            ORANGE -> Color(0xFFFB7F26)
            YELLOW -> Color(0xFFFFD600)
            PINK_MATTE -> Color(0xFFF73498)
            LIME -> Color(0xFFA8B805)
            PURPLE_TIE_DYE -> Color(0xFF7E20E7)
            BLUE_TIE_DYE -> Color(0xFF0164F6)
            ORANGE_TIE_DYE -> Color(0xFFF65606)
            ICY_BLUE -> Color(0xFF7BD9ED)
            GALAXY -> Color(0xFF5D47A1)
            PEACH -> Color(0xFFE29D88)
            LAVENDER -> Color(0xFFA590D6)
            BLOOD_RED -> Color(0xFFA7191F)
        }

    /** Disc-face crop for the picker swatch, in Figma canvas points. */
    val swatchCropX: Float get() = if (this == CLEAR) 440f else 422f
    val swatchCropY: Float get() = if (this == CLEAR) 165f else 145f
    val swatchCropS: Float get() = if (this == CLEAR) 60f else 70f

    val canvasW: Float
        get() = when (this) {
            PINK, ORANGE, YELLOW, PINK_MATTE, LIME,
            PURPLE_TIE_DYE, BLUE_TIE_DYE, ORANGE_TIE_DYE, ICY_BLUE, GALAXY, PEACH, LAVENDER, BLOOD_RED -> 582f
            else -> 585f
        }

    val canvasH: Float get() = canvasW * canvasRatio

    // Vinyl label position fractions (from Figma)
    val labelXFrac: Float
        get() = when (this) {
            BLACK -> 330.77f / 585f
            BLUE -> 320f / 585f
            WHITE -> 325f / 585f
            RED_MATTE -> 324f / 585f
            CLEAR -> 327f / 585f
            PURPLE -> 326f / 585f
            GREEN -> 322f / 585f
            GOLD -> 319.91f / 585f
            PINK -> 311f / 582f
            ORANGE -> 326f / 582f
            YELLOW -> 324f / 582f
            LIME -> 317f / 582f
            PINK_MATTE -> 319f / 582f
            PURPLE_TIE_DYE -> 325f / 582f
            BLUE_TIE_DYE -> 318f / 582f
            ORANGE_TIE_DYE -> 324f / 582f
            ICY_BLUE -> 321f / 582f
            GALAXY -> 322f / 582f
            PEACH -> 321f / 582f
            LAVENDER -> 324f / 582f
            BLOOD_RED -> 325f / 582f
        }

    val labelYFrac: Float
        get() = when (this) {
            BLACK -> 153.98f / 447f
            BLUE -> 151f / 447f
            WHITE -> 158f / 447f
            RED_MATTE -> 150f / 447f
            CLEAR -> 154f / 447f
            PURPLE -> 153f / 447f
            GREEN -> 148f / 447f
            GOLD -> 151.6f / 447f
            PINK -> 142f / 441f
            ORANGE -> 152f / 440f
            YELLOW -> 151f / 440f
            LIME -> 146f / 440f
            PINK_MATTE -> 147f / 440f
            PURPLE_TIE_DYE -> 147f / 440f
            BLUE_TIE_DYE -> 150f / 440f
            ORANGE_TIE_DYE -> 150f / 440f
            ICY_BLUE -> 150f / 440f
            GALAXY -> 150f / 440f
            PEACH -> 151f / 440f
            LAVENDER, BLOOD_RED -> 153f / 440f
        }

    val labelWFrac: Float
        get() = when (this) {
            BLACK -> 82.44f / 585f
            BLUE -> 99f / 585f
            WHITE -> 87f / 585f
            RED_MATTE -> 96f / 585f
            CLEAR -> 88f / 585f
            PURPLE -> 90f / 585f
            GREEN -> 98f / 585f
            GOLD -> 89.29f / 585f
            PINK -> 102f / 582f
            ORANGE -> 82f / 582f
            YELLOW -> 85f / 582f
            LIME -> 95f / 582f
            PINK_MATTE -> 91f / 582f
            PURPLE_TIE_DYE -> 85f / 582f
            BLUE_TIE_DYE -> 91f / 582f
            ORANGE_TIE_DYE -> 89f / 582f
            ICY_BLUE -> 88f / 582f
            GALAXY -> 88f / 582f
            PEACH -> 85f / 582f
            LAVENDER, BLOOD_RED -> 82f / 582f
        }

    val labelHFrac: Float
        get() = when (this) {
            BLACK -> 84.43f / 447f
            BLUE -> 101f / 447f
            WHITE -> 89f / 447f
            RED_MATTE -> 98f / 447f
            CLEAR -> 90f / 447f
            PURPLE -> 92f / 447f
            GREEN -> 100f / 447f
            GOLD -> 91.25f / 447f
            PINK -> 104f / 441f
            ORANGE -> 83f / 440f
            YELLOW -> 87f / 440f
            LIME -> 97f / 440f
            PINK_MATTE -> 93f / 440f
            PURPLE_TIE_DYE -> 86f / 440f
            BLUE_TIE_DYE -> 92f / 440f
            ORANGE_TIE_DYE -> 90f / 440f
            ICY_BLUE -> 89f / 440f
            GALAXY -> 89f / 440f
            PEACH -> 86f / 440f
            LAVENDER, BLOOD_RED -> 83f / 440f
        }

    val canvasRatio: Float
        get() = when (this) {
            PINK -> 441f / 582f
            ORANGE, YELLOW, PINK_MATTE, LIME, PURPLE_TIE_DYE, BLUE_TIE_DYE, ORANGE_TIE_DYE, ICY_BLUE, GALAXY, PEACH, LAVENDER, BLOOD_RED -> 440f / 582f
            else -> 447f / 585f
        }

    val artXFrac: Float
        get() = when (this) {
            PINK, ORANGE, YELLOW, PINK_MATTE, LIME, PURPLE_TIE_DYE, BLUE_TIE_DYE, ORANGE_TIE_DYE, ICY_BLUE, GALAXY, PEACH, LAVENDER, BLOOD_RED -> 105f / 582f
            else -> 106f / 585f
        }

    val artYFrac: Float
        get() = when (this) {
            PINK -> 58f / 441f
            PURPLE_TIE_DYE, BLUE_TIE_DYE, ORANGE_TIE_DYE, ICY_BLUE, GALAXY, PEACH, LAVENDER, YELLOW, BLOOD_RED, PINK_MATTE, ORANGE -> 57f / 440f
            LIME -> 58f / 440f
            else -> 64f / 447f
        }

    val artSizeFrac: Float
        get() = when (this) {
            PINK, ORANGE, YELLOW, PINK_MATTE, LIME, PURPLE_TIE_DYE, BLUE_TIE_DYE, ORANGE_TIE_DYE, ICY_BLUE, GALAXY, PEACH, LAVENDER, BLOOD_RED -> 270f / 582f
            else -> 270f / 585f
        }

    companion object {
        fun from(value: String?): VinylStyle =
            entries.firstOrNull { it.value == value } ?: BLACK
    }
}
