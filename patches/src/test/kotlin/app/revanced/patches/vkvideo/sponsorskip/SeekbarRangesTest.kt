package app.revanced.patches.vkvideo.sponsorskip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val DURATION = 1_000_000L
private val WIDTH = 900
private val SHOWN = IntArray(Category.COUNT) { Behavior.SHOW_SKIP_BUTTON }

private fun segment(startMs: Long, endMs: Long, category: Int) = Segment(startMs, endMs, "s", category)

class SeekbarRangesTest {

    @Test
    fun `a segment maps to the track endpoints and midpoints exactly`() {
        val bands = SeekbarRanges.build(
            listOf(
                segment(0, 100_000, Category.SPONSOR),
                segment(500_000, 600_000, Category.SELFPROMO),
                segment(900_000, DURATION, Category.OUTRO),
            ),
            SHOWN,
            true,
            DURATION,
            0,
            WIDTH,
        )

        assertEquals(0, bands[0].xStart)
        assertEquals(90, bands[0].xEnd)
        assertEquals(450, bands[1].xStart)
        assertEquals(540, bands[1].xEnd)
        assertEquals(810, bands[2].xStart)
        assertEquals(WIDTH, bands[2].xEnd)
    }

    @Test
    fun `starts before and ends after the duration clamp into the track`() {
        val bands = SeekbarRanges.build(
            listOf(segment(-5_000, DURATION + 5_000, Category.INTRO)),
            SHOWN,
            true,
            DURATION,
            0,
            WIDTH,
        )

        assertEquals(0, bands.single().xStart)
        assertEquals(WIDTH, bands.single().xEnd)
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
            SHOWN,
            true,
            DURATION,
            0,
            WIDTH,
        )

        assertEquals(listOf(0, 1, 2, 2), bands.map { it.lane })
        assertTrue(bands[3].xStart > bands[2].xStart, "the reused lane starts after its neighbour")
    }

    @Test
    fun `the master switch and a disabled category hide bands`() {
        val segments = listOf(
            segment(0, 100_000, Category.SPONSOR),
            segment(200_000, 300_000, Category.SELFPROMO),
        )

        assertTrue(SeekbarRanges.build(segments, SHOWN, false, DURATION, 0, WIDTH).isEmpty())

        val behaviors = IntArray(Category.COUNT) { Behavior.SHOW_SKIP_BUTTON }
        behaviors[Category.SELFPROMO] = Behavior.DISABLED
        val gated = SeekbarRanges.build(segments, behaviors, true, DURATION, 0, WIDTH)
        assertEquals(listOf(Category.SPONSOR), gated.map { it.category })
    }

    @Test
    fun `degenerate inputs draw nothing`() {
        assertTrue(SeekbarRanges.build(emptyList(), SHOWN, true, DURATION, 0, WIDTH).isEmpty())
        assertTrue(SeekbarRanges.build(listOf(segment(0, 1, Category.SPONSOR)), SHOWN, true, 0, 0, WIDTH).isEmpty())
        assertTrue(SeekbarRanges.build(listOf(segment(0, 1, Category.SPONSOR)), SHOWN, true, DURATION, 0, 0).isEmpty())
    }

    @Test
    fun `an unclassified segment is skipped`() {
        val bands = SeekbarRanges.build(
            listOf(segment(0, 100_000, -1), segment(200_000, 300_000, Category.SPONSOR)),
            SHOWN,
            true,
            DURATION,
            0,
            WIDTH,
        )

        assertEquals(listOf(Category.SPONSOR), bands.map { it.category })
    }

    @Test
    fun `a band at the very end stays inside the track`() {
        val bands = SeekbarRanges.build(
            listOf(segment(DURATION, DURATION, Category.SPONSOR)),
            SHOWN,
            true,
            DURATION,
            0,
            WIDTH,
        )

        assertEquals(WIDTH - 1, bands.single().xStart)
        assertEquals(WIDTH, bands.single().xEnd)
    }

    @Test
    fun `a zero-length band still gets one pixel`() {
        val bands = SeekbarRanges.build(
            listOf(segment(500_000, 500_000, Category.SPONSOR)),
            SHOWN,
            true,
            DURATION,
            0,
            WIDTH,
        )

        assertEquals(bands.single().xStart + 1, bands.single().xEnd)
    }
}
