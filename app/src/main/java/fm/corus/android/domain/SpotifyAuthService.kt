package fm.corus.android.domain

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spotify App Remote token storage for the full-playback experiment. App Remote
 * authorization returns a short-lived access token via `corus://spotify-auth`;
 * we cache it for reconnects.
 */
@Singleton
class SpotifyAuthService @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Cached App Remote access token (no refresh). null when missing or expired. */
    fun cachedAccessToken(): String? {
        val expiry = prefs.getLong(TOKEN_EXPIRY_KEY, 0L)
        if (expiry <= System.currentTimeMillis() + 30_000) return null
        return prefs.getString(ACCESS_TOKEN_KEY, null)
    }

    /** App Remote authorization returns an access token directly (no refresh). */
    fun storeAppRemoteAccessToken(token: String) {
        prefs.edit()
            .putString(ACCESS_TOKEN_KEY, token)
            .putLong(TOKEN_EXPIRY_KEY, System.currentTimeMillis() + 3_600_000L)
            .apply()
    }

    fun clearAccessToken() {
        prefs.edit()
            .remove(ACCESS_TOKEN_KEY)
            .remove(TOKEN_EXPIRY_KEY)
            .apply()
    }

    companion object {
        const val CLIENT_ID = "6c95dc15990f43b0a3ed93f71f7d9689"
        const val REDIRECT_URI = "corus://spotify-auth"

        private const val PREFS_NAME = "fm.corus.spotify.auth"
        private const val ACCESS_TOKEN_KEY = "accessToken"
        private const val TOKEN_EXPIRY_KEY = "tokenExpiry"
    }
}

data class SpotifyAuthPendingPlay(
    val trackId: String,
    val name: String,
    val artist: String,
    val isrc: String? = null,
    val albumArtURL: String? = null,
    val albumArtLargeURL: String? = null,
    val spotifyWebURL: String? = null,
    val spotifyURI: String? = null,
    val sourcePostId: String? = null,
    val source: fm.corus.android.data.model.TrackSource = fm.corus.android.data.model.TrackSource.SPOTIFY,
)
