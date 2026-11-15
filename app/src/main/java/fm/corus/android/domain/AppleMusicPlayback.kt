package fm.corus.android.domain

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.remote.CloudFunctionsDataSource
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal data class AppleMusicAuthorization(
    val userToken: String,
    val storefront: String,
    val encodedStorefront: String?,
)

internal interface AppleMusicLiveQueue {
    fun stageNext(songId: String?, revision: Long, mediaItem: MediaItem? = null)
    fun stagedPosition(mediaId: String, revision: Long): Long?
    fun hasStagedNext(mediaId: String, revision: Long): Boolean
}

internal interface AppleMusicPlatform {
    fun authorizationIntent(developerToken: String): Intent
    fun authorizationResult(data: Intent): AppleMusicAuthorization?
    fun createPlayer(
        developerToken: String,
        authorization: AppleMusicAuthorization,
        songId: String,
        mediaItem: MediaItem,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        onAuthorizationLost: () -> Unit,
    ): Player
}

@Singleton
class AppleMusicPlayback @Inject constructor(
    @ApplicationContext context: Context,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val auth: FirebaseAuth,
    private val http: HttpClient,
) {
    private val platform by lazy { createAppleMusicSdk(context) }
    private val prefs by lazy {
        SpotifyEncryptedPreferences.open(context, "fm.corus.applemusic.auth", "corus.applemusic.masterkey")
    }
    @Volatile private var developerToken: CloudFunctionsDataSource.AppleMusicDeveloperToken? = null
    private data class PendingAuthorization(
        val userId: String,
        val result: CompletableDeferred<AppleMusicAuthorization?> = CompletableDeferred(),
    )
    private var pendingAuthorization: PendingAuthorization? = null
    private var declinedForUser: String? = null

    private fun savedAuthorization(userId: String): AppleMusicAuthorization? {
        if (prefs.getString("userId", null) != userId) return null
        return AppleMusicAuthorization(
            userToken = prefs.getString("userToken", null) ?: return null,
            storefront = prefs.getString("storefront", null) ?: return null,
            encodedStorefront = prefs.getString("encodedStorefront", null),
        )
    }

    internal suspend fun createPlayer(
        track: QueuedTrack,
        mediaItem: MediaItem,
        interactive: Boolean,
        explicit: Boolean,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
    ): Player? {
        val sdk = platform ?: return null
        val userId = auth.currentUser?.uid ?: return null
        val saved = savedAuthorization(userId)
        if (saved == null && (!interactive || (!explicit && declinedForUser == userId))) return null

        val token = developerToken()
        val authorization = saved ?: authorize(sdk, token.token, userId) ?: return null
        val songId = cloudFunctions.resolveAppleMusicSong(track, authorization.storefront) ?: return null
        if (auth.currentUser?.uid != userId) return null
        return sdk.createPlayer(token.token, authorization, songId, mediaItem, onNext, onPrevious) {
            if (savedAuthorization(userId) == authorization) prefs.edit().clear().apply()
        }
    }

    private suspend fun developerToken(): CloudFunctionsDataSource.AppleMusicDeveloperToken =
        developerToken?.takeIf { it.expiresAt > System.currentTimeMillis() + 60_000 }
            ?: cloudFunctions.getAppleMusicDeveloperToken().also { developerToken = it }

    internal suspend fun resolveConnectedSong(track: QueuedTrack): String? {
        val userId = auth.currentUser?.uid ?: return null
        val authorization = savedAuthorization(userId) ?: return null
        val songId = cloudFunctions.resolveAppleMusicSong(track, authorization.storefront)
        return songId.takeIf { auth.currentUser?.uid == userId && savedAuthorization(userId) == authorization }
    }

    internal suspend fun addToLibrary(track: QueuedTrack, userId: String, stillEnabled: suspend () -> Boolean) {
        if (auth.currentUser?.uid != userId) return
        val authorization = savedAuthorization(userId) ?: return
        val songId = cloudFunctions.resolveAppleMusicSong(track, authorization.storefront) ?: return
        val token = developerToken().token
        if (!stillEnabled() || auth.currentUser?.uid != userId || savedAuthorization(userId) != authorization) return

        val response = http.post("https://api.music.apple.com/v1/me/library") {
            bearerAuth(token)
            header("Music-User-Token", authorization.userToken)
            parameter("ids[songs]", songId)
        }
        check(response.status == HttpStatusCode.Accepted) { "Apple Music library request failed (${response.status.value})." }
    }

    fun isConnected(): Boolean = auth.currentUser?.uid?.let(::savedAuthorization) != null

    suspend fun connect(): Boolean {
        val sdk = platform ?: return false
        val userId = auth.currentUser?.uid ?: return false
        return authorize(sdk, developerToken().token, userId) != null
    }

    fun disconnect() {
        val userId = auth.currentUser?.uid ?: return
        pendingAuthorization?.result?.complete(null)
        if (savedAuthorization(userId) != null) prefs.edit().clear().apply()
        declinedForUser = userId
    }

    private suspend fun authorize(
        sdk: AppleMusicPlatform,
        token: String,
        userId: String,
    ): AppleMusicAuthorization? {
        val activity = SpotifyConnectContext.currentActivity() ?: return null
        val existing = pendingAuthorization
        val result = if (existing != null) {
            if (existing.userId != userId) return null
            existing.result.await()
        } else {
            val pending = PendingAuthorization(userId)
            pendingAuthorization = pending
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(sdk.authorizationIntent(token), AUTH_REQUEST_CODE)
                withTimeout(5 * 60_000L) { pending.result.await() }
            } catch (_: TimeoutCancellationException) {
                null
            } finally {
                pendingAuthorization = null
                pending.result.cancel()
            }
        }
        if (auth.currentUser?.uid != userId) return null
        if (result == null) {
            declinedForUser = userId
            return null
        }
        declinedForUser = null
        prefs.edit()
            .putString("userId", userId)
            .putString("userToken", result.userToken)
            .putString("storefront", result.storefront)
            .putString("encodedStorefront", result.encodedStorefront)
            .apply()
        return result
    }

    fun deliverAuthorizationResult(data: Intent?) {
        val pending = pendingAuthorization ?: return
        val result = try {
            data?.let { platform?.authorizationResult(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        pending.result.complete(result)
    }

    companion object {
        const val AUTH_REQUEST_CODE = 53292
        fun isAvailable(): Boolean = isAppleMusicSdkAvailable()
    }
}
