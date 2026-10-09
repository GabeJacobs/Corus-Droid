package fm.corus.android.ui.components

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import fm.corus.android.domain.collectionArtworkUrl
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollectionArtworkCacheTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun gridRejectsTinyArtAndReusesSharpMemoryAndDiskCaches() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val networkCalls = AtomicInteger()
        val smallUrl = "https://i.scdn.co/image/ab67616d00004851abc"
        val largeUrl = collectionArtworkUrl(smallUrl)
        val smallPng = png(64)
        val largePng = png(640)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            networkCalls.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body((if (chain.request().url.toString() == smallUrl) smallPng else largePng)
                    .toResponseBody("image/png".toMediaType()))
                .build()
        }.build()
        val directory = temporaryFolder.newFolder("artwork-cache").toOkioPath()
        fun cache() = DiskCache.Builder().directory(directory).maxSizeBytes(10L * 1024 * 1024).build()
        fun loader(disk: DiskCache) = ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(8L * 1024 * 1024).build() }
            .diskCache(disk)
            .components { add(OkHttpNetworkFetcherFactory(client)) }
            .build()
        fun request(size: Int) = collectionArtworkRequest(context, largeUrl).newBuilder()
            .size(size).allowHardware(false).coroutineContext(Dispatchers.IO).build()

        var disk = cache()
        var imageLoader = loader(disk)
        try {
            // Prime the old thumbnail, just as an avatar/mini-player might.
            val thumb = imageLoader.execute(ImageRequest.Builder(context).data(smallUrl)
                .size(64).allowHardware(false).coroutineContext(Dispatchers.IO).build())
            assertTrue(thumb.toString(), thumb is SuccessResult)
            assertEquals(1, networkCalls.get())

            val first = imageLoader.execute(request(300))
            assertTrue(first.toString(), first is SuccessResult)
            first as SuccessResult
            assertTrue("The grid must not reuse the 64px image", first.image.width >= 300)
            assertEquals(DataSource.NETWORK, first.dataSource)
            assertEquals(2, networkCalls.get())

            val reopened = imageLoader.execute(request(300)) as SuccessResult
            assertEquals(DataSource.MEMORY_CACHE, reopened.dataSource)
            assertTrue(reopened.image.width >= 300)
            assertEquals(2, networkCalls.get())

            // A larger display must decode enough pixels from cached source bytes.
            val larger = imageLoader.execute(request(420)) as SuccessResult
            assertTrue("A larger tile must not stretch a smaller decode", larger.image.width >= 420)
            assertEquals(2, networkCalls.get())

            // Recreate both loader and disk cache to model an app restart.
            imageLoader.shutdown()
            disk.shutdown()
            disk = cache()
            imageLoader = loader(disk)
            val relaunched = imageLoader.execute(request(300)) as SuccessResult
            assertEquals(DataSource.DISK, relaunched.dataSource)
            assertTrue(relaunched.image.width >= 300)
            assertEquals("Reopening/relaunching must not download again", 2, networkCalls.get())
        } finally {
            imageLoader.shutdown()
            disk.shutdown()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun png(side: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }
}
