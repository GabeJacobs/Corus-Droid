package fm.corus.android.ui.screens.live

internal fun roomsWithDiscoveryCounts(rooms: List<LiveRoom>, counts: Map<String, Int>): List<LiveRoom> =
    rooms.filter { it.id in counts }

internal fun freshDiscoveryCounts(counts: Map<String, Int>, updatedAt: Long, now: Long): Map<String, Int> =
    if (now - updatedAt < 60_000) counts else emptyMap()
