package fm.corus.android.ui.components

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Runs [fetch] with bounded retry, returning an empty list only after every
 * attempt has thrown.
 *
 * The Popular and Club Members rails synthesize each card's 2x2 album-art grid
 * from the user's recent posts. On a cold app launch many callables fire at
 * once and `getProfilePosts` can throw a transient FirebaseFunctions error
 * (auth token not ready yet, function cold start, network blip). The rails
 * previously swallowed that throw into an empty list and cached it for the
 * rail ViewModel's whole lifetime — so the grid stayed blank until the app was
 * killed and relaunched (the "album grid missing until restart" bug).
 *
 * Retrying the throw a few times with a short backoff lets the warm second
 * attempt succeed, so the grid fills in without a restart. A call that
 * legitimately *succeeds* with zero posts is NOT retried — that's a real
 * answer (the card renders without a grid), so we don't hammer the backend for
 * users who genuinely have no media posts.
 *
 * [CancellationException] is always rethrown so a cancelled enrichment job
 * (e.g. the rail left composition) tears down cleanly instead of being retried.
 */
internal suspend fun <T> fetchListWithRetry(
    maxAttempts: Int = 3,
    backoffMs: (attempt: Int) -> Long = { attempt -> 150L * (1L shl attempt) },
    fetch: suspend () -> List<T>,
): List<T> {
    repeat(maxAttempts) { attempt ->
        try {
            return fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (attempt == maxAttempts - 1) return emptyList()
            delay(backoffMs(attempt))
        }
    }
    return emptyList()
}
