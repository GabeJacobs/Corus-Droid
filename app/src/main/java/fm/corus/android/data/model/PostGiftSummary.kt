package fm.corus.android.data.model

data class GiftSender(val id: String, val name: String)
data class GiftAttributionPart(val text: String, val bold: Boolean = false)
data class PostGiftSummary(val total: Int, val senderCount: Int?, val senders: List<GiftSender>) {
    fun attribution(): String = attributionParts().joinToString("") { it.text }
    fun attributionParts(): List<GiftAttributionPart> {
        val people = senders.distinctBy { it.id }
        val count = GiftAttributionPart("$total gifts", true)
        val first = people.firstOrNull() ?: return listOf(count)
        val name = GiftAttributionPart(first.name, true)
        return when {
            senderCount == 1 -> listOf(name, GiftAttributionPart(" sent "), count)
            senderCount == 2 && people.size >= 2 -> listOf(count, GiftAttributionPart(" from "), name, GiftAttributionPart(" and "), GiftAttributionPart(people[1].name, true))
            senderCount != null && senderCount > 2 -> listOf(count, GiftAttributionPart(" from "), name, GiftAttributionPart(" and ${senderCount - 1} others"))
            else -> listOf(count, GiftAttributionPart(" · latest from "), name)
        }
    }
}
