package fm.corus.android.ui.util

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.concurrent.TimeUnit

class DateUtilsTest {

    private val context: Context = mock()

    @Before fun setUp() {
        whenever(context.getString(fm.corus.android.R.string.relative_time_just_now)).thenReturn("just now")
    }

    private fun minutesAgo(n: Long) = Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(n))
    private fun hoursAgo(n: Long) = Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(n))
    private fun daysAgo(n: Long) = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(n))

    @Test fun `under a minute reads just now`() {
        assertEquals("just now", DateUtils.relativeTime(context, Date()))
    }

    @Test fun `minutes use m suffix`() {
        assertEquals("5m", DateUtils.relativeTime(context, minutesAgo(5)))
    }

    @Test fun `hours use h suffix`() {
        assertEquals("3h", DateUtils.relativeTime(context, hoursAgo(3)))
    }

    @Test fun `days use d suffix`() {
        assertEquals("2d", DateUtils.relativeTime(context, daysAgo(2)))
    }

    @Test fun `months use mo suffix distinct from minutes`() {
        val sixtyDays = DateUtils.relativeTime(context, daysAgo(60))
        assertEquals("2mo", sixtyDays)
    }
}
