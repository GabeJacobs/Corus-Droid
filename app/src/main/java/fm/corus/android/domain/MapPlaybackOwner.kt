package fm.corus.android.domain

/** A map session can own Next only while its actual track is selected. */
object MapPlaybackOwner {
    data class Owner(val trackId: String, val next: () -> Unit, val started: () -> Unit, val abandoned: () -> Unit = {}, val previous: (() -> Unit)? = null)
    var current: Owner? = null
    fun yield() { val owner = current; current = null; owner?.abandoned?.invoke() }
    fun previous(trackId: String?): Boolean { val owner = current ?: return false; if (owner.trackId != trackId || owner.previous == null) return false; owner.previous.invoke(); return true }
    fun advance(trackId: String?): Boolean { val owner = current ?: return false; if (owner.trackId != trackId) return false; owner.next(); return true }
    fun started(trackId: String?) { current?.takeIf { it.trackId == trackId }?.started?.invoke() }
}
