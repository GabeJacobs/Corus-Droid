package fm.corus.android.data.model

import android.content.Context
import androidx.annotation.StringRes
import fm.corus.android.R

data class GiftDefinition(
    val id: String,
    @StringRes val nameResource: Int,
    @StringRes val sentPhraseResource: Int,
    val emoji: String,
    val artboard: String? = null,
    @StringRes val shortMeaningResource: Int? = null,
    @StringRes val detailResource: Int? = null,
) {
    // Stable English fallbacks remain available to context-free model callers
    // and old cached-notification tests. UI must use the Context overloads.
    val name: String get() = when (id) {
        "corus_heart" -> "Super Like"
        "flowers" -> "Flowers"
        "mind_blown" -> "Mind Blown"
        "boombox" -> "Boombox"
        "disco_ball" -> "Disco Ball"
        "madvillain_mask" -> "Madvillain Mask"
        else -> "Gift"
    }
    val sentPhrase: String get() = when (id) {
        "corus_heart" -> "a Super Like"
        "flowers" -> "Flowers"
        "mind_blown" -> "Mind Blown"
        "boombox" -> "a Boombox"
        "disco_ball" -> "a Disco Ball"
        "madvillain_mask" -> "a Madvillain Mask"
        else -> "a Gift"
    }

    fun name(context: Context): String = context.getString(nameResource)
    fun sentPhrase(context: Context): String = context.getString(sentPhraseResource)
    fun shortMeaning(context: Context): String = shortMeaningResource?.let(context::getString).orEmpty()
    fun detail(context: Context): String? = detailResource?.let(context::getString)

    companion object {
        val selectable = listOf(
            GiftDefinition("corus_heart", R.string.gift_name_super_like, R.string.gift_phrase_super_like, "💙", "Corus Heart", R.string.gift_meaning_super_like),
            GiftDefinition("flowers", R.string.gift_name_flowers, R.string.gift_phrase_flowers, "💐", "Flowers", R.string.gift_meaning_flowers, R.string.gift_detail_flowers),
            GiftDefinition("mind_blown", R.string.gift_name_mind_blown, R.string.gift_phrase_mind_blown, "🤯", "Mind Blown", R.string.gift_meaning_mind_blown, R.string.gift_detail_mind_blown),
            GiftDefinition("boombox", R.string.gift_name_boombox, R.string.gift_phrase_boombox, "📻", "Boombox", R.string.gift_meaning_boombox),
        )

        fun from(type: String?): GiftDefinition = when (type) {
            "corus_heart", "flowers", "mind_blown", "boombox" -> selectable.first { it.id == type }
            "disco_ball" -> GiftDefinition("disco_ball", R.string.gift_name_disco_ball, R.string.gift_phrase_disco_ball, "🪩")
            "madvillain_mask" -> GiftDefinition("madvillain_mask", R.string.gift_name_madvillain_mask, R.string.gift_phrase_madvillain_mask, "🎭")
            else -> GiftDefinition(type.orEmpty(), R.string.gift_name_generic, R.string.gift_phrase_generic, "🎁")
        }

        fun context(context: Context, title: String?): String = title?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { context.getString(R.string.gift_context_sharing, it) }
            ?: context.getString(R.string.gift_context_generic)

        fun context(title: String?): String = title?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { "For sharing “$it”" } ?: "For something you shared on Corus"
    }
}
