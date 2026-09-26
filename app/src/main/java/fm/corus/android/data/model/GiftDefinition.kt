package fm.corus.android.data.model

data class GiftDefinition(val name: String, val article: String, val emoji: String, val artboard: String? = null) {
    val sentPhrase get() = article + name
    companion object {
        fun from(type: String?): GiftDefinition = when (type) {
            "corus_heart" -> GiftDefinition("Super Like", "a ", "💙", "Corus Heart")
            "flowers" -> GiftDefinition("Flowers", "", "💐", "Flowers")
            "mind_blown" -> GiftDefinition("Mind Blown", "", "🤯", "Mind Blown")
            "boombox" -> GiftDefinition("Boombox", "a ", "📻", "Boombox")
            "disco_ball" -> GiftDefinition("Disco Ball", "a ", "🪩")
            else -> GiftDefinition("Gift", "a ", "🎁")
        }
        fun context(title: String?): String = title?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { "For sharing “$it”" } ?: "For something you shared on Corus"
    }
}
