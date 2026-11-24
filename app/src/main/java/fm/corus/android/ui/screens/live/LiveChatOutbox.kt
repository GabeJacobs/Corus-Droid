package fm.corus.android.ui.screens.live

/** A confirmed comment is displayed only while present in the server query. */
internal data class LiveChatOutbox(val entries: Map<String, Entry> = emptyMap()) {
    enum class Status { PENDING, ACCEPTED, CONFIRMED, FAILED }
    data class Entry(val message: LiveMessage, val status: Status = Status.PENDING)

    fun insert(message: LiveMessage) = copy(entries = entries + (message.id to Entry(message)))
    fun retry(id: String): LiveChatOutbox = if (entries[id]?.status == Status.FAILED) {
        copy(entries = entries + (id to entries.getValue(id).copy(status = Status.PENDING)))
    } else this
    fun settle(id: String, success: Boolean): LiveChatOutbox {
        val entry = entries[id]?.takeUnless { it.status == Status.CONFIRMED } ?: return this
        return copy(entries = entries + (id to entry.copy(status = if (success) Status.ACCEPTED else Status.FAILED)))
    }
    fun reconcile(received: List<LiveMessage>): LiveChatOutbox {
        val ids = received.mapTo(hashSetOf()) { it.id }
        return copy(entries = entries.mapNotNull { (id, entry) ->
            when {
                id in ids -> id to entry.copy(status = Status.CONFIRMED)
                entry.status == Status.CONFIRMED -> null
                else -> id to entry
            }
        }.toMap())
    }
    fun isUnconfirmed(id: String) = entries[id]?.status?.let { it != Status.CONFIRMED } == true
    fun merge(received: List<LiveMessage>): List<LiveMessage> {
        val ids = received.mapTo(hashSetOf()) { it.id }
        return (received.map { row ->
            entries[row.id]?.let { row.copy(fields = row.fields + ("createdAt" to it.message.createdAt)) } ?: row
        } + entries.values.filter { it.status != Status.CONFIRMED && it.message.id !in ids }.map { it.message })
            .sortedBy { it.createdAt }
    }
}
