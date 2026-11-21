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
        listOf("Lnp7/b;", "Ldp7/b;").forEach {
            assertTrue(apply.contains("instance-of v5, v8, $it"), "the inner type check for $it is missing")
        }
        assertTrue(apply.contains("addLayer(Landroid/graphics/drawable/Drawable;)V"), "the no-clip fallback adds no layer")
        val add = apply.substringAfter("\n    :add\n")
        assertTrue(add.contains("if-ltz v5, :add_new"), "the fallback re-adds on every call")
        assertTrue(add.contains("instance-of v7, v6, $PLAYED_SECTIONS_CLASS"), "the last layer is not checked for an existing wrapper")
        assertTrue(add.contains("move-object v9, v6"), "an existing wrapper is not reused")
    }

    @Test
    fun `apply keeps a re-entry guard and does not force bounds`() {
        val apply = sources[SEEK_SECTIONS_CLASS]!!.substringAfter(".method public static apply")
        assertTrue(apply.contains("instance-of v5, v8, $PLAYED_SECTIONS_CLASS"), "the re-entry guard is missing")
        assertTrue(apply.contains("if-nez v5, :reuse"), "a wrapped drawable is not reused")
        assertTrue(apply.contains("invalidateSelf()V"), "the wrapper is not repainted")
        assertFalse(apply.contains("setBounds(IIII)V"), "apply still forces the wrapper bounds")
        assertTrue(apply.contains("setDrawable(ILandroid/graphics/drawable/Drawable;)V"), "the track layer is not replaced in place")
    }

    @Test
    fun `apply rejects foreign drawables and a missing active state`() {
        val apply = sources[SEEK_SECTIONS_CLASS]!!.substringAfter(".method public static apply")
        val composite = apply.indexOf("instance-of v0, p1, Ldp7/c;")
        val page = apply.indexOf("instance-of v0, p1, Lnp7/c;")
        val walk = apply.indexOf("getNumberOfLayers()I")
        assertTrue(composite > 0 && page > composite, "the family guards are missing")
        assertTrue(apply.contains("if-nez v0, :family"), "a composite drawable does not reach the installer")
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
        assertTrue(init.contains("const-class v2, Ldp7/a;"), "the rect item class is not resolved")
        assertTrue(drawable.contains(".method private static field(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;"))
        val rounds = drawable.substringAfter(".method private drawRounds")
        assertTrue(rounds.contains("$PLAYED_SECTIONS_CLASS->selected:Ljava/lang/reflect/Field;"), "the current segment is not read")
        assertTrue(rounds.contains("$PLAYED_SECTIONS_CLASS->factor:Ljava/lang/reflect/Field;"), "the grow factor is not read")
        assertFalse(drawable.contains(".field private static list:Ljava/lang/reflect/Field;"), "the handles are shared between instances")
        assertTrue(drawable.contains(".catch Ljava/lang/Exception; {:try_start .. :try_end} :broken"), "a field mismatch is unguarded")
        assertTrue(drawable.substringAfter(":broken").contains("iput-boolean v0, p0, $PLAYED_SECTIONS_CLASS->broken:Z"), "the guard does not set the failure flag")
        assertTrue(drawable.contains("if-nez v0, :source"), "draw does not bail out on a broken wrapper")
        listOf("drawRounds", "drawRects").forEach { method ->
            val body = drawable.substringAfter(".method private $method")
            assertTrue(body.contains(".catch Ljava/lang/Exception; {:try_read .. :try_end_read} :read_fail"), "$method does not guard its reflective reads")
            assertTrue(body.contains("iput-boolean v0, v15, $PLAYED_SECTIONS_CLASS->broken:Z"), "$method does not set the failure flag")
        }
    }

    @Test
    fun `the played drawable applies alpha and falls back to the native colour`() {
        val drawable = sources[PLAYED_SECTIONS_CLASS]!!
        val alpha = drawable.substringAfter(".method public setAlpha")
        assertTrue(alpha.contains("Paint;->setAlpha(I)V"), "setAlpha does not reach the paint")
        assertTrue(alpha.contains("Landroid/graphics/drawable/Drawable;->setAlpha(I)V"), "setAlpha is not forwarded to the source replay")
        assertTrue(drawable.contains(".field private static final NATIVE:I = 0xff528bcc"), "the native fill colour is not pinned")
        val colour = drawable.substringAfter(".method private colour")
        assertTrue(colour.indexOf(":native") < colour.indexOf("$CATEGORY_CLASS->color(I)I"), "the native branch is not the fallback")
        assertTrue(colour.contains("getBehavior(I)I"), "a disabled category is not routed to the native colour")
        assertTrue(drawable.contains(".method public getOpacity()I"))
        assertTrue(drawable.contains(".method public setColorFilter(Landroid/graphics/ColorFilter;)V"))
    }

    @Test
    fun `the colour picks the segment with the largest overlap, not the nearest start`() {
        val drawable = sources[PLAYED_SECTIONS_CLASS]!!
        val colour = drawable.substringAfter(".method private colour")
        val best = colour.indexOf("move v6, v9")
        assertTrue(best > 0, "the best index is not recorded")
        assertTrue(colour.contains("const/4 v6, -0x1"), "the best index is not seeded to -1, so the native fallback never runs")
        assertTrue(colour.contains("if-ltz v6, :native"), "the missing-pick guard is inverted")
        assertTrue(colour.contains("if-ltz v0, :native"), "the unknown-category guard is inverted")
        assertTrue(colour.contains(smali("$PLAYED_SECTIONS_CLASS;->fractionEnd:F")), "the band end fraction is not read")
        assertTrue(colour.contains("sub-float v5, v5, v14"), "the overlap is not the span intersection")
        assertTrue(colour.contains("cmpl-float v14, v5, v16"), "the overlap is not compared against the best one")
        assertTrue(colour.contains("if-lez v14, :next"), "a non-larger overlap still overwrites the best index")
        assertTrue(colour.indexOf(":lo") < colour.indexOf("sub-float v5, v5, v14"), "the intersection is not clamped before the overlap")
        assertTrue(colour.contains(":hi"), "the upper bound is not clamped")
        // Both clamps take the inner edge: lo = max(start), hi = min(end). Swapping either pair
        // turns the intersection into the outer span, so the last section always wins (#89).
        // The z-forms are single-register (21t): `if-gtz vA, vB, :label` does not assemble, and
        // the miss only surfaces at patch time, not in these string tests (#89).
        assertTrue(colour.contains("if-gtz v14, :lo_band"), "the lower clamp branch is missing or two-register")
        assertTrue(colour.contains("if-gtz v5, :hi_seg"), "the upper clamp branch is missing or two-register")
        // Normalised so the assertions do not depend on the smali indentation depth.
        val flat = colour.lines().joinToString("\n") { it.trim() }
        assertTrue(flat.contains("if-gtz v14, :lo_band\nmove v14, v7\ngoto :lo"), "the lo branch or its band-start fall-through is wrong")
        assertTrue(flat.contains(":lo_band\nmove v14, v11"), "lo takes the band start instead of the segment start")
        assertTrue(flat.contains("if-gtz v5, :hi_seg\nmove v5, v13\ngoto :hi"), "the hi branch or its segment-end fall-through is wrong")
        assertTrue(flat.contains(":hi_seg\nmove v5, v8"), "hi takes the segment end instead of the band end")
        assertFalse(colour.contains("0x7f7fffff"), "the nearest-start distance seed survived")
        // `iget-wide` clobbers the register after its base (11-12, 13-14), so the accumulator
        // must live outside that window or the verifier rejects the class (#89 device VerifyError).
        assertTrue(colour.contains(".registers 17"), "the accumulator register was not widened")
        assertTrue(colour.contains("const/16 v16, 0x0") && colour.contains("cmpl-float v14, v5, v16"), "the accumulator is not v16")
        assertFalse(colour.contains("cmpl-float v14, v5, v12"), "the accumulator sits in the wide-read window")
        // The diagnostics are gated: compared against zero is fine, but `sget v, ...; if-eqz v`
        // on a field declared `Z = 0x1` produces an int static value and the whole dex fails
        // verification ("unexpected static field initial value type: 'I' vs 'Z'").
        assertTrue(drawable.contains(".field private static DEBUG:Z = false"), "the trace flag is not a boolean literal")
        assertFalse(drawable.contains("DEBUG:Z = 0x"), "the trace flag carries an int static value")
    }

    @Test
    fun `a band keeps its own section colour at every bar width`() {
        // Mirror of the app's band producer (np7/c.onBoundsChange) plus the injected selector:
        // a band narrower than the minimum width is absorbed into the NEXT band, which shifts
        // every later band's start off its own section boundary. Portrait (narrow) bars hit the
        // absorption around 13-16%, and a nearest-start match then painted that block with the
        // neighbour's colour (#89).
        val sections = listOf(0.0f to 0.13f, 0.13f to 0.16f, 0.16f to 0.42f, 0.42f to 0.75f, 0.75f to 1.0f)
        val narrow = listOf(0, 2, 3, 4) // 13-16% absorbed into the 16-42% block
        val wide = listOf(0, 1, 2, 3, 4)
        for (width in listOf(240, 360, 720, 1280)) {
            val expected = if (width == 240) narrow else wide
            val bands = bandsFor(sections, width, minWidth = 8f)
            val colours = bands.map { (start, end) -> dominant(sections, start / width, end / width) }
            assertEquals(expected, colours, "colour order at width $width")
            // Every band takes a distinct, forward-ordered section colour: none repeats a
            // section and none steps back to a previous neighbour (the portrait bug).
            val ordered = colours.zipWithNext().all { (left, right) -> right > left }
            assertTrue(ordered, "colour order is not strictly forward at width $width: $colours")

            // The block covering the 13-16% region must carry a section that really covers
            // most of that region's pixels, in both the absorbed (portrait) and split cases.
            val from = (0.13f * width).toInt()
            val to = (0.16f * width).toInt()
            val sample = bands.indexOfFirst { (start, end) -> start <= from && end >= to }
            if (sample >= 0) {
                assertEquals(if (width == 240) 2 else 1, colours[sample], "the 13-16% block took the wrong colour at width $width")
            }
        }
    }

    /** The app's producer: bands narrower than [minWidth] are carried into the next band. */
    private fun bandsFor(sections: List<Pair<Float, Float>>, width: Int, minWidth: Float): List<Pair<Float, Float>> {
        val bounds = sections.map { it.first } + sections.last().second
        val bands = mutableListOf<Pair<Float, Float>>()
        var left = 0f
        var carry = 0f
        sections.indices.forEach { i ->
            val units = (bounds[i + 1] - bounds[i]) * 10000f + carry
            val end = minOf(width.toFloat(), left + units / 10000f * width)
            if (end >= left + minWidth) {
                bands += left to end
                left = end
                carry = 0f
            } else if (i == sections.lastIndex) {
                bands[bands.lastIndex] = bands.last()!!.first to end
            } else {
                carry = units
            }
        }
        return bands
    }

    /** The injected selector: the section overlapping the band the most wins. */
    private fun dominant(sections: List<Pair<Float, Float>>, start: Float, end: Float): Int {
        var best = 0
        var bestOverlap = Float.NEGATIVE_INFINITY
        sections.forEachIndexed { index, (segmentStart, segmentEnd) ->
            val overlap = minOf(segmentEnd, end) - maxOf(segmentStart, start)
            if (overlap > bestOverlap) {
                bestOverlap = overlap
                best = index
            }
        }
        return best
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
        assertTrue(
            sections.contains(".field private static volatile CURRENT:Ljava/lang/ref/WeakReference;"),
            "the cross-thread reference is not volatile",
        )
    }

    @Test
    fun `the state keeps no overlay bookkeeping`() {
        val state = sources[SKIP_STATE_CLASS]!!
        listOf("view:Landroid/view/View;", "anchor:Landroid/view/View;", "bar:Landroid/view/View;", "anchorX:I", "anchorY:I").forEach {
            assertTrue(state.contains(".field public $it") || state.contains(".field public final $it"), "missing $it")
        }
        listOf("overlay:", "dirty:", "bands:", "stamp:", "sections:").forEach {
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
        val gate = tick.lines().first { it.contains(":hide") && !it.trim().startsWith("#") }
        assertTrue(Regex("if-eqz v\\d+, :hide").containsMatchIn(gate), "the gate is inverted: $gate")
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
    fun `apply walks the track layer after the played install and replaces it in place`() {
        val apply = sources[SEEK_SECTIONS_CLASS]!!.substringAfter(".method public static apply")
        assertTrue(apply.contains("const v6, 0x102000d"), "the fill walk constant is missing")
        assertTrue(apply.contains("const v7, 0x1020000"), "the track walk constant is missing")
        assertTrue(apply.contains("if-ne v5, v7, :track_next"), "the track id match is missing")
        val installed = apply.indexOf(":installed")
        val trackWalk = apply.indexOf(":track_walk")
        assertTrue(installed in 0 until trackWalk, "the track pass must follow the played install")
        assertTrue(
            apply.contains(
                "invoke-virtual {v2, v0, v9}, Landroid/graphics/drawable/LayerDrawable;->setDrawable(ILandroid/graphics/drawable/Drawable;)V",
            ),
            "the track wrapper is not installed at its own index",
        )
        listOf(":track_walk", ":track_walked", ":track_indexed", ":track_next", ":track_reuse", ":track_wrap", ":track_add", ":track_installed")
            .forEach { label ->
                assertEquals(1, apply.lines().count { it.trim() == label }, "label $label is missing or duplicated")
            }
    }

    @Test
    fun `the track pass unwraps a clip and reuses before the type guard`() {
        val track = sources[SEEK_SECTIONS_CLASS]!!.substringAfter(":track_walked")
        assertTrue(track.contains("instance-of v5, v6, Landroid/graphics/drawable/ClipDrawable;"), "the track clip check is missing")
        assertTrue(track.contains("ClipDrawable;->getDrawable()Landroid/graphics/drawable/Drawable;"), "the track inner is not unwrapped")
        val reuse = track.indexOf("instance-of v5, v6, $PLAYED_SECTIONS_CLASS")
        val round = track.indexOf("instance-of v5, v6, Lnp7/b;")
        assertTrue(reuse in 0 until round, "the reuse guard must precede the type guard")
        listOf("Lnp7/b;", "Ldp7/b;").forEach {
            assertTrue(track.contains("instance-of v5, v6, $it"), "the track inner check for $it is missing")
        }
    }

    @Test
    fun `the track flag gates the background replay on the 4-arg constructor`() {
        val drawable = sources[PLAYED_SECTIONS_CLASS]!!
        val draw = drawable.substringAfter(".method public draw")
        val flag = draw.indexOf("$PLAYED_SECTIONS_CLASS->track:Z")
        val replay = draw.indexOf("Landroid/graphics/drawable/Drawable;->draw(Landroid/graphics/Canvas;)V")
        val branch = draw.indexOf("if-eqz v11, :rect")
        assertTrue(flag in 0 until replay, "draw does not read the track flag")
        assertTrue(replay in 0 until branch, "the background replay must precede the bands")
        assertTrue(
            drawable.contains(".method public constructor <init>(Landroid/graphics/drawable/Drawable;IIZ)V"),
            "the 4-arg constructor is missing",
        )
        assertFalse(
            drawable.contains(".method public constructor <init>(Landroid/graphics/drawable/Drawable;)V"),
            "the 1-arg constructor survived",
        )
        assertTrue(drawable.contains(".field private final strength:I"), "the strength field is missing")
        assertTrue(drawable.contains(".field private final track:Z"), "the track field is missing")
    }

    @Test
    fun `the track instance is dimmer and skips native bands in both draw branches`() {
        val apply = sources[SEEK_SECTIONS_CLASS]!!
        assertTrue(apply.contains("const/16 v5, 0xff"), "the played multiplier is missing")
        assertTrue(apply.contains("const/16 v6, 0xff"), "the played strength is missing")
        assertTrue(apply.contains("const/16 v5, 0xb2"), "the track alpha is missing")
        assertTrue(apply.contains("const/16 v7, 0xf2"), "the track strength is missing")
        assertTrue(apply.contains("const/4 v4, 0x1"), "the track flag is not set on the track instance")
        val drawable = sources[PLAYED_SECTIONS_CLASS]!!
        listOf("drawRounds", "drawRects").forEach { method ->
            val body = drawable.substringAfter(".method private $method")
            assertTrue(body.contains("iget-boolean v13, v15, $PLAYED_SECTIONS_CLASS->track:Z") || body.contains("iget-boolean v14, v15, $PLAYED_SECTIONS_CLASS->track:Z"), "$method does not gate the skip on the track flag")
            assertTrue(body.contains("NATIVE:I"), "$method does not compare the native colour")
        }
        val alpha = drawable.substringAfter(".method public setAlpha")
        assertTrue(alpha.contains("$PLAYED_SECTIONS_CLASS->strength:I"), "setAlpha ignores the strength field")
    }

    @Test
    fun `the constructor resolution stays guarded and degrades to the source draw`() {
        val drawable = sources[PLAYED_SECTIONS_CLASS]!!
        val init = drawable.substringAfter(".method public constructor <init>(Landroid/graphics/drawable/Drawable;IIZ)V")
        assertTrue(init.contains(".catch Ljava/lang/Exception; {:try_start .. :try_end} :broken"), "the constructor resolution is unguarded")
        assertTrue(init.contains("instance-of v0, p1, Lnp7/b;"), "the round family is not detected in the constructor")
        val degraded = drawable.substringAfter(":broken")
        assertTrue(degraded.contains("iput-boolean v0, p0, $PLAYED_SECTIONS_CLASS->broken:Z"), "the guard does not set the failure flag")
        assertTrue(drawable.contains("if-nez v0, :source"), "draw does not degrade to the source draw")
    }

    @Test
    fun `the guard entry pins the 4-arg constructor and the added fields`() {
        val methods = requiredMethods.getValue(PLAYED_SECTIONS_CLASS)
        assertTrue(methods.contains("<init>:4"), "the guard entry does not pin the 4-arg constructor")
        assertFalse(methods.contains("<init>:1"), "the stale 1-arg entry survived")
        assertTrue(requiredFields.getValue(PLAYED_SECTIONS_CLASS).containsAll(listOf("strength", "track")), "the new fields are not pinned")
    }


    @Test
    fun `a resume re-attaches the layer and never assumes the caller has a bar`() {
        val sections = sources[SEEK_SECTIONS_CLASS]!!
        val resume = sections.substringAfter(".method public static resume").substringBefore(".end method")
        assertTrue(resume.contains("if-nez p0, :have"), "a supplied view is not used before the remembered-bar fallback")
        assertTrue(resume.contains(smali("$SEEK_SECTIONS_CLASS;->WATCHED")), "the remembered bar is not the fallback")
        assertTrue(resume.contains("if-eqz v0, :return"), "a cleared reference is not tolerated")
        assertTrue(resume.contains(smali("$SEEK_SECTIONS_CLASS;->rebind(Landroid/view/View;)V")), "resume does not rebind")

        val rebind = sections.substringAfter(".method public static rebind").substringBefore(".end method")
        assertTrue(rebind.contains("instance-of v0, p0, Landroid/widget/ProgressBar;"), "a bar is not recognised")
        assertTrue(rebind.contains(smali("$SKIP_ENGINE_CLASS;->findBar(Landroid/view/View;I)Landroid/widget/ProgressBar;")), "a surface is not walked down to its bar")
        assertTrue(rebind.contains("isAttachedToWindow()Z"), "a detached bar is not skipped")
        assertTrue(rebind.contains("if-eqz v0, :return"), "an attached bar is rejected instead of the detached one")
        assertTrue(rebind.contains("getProgressDrawable()Landroid/graphics/drawable/Drawable;"), "the live drawable is not read")
        assertTrue(rebind.contains(smali("$SEEK_SECTIONS_CLASS;->apply(Landroid/view/View;Landroid/graphics/drawable/Drawable;)V")), "the layer is not re-applied")
        assertTrue(sections.contains(".method public static findBar") || sources[SKIP_ENGINE_CLASS]!!.contains(".method public static findBar"), "findBar is not reachable from the layer")
    }

    @Test
    fun `every bar (re)creation path re-derives the colouring`() {
        // Trigger matrix: rotation, colour refresh, app STOP -> RESTART and surface hand-off
        // all have to end in the same idempotent re-attach (#89).
        val engine = sources[SKIP_ENGINE_CLASS]!!
        val attached = engine.substringAfter(".method public static onViewAttached").substringBefore(".end method")
        val inline = attached.substringAfter(":inline").substringBefore(":rebuild")
        assertTrue(inline.contains(smali("$SEEK_SECTIONS_CLASS;->resume(Landroid/view/View;)V")), "the main attach path does not resume the bands")
        val rebuild = attached.substringAfter("\n    :rebuild")
        // The resume rides on the posted attach op (op4), so a rebuilt state must post it.
        assertTrue(rebuild.contains("const/4 v2, 0x4"), "a rebuilt state does not post the attach op")
        val posts = rebuild.substringBefore(".end method")
        assertTrue(posts.contains("postMain(Ljava/lang/Runnable;)V"), "the post-rebuild resume is not posted")
        assertTrue(
            posts.indexOf("onVideoChanged(Lp73/a;Lcom/vk/dto/common/VideoFile;)V") < posts.indexOf("postMain"),
            "the resume is posted before the state rebuild",
        )

        val main = sources[SKIP_MAIN_CLASS]!!
        val op4 = main.substringAfter("\n    :op4").substringBefore(".end method")
        assertTrue(op4.contains(smali("$SEEK_SECTIONS_CLASS;->resume(Landroid/view/View;)V")), "the attach op does not resume the bands")
        assertTrue(op4.contains(smali("$SKIP_MAIN_CLASS->target:Landroid/view/View;")), "the attach op drops its surface")

        val relayout = sources[SEEK_SECTIONS_CLASS]!!.substringAfter(".method public static relayout").substringBefore(".end method")
        assertTrue(relayout.contains(smali("$SEEK_SECTIONS_CLASS;->resume(Landroid/view/View;)V")), "rotation does not resume")

        val tick = engine.substringAfter(".method public static tick")
        val alive = tick.substringAfter(":alive").substringBefore("\n    :probed")
        assertTrue(alive.contains(smali("$SKIP_STATE_CLASS->deadTicks:I")), "the dead->alive transition is not read")
        assertTrue(alive.contains("if-eqz v1, :unattached"), "an attached probe is routed to the dead-tick counter")
        assertTrue(alive.contains("if-lez v2, :probed"), "a returning tick (positive deadTicks) skips the resume")
        assertTrue(alive.contains(smali("$SEEK_SECTIONS_CLASS;->resume(Landroid/view/View;)V")), "a returning surface does not resume")
    }

    @Test
    fun `the layer keeps painting remembered sections without an engine state`() {
        val draw = sources[PLAYED_SECTIONS_CLASS]!!.substringAfter(".method public draw").substringBefore(".end method")
        val active = draw.indexOf(smali("$SKIP_ENGINE_CLASS->active()"))
        val cached = draw.indexOf(smali("$PLAYED_SECTIONS_CLASS;->segments:[L"))
        assertTrue(active in 0 until cached, "the live state is not probed before the cache check")
        assertTrue(draw.contains("if-eqz v4, :source"), "an empty cache does not fall back to the source")
        assertTrue(draw.contains(smali("$PLAYED_SECTIONS_CLASS;->duration:J")), "the remembered duration is not kept")
        val refresh = draw.substringAfter(smali("$SKIP_ENGINE_CLASS->active()"))
        assertTrue(refresh.contains("if-eqz v2, :cached"), "a missing state does not skip to the cached draw")
        assertTrue(refresh.contains(":cached"), "the cached draw label is missing")
        assertTrue(refresh.contains("iput-object v5, p0, $PLAYED_SECTIONS_CLASS->segments:[L"), "a live state does not refresh the cache")
        assertTrue(
            refresh.indexOf(":cached") < refresh.indexOf(smali("$PLAYED_SECTIONS_CLASS;->list:Ljava/lang/reflect/Field;")),
            "the cached draw is not the shared continuation",
        )
    }

    @Test
    fun `apply revives a state dropped by the app stop`() {
        val engine = sources[SKIP_ENGINE_CLASS]!!
        val revive = engine.substringAfter(".method public static revive").substringBefore(".end method")
        assertTrue(revive.contains(smali("$SKIP_ENGINE_CLASS->ACTIVE")), "a live state is not short-circuited")
        assertTrue(revive.contains("if-nez v0, :return"), "the short circuit returns on a null state instead of a live one")
        assertTrue(revive.contains("if-eqz v5, :scan"), "the scan exits while entries remain")
        assertTrue(revive.contains(smali("$SKIP_ENGINE_CLASS->VIDEOS")), "the remembered videos are not scanned")
        assertTrue(revive.contains("entrySet()Ljava/util/Set;"), "the scan does not walk the entries")
        assertTrue(revive.contains("Ljava/util/Map\$Entry;->getValue()Ljava/lang/Object;"), "the remembered video is not read")
        assertTrue(revive.contains("Ljava/util/Map\$Entry;->getKey()Ljava/lang/Object;"), "the remembered controller is not read")
        assertTrue(revive.contains(smali("$SKIP_ENGINE_CLASS;->onVideoChanged(Lp73/a;Lcom/vk/dto/common/VideoFile;)V")), "the state is not rebuilt")
        val apply = sources[SEEK_SECTIONS_CLASS]!!.substringAfter(".method public static apply")
        assertTrue(apply.contains(smali("$SKIP_ENGINE_CLASS;->revive()V")), "apply does not revive")
    }

    @Test
    fun `the re-attach is idempotent on an already wrapped drawable`() {
        val body = sources[SEEK_SECTIONS_CLASS]!!.substringAfter(".method public static apply")
        val reuse = body.indexOf("\n    :reuse")
        assertTrue(reuse > 0, "the reuse path is missing")
        assertTrue(body.substring(0, reuse).contains("if-nez v5, :reuse"), "a wrapped drawable does not reuse")
        assertTrue(body.contains("addLayer"), "the add path is gone")
        // A second apply on the same bar walks the same drawable and lands in :reuse, so the
        // layer count does not grow (the `:add` branch itself checks the last layer first).
        val add = body.substringAfter("\n    :add\n")
        assertTrue(add.contains(smali("instance-of v7, v6, $PLAYED_SECTIONS_CLASS")), "the add path can duplicate the wrapper")
    }

    /** The injected sources collapse a doubled `;` (descriptor + delimiter) into one. */
    private fun smali(text: String) = text.replace(";;", ";")

    @Test
    fun `the band width comes from the live layer rect, not the wrapper bounds`() {
        val drawable = sources[PLAYED_SECTIONS_CLASS]!!
        val draw = drawable.substringAfter(".method public draw").substringBefore(".end method")
        val source = draw.indexOf(smali("$PLAYED_SECTIONS_CLASS;->source:Landroid/graphics/drawable/Drawable;"))
        val bounds = draw.indexOf("Landroid/graphics/drawable/Drawable;->getBounds()Landroid/graphics/Rect;")
        val width = draw.indexOf(smali("$PLAYED_SECTIONS_CLASS;->width:I"))
        assertTrue(source >= 0 && source < bounds, "the source is not read before the rect")
        assertTrue(bounds < width, "the rect is not read before the width is stored")
        assertTrue(
            draw.contains("invoke-virtual {v3}, Landroid/graphics/drawable/Drawable;->getBounds()Landroid/graphics/Rect;"),
            "the bounds are not taken from the source drawable",
        )
        assertTrue(
            draw.contains("invoke-virtual {p0, v0}, Landroid/graphics/drawable/Drawable;->setBounds(Landroid/graphics/Rect;)V"),
            "the stale wrapper bounds are not refreshed from the live rect",
        )
        assertTrue(
            draw.contains("invoke-virtual {v3, v0}, Landroid/graphics/drawable/Drawable;->setBounds(Landroid/graphics/Rect;)V"),
            "the source is not re-bounded from the live rect",
        )
        assertFalse(
            draw.substringBefore(smali("iget-object v3, p0, $PLAYED_SECTIONS_CLASS;->source")).contains("getBounds"),
            "draw still measures the wrapper's own (stale) bounds",
        )
    }

    @Test
    fun `a bounds change refreshes the mapping and repaints`() {
        val drawable = sources[PLAYED_SECTIONS_CLASS]!!
        val change = drawable.substringAfter(".method protected onBoundsChange").substringBefore(".end method")
        assertTrue(change.contains("invoke-super"), "the super bounds change is not called")
        assertTrue(
            change.contains(smali("$PLAYED_SECTIONS_CLASS;->source:Landroid/graphics/drawable/Drawable;")),
            "the wrapped source is not forwarded the new bounds",
        )
        assertTrue(
            change.contains("invoke-virtual {v0, p1}, Landroid/graphics/drawable/Drawable;->setBounds(Landroid/graphics/Rect;)V"),
            "a resize does not re-bound the source, so the bands stay mapped to the old width",
        )
        assertTrue(
            change.indexOf("if-eqz v0, :nosource") < change.indexOf(":nosource"),
            "the source forward is not skipped when the source is still empty",
        )
        assertTrue(
            change.indexOf(smali("$PLAYED_SECTIONS_CLASS;->source:Landroid/graphics/drawable/Drawable;")) <
                change.indexOf("Landroid/graphics/Rect;->width()I"),
            "the source is not re-bounded before the width is read",
        )
        assertTrue(change.contains("Landroid/graphics/Rect;->width()I"), "the new width is not read")
        assertTrue(change.contains(smali("$PLAYED_SECTIONS_CLASS;->width:I")), "the new width is not stored")
        assertTrue(change.contains("Landroid/graphics/Rect;->centerY()I"), "the new centre is not read")
        assertTrue(change.contains(smali("$PLAYED_SECTIONS_CLASS;->center:F")), "the new centre is not stored")
        assertTrue(change.contains("invalidateSelf()V"), "the bands are not repainted on a size change")
    }

    @Test
    fun `apply watches the bar for layout changes and registers one listener per bar`() {
        val sections = sources[SEEK_SECTIONS_CLASS]!!
        val apply = sections.substringAfter(".method public static apply")
        assertTrue(apply.contains(smali("$SEEK_SECTIONS_CLASS;->watch(Landroid/view/View;)V")), "the bar is not watched")
        val watch = sections.substringAfter(".method public static watch").substringBefore(".end method")
        assertTrue(watch.contains(smali("$SEEK_SECTIONS_CLASS;->WATCHED")), "the watched bar is not remembered")
        assertTrue(watch.contains("if-ne v0, p0, :fresh"), "a different bar does not get a fresh registration")
        assertTrue(
            watch.substringAfter("if-ne v0, p0, :fresh").trimStart().startsWith("return-void"),
            "the same bar is not returned for without re-registering",
        )
        val referent = watch.substringAfter("Ljava/lang/ref/Reference;->get()Ljava/lang/Object;")
            .substringBefore("if-ne v0, p0, :fresh")
        assertTrue(
            referent.contains("move-result-object v0") && !referent.contains("if-"),
            "a cleared reference is not treated as a different bar",
        )
        assertTrue(watch.contains(smali("$SEEK_SECTIONS_CLASS;->LISTENER")), "the listener is not cached")
        assertTrue(watch.contains("addOnLayoutChangeListener"), "the listener is not attached")
        assertTrue(
            watch.contains("removeOnLayoutChangeListener"),
            "the listener is not dropped from the previous bar when a different bar is watched",
        )
        assertTrue(
            watch.indexOf("removeOnLayoutChangeListener") < watch.indexOf("addOnLayoutChangeListener"),
            "the old bar is detached after the new bar is attached",
        )
        assertTrue(
            watch.indexOf(":detached") < watch.indexOf("new-instance") &&
                watch.contains("if-eqz v1, :detached"),
            "a cleared reference is not treated as a fresh registration",
        )
        val relayout = sections.substringAfter(".method public static relayout").substringBefore(".end method")
        assertTrue(
            relayout.contains(smali("$SEEK_SECTIONS_CLASS;->resume(Landroid/view/View;)V")),
            "a layout change does not re-attach the layer",
        )
        val listener = sources[SEEK_RELAYOUT_CLASS]!!
        assertTrue(listener.contains(".implements Landroid/view/View\$OnLayoutChangeListener;"), "the listener does not implement the interface")
        assertTrue(
            listener.contains(smali("invoke-static/range {p1 .. p9}, $SEEK_SECTIONS_CLASS;->relayout(Landroid/view/View;IIIIIIII)V")),
            "the listener does not forward the full callback",
        )
    }
}
