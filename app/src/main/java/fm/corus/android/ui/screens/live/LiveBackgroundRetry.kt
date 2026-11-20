package fm.corus.android.ui.screens.live

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

// Only for passive startup listeners. Explicit Live operations keep their error UI.
internal fun <T> Flow<T>.retryLiveBackground(source: String): Flow<T> = retryWhen { error, attempt ->
    if (error is CancellationException) throw error
    val retryMs = (2_000L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(60_000L)
    val code = (error as? FirebaseFirestoreException)?.code?.name ?: error.javaClass.simpleName
    // Log the source and code, without account IDs, room data, or server messages.
    Log.w("LiveBackground", "$source failed ($code); retrying in ${retryMs}ms")
    delay(retryMs)
    true
}
