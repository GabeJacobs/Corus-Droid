package fm.corus.android.domain

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * App Remote authorization (`showAuthView = true`) must run with a visible
 * Activity context — connecting with [Application] fails to surface Spotify's
 * consent UI and play calls return "Explicit user authorization is required".
 */
object SpotifyConnectContext {
    private var activityRef: WeakReference<Activity>? = null

    fun setActivity(activity: Activity?) {
        activityRef = activity?.let { WeakReference(it) }
    }

    fun activityOr(applicationContext: android.content.Context): android.content.Context =
        activityRef?.get() ?: applicationContext
}
