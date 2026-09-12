package ro.smakeru.cgmglance.phone

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HealthExportSettingsTest {
    @Test
    fun `the default is on, every fifteen minutes`() {
        assertTrue(HealthExportSettings.default.enabled)
        assertEquals(15, HealthExportSettings.default.intervalMinutes)
    }

    @Test
    fun `an interval below the floor is refused`() {
        // Rejected rather than clamped, so the settings screen can say why Save is disabled.
        assertNull(HealthExportSettings.create(enabled = true, intervalMinutes = 4))
    }

    @Test
    fun `an interval above the ceiling is refused`() {
        assertNull(HealthExportSettings.create(enabled = true, intervalMinutes = 181))
    }

    @Test
    fun `a nonsense interval is refused`() {
        // What an empty or unparseable field becomes on the settings screen.
        assertNull(HealthExportSettings.create(enabled = true, intervalMinutes = -1))
        assertNull(HealthExportSettings.create(enabled = true, intervalMinutes = 0))
    }

    @Test
    fun `the bounds themselves are allowed`() {
        assertNotNull(HealthExportSettings.create(enabled = true, intervalMinutes = 5))
        assertNotNull(HealthExportSettings.create(enabled = true, intervalMinutes = 180))
    }

    @Test
    fun `the interval is validated even when the export is off`() {
        // Otherwise an out of range value could be stored while disabled and then silently
        // honoured the moment someone ticked the box.
        assertNull(HealthExportSettings.create(enabled = false, intervalMinutes = 1))
    }

    @Test
    fun `minutes convert to the milliseconds the policy measures with`() {
        val settings = assertNotNull(HealthExportSettings.create(enabled = true, intervalMinutes = 20))
        assertEquals(20 * 60 * 1000L, settings.intervalMillis)
    }
}
