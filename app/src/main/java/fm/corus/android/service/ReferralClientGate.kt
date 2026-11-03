package fm.corus.android.service

/** A rollout assignment belongs only to the account whose config was fetched. */
internal class ReferralClientGate {
    private var resolvedUid: String? = null
    private var enabled = false
    fun resolve(value: Boolean, uid: String?) { enabled = value; resolvedUid = uid }
    fun enabledFor(uid: String?): Boolean = enabled && uid != null && uid == resolvedUid
}
