package fm.corus.android.ui.screens.search

internal fun popularConcertCitySubtitle(id: String): String = when (id) {
    "new-york-us" -> "NY, US"
    "geonames-5368361", "geonames-5391959" -> "CA, US"
    "geonames-4887398" -> "IL, US"
    "geonames-4164138" -> "FL, US"
    "geonames-4671654" -> "TX, US"
    "geonames-4180439" -> "GA, US"
    "geonames-4930956" -> "MA, US"
    "geonames-5809844" -> "WA, US"
    "geonames-6167865" -> "ON, Canada"
    "geonames-3530597" -> "Mexico"
    "geonames-2643743" -> "United Kingdom"
    "geonames-2988507" -> "France"
    "geonames-2950159" -> "Germany"
    "geonames-2147714" -> "NSW, Australia"
    "geonames-1850147" -> "Japan"
    else -> ""
}
