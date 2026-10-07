package fm.corus.android.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AppIcon(val alias: String) {
    DEFAULT("DefaultLauncher"),
    CORUS_BLUE("CorusBlueLauncher"),
}

/** Component state is the source of truth, including after an app update. */
@Singleton
class AppIconManager @Inject constructor(@ApplicationContext context: Context) {
    private val packageManager = context.packageManager
    private val packageName = context.packageName

    private fun component(icon: AppIcon) = ComponentName(packageName, "$packageName.${icon.alias}")

    fun selectedIcon(): AppIcon = if (
        packageManager.getComponentEnabledSetting(component(AppIcon.CORUS_BLUE)) ==
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    ) AppIcon.CORUS_BLUE else AppIcon.DEFAULT

    @Synchronized
    fun changeIcon(icon: AppIcon) {
        val previousStates = AppIcon.entries.associateWith {
            packageManager.getComponentEnabledSetting(component(it))
        }
        val newStates = AppIcon.entries.associateWith {
            if (it == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (previousStates == newStates) return

        try {
            applyStates(newStates, icon)
        } catch (error: Exception) {
            // Keep a launchable entry if a pre-33 sequential update fails.
            try {
                val previousIcon = if (previousStates[AppIcon.CORUS_BLUE] ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                ) AppIcon.CORUS_BLUE else AppIcon.DEFAULT
                applyStates(previousStates, previousIcon)
            } catch (rollbackError: Exception) {
                error.addSuppressed(rollbackError)
            }
            throw error
        }
    }

    private fun applyStates(states: Map<AppIcon, Int>, selected: AppIcon) {
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.setComponentEnabledSettings(states.map { (icon, state) ->
                PackageManager.ComponentEnabledSetting(component(icon), state, PackageManager.DONT_KILL_APP)
            })
        } else {
            // Enable the destination first: never leave the app without a launcher entry.
            val order = listOf(selected) + AppIcon.entries.filter { it != selected }
            order.forEach { icon ->
                packageManager.setComponentEnabledSetting(
                    component(icon), states.getValue(icon), PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
