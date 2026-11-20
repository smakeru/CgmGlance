package ro.smakeru.cgmglance.phone

import android.app.Application
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The language choice, on both sides of Android 13.
 *
 * Under Robolectric because the thing under test is which resources a context returns; the two
 * SDK levels are the two code paths, one owned by the platform and one by this app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppLanguageTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `a stored tag maps to a shipped language regardless of region`() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromTag("de"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromTag("de-AT"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTag("fr_CA"))
        assertEquals(AppLanguage.ROMANIAN, AppLanguage.fromTag("RO"))
    }

    @Test
    fun `anything unshipped or unreadable follows the system`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("es"))
    }

    @Test
    @Config(sdk = [30])
    fun `before Android 13 the choice is stored here and applied by wrapping`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguageController.current(context))
        assertSame(context, AppLanguageController.wrap(context), "system default must not wrap")

        AppLanguageController.apply(context, AppLanguage.GERMAN)

        assertEquals(AppLanguage.GERMAN, AppLanguageController.current(context))
        assertEquals("Speichern", AppLanguageController.wrap(context).getString(R.string.action_save))

        AppLanguageController.apply(context, AppLanguage.FRENCH)
        assertEquals("Enregistrer", AppLanguageController.wrap(context).getString(R.string.action_save))

        AppLanguageController.apply(context, AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM, AppLanguageController.current(context))
        assertEquals("Save", AppLanguageController.wrap(context).getString(R.string.action_save))
    }

    @Test
    @Config(sdk = [33])
    fun `from Android 13 the platform holds the choice`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguageController.current(context))

        AppLanguageController.apply(context, AppLanguage.ROMANIAN)
        assertEquals(AppLanguage.ROMANIAN, AppLanguageController.current(context))
        // The platform localises contexts itself; wrapping must stay out of its way.
        assertSame(context, AppLanguageController.wrap(context))

        AppLanguageController.apply(context, AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM, AppLanguageController.current(context))
    }

    @Test
    @Config(qualifiers = "de")
    fun `German resources resolve`() {
        assertEquals("Einstellungen", context.getString(R.string.settings_title))
        assertEquals("vor 3 Min.", context.resources.getQuantityString(R.plurals.age_minutes_ago, 3, 3))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `French resources resolve with their apostrophes intact`() {
        assertEquals("Paramètres", context.getString(R.string.settings_title))
        assertEquals("Unité d’affichage", context.getString(R.string.settings_display_unit))
        assertEquals("il y a 1 min", context.resources.getQuantityString(R.plurals.age_minutes_ago, 1, 1))
    }
}
