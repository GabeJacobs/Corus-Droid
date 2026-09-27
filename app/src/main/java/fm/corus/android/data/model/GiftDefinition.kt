package fm.corus.android.data.model

data class GiftDefinition(
    val id: String,
    val name: String,
    val article: String,
    val emoji: String,
    val artboard: String? = null,
    val shortMeaning: String = "",
    val detail: String? = null,
) {
    val sentPhrase get() = article + name
    companion object {
        val selectable = listOf(
            GiftDefinition("corus_heart", "Super Like", "a ", "💙", "Corus Heart", "I love this"),
            GiftDefinition("flowers", "Flowers", "", "💐", "Flowers", "Thank you", "A thank-you for sharing something worth keeping."),
            GiftDefinition("mind_blown", "Mind Blown", "", "🤯", "Mind Blown", "You blew my mind", "For the discovery that completely blew you away."),
            GiftDefinition("boombox", "Boombox", "a ", "📻", "Boombox", "Turn it up"),
        )

        fun from(type: String?): GiftDefinition = when (type) {
            "corus_heart", "flowers", "mind_blown", "boombox" -> selectable.first { it.id == type }
            "disco_ball" -> GiftDefinition("disco_ball", "Disco Ball", "a ", "🪩")
            "madvillain_mask" -> GiftDefinition("madvillain_mask", "Madvillain Mask", "a ", "🎭")
            else -> GiftDefinition(type.orEmpty(), "Gift", "a ", "🎁")
        }
        fun context(title: String?): String = title?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { "For sharing “$it”" } ?: "For something you shared on Corus"
    }
}
