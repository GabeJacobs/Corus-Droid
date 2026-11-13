package fm.corus.android.ui.screens.live

internal data class LiveEffectSelection(
    val requested: String? = null,
    val awaitingSnapshot: String? = null,
) {
    fun visible(observed: String): String = requested ?: awaitingSnapshot ?: observed

    fun toggle(effect: String, observed: String): LiveEffectSelection =
        copy(requested = if (visible(observed) == effect) "off" else effect)

    fun saved(effect: String, observed: String): LiveEffectSelection =
        finished(effect).copy(awaitingSnapshot = effect.takeUnless { it == observed })

    fun finished(effect: String): LiveEffectSelection =
        if (requested == effect) copy(requested = null) else this

    fun observe(effect: String): LiveEffectSelection =
        if (awaitingSnapshot == effect) copy(awaitingSnapshot = null) else this
}
