package fm.corus.android.data.local

import android.content.ContentResolver
import android.provider.ContactsContract

/**
 * Reads every phone number from the device contacts, stripped to digits and a
 * leading '+'. Shared by the onboarding, search, and settings sync flows so
 * all three normalize numbers identically (the backend matches on these).
 */
fun readContactPhoneNumbers(contentResolver: ContentResolver): List<String> {
    val numbers = mutableSetOf<String>()
    try {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )
        cursor?.use {
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val number = it.getString(numberIndex)?.replace(Regex("[^+\\d]"), "")
                if (!number.isNullOrBlank()) numbers.add(number)
            }
        }
    } catch (_: Exception) { }
    return numbers.toList()
}
