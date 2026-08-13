package fm.corus.android.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Local, silent composer drafts for DMs. Mirrors iMessage: the unsent text is
 * written as the user types and restored when they reopen the thread. No
 * confirmation — empty / whitespace-only text just clears the stored draft.
 * Per-uid so an account switch on a shared device can't leak a group thread's
 * draft to someone else.
 *
 * Backed by [SharedPreferences] (not DataStore) because restore happens on
 * first composition of the thread screen and must be synchronous.
 */
class DMDraftStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun load(uid: String?, threadId: String): String? {
        val key = key(uid, threadId) ?: return null
        val text = prefs.getString(key, null)
        return if (text.isNullOrEmpty()) null else text
    }

    fun save(uid: String?, threadId: String, text: String) {
        val key = key(uid, threadId) ?: return
        val normalized = normalized(text)
        if (normalized == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, normalized).apply()
        }
    }

    fun clear(uid: String?, threadId: String) {
        save(uid, threadId, "")
    }

    companion object {
        const val PREFS_NAME = "corus_dm_drafts"
        const val MAX_LENGTH = 10_000
        private const val KEY_PREFIX = "dm_draft."

        fun key(uid: String?, threadId: String): String? {
            if (uid.isNullOrEmpty() || threadId.isEmpty()) return null
            return "$KEY_PREFIX$uid.thread.$threadId"
        }

        /** Empty / whitespace-only drafts are discarded. Trailing spaces stay. */
        fun normalized(text: String): String? {
            if (text.isBlank()) return null
            return if (text.length <= MAX_LENGTH) text else text.take(MAX_LENGTH)
        }
    }
}
