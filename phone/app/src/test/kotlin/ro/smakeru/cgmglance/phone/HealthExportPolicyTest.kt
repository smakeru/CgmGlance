package ro.smakeru.cgmglance.phone

import org.junit.jupiter.api.Test
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HealthExportPolicyTest {
    private val policy = HealthExportPolicy()
    private val base = 1_785_662_760_000L
    private val minute = 60_000L

    private fun reading(
        mgdl: Int,
        minutesAfter: Long,
    ) = requireNotNull(GlucoseReading.create(mgdl, base + minutesAfter * minute))

    private fun at(minutesAfter: Long) = base + minutesAfter * minute

    @Test
    fun `the first reading is written immediately`() {
        // So a user who has just granted permission sees it work, rather than waiting a
        // quarter of an hour to find out whether they set it up correctly.
        assertTrue(policy.shouldWrite(reading(112, 0), lastWrittenMillis = null))
    }

    @Test
    fun `a reading inside the interval is not written`() {
        // The common case: LinX delivers roughly once a minute, and fourteen of every fifteen
        // of those must not reach the health log.
        assertFalse(policy.shouldWrite(reading(118, 7), at(0)))
    }

    @Test
    fun `a reading exactly on the interval is written`() {
        assertTrue(policy.shouldWrite(reading(118, 15), at(0)))
    }

    @Test
    fun `a reading past the interval is written`() {
        assertTrue(policy.shouldWrite(reading(118, 16), at(0)))
    }

    @Test
    fun `an unchanged value is still written once the interval has passed`() {
        // A steady glucose is a real measurement, not a repeat to be collapsed. Skipping it
        // would leave a gap in the log exactly when the reading is most reassuring.
        assertTrue(policy.shouldWrite(reading(112, 15), at(0)))
    }

    @Test
    fun `a replayed older reading is never written`() {
        // A rebind can hand back a notification the app has already seen. Writing it would put
        // a record behind one already in the log, and would then make the next in-order
        // reading look as though it had satisfied the interval.
        assertFalse(policy.shouldWrite(reading(104, 3), at(10)))
    }

    @Test
    fun `a reading with the same timestamp as the last write is not written twice`() {
        assertFalse(policy.shouldWrite(reading(112, 10), at(10)))
    }

    @Test
    fun `the interval is measured from the last write, not from the first reading`() {
        // Writes at 0 and 15 must not make a reading at 20 eligible just because 20 minutes
        // have passed since the run began.
        assertFalse(policy.shouldWrite(reading(120, 20), at(15)))
        assertTrue(policy.shouldWrite(reading(120, 30), at(15)))
    }

    @Test
    fun `a custom interval is honoured`() {
        val everyThirty = HealthExportPolicy { 30 * minute }
        assertFalse(everyThirty.shouldWrite(reading(118, 20), at(0)))
        assertTrue(everyThirty.shouldWrite(reading(118, 30), at(0)))
    }

    @Test
    fun `a shortened interval takes effect on the next reading`() {
        // The supplier is read per reading, not captured once, so an edit in settings applies
        // immediately rather than at the next restart.
        var minutes = 30
        val policy = HealthExportPolicy { minutes * minute }

        assertFalse(policy.shouldWrite(reading(118, 20), at(0)))
        minutes = 15
        assertTrue(policy.shouldWrite(reading(118, 20), at(0)))
    }

    @Test
    fun `a long gap in readings does not queue up several writes`() {
        // The phone was off for two hours. The reading that arrives afterwards is written once,
        // and the interval restarts from it — there is no backlog to catch up on, because the
        // readings for those two hours do not exist.
        assertTrue(policy.shouldWrite(reading(140, 120), at(0)))
        assertFalse(policy.shouldWrite(reading(141, 121), at(120)))
    }
}
