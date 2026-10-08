package fm.corus.android.ui.util

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import fm.corus.android.localization.CorusStrings
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DateUtilsLocalizationTest {
    private fun localizedContext(tag: String): Context {
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        }
        return app.createConfigurationContext(config)
    }

    @Test fun `all supported locales format older posts from this year and previous years`() {
        for (tag in listOf("en", "pt-BR", "es", "ja", "zh-Hans", "de", "fr", "ko", "it")) {
            val context = localizedContext(tag)
            val locale = context.resources.configuration.locales[0]
            val thisYear = Calendar.getInstance().apply {
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 2)
            }.time
            val lastYear = Calendar.getInstance().apply {
                time = thisYear
                add(Calendar.YEAR, -1)
            }.time
            for ((date, resource) in listOf(
                thisYear to CorusStrings.post_time_date_format_same_year,
                lastYear to CorusStrings.post_time_date_format_other_year,
            )) {
                // Constructing the formatter independently also rejects bad bundled patterns.
                val expected = SimpleDateFormat(context.getString(resource), locale).format(date)
                assertEquals(tag, expected, DateUtils.relativeTimeLong(context, date))
            }
        }
    }

    @Test fun `Portuguese and Spanish dates preserve literal de`() {
        for (tag in listOf("pt-BR", "es")) {
            val context = localizedContext(tag)
            val date = Calendar.getInstance().apply {
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 2)
            }.time
            val month = if (tag == "pt-BR") "janeiro" else "enero"
            assertEquals("2 de $month", DateUtils.relativeTimeLong(context, date))
        }
    }

    @Test fun `malformed same year and previous year patterns fall back without crashing`() {
        val realContext = localizedContext("pt-BR")
        val context: Context = mock()
        whenever(context.resources).thenReturn(realContext.resources)
        whenever(context.getString(CorusStrings.post_time_date_format_same_year)).thenReturn("d ’de’ MMMM")
        whenever(context.getString(CorusStrings.post_time_date_format_other_year)).thenReturn("d 'de MMMM yyyy")
        for (yearOffset in listOf(0, -1)) {
            val date: Date = Calendar.getInstance().apply {
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 2)
                add(Calendar.YEAR, yearOffset)
            }.time
            val expected = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag("pt-BR")).format(date)
            assertEquals(expected, DateUtils.relativeTimeLong(context, date))
        }
    }
}
