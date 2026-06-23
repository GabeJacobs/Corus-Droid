package fm.corus.android.i18n

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LanguageManagerTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        context.getSharedPreferences("corus_locale", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Test
    fun `fromTag pt-BR returns PORTUGUESE_BR`() {
        assertEquals(AppLanguage.PORTUGUESE_BR, AppLanguage.fromTag("pt-BR"))
    }

    @Test
    fun `fromTag pt returns PORTUGUESE_BR`() {
        assertEquals(AppLanguage.PORTUGUESE_BR, AppLanguage.fromTag("pt"))
    }

    @Test
    fun `fromTag en returns ENGLISH`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
    }

    @Test
    fun `fromTag es returns SPANISH`() {
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag("es"))
    }

    @Test
    fun `fromTag es_419 returns SPANISH`() {
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag("es_419"))
    }

    @Test
    fun `SPANISH tag is es`() {
        assertEquals("es", AppLanguage.SPANISH.tag)
    }

    @Test
    fun `fromTag maps the new locales`() {
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromTag("ja"))
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.fromTag("zh-Hans"))
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.fromTag("zh"))
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.fromTag("zh-CN"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromTag("de"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTag("fr"))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromTag("ko"))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.fromTag("it"))
    }

    @Test
    fun `new locale tags are correct`() {
        assertEquals("ja", AppLanguage.JAPANESE.tag)
        assertEquals("zh-Hans", AppLanguage.CHINESE_SIMPLIFIED.tag)
        assertEquals("de", AppLanguage.GERMAN.tag)
        assertEquals("fr", AppLanguage.FRENCH.tag)
        assertEquals("ko", AppLanguage.KOREAN.tag)
        assertEquals("it", AppLanguage.ITALIAN.tag)
    }

    @Test
    fun `wrapContext applies japanese locale when persisted`() {
        context.getSharedPreferences("corus_locale", Context.MODE_PRIVATE)
            .edit().putString("tag", "ja").apply()
        val wrapped = LanguageManager.wrapContext(context)
        assertEquals("ja", wrapped.resources.configuration.locales[0].language)
    }

    @Test
    fun `fromTag null returns SYSTEM`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
    }

    @Test
    fun `fromTag empty returns SYSTEM`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
    }

    @Test
    fun `current returns SYSTEM when nothing persisted`() {
        assertEquals(AppLanguage.SYSTEM, LanguageManager.current(context))
    }

    @Test
    fun `current reflects persisted pt-BR tag`() {
        context.getSharedPreferences("corus_locale", Context.MODE_PRIVATE)
            .edit().putString("tag", "pt-BR").apply()
        assertEquals(AppLanguage.PORTUGUESE_BR, LanguageManager.current(context))
    }

    @Test
    fun `wrapContext returns base when no language persisted`() {
        val wrapped = LanguageManager.wrapContext(context)
        // No persisted locale → no wrapping needed; base context is returned directly.
        assertEquals(context, wrapped)
    }

    @Test
    fun `wrapContext applies pt-BR locale to configuration when persisted`() {
        context.getSharedPreferences("corus_locale", Context.MODE_PRIVATE)
            .edit().putString("tag", "pt-BR").apply()
        val wrapped = LanguageManager.wrapContext(context)
        val locale = wrapped.resources.configuration.locales[0]
        assertEquals("pt", locale.language)
        assertEquals("BR", locale.country)
    }

    @Test
    fun `wrapContext applies english locale when persisted`() {
        context.getSharedPreferences("corus_locale", Context.MODE_PRIVATE)
            .edit().putString("tag", "en").apply()
        val wrapped = LanguageManager.wrapContext(context)
        assertEquals("en", wrapped.resources.configuration.locales[0].language)
    }

    @Test
    fun `current reflects persisted es tag`() {
        context.getSharedPreferences("corus_locale", Context.MODE_PRIVATE)
            .edit().putString("tag", "es").apply()
        assertEquals(AppLanguage.SPANISH, LanguageManager.current(context))
    }

    @Test
    fun `wrapContext applies spanish locale when persisted`() {
        context.getSharedPreferences("corus_locale", Context.MODE_PRIVATE)
            .edit().putString("tag", "es").apply()
        val wrapped = LanguageManager.wrapContext(context)
        assertEquals("es", wrapped.resources.configuration.locales[0].language)
    }
}
