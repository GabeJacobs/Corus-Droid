package fm.corus.android.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStorageDataSource @Inject constructor(
    private val storage: FirebaseStorage,
) {
    companion object {
        /** Maximum pixel dimension for uploaded avatars. */
        private const val AVATAR_MAX_DIMENSION = 512
    }

    suspend fun uploadAvatar(uid: String, imageData: ByteArray): String {
        val dataToUpload = downscaleIfNeeded(imageData)
        val ref = storage.reference.child("avatars/$uid.jpg")
        ref.putBytes(dataToUpload).await()
        return ref.downloadUrl.await().toString()
    }

    /** Downscale image to max 512px so the full avatar stays small (~30-50 KB). */
    private fun downscaleIfNeeded(imageData: ByteArray): ByteArray {
        val original = BitmapFactory.decodeByteArray(imageData, 0, imageData.size) ?: return imageData
        val maxDim = maxOf(original.width, original.height)
        if (maxDim <= AVATAR_MAX_DIMENSION) return imageData

        val scale = AVATAR_MAX_DIMENSION.toFloat() / maxDim
        val newWidth = (original.width * scale).toInt()
        val newHeight = (original.height * scale).toInt()
        val resized = Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (resized !== original) resized.recycle()
        original.recycle()
        return out.toByteArray()
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
