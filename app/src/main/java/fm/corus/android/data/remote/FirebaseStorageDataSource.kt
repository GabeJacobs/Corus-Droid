package fm.corus.android.data.remote

import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStorageDataSource @Inject constructor(
    private val storage: FirebaseStorage,
) {
    suspend fun uploadAvatar(uid: String, imageData: ByteArray): String {
        val ref = storage.reference.child("avatars/$uid.jpg")
        ref.putBytes(imageData).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun uploadMessageImage(threadId: String, messageId: String, imageData: ByteArray): String {
        val ref = storage.reference.child("messages/$threadId/$messageId.jpg")
        ref.putBytes(imageData).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun uploadVoiceNote(postId: String, audioData: ByteArray): String {
        val ref = storage.reference.child("voiceNotes/$postId.m4a")
        ref.putBytes(audioData).await()
        return ref.downloadUrl.await().toString()
    }
}
