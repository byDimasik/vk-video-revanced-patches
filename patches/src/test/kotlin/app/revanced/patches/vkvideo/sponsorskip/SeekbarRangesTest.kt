package app.revanced.patches.vkvideo.sponsorskip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val DURATION = 1_000_000L
private val SHOWN = IntArray(Category.COUNT) { Behavior.SHOW_SKIP_BUTTON }

private fun segment(startMs: Long, endMs: Long, category: Int) = Segment(startMs, endMs, "s", category)

class SeekbarRangesTest {

    @Test
    fun `a segment maps onto the track fractions`() {
        val bands = SeekbarRanges.build(
            listOf(
                segment(0, 100_000, Category.SPONSOR),
                segment(500_000, 600_000, Category.SELFPROMO),
                segment(900_000, DURATION, Category.OUTRO),
            ),
            DURATION,
        )

        assertEquals(0f, bands[0].xStart)
        assertEquals(0.1f, bands[0].xEnd)
        assertEquals(0.5f, bands[1].xStart)
        assertEquals(0.6f, bands[1].xEnd)
        assertEquals(0.9f, bands[2].xStart)
        assertEquals(1f, bands[2].xEnd)
    }

    @Test
    fun `starts before and ends after the duration clamp into the track`() {
        val bands = SeekbarRanges.build(listOf(segment(-5_000, DURATION + 5_000, Category.INTRO)), DURATION)

        assertEquals(0f, bands.single().xStart)
        assertEquals(1f, bands.single().xEnd)
    }

    @Test
    fun `overlapping segments stack into lanes and a fourth reuses the last lane`() {
        val bands = SeekbarRanges.build(
            listOf(
                segment(0, 400_000, Category.SPONSOR),
                segment(100_000, 500_000, Category.SELFPROMO),
                segment(200_000, 600_000, Category.INTRO),
                segment(300_000, 700_000, Category.OUTRO),
            ),
            DURATION,
        )

        assertEquals(listOf(0, 1, 2, 2), bands.map { it.lane })
        assertTrue(bands[3].xStart > bands[2].xStart, "the reused lane starts after its neighbour")
    }

    @Test
    fun `the master switch and a disabled category hide bands`() {
        val bands = SeekbarRanges.build(
            listOf(
                segment(0, 100_000, Category.SPONSOR),
                segment(200_000, 300_000, Category.SELFPROMO),
            ),
            DURATION,
        )

        assertTrue(SeekbarRanges.visible(bands, SHOWN, false).isEmpty())

        val behaviors = SHOWN.copyOf()
        behaviors[Category.SELFPROMO] = Behavior.DISABLED
        assertEquals(listOf(Category.SPONSOR), SeekbarRanges.visible(bands, behaviors, true).map { it.category })
    }

    @Test
    fun `degenerate inputs draw nothing`() {
        assertTrue(SeekbarRanges.build(emptyList(), DURATION).isEmpty())
        assertTrue(SeekbarRanges.build(listOf(segment(0, 1, Category.SPONSOR)), 0).isEmpty())
    }

    @Test
    fun `an unclassified segment is skipped`() {
        val bands = SeekbarRanges.build(
            listOf(segment(0, 100_000, -1), segment(200_000, 300_000, Category.SPONSOR)),
            DURATION,
        )

        assertEquals(listOf(Category.SPONSOR), bands.map { it.category })
    }

    @Test
    fun `a band at the very end stays inside the track`() {
        val bands = SeekbarRanges.build(listOf(segment(DURATION, DURATION, Category.SPONSOR)), DURATION)

        assertEquals(1f - SeekbarRanges.GAP, bands.single().xStart, 1e-6f)
        assertEquals(1f, bands.single().xEnd)
    }

    @Test
    fun `a zero-length band still gets the minimum gap`() {
        val bands = SeekbarRanges.build(listOf(segment(500_000, 500_000, Category.SPONSOR)), DURATION)

        assertEquals(SeekbarRanges.GAP, bands.single().xEnd - bands.single().xStart, 1e-6f)
    }
}
