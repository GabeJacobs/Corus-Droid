package fm.corus.android.ui.screens.map

import android.location.LocationManager

internal const val CITY_LOCATION_ATTEMPT_TIMEOUT_MS = 20_000L
internal const val CITY_LOCATION_MAX_ATTEMPTS = 2

/**
 * City sharing only needs approximate location. Prefer providers that can
 * legally serve a coarse grant; precise GPS is an optional fallback only when
 * the user independently granted fine location.
 */
internal fun cityLocationProviders(enabled: Set<String>, hasFine: Boolean): List<String> = buildList {
    listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER).forEach { provider ->
        if (provider in enabled) add(provider)
    }
    if (hasFine && LocationManager.GPS_PROVIDER in enabled) add(LocationManager.GPS_PROVIDER)
}.distinct()

internal fun shouldWarmUpCityLocation(attempt: Int): Boolean =
    attempt < CITY_LOCATION_MAX_ATTEMPTS

internal data class CityLocationPresentation(
    val resolving: Boolean,
    val recoverySheet: Boolean,
    val genericError: String?,
)

internal fun cityLocationStarted() = CityLocationPresentation(
    resolving = true,
    recoverySheet = false,
    genericError = null,
)

internal fun cityLocationFinished() = CityLocationPresentation(
    resolving = false,
    recoverySheet = false,
    genericError = null,
)

internal fun cityLocationFailed() = CityLocationPresentation(
    resolving = false,
    recoverySheet = true,
    genericError = null,
)
