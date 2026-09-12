package fm.corus.android.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Revision survives map navigation; no personal data is retained. */
object MapPostChanges {
    private val mutable = MutableStateFlow(0L)
    val revisions = mutable.asStateFlow()
    fun changed() { mutable.update { it + 1 } }
}
