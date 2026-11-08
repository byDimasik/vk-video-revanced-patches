package app.revanced.patches.vkvideo.sponsorskip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeekbarPatchTest {

    private val sources get() = sponsorSkipSources()

    @Test
    fun `the ranges builder caps lanes and clamps both edges`() {
        val ranges = sources[RANGES_CLASS]!!
        assertTrue(ranges.contains("const/4 v13, 0x2"), "the lane fallback is missing")
        assertTrue(ranges.contains("if-ge v15, v11, :lane_next"), "lane admission is missing")
        assertTrue(ranges.contains("move v13, v14"), "a free lane is not recorded")
        val fallback = ranges.substringAfter("if-ltz v13, :lane_ok").trimStart()
        assertTrue(fallback.startsWith("const/4 v13, 0x2"), "lane 2 is not the fallback")
        assertTrue(ranges.contains("move v11, v3"), "the left clamp is missing")
        assertTrue(ranges.contains("move v12, v14"), "the right clamp is missing")
        assertTrue(ranges.contains("if-gt v12, v11, :width_ok"), "the one-pixel guard is missing")
    }

    @Test
    fun `the overlay caches its paint and reads the track insets`() {
        val overlay = sources[OVERLAY_CLASS]!!
        assertTrue(overlay.contains(".field private static COLORS:[I"))
        assertTrue(overlay.contains("setAlpha(I)V") && overlay.contains("const/16 v1, 0xb2"))
        assertTrue(overlay.contains("getThumbOffset"))
        assertTrue(overlay.contains("getPaddingLeft"))
        assertTrue(overlay.contains("if-eqz v0, :return"), "the unbound early return is missing")
        assertTrue(overlay.contains("setWillNotDraw(Z)V"))
        assertTrue(overlay.contains("setClickable(Z)V"))
    }

    @Test
    fun `the state carries the overlay lifecycle fields and is checked for staleness`() {
        val state = sources[SKIP_STATE_CLASS]!!
        listOf("bar:Landroid/view/View;", "overlay:Landroid/view/View;", "dirty:Z", "bands:$BAND_ARRAY").forEach {
            assertTrue(state.contains(".field public $it") || state.contains(".field public final $it"), "missing $it")
        }

        val engine = sources[SKIP_ENGINE_CLASS]!!
        assertTrue(engine.contains("const v3, $SEEK_BAR_VIEW_ID"))
        assertTrue(engine.contains("const v3, $SEEK_BAR_ID"))
        assertTrue(engine.contains("getProgressSeekBarView()"))
        assertTrue(engine.contains("instance-of v4, v3, Landroid/view/ViewGroup;"))
        assertTrue(engine.contains("iput-object v5, p0, $SKIP_STATE_CLASS->overlay:Landroid/view/View;"))
    }

    @Test
    fun `the engine owns every injected seekbar class`() {
        listOf(BAND_CLASS, RANGES_CLASS, OVERLAY_CLASS).forEach { descriptor ->
            assertTrue(sources.containsKey(descriptor), "missing $descriptor")
        }
    }

    @Test
    fun `the closed block decides from the list size, not a dead flag`() {
        val episodes = sources[EPISODES_CLASS]!!
        assertFalse(episodes.contains("v14"), "the always-set flag is back")
        val lines = episodes.lines()
        val at = lines.indexOfFirst { it.trim() == ":closed" }
        assertEquals("const/16 v10, 0x1f4", lines[at + 1].trim(), "the cap compare is not first in the closed block")
        assertEquals("if-gt v3, v10, :no_end", lines[at + 2].trim(), "the cap compare is not a size guard")
    }

    @Test
    fun `the tick gate branches to the hide path when disabled`() {
        val tick = sources[SKIP_ENGINE_CLASS]!!.substringAfter(".method public static tick")
        val gate = tick.lines().first { it.contains(":hide") }
        assertTrue(gate.contains("if-eqz v3, :hide"), "the gate is inverted: $gate")
    }
}
