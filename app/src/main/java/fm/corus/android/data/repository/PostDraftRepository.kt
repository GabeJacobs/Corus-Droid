package fm.corus.android.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import fm.corus.android.data.model.PostDraft
import fm.corus.android.data.remote.FirebaseStorageDataSource
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads/writes post drafts under `users_v2/{uid}/postDrafts/{draftId}`.
 *
 * Mirrors Corus-Web/app/lib/firestore/post-drafts.ts: 30-draft cap trimmed
 * oldest-first on a fresh save, `merge` on edit (preserving the original
 * `createdAt`), numeric millisecond timestamps, and the canonical cross-
 * platform doc shape (see [PostDraft]). Client-written on a rules-guarded
 * owner path — no Cloud Function.
 */
@Singleton
class PostDraftRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storageDataSource: FirebaseStorageDataSource,
) {
    private fun draftsCollection(uid: String) =
        firestore.collection("users_v2").document(uid).collection("postDrafts")

    /** Newest-first list, capped at [PostDraft.MAX_POST_DRAFTS]. */
    suspend fun listDrafts(uid: String): List<PostDraft> {
        if (uid.isEmpty()) return emptyList()
        val snap = draftsCollection(uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(PostDraft.MAX_POST_DRAFTS.toLong())
            .get()
            .await()
        return snap.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            PostDraft.fromDoc(doc.id, data)
        }
    }

    /**
     * Create or update a draft.
     *
     * @param existingId when non-null, updates that doc via `merge` and keeps
     *   its original [existingCreatedAt] (a resume-save never clobbers the
     *   first-saved time).
     * @return the persisted [PostDraft] (with resolved id + timestamps).
     */
    suspend fun saveDraft(
        uid: String,
        input: PostDraft,
        existingId: String? = null,
        existingCreatedAt: Long? = null,
    ): PostDraft {
        val now = System.currentTimeMillis()
        val col = draftsCollection(uid)
        val id = existingId ?: col.document().id
        val createdAt = if (existingId != null) existingCreatedAt ?: now else now

        val record = input.copy(id = id, createdAt = createdAt, updatedAt = now)
            .toCanonicalMap()
            .toMutableMap()
        record["updatedAt"] = now
        // Only (re)write createdAt when we know it — a merge-save with an unknown
        // original createdAt leaves the stored value untouched (matches web).
        if (existingId == null || existingCreatedAt != null) {
            record["createdAt"] = createdAt
        }

        val docRef = col.document(id)
        if (existingId != null) {
            docRef.set(record, SetOptions.merge()).await()
        } else {
            docRef.set(record).await()
        }

        if (existingId == null) trimToCapacity(uid)

        return input.copy(id = id, createdAt = createdAt, updatedAt = now)
    }

    suspend fun deleteDraft(uid: String, id: String) {
        if (uid.isEmpty() || id.isEmpty()) return
        draftsCollection(uid).document(id).delete().await()
    }

    /** Upload a freshly recorded voice memo, returning its download URL. */
    suspend fun uploadVoiceNote(uid: String, audioData: ByteArray): String {
        val voiceId = UUID.randomUUID().toString()
        return storageDataSource.uploadVoiceNote(uid, voiceId, audioData)
    }

    /** Delete drafts beyond the cap, oldest first. */
    private suspend fun trimToCapacity(uid: String) {
        val snap = draftsCollection(uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .get()
            .await()
        val overflow = snap.documents.drop(PostDraft.MAX_POST_DRAFTS)
        for (doc in overflow) {
            runCatching { doc.reference.delete().await() }
        }
    }
}
