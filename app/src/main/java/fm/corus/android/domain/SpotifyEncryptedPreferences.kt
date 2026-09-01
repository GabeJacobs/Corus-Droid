package fm.corus.android.domain

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

/**
 * Opens Spotify's encrypted credential storage and recovers from an unusable
 * Android Keystore key. This can happen when encrypted preferences are restored
 * without the device-bound key that originally encrypted them.
 *
 * Both Spotify stores use AndroidX's default master-key alias. If that key is
 * unusable, reset both stores together so one cannot be left encrypted with a
 * key that has just been replaced. This only forgets Spotify authorization;
 * the user's Corus account and other preferences are untouched.
 */
internal object SpotifyEncryptedPreferences {
    const val APP_REMOTE_PREFS_NAME = "fm.corus.spotify.auth"
    const val LIBRARY_PREFS_NAME = "fm.corus.spotify.libraryauth"

    @Synchronized
    fun open(context: Context, name: String): SharedPreferences {
        return try {
            create(context, name)
        } catch (error: GeneralSecurityException) {
            recover(context, name, error)
        } catch (error: IOException) {
            recover(context, name, error)
        }
    }

    private fun recover(
        context: Context,
        name: String,
        cause: Exception,
    ): SharedPreferences {
        Log.w(TAG, "Resetting unreadable Spotify credentials", cause)

        context.deleteSharedPreferences(APP_REMOTE_PREFS_NAME)
        context.deleteSharedPreferences(LIBRARY_PREFS_NAME)

        // Best effort: a missing alias is expected after backup/restore. If the
        // alias exists but is invalid, removing it lets MasterKey create a fresh
        // device-bound key on the retry below.
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        } catch (error: GeneralSecurityException) {
            Log.w(TAG, "Could not remove the unusable Spotify master key", error)
        } catch (error: IOException) {
            Log.w(TAG, "Could not load Android Keystore during Spotify recovery", error)
        }

        return create(context, name)
    }

    private fun create(context: Context, name: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private const val TAG = "SpotifyEncryptedPrefs"
}
