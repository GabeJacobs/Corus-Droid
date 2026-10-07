package fm.corus.android.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Android encoder, plane strides, orientation and MP4 timestamps. */
@RunWith(AndroidJUnit4::class)
class ProfileStoryVideoTest {
    // Coil's Android ImageDecoder rejects Robolectric's JVM source with
    // "Only supported on Android"; exercise the real decoder here instead.
    @Test fun oversizedArtworkIsDecodedAtBoundedResolutionAndReusedAcrossColors() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val file = File.createTempFile("profile-art-test-", ".png", context.cacheDir)
        try {
            file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
            source.recycle()
            val profile = ShareProfileSubject("fixture", "gabe", "Gabe", null, postCount = 28,
                artworkUrls = List(28) { Uri.fromFile(file).toString() })
            val prepared = ProfileShareArtCache.prepare(context, profile)
            assertEquals(28, prepared.slots.size)
            prepared.slots.forEach { bitmap ->
                assertNotNull(bitmap)
                assertTrue(bitmap!!.width <= 384 && bitmap.height <= 384)
            }
            assertSame(prepared, ProfileShareArtCache.prepare(context, profile))
            val blue = renderProfileShareStory(context, profile, prepared, ProfileStoryGridSize.STANDARD, ProfileStoryBackground.CORUS_BLUE)
            val white = renderProfileShareStory(context, profile, prepared, ProfileStoryGridSize.STANDARD, ProfileStoryBackground.LIGHT)
            try { assertEquals(blue.getPixel(100, 500), white.getPixel(100, 500)) }
            finally { blue.recycle(); white.recycle() }
        } finally { file.delete(); if (!source.isRecycled) source.recycle() }
    }

    @Test fun weatherExportsTenSecondPortraitVideoAlreadyFilledAtFrameZero() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val card = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888).apply { eraseColor(ProfileStoryBackground.CORUS_BLUE.color) }
        try {
            for (effect in listOf(ProfileStoryWeather.RAIN, ProfileStoryWeather.SNOW)) {
                val file = File.createTempFile("profile-weather-test-", ".mp4", context.cacheDir)
                val retriever = MediaMetadataRetriever()
                try {
                    withContext(Dispatchers.Default) { encodeProfileStoryVideo(card, effect, file) }
                    assertTrue(file.length() > 1000)
                    retriever.setDataSource(file.absolutePath)
                    assertEquals("1080", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                    assertEquals("1920", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                    assertTrue("Duration was $duration", duration in 9900..10100)
                    val first = requireNotNull(retriever.getFrameAtTime(0))
                    var weatherPixels = 0
                    for (y in 50 until 1850 step 2) for (x in 20 until 1060 step 2) {
                        val pixel = first.getPixel(x, y)
                        if (Color.red(pixel) > 180 && Color.green(pixel) > 185 && Color.blue(pixel) > 220) weatherPixels++
                    }
                    assertTrue("The first frame should already contain the effect", weatherPixels > 10)
                    first.recycle()
                } finally { retriever.release(); file.delete() }
            }
        } finally { card.recycle() }
    }
}
