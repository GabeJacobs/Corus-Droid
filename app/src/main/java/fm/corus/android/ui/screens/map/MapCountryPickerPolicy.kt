package fm.corus.android.ui.screens.map

internal data class MapCountryPickerSelection(
    val activeCountryCodes: Set<String>,
    val isSelectingMultiple: Boolean = false,
    val pendingCountryCodes: Set<String> = emptySet(),
) {
    val canStart: Boolean get() = isSelectingMultiple && pendingCountryCodes.isNotEmpty()
}

internal fun mapCountryPickerOpened(activeCountryCodes: Set<String>) = MapCountryPickerSelection(
    activeCountryCodes = activeCountryCodes.map(String::uppercase).toSet(),
)

internal fun MapCountryPickerSelection.beginMultiple() = copy(
    isSelectingMultiple = true,
    // A new multiple-country draft is intentionally empty. The currently
    // playing scope remains visible separately and is not mutated by editing.
    pendingCountryCodes = emptySet(),
)

internal fun MapCountryPickerSelection.cancelMultiple() = copy(
    isSelectingMultiple = false,
    pendingCountryCodes = emptySet(),
)

internal fun MapCountryPickerSelection.toggleCountry(code: String): MapCountryPickerSelection {
    if (!isSelectingMultiple) return this
    val normalized = code.uppercase()
    return copy(
        pendingCountryCodes = if (normalized in pendingCountryCodes) {
            pendingCountryCodes - normalized
        } else {
            pendingCountryCodes + normalized
        },
    )
}
