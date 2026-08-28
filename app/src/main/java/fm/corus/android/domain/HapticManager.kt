package fm.corus.android.domain

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var cachedEnabled: Boolean = true

    val hapticsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[HAPTICS_ENABLED] ?: true
    }

    init {
        scope.launch {
            hapticsEnabled.collect { cachedEnabled = it }
        }
    }

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[HAPTICS_ENABLED] = enabled }
    }

    /** Whether this device has a vibrator capable of producing haptic feedback. */
    fun hasVibrator(): Boolean = vibrator.hasVibrator()

    /** Light impact — button taps, tab switches. Matches iOS `.light`. */
    fun impact(style: ImpactStyle = ImpactStyle.LIGHT) {
        if (!isEnabled()) return
        if (style == ImpactStyle.LIGHT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrateTouch(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            return
        }
        val amplitude = when (style) {
            ImpactStyle.LIGHT -> 40
            ImpactStyle.MEDIUM -> 100
            ImpactStyle.HEAVY -> 200
        }
        vibrateTouch(VibrationEffect.createOneShot(20, amplitude))
    }

    /** Notification-style feedback — success, warning, error. */
    fun notification(type: NotificationType = NotificationType.SUCCESS) {
        if (!isEnabled()) return
        val effect = when (type) {
            NotificationType.SUCCESS -> VibrationEffect.createOneShot(30, 80)
            NotificationType.WARNING -> VibrationEffect.createOneShot(40, 120)
            NotificationType.ERROR -> VibrationEffect.createOneShot(50, 200)
        }
        vibrator.vibrate(effect)
    }

    /** Selection tick — subtle change feedback. */
    fun selection() {
        if (!isEnabled()) return
        vibrateTouch(VibrationEffect.createOneShot(10, 30))
    }

    /**
     * USAGE_TOUCH so the tick is allowed while a finger is still down
     * (page-tab swipe). Untagged one-shots are often swallowed mid-gesture.
     */
    private fun vibrateTouch(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .build()
            vibrator.vibrate(effect, attrs)
        } else {
            vibrator.vibrate(effect)
        }
    }

    private fun isEnabled(): Boolean = cachedEnabled

    enum class ImpactStyle { LIGHT, MEDIUM, HEAVY }
    enum class NotificationType { SUCCESS, WARNING, ERROR }
}
