package fm.corus.android.ui.components

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Regression tests for the rail album-art enrichment retry.
 *
 * Guards the "album grid missing until restart" bug: a single transient
 * cold-start failure of `getProfilePosts` used to be swallowed into an empty
 * list and cached for the rail ViewModel's lifetime, leaving the 2x2 grid
 * blank until the app was killed and relaunched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FetchListWithRetryTest {

    @Test
    fun `returns the result on first success without retrying`() = runTest {
        var calls = 0
        val result = fetchListWithRetry {
            calls++
            listOf("a", "b")
        }
        assertEquals(1, calls)
        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `retries a transient throw and returns the warm result`() = runTest {
        var calls = 0
        val result = fetchListWithRetry {
            calls++
            if (calls < 3) throw IOException("cold start")
            listOf("warm")
        }
        assertEquals(3, calls)
        assertEquals(listOf("warm"), result)
    }

    @Test
    fun `gives up with an empty list after exhausting attempts`() = runTest {
        var calls = 0
        val result = fetchListWithRetry<String>(maxAttempts = 3) {
            calls++
            throw IOException("still cold")
        }
        assertEquals(3, calls)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `does not retry a legitimately empty success`() = runTest {
        var calls = 0
        val result = fetchListWithRetry<String> {
            calls++
            emptyList()
        }
        // A successful empty answer is real — retrying would hammer the backend
        // for users who genuinely have no media posts.
        assertEquals(1, calls)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `propagates cancellation instead of retrying`() = runTest {
        var calls = 0
        var thrown: Throwable? = null
        try {
            fetchListWithRetry<String> {
                calls++
                throw CancellationException("rail left composition")
            }
        } catch (e: CancellationException) {
            thrown = e
        }
        assertEquals(1, calls)
        assertTrue(thrown is CancellationException)
    }
}
