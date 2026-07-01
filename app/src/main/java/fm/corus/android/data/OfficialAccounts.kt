package fm.corus.android.data

/**
 * The official @corusteam account (moderation + welcome DMs). Users must never
 * be able to silence it, so blocking/muting it is hidden in the UI here and
 * enforced server-side in firestore.rules + the blockUser/muteUser callables.
 *
 * Keep [CORUS_TEAM_UID] in sync with CORUS_TEAM_UID in the backend
 * functions/index.js and corusTeamUid() in firestore.rules.
 */
object OfficialAccounts {
    const val CORUS_TEAM_UID: String = "v9s6F4HKLLbEmfX3Tc6MX3lHfpl2"

    /** True when [uid] is an official account that can't be blocked or muted. */
    fun isOfficial(uid: String?): Boolean = uid == CORUS_TEAM_UID
}
