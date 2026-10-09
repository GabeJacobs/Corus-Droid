package fm.corus.android.domain

/** Mirrors iOS: the extra style shortcut belongs to the owner's collection header. */
object ProfileHeaderStylePolicy {
    fun visible(styleEnabled: Boolean, collectionVisible: Boolean, isOwnProfile: Boolean): Boolean =
        styleEnabled && collectionVisible && isOwnProfile
}
