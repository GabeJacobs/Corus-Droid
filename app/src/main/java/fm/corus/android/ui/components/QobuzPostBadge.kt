package fm.corus.android.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.domain.QobuzLinks
import fm.corus.android.service.RemoteConfigService

@EntryPoint
@InstallIn(SingletonComponent::class)
interface QobuzBadgeDependencies { fun remoteConfig(): RemoteConfigService }

@Composable
fun rememberQobuzEnabled(requested: Boolean = true): Boolean {
    val context = LocalContext.current
    val flags = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, QobuzBadgeDependencies::class.java).remoteConfig() }
    val revision by flags.revision.collectAsState()
    @Suppress("UNUSED_VARIABLE") val flagRevision = revision
    return requested && flags.qobuzEnabled && QobuzLinks.enabled
}

@Composable
fun rememberQobuzURL(track: CymbalTrack, requested: Boolean): String? {
    val context = LocalContext.current
    val flags = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, QobuzBadgeDependencies::class.java).remoteConfig() }
    val revision by flags.revision.collectAsState()
    val entries by QobuzLinks.entries.collectAsState()
    val enabled = requested && flags.qobuzEnabled && com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
    LaunchedEffect(track, enabled, revision) {
        if (enabled) QobuzLinks.resolve(track)
    }
    @Suppress("UNUSED_VARIABLE") val cacheRevision = entries
    return if (enabled) QobuzLinks.url(track) else null
}
