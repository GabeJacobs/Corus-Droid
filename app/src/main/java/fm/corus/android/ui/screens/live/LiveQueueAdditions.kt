package fm.corus.android.ui.screens.live

/** Pure additions need no queue version: the server appends them atomically. */
internal fun <T> liveQueueAdditions(previous: List<T>, desired: List<T>): List<T>? =
    if (desired.size > previous.size && desired.take(previous.size) == previous) desired.drop(previous.size) else null

/** Rebase edits on a fresh queue, preserving turns and additions from other clients. */
internal fun <T> liveRebaseQueue(
    desired: List<T>, previous: List<T>, latest: List<T>, hosting: Boolean, identity: (T) -> String,
): List<T>? {
    if (hosting) {
        var cursor = 0
        for (track in previous) if (cursor < latest.size && identity(track) == identity(latest[cursor])) cursor++
        if (cursor != latest.size) return null
        val consumed = previous.groupingBy(identity).eachCount().toMutableMap()
        for (track in latest) { val id = identity(track); consumed[id] = (consumed[id] ?: 0) - 1 }
        return desired.filter { track ->
            val id = identity(track)
            val count = consumed[id] ?: 0
            if (count == 0) true else { consumed[id] = count - 1; false }
        }
    }
    fun keyed(queue: List<T>): List<Pair<Pair<String, Int>, T>> {
        val counts = mutableMapOf<String, Int>()
        return queue.map { track ->
            val id = identity(track)
            val occurrence = counts[id] ?: 0
            counts[id] = occurrence + 1
            (id to occurrence) to track
        }
    }
    val before = keyed(previous); val wanted = keyed(desired); val current = keyed(latest)
    val beforeKeys = before.map { it.first }.toSet()
    val wantedKeys = wanted.map { it.first }.toSet()
    val currentKeys = current.map { it.first }.toSet()
    val retained = beforeKeys.intersect(wantedKeys).intersect(currentKeys)
    val reordered = wanted.filter { it.first in retained }.map { it.first } != before.filter { it.first in retained }.map { it.first }
    val merged = (if (reordered) wanted.filter { it.first !in beforeKeys || it.first in currentKeys }
        else current.filter { it.first !in beforeKeys || it.first in wantedKeys }).toMutableList()
    val keys = merged.map { it.first }.toMutableSet()
    for (row in if (reordered) current else wanted) {
        if (row.first !in beforeKeys && keys.add(row.first)) merged.add(row)
    }
    return merged.map { it.second }
}
