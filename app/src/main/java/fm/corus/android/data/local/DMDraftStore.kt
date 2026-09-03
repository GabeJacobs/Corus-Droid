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

    fun load(
        uid: String?,
        threadId: String,
        peerUserId: String? = null,
    ): String? {
        key(uid, threadId, peerUserId = null)?.let { threadKey ->
            val text = prefs.getString(threadKey, null)
            if (!text.isNullOrEmpty()) return text
        }
        key(uid, threadId = "", peerUserId)?.let { peerKey ->
            val text = prefs.getString(peerKey, null)
            if (!text.isNullOrEmpty()) return text
        }
        return null
    }

    /**
     * Persists [text] for the thread (or peer, if the thread isn't resolved
     * yet). Whitespace-only input deletes the draft. Once a thread id exists,
     * any leftover peer-keyed draft is removed so it can't resurrect later.
     */
    fun save(
        uid: String?,
        threadId: String,
        text: String,
        peerUserId: String? = null,
    ) {
        val normalized = normalized(text)
        key(uid, threadId, peerUserId = null)?.let { threadKey ->
            val editor = prefs.edit()
            if (normalized == null) editor.remove(threadKey)
            else editor.putString(threadKey, normalized)
            key(uid, threadId = "", peerUserId)?.let { editor.remove(it) }
            editor.apply()
            return
        }
        val peerKey = key(uid, threadId = "", peerUserId) ?: return
        val editor = prefs.edit()
        if (normalized == null) editor.remove(peerKey)
        else editor.putString(peerKey, normalized)
        editor.apply()
    }

    fun clear(
        uid: String?,
        threadId: String,
        peerUserId: String? = null,
    ) {
        save(uid, threadId, "", peerUserId)
    }

    companion object {
        const val PREFS_NAME = "corus_dm_drafts"
        const val MAX_LENGTH = 10_000
        private const val KEY_PREFIX = "dm_draft."

        /**
         * Prefer a resolved thread id; fall back to the peer so a brand-new
         * conversation can keep a draft before `getOrCreateThread` returns.
         */
        fun key(uid: String?, threadId: String?, peerUserId: String? = null): String? {
            if (uid.isNullOrEmpty()) return null
            if (!threadId.isNullOrEmpty()) return "$KEY_PREFIX$uid.thread.$threadId"
            if (!peerUserId.isNullOrEmpty()) return "$KEY_PREFIX$uid.peer.$peerUserId"
            return null
        }

        /** Empty / whitespace-only drafts are discarded. Trailing spaces stay. */
        fun normalized(text: String): String? {
            if (text.isBlank()) return null
            return if (text.length <= MAX_LENGTH) text else text.take(MAX_LENGTH)
        }
    }
}
