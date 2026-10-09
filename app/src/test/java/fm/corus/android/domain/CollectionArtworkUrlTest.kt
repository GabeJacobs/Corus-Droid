package fm.corus.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionArtworkUrlTest {
    @Test fun spotifyThumbnailsUseThe640PixelVariant() {
        for (token in listOf("00004851", "00001e02", "0000b273")) {
            assertEquals("https://i.scdn.co/image/ab67616d0000b273abc",
                collectionArtworkUrl("https://i.scdn.co/image/ab67616d${token}abc"))
        }
    }

    @Test fun appleThumbnailsUse600PixelsAndPreserveTheAssetAndQuery() {
        for (size in listOf(60, 100, 300, 600)) {
            assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music/cover.jpg/600x600bb.jpg?x=1",
                collectionArtworkUrl("https://is1-ssl.mzstatic.com/image/thumb/Music/cover.jpg/${size}x${size}bb.jpg?x=1"))
        }
    }

    @Test fun largerAppleArtIsNotDowngraded() {
        val url = "https://is1-ssl.mzstatic.com/image/thumb/Music/cover/1000x1000bb.jpg"
        assertEquals(url, collectionArtworkUrl(url))
    }

    @Test fun soundCloudThumbnailsUse500Pixels() {
        assertEquals("https://i1.sndcdn.com/artworks-abc-t500x500.jpg",
            collectionArtworkUrl("https://i1.sndcdn.com/artworks-abc-large.jpg"))
    }

    @Test fun unknownAndSignedUrlsRemainUntouched() {
        for (url in listOf("", "//i.scdn.co/image/ab67616d00004851abc", "spotify:image:abc", "https://img.example/100x100bb.jpg?token=abc", "https://example.com/i.scdn.co/image/ab67616d00004851abc", "https://storage.googleapis.com/cover.jpg?signature=abc")) {
            assertEquals(url, collectionArtworkUrl(url))
        }
    }
}
