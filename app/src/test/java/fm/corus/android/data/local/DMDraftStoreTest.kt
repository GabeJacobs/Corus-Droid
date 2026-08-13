package fm.corus.android.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DMDraftStoreTest {

    private lateinit var store: DMDraftStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences(DMDraftStore.PREFS_NAME, 0).edit().clear().commit()
        store = DMDraftStore(context)
    }

    @Test
    fun emptyByDefault() {
        assertNull(store.load("me", "t1"))
    }

    @Test
    fun saveLoadRoundTrip() {
        store.save("me", "t1", "hello there")
        assertEquals("hello there", store.load("me", "t1"))
    }

    @Test
    fun whitespaceOnlyClears() {
        store.save("me", "t1", "kept")
        store.save("me", "t1", "   \n")
        assertNull(store.load("me", "t1"))
    }

    @Test
    fun preservesTrailingSpacesTheUserTyped() {
        store.save("me", "t1", "hello ")
        assertEquals("hello ", store.load("me", "t1"))
    }

    @Test
    fun clearRemovesDraft() {
        store.save("me", "t1", "kept")
        store.clear("me", "t1")
        assertNull(store.load("me", "t1"))
    }

    @Test
    fun draftsArePerUserAndThread() {
        store.save("me", "t1", "mine")
        store.save("other", "t1", "theirs")
        store.save("me", "t2", "other thread")
        assertEquals("mine", store.load("me", "t1"))
        assertEquals("theirs", store.load("other", "t1"))
        assertEquals("other thread", store.load("me", "t2"))
    }

    @Test
    fun missingUidDoesNotWrite() {
        store.save(null, "t1", "nope")
        store.save("", "t1", "nope")
        assertNull(store.load("me", "t1"))
        assertNull(store.load(null, "t1"))
    }

    @Test
    fun truncatesOverlongDrafts() {
        val long = "a".repeat(DMDraftStore.MAX_LENGTH + 50)
        store.save("me", "t1", long)
        assertEquals(DMDraftStore.MAX_LENGTH, store.load("me", "t1")?.length)
    }
}
