package fm.corus.android.data.model

import org.junit.Assert.*
import org.junit.Test

class CymbalCommentPreviewTest {

    @Test
    fun `fromPreviewMap parses flat preview comment correctly`() {
        val data = mapOf<String, Any?>(
            "commentId" to "comment123",
            "userId" to "user456",
            "username" to "alice",
            "displayName" to "Alice Smith",
            "avatarURL" to "https://example.com/avatar.jpg",
            "text" to "Great post!",
            "isVerified" to true,
            "isBot" to false,
            "createdAt" to 1700000000000L,
        )

        val comment = CymbalComment.fromPreviewMap(data)

        assertNotNull(comment)
        assertEquals("comment123", comment!!.id)
        assertEquals("Great post!", comment.text)
        assertEquals("user456", comment.user.id)
        assertEquals("alice", comment.user.username)
        assertEquals("Alice Smith", comment.user.displayName)
        assertEquals("https://example.com/avatar.jpg", comment.user.avatarURL)
        assertTrue(comment.user.isVerified)
    }

    @Test
    fun `fromPreviewMap returns null when required fields are missing`() {
        assertNull(CymbalComment.fromPreviewMap(mapOf("userId" to "u1", "text" to "hi")))
        assertNull(CymbalComment.fromPreviewMap(mapOf("commentId" to "c1", "text" to "hi")))
        assertNull(CymbalComment.fromPreviewMap(mapOf("commentId" to "c1", "userId" to "u1")))
    }

    @Test
    fun `fromCloudData parses previewComments into post comments`() {
        val postData = mapOf<String, Any?>(
            "id" to "post1",
            "user" to mapOf("id" to "u1", "username" to "bob", "displayName" to "Bob"),
            "trackId" to "t1",
            "trackName" to "Song",
            "artistName" to "Artist",
            "albumName" to "Album",
            "spotifyURI" to "",
            "spotifyWebURL" to "",
            "commentCount" to 5,
            "previewComments" to listOf(
                mapOf<String, Any?>(
                    "commentId" to "c1",
                    "userId" to "u2",
                    "username" to "carol",
                    "displayName" to "Carol",
                    "text" to "Love this!",
                ),
                mapOf<String, Any?>(
                    "commentId" to "c2",
                    "userId" to "u3",
                    "username" to "dave",
                    "displayName" to "Dave",
                    "text" to "Same here",
                ),
            ),
        )

        val post = CymbalPost.fromCloudData(postData)

        assertEquals(2, post.comments.size)
        assertEquals("c1", post.comments[0].id)
        assertEquals("carol", post.comments[0].user.username)
        assertEquals("Love this!", post.comments[0].text)
        assertEquals("c2", post.comments[1].id)
        assertEquals("dave", post.comments[1].user.username)
    }
}
