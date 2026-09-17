package fm.corus.android.data.model

import java.util.Locale

data class ProfileMapCity(val cityId: String, val label: String) {
    companion object {
        fun fromPayload(raw: Map<String, Any?>?): ProfileMapCity? {
            val cityId = raw?.get("cityId") as? String ?: return null
            val cityName = raw["cityName"] as? String ?: return null
            if (cityId.isEmpty() || cityName.isBlank()) return null
            val label = MapLocationLabel.title(
                cityName,
                raw["regionName"] as? String ?: "",
                raw["countryCode"] as? String ?: "",
            )
            if (label.isEmpty()) return null
            return ProfileMapCity(cityId, label)
        }

        fun fromPresence(data: Map<String, Any?>?): ProfileMapCity? {
            val audience = data?.get("audience") as? String ?: return null
            if (audience != "everyone" && audience != "following") return null
            return fromPayload(data)
        }
    }
}

object MapLocationLabel {
    fun title(city: String, region: String, countryCode: String): String {
        val name = city.trim()
        val regionName = region.trim()
        val code = countryCode.trim().uppercase(Locale.US)
        val country = if (code.isEmpty()) "" else Locale("", code).displayCountry.ifBlank { code }
        val context = if (code == "US" && regionName.isNotEmpty()) {
            val stateCode = regionName.uppercase(Locale.US).removePrefix("US-")
            US_STATES[stateCode] ?: regionName
        } else {
            country.ifEmpty { regionName }
        }
        if (name.isEmpty()) return context
        if (context.isEmpty() || name.equals(context, ignoreCase = true) ||
            name.lowercase(Locale.US).endsWith(", ${context.lowercase(Locale.US)}")
        ) return name
        return "$name, $context"
    }

    private val US_STATES = mapOf(
        "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas",
        "CA" to "California", "CO" to "Colorado", "CT" to "Connecticut", "DE" to "Delaware",
        "FL" to "Florida", "GA" to "Georgia", "HI" to "Hawaii", "ID" to "Idaho",
        "IL" to "Illinois", "IN" to "Indiana", "IA" to "Iowa", "KS" to "Kansas",
        "KY" to "Kentucky", "LA" to "Louisiana", "ME" to "Maine", "MD" to "Maryland",
        "MA" to "Massachusetts", "MI" to "Michigan", "MN" to "Minnesota", "MS" to "Mississippi",
        "MO" to "Missouri", "MT" to "Montana", "NE" to "Nebraska", "NV" to "Nevada",
        "NH" to "New Hampshire", "NJ" to "New Jersey", "NM" to "New Mexico", "NY" to "New York",
        "NC" to "North Carolina", "ND" to "North Dakota", "OH" to "Ohio", "OK" to "Oklahoma",
        "OR" to "Oregon", "PA" to "Pennsylvania", "RI" to "Rhode Island", "SC" to "South Carolina",
        "SD" to "South Dakota", "TN" to "Tennessee", "TX" to "Texas", "UT" to "Utah",
        "VT" to "Vermont", "VA" to "Virginia", "WA" to "Washington", "WV" to "West Virginia",
        "WI" to "Wisconsin", "WY" to "Wyoming", "DC" to "District of Columbia",
    )
}
