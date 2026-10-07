package fm.corus.android.ui.screens.auth

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import fm.corus.android.data.model.*
import fm.corus.android.ui.components.PopularUsersInfiniteGrid
import fm.corus.android.ui.components.PopularUsersInfiniteGridViewModel
import fm.corus.android.ui.theme.CorusTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star

/** Render the production Compose screen and popular grid with local data, without Firebase. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RevisedTasteSuggestionsLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val followed = mutableStateOf(emptySet<String>())
    private val finishing = mutableStateOf(false)
    private val query = mutableStateOf("")
    private var previews = 0
    private var continues = 0
    private val output = File("/tmp/corus-android-revised-parity-previews").apply { mkdirs() }

    private fun render(dark: Boolean = false, required: Int = 0, fontScale: Float = 1f, quizTaken: Boolean = true) {
        val matches = (1..6).map { fixture(it) }
        val popular = mock<PopularUsersInfiniteGridViewModel>()
        whenever(popular.matches).thenReturn(MutableStateFlow(listOf(fixture(8), fixture(9))))
        whenever(popular.isLoading).thenReturn(MutableStateFlow(false))
        whenever(popular.endReached).thenReturn(MutableStateFlow(true))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                CorusTheme(darkTheme = dark) {
                    RevisedTasteSuggestionsContent(
                        matches, quizTaken, emptyList(), followed.value, query.value, if (query.value.isBlank()) emptyList() else listOf(matches.last().user), false, required, finishing.value,
                        onSearch = { query.value = it }, onPreview = { previews++ }, onFollow = { toggle(it) }, onTasteFollow = { toggle(it) },
                        onBack = {}, onContinue = { continues++ }, discoveryContent = { top ->
                            PopularUsersInfiniteGrid(emptySet(), followed.value, { previews++ }, { toggle(it) },
                                modifier = Modifier.testTag("discovery"), bottomContentPadding = 108.dp,
                                headerVerticalPadding = 0.dp, topContent = top, viewModel = popular,
                                headerTitle = "CORUS STARS", headerIcon = Icons.Filled.Star)
                        },
                    )
                }
            }
        }
    }
    private fun toggle(user: CymbalUser) {
        followed.value = if (user.id in followed.value) followed.value - user.id else followed.value + user.id
    }

    @Test fun `light screen preserves partial matches and see all opens every ranked match`() {
        render()
        compose.onNodeWithText("YOUR CLOSEST TASTE MATCH").assertIsDisplayed()
        compose.onNodeWithText("Artist 1").assertIsDisplayed()
        save("light-top.png")
        compose.onNodeWithTag("closest-match-follow").performClick()
        compose.onNodeWithTag("closest-match-follow").assertTextContains("FOLLOWING")
        assertEquals(0, previews)
        compose.onNodeWithText("See all 6 Taste Matches").performScrollTo()
        compose.onNodeWithText("See all 6 Taste Matches").performClick()
        compose.onNode(hasScrollToIndexAction() and !hasTestTag("discovery")).performScrollToIndex(5)
        compose.onNodeWithText("listener6").assertIsDisplayed()
        compose.onNodeWithText("listener6").performClick()
        assertEquals(1, previews)
    }
    @Test fun `compact follow target follows without opening a profile and remains accessible`() {
        render(dark = true)
        save("dark-top.png")
        compose.onNodeWithTag("follow-2").performScrollTo()
        val bounds = compose.onNodeWithTag("follow-2").getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top >= 48.dp)
        compose.onNodeWithTag("follow-2").performClick()
        compose.onNodeWithTag("follow-2").assertTextContains("Following")
        assertEquals(setOf("2"), followed.value)
        assertEquals(0, previews)
        save("dark-secondary.png")
        compose.onNodeWithTag("discovery").performScrollToNode(hasText("CORUS STARS"))
        compose.onNodeWithText("CORUS STARS").assertIsDisplayed()
        save("dark-stars.png")
    }
    @Test fun `required follows count down and pending completion disables continue`() {
        render(required = 3)
        compose.onNodeWithTag("onboarding-continue").assertIsNotEnabled().assertTextContains("FOLLOW 3 PEOPLE")
        compose.runOnIdle { followed.value = setOf("1") }
        compose.onNodeWithTag("onboarding-continue").assertIsNotEnabled().assertTextContains("FOLLOW 2 MORE")
        compose.runOnIdle { followed.value = setOf("1", "2", "3") }
        compose.onNodeWithTag("onboarding-continue").assertIsEnabled().performClick()
        assertEquals(1, continues)
        compose.runOnIdle { finishing.value = true }
        compose.onNodeWithTag("onboarding-continue").assertIsNotEnabled()
    }
    @Test fun `large text keeps all four artwork tiles in a full width strip`() {
        render(dark = true, fontScale = 1.4f)
        val art = compose.onNodeWithTag("closest-match-artwork", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val width = art.right - art.left
        val height = art.bottom - art.top
        assertTrue(width > 300.dp)
        assertTrue(abs(width.value / height.value - 4f) < 0.05f)
        compose.onNodeWithTag("closest-match-follow").assertIsDisplayed()
        save("dark-large-text.png")
    }
    @Test fun `search rows keep the same follow state without triggering profile navigation`() {
        render()
        compose.onNode(hasSetTextAction()).performTextInput("listener6")
        compose.onNodeWithText("YOUR CLOSEST TASTE MATCH").assertDoesNotExist()
        compose.onNodeWithText("Listener 6").assertIsDisplayed()
        compose.onNodeWithTag("follow-6").performClick()
        assertEquals(setOf("6"), followed.value)
        assertEquals(0, previews)
        compose.onNodeWithText("Listener 6").performClick()
        assertEquals(1, previews)
    }
    @Test fun `skipped quiz goes straight to aligned Corus Stars without invented matches`() {
        render(dark = true, quizTaken = false)
        compose.onNodeWithText("YOUR CLOSEST TASTE MATCH").assertDoesNotExist()
        compose.onNodeWithText("MORE PEOPLE WITH YOUR TASTE").assertDoesNotExist()
        compose.onNodeWithText("CORUS STARS").assertIsDisplayed()
        compose.onNodeWithText("listener8").assertIsDisplayed()
        save("dark-stars-initial.png")
    }
    private fun save(name: String) {
        // Robolectric has no PixelCopy window callback; draw the actual laid-out Android view.
        compose.runOnIdle {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun fixture(i: Int): SuggestedUserMatch {
        val covers = (1..4).map { j ->
            val file = File(output, "cover-$j.png")
            if (!file.exists()) {
                val bmp = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(listOf(Color.rgb(238, 195, 112), Color.rgb(102, 167, 179), Color.rgb(117, 107, 151), Color.rgb(182, 98, 77))[j-1])
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 26f }
                canvas.drawText("RECORD $j", 18f, 48f, paint)
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; bmp.recycle()
            }
            SharedTrackPreview("track$j", "Song $j", "Artist $i", "file://${file.absolutePath}")
        }
        return SuggestedUserMatch(CymbalUser(id = "$i", username = "listener$i", displayName = if (i==1) "Alex Rivera" else "Listener $i"),
            MusicMatchData(sharedArtistNames = listOf("Artist $i"), sharedTrackPreviews = covers))
    }
}
