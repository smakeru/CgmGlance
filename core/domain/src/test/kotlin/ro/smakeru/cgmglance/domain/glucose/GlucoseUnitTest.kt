package ro.smakeru.cgmglance.domain.glucose

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlucoseUnitTest {
    @Test
    fun `recognises the exact label the LinX notification emits`() {
        // Captured from com.microtech.aidexx.mgdl 2.5.0, view id tv_unit.
        assertEquals(GlucoseUnit.MG_DL, GlucoseUnit.fromLabel("mg/dL"))
    }

    @Test
    fun `tolerates casing spacing and a trailing period`() {
        val variants = listOf("MG/DL", " mg / dl ", "mg/dl.", "mgdl")
        variants.forEach { variant ->
            assertEquals(GlucoseUnit.MG_DL, GlucoseUnit.fromLabel(variant), "failed for '$variant'")
        }
    }

    @Test
    fun `recognises the millimolar label used outside mg per dL markets`() {
        listOf("mmol/L", "MMOL/L", "mmol / l", "mmol").forEach { variant ->
            assertEquals(GlucoseUnit.MMOL_L, GlucoseUnit.fromLabel(variant), "failed for '$variant'")
        }
    }

    @Test
    fun `returns null for an unknown label instead of guessing a default`() {
        // A wrong guess silently reinterprets the scale, so there is no safe default here.
        assertNull(GlucoseUnit.fromLabel("mg%"))
        assertNull(GlucoseUnit.fromLabel(""))
        assertNull(GlucoseUnit.fromLabel(null))
    }

    @Test
    fun `decimals reflect how each scale is conventionally displayed`() {
        assertEquals(0, GlucoseUnit.MG_DL.decimals)
        assertEquals(1, GlucoseUnit.MMOL_L.decimals)
    }

    @Test
    fun `converts a known clinical value between scales`() {
        // 100 mg/dL is 5.55 mmol/L by the standard factor.
        assertTrue(abs(GlucoseConversion.mgdlToMmol(100) - 5.5499) < 0.001)
    }

    @Test
    fun `round trips through mmol without drifting`() {
        // Repeated conversion must not creep downwards, which is why mmolToMgdl rounds
        // to nearest rather than truncating.
        var value = 246
        repeat(20) {
            value = GlucoseConversion.mmolToMgdl(GlucoseConversion.mgdlToMmol(value))
        }
        assertEquals(246, value)
    }

    @Test
    fun `covers the full physiologically plausible range without drift`() {
        (20..600).forEach { mgdl ->
            val roundTripped = GlucoseConversion.mmolToMgdl(GlucoseConversion.mgdlToMmol(mgdl))
            assertEquals(mgdl, roundTripped, "round trip changed $mgdl")
        }
    }
}
