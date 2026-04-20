package fm.corus.android.domain

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fm.corus.android.data.model.MusicService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MusicServicePreferenceTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setUp() {
        context = mock()
        prefs = mock()
        editor = mock()
        whenever(context.getSharedPreferences("corus_prefs", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
    }

    @Test fun `defaults to Spotify when no value cached`() {
        whenever(prefs.getString(eq("musicService"), any())).thenReturn(MusicService.SPOTIFY.value)
        val pref = MusicServicePreference(context, mock<FirebaseFirestore>(), mock<FirebaseAuth>())
        assertEquals(MusicService.SPOTIFY, pref.current.value)
    }

    @Test fun `loads Apple Music from cache on init`() {
        whenever(prefs.getString(eq("musicService"), any())).thenReturn(MusicService.APPLE_MUSIC.value)
        val pref = MusicServicePreference(context, mock<FirebaseFirestore>(), mock<FirebaseAuth>())
        assertEquals(MusicService.APPLE_MUSIC, pref.current.value)
    }

    @Test fun `set writes to prefs and updates StateFlow`() {
        whenever(prefs.getString(eq("musicService"), any())).thenReturn(MusicService.SPOTIFY.value)
        val pref = MusicServicePreference(context, mock<FirebaseFirestore>(), mock<FirebaseAuth>())

        pref.set(MusicService.APPLE_MUSIC)

        verify(editor).putString("musicService", MusicService.APPLE_MUSIC.value)
        verify(editor).apply()
        assertEquals(MusicService.APPLE_MUSIC, pref.current.value)
    }
}
