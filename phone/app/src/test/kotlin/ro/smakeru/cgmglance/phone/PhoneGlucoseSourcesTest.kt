package ro.smakeru.cgmglance.phone

import org.junit.jupiter.api.Test
import ro.smakeru.cgmglance.domain.glucose.GlucoseReading
import ro.smakeru.cgmglance.domain.glucose.TrendArrow
import ro.smakeru.cgmglance.domain.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneGlucoseSourcesTest {
    private val start = 1_785_738_000_000L
    private var now = start
    private val clock = Clock { now }
    private val linx = GlucoseSource.LINX
    private val dexcom = GlucoseSource.DEXCOM_ONE_PLUS
    private val repositories = GlucoseSource.entries.associateWith { PhoneGlucoseRepository(clock) }
    private val sources = PhoneGlucoseSources(repositories)

    private fun reading(
        mgdl: Int,
        offset: Long = 0L,
    ): GlucoseReading = requireNotNull(GlucoseReading.create(mgdl, start + offset))

    private fun record(
        source: GlucoseSource,
        mgdl: Int,
        offset: Long = 0L,
    ): PhoneGlucoseRepository.Outcome? {
        now = start + offset
        return sources.record(source, reading(mgdl, offset), usedFallback = false)
    }

    @Test
    fun `missing or unrecognised preference preserves LinX priority`() {
        assertEquals(linx, GlucoseSource.fromStored(null))
        assertEquals(linx, GlucoseSource.fromStored("future-source"))
        assertEquals(dexcom, GlucoseSource.fromStored("DEXCOM_ONE_PLUS"))
    }

    @Test
    fun `Dexcom arriving first cannot replace default LinX`() {
        assertNull(record(dexcom, 230))
        assertEquals(linx, sources.snapshot().source)
        assertNull(sources.snapshot().latest)
        val sent = assertNotNull(record(linx, 140, 1_000)?.payload)
        assertEquals(140, sent.reading.mgdl)
    }

    @Test
    fun `Dexcom after LinX never wins by notification order`() {
        record(linx, 140)
        assertNull(record(dexcom, 230, 1_000))
        assertEquals(140, sources.snapshot().latest?.mgdl)
        assertEquals(230, repositories.getValue(dexcom).latest?.mgdl)
    }

    @Test
    fun `silence of LinX is not permission to switch silently`() {
        record(linx, 140)
        assertNull(record(dexcom, 230, 30 * 60_000L))
        assertEquals(linx, sources.snapshot().source)
        assertEquals(start, sources.snapshot().latest?.timestampMillis)
    }

    @Test
    fun `explicit switch sends cached Dexcom with its original age`() {
        record(linx, 140)
        record(dexcom, 230, 60_000)
        now += 60_000
        val switched = assertNotNull(sources.select(dexcom))
        val sent = assertNotNull(switched.payload)
        assertTrue(switched.urgent)
        assertEquals(230, sent.reading.mgdl)
        assertEquals(start + 60_000, sent.reading.timestampMillis)
        assertEquals(1, repositories.getValue(dexcom).readings.size)
        assertNull(record(linx, 141, 180_000))
        assertEquals(230, sources.snapshot().latest?.mgdl)
    }

    @Test
    fun `switching back republishes an unchanged already sent value`() {
        record(linx, 140)
        record(dexcom, 140)
        sources.select(dexcom)
        val back = assertNotNull(sources.select(linx)?.payload)
        assertEquals(140, back.reading.mgdl)
        assertEquals(start, back.reading.timestampMillis)
        assertEquals(1, repositories.getValue(linx).readings.size)
        assertNull(sources.select(linx))
    }

    @Test
    fun `a source without data waits rather than relabelling another source`() {
        record(linx, 140)
        val switched = assertNotNull(sources.select(dexcom))
        assertNull(switched.payload)
        assertNull(sources.snapshot().latest)
        val sent = assertNotNull(record(dexcom, 220, 60_000)?.payload)
        assertEquals(220, sent.reading.mgdl)
    }

    @Test
    fun `a stored Dexcom choice is respected after restart`() {
        val restarted = PhoneGlucoseSources(repositories, initialSource = dexcom)
        assertNull(restarted.record(linx, reading(140), false))
        val sent = assertNotNull(restarted.record(dexcom, reading(220), false)?.payload)
        assertEquals(220, sent.reading.mgdl)
    }

    @Test
    fun `histories and trends stay independent during simultaneous acquisition`() {
        for (minute in listOf(0, 5, 10)) {
            record(linx, 140, minute * 60_000L)
            record(dexcom, 220 + minute * 3, minute * 60_000L)
        }
        val rising = assertNotNull(sources.select(dexcom)?.payload)
        assertTrue(rising.trend != TrendArrow.STEADY && rising.trend != TrendArrow.UNKNOWN)
        val steady = assertNotNull(sources.select(linx)?.payload)
        assertEquals(TrendArrow.STEADY, steady.trend)
        assertEquals(listOf(140, 140, 140), repositories.getValue(linx).readings.map { it.mgdl })
        assertEquals(listOf(220, 235, 250), repositories.getValue(dexcom).readings.map { it.mgdl })
    }

    @Test
    fun `a replay cannot move a source backwards or duplicate its history`() {
        record(linx, 140, 60_000)
        assertNull(sources.record(linx, reading(130), false))
        assertNull(sources.record(linx, reading(140, 60_000), false))
        assertEquals(140, sources.snapshot().latest?.mgdl)
        assertEquals(1, repositories.getValue(linx).readings.size)
    }

    @Test
    fun `fallback diagnostics belong to the selected source only`() {
        record(linx, 140)
        sources.record(dexcom, reading(220), usedFallback = true)
        assertEquals(false, sources.snapshot().usedFallbackParser)
        sources.select(dexcom)
        assertEquals(true, sources.snapshot().usedFallbackParser)
    }

    @Test
    fun `a fixed Dexcom widget cannot change the watch source`() {
        record(linx, 140)
        record(dexcom, 220)
        val widget = sources.snapshot(dexcom)
        assertEquals(dexcom, widget.source)
        assertEquals(220, widget.latest?.mgdl)
        assertEquals(linx, sources.snapshot().source)
        assertEquals(140, sources.snapshot().latest?.mgdl)
    }

    @Test
    fun `parser failures do not cross sources or refresh the reading`() {
        record(linx, 140)
        sources.recordFailure(dexcom, "UNIT_MISSING")
        assertNull(sources.snapshot().parseFailure)
        assertEquals("UNIT_MISSING", sources.snapshot(dexcom).parseFailure)
        sources.recordFailure(linx, "NO_VALUE_FOUND")
        assertEquals(start, sources.snapshot().latest?.timestampMillis)
        assertEquals("NO_VALUE_FOUND", sources.snapshot().parseFailure)
        record(linx, 141, 60_000)
        assertNull(sources.snapshot().parseFailure)
    }

    @Test
    fun `restored histories stay isolated and retain the selected source`() {
        val persisted =
            GlucoseSource.entries.associateWith { source ->
                object : HistoryPersistence {
                    override fun load() = listOf(reading(if (source == linx) 140 else 220))

                    override fun save(readings: List<GlucoseReading>) = Unit
                }
            }
        val restoredRepositories =
            GlucoseSource.entries.associateWith {
                PhoneGlucoseRepository(clock, persistence = persisted.getValue(it))
            }
        val restored = PhoneGlucoseSources(restoredRepositories, initialSource = dexcom)
        assertEquals(220, restored.snapshot().latest?.mgdl)
        val sent = assertNotNull(restored.select(linx)?.payload)
        assertEquals(140, sent.reading.mgdl)
    }
}
