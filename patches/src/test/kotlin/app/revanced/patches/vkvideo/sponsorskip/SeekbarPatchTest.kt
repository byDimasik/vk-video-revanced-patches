package app.revanced.patches.vkvideo.sponsorskip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeekbarPatchTest {

    private val sources get() = sponsorSkipSources()

    @Test
    fun `apply finds the fill layer by index, never by the overridden id lookup`() {
        val apply = sources[SEEK_SECTIONS_CLASS]!!.substringAfter(".method public static apply")
        val walk = apply.indexOf("getNumberOfLayers()I")
        val getId = apply.indexOf("getId(I)I")
        val install = apply.indexOf("setDrawable(Landroid/graphics/drawable/Drawable;)V")
        assertTrue(walk > 0 && getId > walk, "the layer walk is missing")
        assertTrue(apply.contains("const v6, 0x102000d"), "the fill id assertion is missing")
        assertTrue(apply.contains("const/4 v0, 0x2"), "the index-2 fallback is missing")
        assertTrue(apply.contains("if-ge v0, v3, :fallback"), "the fallback is not guarded by the layer count")
        assertTrue(install > walk, "the found layer is installed before the walk")
        assertFalse(apply.contains("findDrawableByLayerId"), "the overridden id lookup is used")
    }

    @Test
    fun `apply requires a clip layer and a known inner drawable`() {
        val apply = sources[SEEK_SECTIONS_CLASS]!!.substringAfter(".method public static apply")
        assertTrue(apply.contains("instance-of v6, v5, Landroid/graphics/drawable/ClipDrawable;"), "the clip check is missing")
        assertTrue(apply.contains("getDrawable()Landroid/graphics/drawable/Drawable;"), "the inner drawable is not read")
        listOf("Lyo7/b;", "Loo7/b;").forEach {
            assertTrue(apply.contains("instance-of v5, v8, $it"), "the inner type check for $it is missing")
        }
        assertTrue(apply.contains("addLayer(Landroid/graphics/drawable/Drawable;)V"), "the no-clip fallback adds no layer")
    }

    @Test
    fun `apply keeps a re-entry guard and does not force bounds`() {
        val apply = sources[SEEK_SECTIONS_CLASS]!!.substringAfter(".method public static apply")
        assertTrue(apply.contains("instance-of v5, v8, $PLAYED_SECTIONS_CLASS"), "the re-entry guard is missing")
        assertTrue(apply.contains("if-nez v5, :reuse"), "a wrapped drawable is not reused")
        assertTrue(apply.contains("invalidateSelf()V"), "the wrapper is not repainted")
        assertFalse(apply.contains("setBounds(IIII)V"), "apply still forces the wrapper bounds")
        assertFalse(apply.contains("setDrawable(ILandroid/graphics/drawable/Drawable;)V"), "layer 0 is replaced again")
    }

    @Test
    fun `apply rejects foreign drawables and a missing active state`() {
        val apply = sources[SEEK_SECTIONS_CLASS]!!.substringAfter(".method public static apply")
        val composite = apply.indexOf("instance-of v0, p1, Loo7/c;")
        val page = apply.indexOf("instance-of v0, p1, Lyo7/c;")
        val walk = apply.indexOf("getNumberOfLayers()I")
        assertTrue(composite > 0 && page > composite, "the family guards are missing")
        assertTrue(apply.contains("if-eqz v0, :family"), "a composite drawable does not reach the installer")
        assertTrue(apply.contains("if-eqz v0, :return"), "a foreign drawable is not rejected")
        assertTrue(walk > page, "the walk must follow the family guard")
        assertFalse(apply.contains("$SKIP_ENGINE_CLASS->active()"), "apply still bails on a missing state")
    }

    @Test
    fun `the played drawable draws inside its own bounds with the native geometry`() {
        val drawable = sources[PLAYED_SECTIONS_CLASS]!!
        val rounds = drawable.substringAfter(".method private drawRounds")
        assertTrue(rounds.contains("drawRoundRect(FFFFFFLandroid/graphics/Paint;)V"), "the round family does not round")
        assertTrue(drawable.contains("$PLAYED_SECTIONS_CLASS->center:F"), "the layer centre is not cached")
        assertTrue(rounds.contains("$PLAYED_SECTIONS_CLASS->center:F"), "the bands are not centred on the layer")
        assertTrue(rounds.contains("div-float v12, v6, v12"), "the thickness is not halved")
        assertTrue(drawable.contains("Landroid/graphics/drawable/Drawable;->getBounds()Landroid/graphics/Rect;"), "the bounds are not read")
        val rects = drawable.substringAfter(".method private drawRects")
        assertTrue(rects.contains("drawRect(FFFFLandroid/graphics/Paint;)V"), "the rect family does not draw rects")
    }

    @Test
    fun `the played drawable reads the thickness family through reflection`() {
        val drawable = sources[PLAYED_SECTIONS_CLASS]!!
        val init = drawable.substringAfter(".method public constructor <init>")
        listOf("d", "a", "b", "e", "f").forEach {
            assertTrue(init.contains("const-string v1, \"$it\""), "the round field $it is not resolved")
        }
        assertTrue(init.contains("const-class v2, Loo7/a;"), "the rect item class is not resolved")
        assertTrue(drawable.contains(".method private static field(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;"))
        val rounds = drawable.substringAfter(".method private drawRounds")
        assertTrue(rounds.contains("$PLAYED_SECTIONS_CLASS->SELECTED:Ljava/lang/reflect/Field;"), "the current segment is not read")
        assertTrue(rounds.contains("$PLAYED_SECTIONS_CLASS->FACTOR:Ljava/lang/reflect/Field;"), "the grow factor is not read")
    }

    @Test
    fun `the played drawable applies alpha and falls back to the native colour`() {
        val drawable = sources[PLAYED_SECTIONS_CLASS]!!
        val alpha = drawable.substringAfter(".method public setAlpha")
        assertTrue(alpha.contains("Paint;->setAlpha(I)V"), "setAlpha does not reach the paint")
        assertTrue(drawable.contains(".field private static final NATIVE:I = 0xff528bcc"), "the native fill colour is not pinned")
        val colour = drawable.substringAfter(".method private colour")
        assertTrue(colour.indexOf(":native") < colour.indexOf("$CATEGORY_CLASS->color(I)I"), "the native branch is not the fallback")
        assertTrue(colour.contains("getBehavior(I)I"), "a disabled category is not routed to the native colour")
        assertTrue(drawable.contains(".method public getOpacity()I"))
        assertTrue(drawable.contains(".method public setColorFilter(Landroid/graphics/ColorFilter;)V"))
    }

    @Test
    fun `the colour picks the nearest segment start`() {
        val colour = sources[PLAYED_SECTIONS_CLASS]!!.substringAfter(".method private colour")
        val best = colour.indexOf("move v6, v9")
        assertTrue(best > 0, "the nearest index is not recorded")
        assertTrue(colour.contains("cmpg-float"), "the distance sign is not handled")
        assertTrue(colour.contains("if-ltz v13, :neg"), "a negative delta is not negated")
        assertTrue(colour.contains("if-gez v13, :next"), "a farther segment overwrites the best one")
        assertFalse(colour.contains(":equal"), "the exact-count shortcut survived without a counter")
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
        listOf(PLAYED_SECTIONS_CLASS, SEEK_SECTIONS_CLASS).forEach { descriptor ->
            assertTrue(sources.containsKey(descriptor), "missing $descriptor")
        }
        assertFalse(sources.containsKey(OVERLAY_CLASS), "the overlay class is still injected")
        listOf("Band;", "Ranges;", "SectionsDrawable;").forEach {
            assertFalse(sources.keys.any { key -> key.endsWith("/$it") }, "$it is still injected")
        }
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
