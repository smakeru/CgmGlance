package ro.smakeru.cgmglance.domain.sync

import ro.smakeru.cgmglance.domain.glucose.GlucoseRange
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncDecisionPolicyTest {
    private val policy = SyncDecisionPolicy()
    private val base = 1_785_662_760_000L
    private val minute = 60_000L

    private fun reading(
        mgdl: Int,
        minutesAfter: Long,
    ) = requireNotNull(GlucoseReading.create(mgdl, base + minutesAfter * minute))

    private fun sent(
        mgdl: Int,
        minutesAfter: Long,
        range: GlucoseRange = GlucoseRange.IN_RANGE,
    ) = LastSent(mgdl, base + minutesAfter * minute, range)

    @Test
    fun `the first reading always goes immediately`() {
        assertEquals(
            SyncDecision.SEND_URGENT,
            policy.decide(reading(112, 0), GlucoseRange.IN_RANGE, lastSent = null),
        )
    }

    @Test
    fun `a small change soon after the last send is not worth the radio`() {
        // This is where the saving comes from: a once-a-minute sensor drifting by a few points.
        assertEquals(
            SyncDecision.SKIP,
            policy.decide(reading(115, 1), GlucoseRange.IN_RANGE, sent(112, 0)),
        )
    }

    @Test
    fun `a significant change goes immediately`() {
        assertEquals(
            SyncDecision.SEND_URGENT,
            policy.decide(reading(122, 1), GlucoseRange.IN_RANGE, sent(112, 0)),
        )
    }

    @Test
    fun `crossing the range boundary goes immediately even for a tiny change`() {
        // 69 to 71 is two points, but it is the difference between "low" and "in range" on the
        // watch face, which is what the user actually reacts to.
        assertEquals(
            SyncDecision.SEND_URGENT,
            policy.decide(reading(71, 1), GlucoseRange.IN_RANGE, sent(69, 0, GlucoseRange.LOW)),
        )
    }

    @Test
    fun `a quiet stretch still refreshes so the value does not drift far behind`() {
        assertEquals(
            SyncDecision.SEND,
            policy.decide(reading(114, 5), GlucoseRange.IN_RANGE, sent(112, 0)),
        )
    }

    @Test
    fun `data resuming after an outage goes immediately`() {
        // The watch has been showing something stale or nothing at all.
        assertEquals(
            SyncDecision.SEND_URGENT,
            policy.decide(reading(113, 20), GlucoseRange.IN_RANGE, sent(112, 0)),
        )
    }

    @Test
    fun `a reading older than what the watch already holds is dropped`() {
        assertEquals(
            SyncDecision.SKIP,
            policy.decide(reading(200, 0), GlucoseRange.HIGH, sent(112, 5)),
        )
        assertEquals(
            SyncDecision.SKIP,
            policy.decide(reading(200, 5), GlucoseRange.HIGH, sent(112, 5)),
        )
    }

    @Test
    fun `a once a minute sensor produces far fewer transmissions than readings`() {
        // Twenty minutes of a slow, realistic drift. Counting the sends is the whole point:
        // this is the app's battery budget expressed as a test rather than as a hope.
        var last: LastSent? = sent(112, 0)
        var sends = 0
        val drift = listOf(113, 112, 114, 113, 115, 114, 116, 115, 117, 116, 118, 117, 119, 118, 120)

        drift.forEachIndexed { index, mgdl ->
            val candidate = reading(mgdl, (index + 1).toLong())
            val decision = policy.decide(candidate, GlucoseRange.IN_RANGE, last)
            if (decision != SyncDecision.SKIP) {
                sends++
                last = LastSent(candidate.mgdl, candidate.timestampMillis, GlucoseRange.IN_RANGE)
            }
        }

        assertEquals(3, sends, "15 readings should collapse into 3 transmissions")
    }
}
