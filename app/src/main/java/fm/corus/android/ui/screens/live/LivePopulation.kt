package fm.corus.android.ui.screens.live

internal fun livePeopleCount(listeners: Int?, guestDJs: Int, people: Int? = null): Int? {
    if (people != null && people >= 1) return people
    if (listeners == null || listeners < 0) return null
    return listeners + guestDJs.coerceAtLeast(0) + 1
}
