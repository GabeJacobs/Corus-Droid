package fm.corus.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import fm.corus.android.ui.navigation.UserRepositoryEntryPoint

@Composable
fun rememberHiddenUserIds(): Set<String> {
    val context = LocalContext.current
    val repo = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            UserRepositoryEntryPoint::class.java,
        ).userRepository()
    }
    val hidden by repo.hiddenUserIds.collectAsState()
    return hidden
}
