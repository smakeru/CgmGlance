package ro.smakeru.cgmglance.phone

import org.junit.jupiter.api.Test
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PhoneGlucoseRepositoryTest {
    private val start = 1_785_738_000_000L
    private var now = start

    private val repository = PhoneGlucoseRepository(clock = Clock { now })

    private fun record(
        mgdl: Int,
        millisAfter: Long,
    ): PhoneGlucoseRepository.Outcome {
        now = start + millisAfter
        return repository.record(requireNotNull(GlucoseReading.create(mgdl, now)))
    }

    @Test
    fun `the first reading is transmitted immediately`() {
        val outcome = record(148, 0)

        assertNotNull(outcome.payload)
        assertEquals(true, outcome.urgent)
        assertEquals(148, outcome.payload.reading.mgdl)
    }

    @Test
    fun `a burst of reposts collapses into a single entry`() {
        // Taken from a real device: LinX republishes the same value four times within a few
        // milliseconds, each repost carrying its own postTime.
        record(148, 0)
        record(148, 3)
        record(148, 5)
        record(148, 420)

        assertEquals(1, repository.readings.size)
        // The latest confirmation wins, because freshness is measured against it.
        assertEquals(start + 420, repository.latest?.timestampMillis)
    }

    @Test
    fun `a burst does not consume the whole window`() {
        // Four reposts inside half a second collapse to one, and the window is still measured
        // from the first of them.
        record(148, 0)
        record(148, 120)
        record(148, 300)
        record(148, 480)

        assertEquals(1, repository.readings.size)
    }

    @Test
    fun `an unchanging value still accumulates points for the trend fit`() {
        // The regression that failed in the field: with the window measured from the entry
        // last stored, reposts arriving closer together than the window slide it forward
        // forever, so an unchanging glucose collapses into a single entry and never produces
        // an arrow. Steady is exactly when an arrow is worth showing.
        //
        // Fifteen minutes of reposts every twenty seconds, value never moving.
        repeat(45) { index -> record(148, index * 20_000L) }

        val stored = repository.readings

        // Enough points to fit a line, which is the property that matters. The exact count
        // depends on how the source's rhythm lands against the window and is not worth
        // pinning down.
        assertEquals(true, stored.size >= 3, "only ${stored.size} points stored")

        // No two stored points are closer together than the repost window, so bursts cannot
        // crowd the fit with points that share an x coordinate.
        stored.zipWithNext { earlier, later ->
            assertEquals(
                true,
                later.timestampMillis - earlier.timestampMillis >= PhoneGlucoseRepository.REPOST_WINDOW_MILLIS,
                "points only ${later.timestampMillis - earlier.timestampMillis} ms apart",
            )
        }

        // And the buffer stays proportionate: far fewer entries than reposts.
        assertEquals(true, stored.size < 45 / 2, "collapsing did not reduce the buffer enough")
    }

    @Test
    fun `a changed value is a new entry even seconds later`() {
        record(148, 0)
        record(147, 20_000)

        assertEquals(2, repository.readings.size)
    }

    @Test
    fun `an unchanged value after the repost window is a genuine new measurement`() {
        record(148, 0)
        record(148, 90_000)

        assertEquals(2, repository.readings.size)
    }

    @Test
    fun `reposts are not transmitted to the watch`() {
        assertNotNull(record(148, 0).payload)

        // Nothing the watch would notice has changed, so the radio stays quiet.
        assertNull(record(148, 3).payload)
        assertNull(record(148, 20_000).payload)
    }

    @Test
    fun `readings older than the window are dropped`() {
        record(148, 0)
        record(150, 60_000)
        record(152, PhoneGlucoseRepository.HISTORY_WINDOW_MILLIS + 120_000)

        assertEquals(1, repository.readings.size)
        assertEquals(152, repository.latest?.mgdl)
    }

    /** In-memory stand-in for the on-disk store. */
    private class FakePersistence(
        var stored: List<GlucoseReading> = emptyList(),
    ) : HistoryPersistence {
        var saveCount = 0

        override fun load(): List<GlucoseReading> = stored

        override fun save(readings: List<GlucoseReading>) {
            stored = readings
            saveCount++
        }
    }

    @Test
    fun `history survives a restart so the trend does not blank`() {
        // The failure this exists to prevent, seen on a real device: after the process was
        // replaced the arrow vanished for about seven minutes while the fit gathered points
        // again. Android reclaims apps routinely, so this is a weekly event, not a rare one.
        val disk = FakePersistence()
        val first = PhoneGlucoseRepository(clock = Clock { now }, persistence = disk)

        now = start
        first.record(requireNotNull(GlucoseReading.create(148, now)))
        now = start + 60_000
        first.record(requireNotNull(GlucoseReading.create(150, now)))
        now = start + 120_000
        first.record(requireNotNull(GlucoseReading.create(152, now)))

        val restarted = PhoneGlucoseRepository(clock = Clock { now }, persistence = disk)

        assertEquals(3, restarted.readings.size)
        assertEquals(152, restarted.latest?.mgdl)
    }

    @Test
    fun `readings older than the window are not restored`() {
        // A phone that was off for hours holds readings that are useless to the fit and
        // actively misleading: a regression across that gap looks confident and means nothing.
        val stale = requireNotNull(GlucoseReading.create(148, start - PhoneGlucoseRepository.HISTORY_WINDOW_MILLIS - 1))
        val fresh = requireNotNull(GlucoseReading.create(150, start))
        val disk = FakePersistence(listOf(stale, fresh))

        now = start
        val restored =
            PhoneGlucoseRepository(
                clock = Clock { now },
                persistence =
                    object : HistoryPersistence {
                        override fun load() =
                            disk.stored.filter {
                                it.timestampMillis >=
                                    now - PhoneGlucoseRepository.HISTORY_WINDOW_MILLIS
                            }

                        override fun save(readings: List<GlucoseReading>) = disk.save(readings)
                    },
            )

        assertEquals(1, restored.readings.size)
        assertEquals(150, restored.latest?.mgdl)
    }

    @Test
    fun `collapsed reposts do not each cause a write`() {
        // Reposts arrive every twenty seconds. Persisting each one would mean thousands of
        // disk writes a day to preserve a timestamp the next reading replaces anyway.
        val disk = FakePersistence()
        val repo = PhoneGlucoseRepository(clock = Clock { now }, persistence = disk)

        now = start
        repo.record(requireNotNull(GlucoseReading.create(148, now)))
        val afterFirst = disk.saveCount

        listOf(3L, 20_000L, 40_000L).forEach { offset ->
            now = start + offset
            repo.record(requireNotNull(GlucoseReading.create(148, now)))
        }

        assertEquals(afterFirst, disk.saveCount, "a collapsed repost triggered a write")
    }

    @Test
    fun `the sequence never restarts from zero`() {
        // The Data Layer ignores a payload identical to one it already holds. A counter that
        // restarted at zero after a process death would replay numbers the watch has seen, and
        // those readings would silently fail to arrive.
        val outcome = record(148, 0)

        assertNotNull(outcome.payload)
        assertEquals(true, outcome.payload.sequence > 1_000_000L)
    }

    @Test
    fun `payload includes delta when enough history exists`() {
        record(140, 0)
        val outcome = record(146, 10 * 60_000L)

        assertNotNull(outcome.payload)
        assertEquals(6, outcome.payload.deltaMgdl)
        assertNotNull(repository.delta)
        assertEquals(6, repository.delta?.deltaMgdl)
    }
}
