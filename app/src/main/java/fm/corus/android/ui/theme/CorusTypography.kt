package fm.corus.android.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fm.corus.android.R

@OptIn(ExperimentalTextApi::class)
val NunitoFamily = FontFamily(
    Font(
        R.font.nunito,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.nunito,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.nunito,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
        R.font.nunito,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
    Font(
        R.font.nunito,
        weight = FontWeight.Black,
        variationSettings = FontVariation.Settings(FontVariation.weight(900)),
    ),
)

/**
 * Design tokens ported 1:1 from iOS CymbalFont.
 * Weight mapping: book=400, medium=500, heavy=800, black=900
 */
object CorusFont {
    // Title tokens
    val appTitle = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Black,
        fontSize = 24.sp,
    )
    val logoLarge = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
    )
    val screenTitle = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
    )
    val sectionHeader = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp,
    )

    // Song/Movie tokens
    val songTitle = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
    )
    val songTitleLarge = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
    )
    val artistName = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    )
    val artistNameLarge = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    )
    val albumName = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    )

    // User tokens
    val username = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
    )
    val usernameLarge = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
    )
    val displayName = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
    )
    val bio = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    )

    // Body tokens
    val body = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    )
    val bodyMedium = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    )
    val caption = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    )
    val captionMedium = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
    )
    val timestamp = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    )

    // Button tokens
    val button = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
    )
    val buttonSmall = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp,
    )
    val tabLabel = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
    )

    // Stat tokens
    val stat = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
    )
    val statLabel = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
    )

    /** Custom weight+size helper matching iOS CymbalFont.custom(weight:size:) */
    fun custom(weight: Int, size: Int) = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = when (weight) {
            900 -> FontWeight.Black
            800 -> FontWeight.ExtraBold
            700 -> FontWeight.Bold
            500 -> FontWeight.Medium
            else -> FontWeight.Normal
        },
        fontSize = size.sp,
    )

    // Misc tokens
    val hashtagPill = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp,
    )
    val engagementCount = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 13.sp,
    )
}
