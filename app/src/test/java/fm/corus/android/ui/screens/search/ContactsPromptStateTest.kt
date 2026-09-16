package fm.corus.android.ui.screens.search

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsPromptStateTest {
    @Test
    fun `contact prompt waits for persisted sync status`() {
        val viewModel = File(
            "src/main/java/fm/corus/android/ui/screens/search/SearchViewModel.kt",
        ).readText()
        val screen = File(
            "src/main/java/fm/corus/android/ui/screens/search/SearchScreen.kt",
        ).readText()

        assertTrue(viewModel.contains("WhileSubscribed(5000), \"unresolved\""))
        assertTrue(screen.contains("visible = contactsSyncStatus == \"notAsked\""))
    }
}
