package fm.corus.android.ui.screens.compose

import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.PostDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftMediaScopeTest {
    private fun draft(id: String, type: MediaType) = PostDraft(
        id = id, mediaType = type, caption = "", captionMode = "text",
        createdAt = 0, updatedAt = 0,
    )
    private val song = draft("song", MediaType.TRACK)
    private val film = draft("film", MediaType.MOVIE)

    @Test fun `only drafts from the active picker type are offered`() {
        assertEquals(listOf(song), draftsForMediaType(listOf(film, song), MediaType.TRACK))
        assertEquals(listOf(film), draftsForMediaType(listOf(film, song), MediaType.MOVIE))
    }
    @Test fun `no matching drafts means no entry or count`() {
        assertTrue(draftsForMediaType(listOf(film), MediaType.TRACK).isEmpty())
        assertTrue(draftsForMediaType(listOf(song), MediaType.MOVIE).isEmpty())
        assertTrue(draftsForMediaType(emptyList(), MediaType.TRACK).isEmpty())
    }
    @Test fun `all picker does not mix draft types`() {
        assertTrue(draftsForMediaType(listOf(song, film), null).isEmpty())
    }
}
