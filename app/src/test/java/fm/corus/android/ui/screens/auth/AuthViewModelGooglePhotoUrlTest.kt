package fm.corus.android.ui.screens.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthViewModelGooglePhotoUrlTest {

    @Test fun `upgrades small size segment to 400`() {
        val input = "https://lh3.googleusercontent.com/a/ACg8oc-abc=s96-c"
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8oc-abc=s400-c",
            AuthViewModel.upgradeGooglePhotoUrlSize(input),
        )
    }

    @Test fun `upgrades larger size segment to 400`() {
        val input = "https://lh3.googleusercontent.com/a/ACg8oc-abc=s512-c"
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8oc-abc=s400-c",
            AuthViewModel.upgradeGooglePhotoUrlSize(input),
        )
    }

    @Test fun `upgrades size segment without -c suffix`() {
        val input = "https://lh3.googleusercontent.com/a/ACg8oc-abc=s96"
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8oc-abc=s400-c",
            AuthViewModel.upgradeGooglePhotoUrlSize(input),
        )
    }

    @Test fun `preserves query string when upgrading`() {
        val input = "https://lh3.googleusercontent.com/a/ACg8oc-abc=s96-c?foo=bar"
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8oc-abc=s400-c?foo=bar",
            AuthViewModel.upgradeGooglePhotoUrlSize(input),
        )
    }

    @Test fun `passes through URL with no size segment`() {
        val input = "https://example.com/photo.jpg"
        assertEquals(input, AuthViewModel.upgradeGooglePhotoUrlSize(input))
    }

    @Test fun `does not match digit sequences elsewhere in path`() {
        // The path contains "=s" only as part of the size segment marker.
        // A token like "user123" should not be mistaken for a size segment.
        val input = "https://example.com/user123/photo.jpg"
        assertEquals(input, AuthViewModel.upgradeGooglePhotoUrlSize(input))
    }
}
