package fm.corus.android.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayInputStream
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

    /** Normalize EXIF orientation and downscale to max 512px. */
    private fun downscaleIfNeeded(imageData: ByteArray): ByteArray {
        val decoded = BitmapFactory.decodeByteArray(imageData, 0, imageData.size) ?: return imageData

        // Apply EXIF orientation so pixels are right-side-up
        val original = applyExifOrientation(decoded, imageData)

        val maxDim = maxOf(original.width, original.height)
        if (maxDim <= AVATAR_MAX_DIMENSION) {
            // Still re-encode to strip any leftover EXIF metadata
            val out = ByteArrayOutputStream()
            original.compress(Bitmap.CompressFormat.JPEG, 90, out)
            if (original !== decoded) original.recycle()
            decoded.recycle()
            return out.toByteArray()
        }

        val scale = AVATAR_MAX_DIMENSION.toFloat() / maxDim
        val newWidth = (original.width * scale).toInt()
        val newHeight = (original.height * scale).toInt()
        val resized = Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (resized !== original) resized.recycle()
        if (original !== decoded) original.recycle()
        decoded.recycle()
        return out.toByteArray()
    }

    /** Read EXIF orientation from raw JPEG bytes and rotate the bitmap if needed. */
    private fun applyExifOrientation(bitmap: Bitmap, imageData: ByteArray): Bitmap {
        val exif = try {
            ExifInterface(ByteArrayInputStream(imageData))
        } catch (_: Exception) {
            return bitmap
        }
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    suspend fun uploadMessageImage(userId: String, threadId: String, messageId: String, imageData: ByteArray): String {
        val ref = storage.reference.child("messageMedia/$userId/$threadId/$messageId.jpg")
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        ref.putBytes(imageData, metadata).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun uploadVoiceNote(userId: String, postId: String, audioData: ByteArray): String {
        val ref = storage.reference.child("voiceNotes/$userId/$postId.m4a")
        val metadata = StorageMetadata.Builder().setContentType("audio/mp4").build()
        ref.putBytes(audioData, metadata).await()
        return ref.downloadUrl.await().toString()
    }
}
