package fm.corus.android.ui.screens.live

internal class LiveReactionPresentation {
    private data class Pending(val count: Long, val notice: Boolean, val deadline: Long)
    private val pending = linkedMapOf<String, Pending>()
    private val waiting = linkedMapOf<String, Long>()
    private var groups = emptyList<List<String>>()
    private val released = linkedSetOf<String>()
    private val seen = linkedSetOf<String>()
    private fun remember(id: String) {
        seen.add(id)
        if (seen.size > 512) seen.remove(seen.first())
    }

    private fun release(id: String) {
        released.add(id)
        if (released.size > 512) released.remove(released.first())
        pending.remove(id)
        waiting.remove(id)
    }
    fun baseline(ids: List<String>) { ids.forEach { remember(it); release(it) } }
    fun reaction(id: String, count: Long, notice: Boolean, now: Long) {
        if (id !in seen) {
            remember(id)
            pending[id] = Pending(count, notice, now + 500)
        }
    }
    fun messages(ids: List<String>, now: Long) { messageGroups(ids.map { listOf(it) }, now) }
    fun messageGroups(groups: List<List<String>>, now: Long) {
        this.groups = groups
        groups.flatten().filter { it !in released && it !in waiting }.forEach { waiting[it] = now + 500 }
    }
    fun flush(now: Long): Long {
        var count = 0L
        groups.forEach { group ->
            val ready = group.all { it in released || it in pending }
            val expired = group.any { (waiting[it] ?: Long.MAX_VALUE) <= now || (pending[it]?.deadline ?: Long.MAX_VALUE) <= now }
            if (ready || expired) group.forEach { id ->
                count += pending[id]?.count ?: 0
                release(id)
            }
        }
        pending.toMap().forEach { (id, event) ->
            if (!event.notice || event.deadline <= now) {
                count += event.count
                release(id)
            }
        }
        waiting.toMap().filterValues { it <= now }.keys.forEach(::release)
        return count
    }
    fun allows(ids: List<String>): Boolean = ids.all { it in released }
    val deadline: Long? get() = (waiting.values + pending.values.map { it.deadline }).minOrNull()
}
