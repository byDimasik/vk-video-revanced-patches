package app.revanced.patches.vkvideo.sponsorskip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeekbarPatchTest {

    private val sources get() = sponsorSkipSources()

    @Test
    fun `the ranges builder clamps both edges and stacks lanes`() {
        val ranges = sources[RANGES_CLASS]!!
        assertTrue(ranges.contains("const/4 v10, 0x2"), "the lane fallback is missing")
        assertTrue(ranges.contains("if-lt v6, v15, :lane_body"), "the lane scan bound is missing")
        assertTrue(ranges.contains("if-gez v10, :lane_ok"), "a negative lane is not replaced")
        assertTrue(ranges.contains("const v19, 0x3a83126f"), "the fraction gap is missing")
        assertTrue(ranges.contains("move/from16 v11, v20"), "the one-end clamp is missing")
        assertTrue(ranges.contains("move/from16 v12, v20"), "the other-end clamp is missing")
    }

    @Test
    fun `apply rejects foreign drawables and both null preconditions`() {
        val apply = sources[SEEK_SECTIONS_CLASS]!!.substringAfter(".method public static apply")
        val composite = apply.indexOf("instance-of v0, p1, Loo7/c;")
        val page = apply.indexOf("instance-of v0, p1, Lyo7/c;")
        val active = apply.indexOf("$SKIP_ENGINE_CLASS->active()")
        val install = apply.indexOf("setDrawable(I")
        assertTrue(composite > 0 && page > composite, "the family guards are missing")
        assertTrue(active > page, "the active-state guard must follow the family guard")
        assertTrue(apply.contains("if-eqz v0, :return"), "an unresolved active state does not bail")
        assertTrue(active < install, "the guard must run before the layer is replaced")
        assertTrue(apply.contains("instance-of v4, v2, $SECTIONS_DRAWABLE_CLASS"), "the re-entry guard is missing")
        assertTrue(apply.contains("getNumberOfLayers()I"), "the empty-layer guard is missing")
        assertFalse(apply.contains("mProgressDrawable"), "apply must not call setProgressDrawable again")
    }

    @Test
    fun `the sections drawable resolves the active state and re-reads the gating`() {
        val draw = sources[SECTIONS_DRAWABLE_CLASS]!!.substringAfter(".method public draw(")
        val active = draw.indexOf("$SKIP_ENGINE_CLASS->active()")
        val enabled = draw.indexOf("$BEHAVIOR_STORE_CLASS->isEnabled()Z")
        val behavior = draw.indexOf("$BEHAVIOR_STORE_CLASS->getBehavior(I)I")
        assertTrue(active in 1 until enabled, "the drawable does not resolve the active state first")
        assertTrue(behavior > enabled, "the per-category gate is missing from the loop")
        assertTrue(draw.contains("const/4 v10, 0x2"), "the DISABLED comparison is missing")
        assertTrue(draw.contains("$RANGES_CLASS->build(${SEGMENT_ARRAY}J)$BAND_ARRAY"), "the drawable does not build bands")
        assertTrue(draw.contains("Landroid/graphics/drawable/Drawable;->getBounds()Landroid/graphics/Rect;"), "the band coordinates do not use the bounds")
        assertTrue(draw.contains("0xb2"), "the band alpha is missing")
        assertTrue(draw.contains("0x3e800000"), "the 25 % inset is missing")
        assertTrue(draw.contains("Canvas;->drawRect(FFFFLandroid/graphics/Paint;)V"))
        assertFalse(sources[SECTIONS_DRAWABLE_CLASS]!!.contains("Landroid/content/Context;"), "the drawable still needs a context")
    }

    @Test
    fun `the engine exposes the most recent state and invalidates it on tick`() {
        val engine = sources[SKIP_ENGINE_CLASS]!!
        assertTrue(engine.contains(".method public static active()$SKIP_STATE_CLASS"), "active() is missing")
        assertTrue(engine.contains("sput-object p1, $SKIP_ENGINE_CLASS->ACTIVE:$SKIP_STATE_CLASS"), "put does not record the state")
        val tick = engine.substringAfter(".method public static tick")
        assertTrue(tick.contains("$SEEK_SECTIONS_CLASS->invalidate()V"), "the tick does not repaint the sections")
        assertFalse(engine.contains(".method private static refreshOverlay"), "the overlay refresh survived")
        assertFalse(engine.contains(".method private static bind"), "the view-walk bind survived")
    }

    @Test
    fun `invalidate repaints the current drawable through a weak reference`() {
        val sections = sources[SEEK_SECTIONS_CLASS]!!
        val invalidate = sections.substringAfter(".method public static invalidate")
        assertTrue(invalidate.contains("$SEEK_SECTIONS_CLASS->CURRENT:Ljava/lang/ref/WeakReference"), "the reference is not read")
        assertTrue(invalidate.contains("invalidateSelf()V"), "the drawable is never invalidated")
        assertTrue(sections.contains(".field private static CURRENT:Ljava/lang/ref/WeakReference;"))
    }

    @Test
    fun `the state keeps no overlay bookkeeping`() {
        val state = sources[SKIP_STATE_CLASS]!!
        listOf("view:Landroid/view/View;").forEach {
            assertTrue(state.contains(".field public $it") || state.contains(".field public final $it"), "missing $it")
        }
        listOf("bar:", "overlay:", "dirty:", "bands:", "stamp:", "sections:").forEach {
            assertFalse(state.contains(it), "the removed state field $it survived")
        }
    }

    @Test
    fun `the engine owns every injected seekbar class`() {
        listOf(BAND_CLASS, RANGES_CLASS, SECTIONS_DRAWABLE_CLASS, SEEK_SECTIONS_CLASS).forEach { descriptor ->
            assertTrue(sources.containsKey(descriptor), "missing $descriptor")
        }
        assertFalse(sources.containsKey(OVERLAY_CLASS), "the overlay class is still injected")
    }

    @Test
    fun `the tick gate branches to the hide path when disabled`() {
        val tick = sources[SKIP_ENGINE_CLASS]!!.substringAfter(".method public static tick")
        val gate = tick.lines().first { it.contains(":hide") }
        assertTrue(gate.contains("if-eqz v3, :hide"), "the gate is inverted: $gate")
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
}
