package fm.corus.android.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Sticky assignment and confirmed unique follows. Local IDs never enter analytics. */
@Serializable
data class OnboardingFollowSession(
    val revised: Boolean,
    val debugBuild: Boolean,
    val quizPickCount: Int,
    val matchCount: Int,
    val id: String = UUID.randomUUID().toString(),
    val confirmedIds: Set<String> = emptySet(),
    val revision: Int = 0,
    val exposed: Boolean = false,
    val completed: Boolean = false,
    // Missing in persisted sessions from the earlier preview-only treatment.
    val uiVersion: String = "android_preview_v1",
    val minimumFollows: Int = 0,
) {
    val usesRevisedSuggestions: Boolean get() = revised && uiVersion == REVISED_UI_VERSION

    fun parameters(): Map<String, Any> = mapOf(
        "experiment" to "revised_onboarding_v1",
        "variant" to if (revised) "revised" else "control",
        "onboarding_session_id" to id,
        "measurement_version" to 1,
        "ui_version" to uiVersion,
        "debug_build" to if (debugBuild) "true" else "false",
        "minimum_follows" to minimumFollows.coerceAtLeast(0),
        "quiz_pick_count" to quizPickCount.coerceAtLeast(0),
        "match_count" to matchCount.coerceAtLeast(0),
        "followed_count" to confirmedIds.size,
        "measurement_index" to revision,
    )

    companion object { const val REVISED_UI_VERSION = "android_revised_v1" }
}
