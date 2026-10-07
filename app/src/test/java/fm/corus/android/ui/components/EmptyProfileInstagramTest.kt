package fm.corus.android.ui.components

import android.app.Application
import android.content.pm.PackageInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp")
class EmptyProfileInstagramTest {
    @get:Rule val compose = createComposeRule()
    private val profile = mutableStateOf(ShareProfileSubject("fixture", "fixture", "Fixture", null, postCount = 0))

    private fun sheet(v2: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = "com.instagram.android" })
        compose.setContent {
            CorusTheme(darkTheme = false) {
                ShareMediaSheet(
                    subject = ShareMediaSubject.Profile(profile.value), recentContacts = emptyList(),
                    searchResults = emptyList(), isSearching = false, isLoadingContacts = false,
                    onSearchQueryChange = {}, onSendToUser = { _, _ -> }, onDismiss = {},
                    isOwnProfile = true, instagramShareEnabled = true, profileSharingV2 = v2,
                )
            }
        }
    }

    @Test fun `empty legacy profile hides installed Instagram and keeps link sharing`() {
        sheet(v2 = false)
        compose.onNodeWithText("Instagram").assertDoesNotExist()
        compose.onNodeWithText("Copy Link").assertIsDisplayed()
    }

    @Test fun `empty flagged profile hides installed Instagram and keeps link sharing`() {
        sheet(v2 = true)
        compose.onNodeWithText("Instagram").assertDoesNotExist()
        compose.onNodeWithText("Copy Link").assertIsDisplayed()
    }

    @Test fun `Instagram updates with post count and known zero overrides cached artwork`() {
        sheet(v2 = false)
        compose.runOnIdle { profile.value = profile.value.copy(postCount = 1) }
        compose.onNodeWithText("Instagram").assertIsDisplayed()
        compose.runOnIdle { profile.value = profile.value.copy(postCount = 0, artworkUrls = listOf("")) }
        compose.onNodeWithText("Instagram").assertDoesNotExist()
        compose.runOnIdle { profile.value = profile.value.copy(postCount = null) }
        compose.onNodeWithText("Instagram").assertIsDisplayed()
        compose.runOnIdle { profile.value = profile.value.copy(artworkUrls = emptyList()) }
        compose.onNodeWithText("Instagram").assertDoesNotExist()
    }
}
