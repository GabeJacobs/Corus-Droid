package fm.corus.android.data.model

data class GiftSender(val id: String, val name: String)
data class PostGiftSummary(val total: Int, val senderCount: Int?, val senders: List<GiftSender>) {
    fun attribution(): String {
        val people = senders.distinctBy { it.id }
        val first = people.firstOrNull() ?: return "$total gifts"
        return when {
            senderCount == 1 -> "${first.name} sent $total gifts"
            senderCount == 2 && people.size >= 2 -> "$total gifts from ${first.name} and ${people[1].name}"
            senderCount != null && senderCount > 2 -> "$total gifts from ${first.name} and ${senderCount - 1} others"
            else -> "$total gifts · latest from ${first.name}"
        }
    }
}
