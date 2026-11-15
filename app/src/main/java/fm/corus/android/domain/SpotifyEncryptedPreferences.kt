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
 * Opens encrypted music service credential storage and recovers from an unusable
 * Android Keystore key. This can happen when encrypted preferences are restored
 * without the device-bound key that originally encrypted them.
 *
 * Both Spotify stores use AndroidX's default master-key alias. If that key is
 * unusable, reset both stores together so one cannot be left encrypted with a
 * key that has just been replaced. A store with a dedicated alias owns its
 * key and gets reset independently.
 */
internal object SpotifyEncryptedPreferences {
    const val APP_REMOTE_PREFS_NAME = "fm.corus.spotify.auth"
    const val LIBRARY_PREFS_NAME = "fm.corus.spotify.libraryauth"

    @Synchronized
    fun open(
        context: Context,
        name: String,
        masterKeyAlias: String = MasterKey.DEFAULT_MASTER_KEY_ALIAS,
    ): SharedPreferences {
        return try {
            create(context, name, masterKeyAlias)
        } catch (error: GeneralSecurityException) {
            recover(context, name, masterKeyAlias, error)
        } catch (error: IOException) {
            recover(context, name, masterKeyAlias, error)
        }
    }

    private fun recover(
        context: Context,
        name: String,
        masterKeyAlias: String,
        cause: Exception,
    ): SharedPreferences {
        Log.w(TAG, "Resetting unreadable music service credentials", cause)

        if (masterKeyAlias == MasterKey.DEFAULT_MASTER_KEY_ALIAS) {
            context.deleteSharedPreferences(APP_REMOTE_PREFS_NAME)
            context.deleteSharedPreferences(LIBRARY_PREFS_NAME)
        }
        context.deleteSharedPreferences(name)

        // Best effort: a missing alias is expected after backup/restore. If the
        // alias exists but is invalid, removing it lets MasterKey create a fresh
        // device-bound key on the retry below.
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(masterKeyAlias)
        } catch (error: GeneralSecurityException) {
            Log.w(TAG, "Could not remove the unusable master key", error)
        } catch (error: IOException) {
            Log.w(TAG, "Could not load Android Keystore during credential recovery", error)
        }

        return create(context, name, masterKeyAlias)
    }

    private fun create(context: Context, name: String, masterKeyAlias: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context, masterKeyAlias)
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
