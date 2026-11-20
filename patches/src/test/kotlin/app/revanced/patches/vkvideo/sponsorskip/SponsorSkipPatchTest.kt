package app.revanced.patches.vkvideo.sponsorskip

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil
import app.revanced.patches.vkvideo.util.PatchTestUtil.createBytecodeContext
import app.revanced.patches.vkvideo.util.PatchTestUtil.execute
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SponsorSkipPatchTest {

    /**
     * The fingerprint predicates read the process-global controller resolved from
     * the fixture, so tests that match without executing the patch must not rely
     * on another test having set it.
     */
    @BeforeEach
    fun resolveFixtureAutoPlayApi() {
        autoPlayApi = resolveAutoPlayApi(createBytecodeContext(*fixtures.toTypedArray()).classDefs.toList())
    }

    @Test
    fun `every injected class compiles`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        sponsorSkipSources().keys.forEach { descriptor ->
            assertTrue(context.classDefs.any { it.type == descriptor }, "missing $descriptor")
        }
    }

    @Test
    fun `the state keeps the video it must re-read episodes from`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        val hook = context.classDefs.first { it.type == SKIP_ENGINE_CLASS }.methods
            .first { it.name == "onVideoChanged" }.instr()

        val write = hook.indexOfFirst {
            it.opcode == Opcode.IPUT_OBJECT &&
                (it as ReferenceInstruction).reference.toString().endsWith("->video:Lcom/vk/dto/common/VideoFile;")
        }
        assertTrue(write >= 0, "the state does not keep the video")
        // v1 is the enabled flag by then, so the field must take the parameter copy.
        val source = (hook[write] as TwoRegisterInstruction).registerA
        assertTrue(
            hook.take(write).any {
                it.opcode == Opcode.MOVE_OBJECT_FROM16 &&
                    (it as TwoRegisterInstruction).registerA == source &&
                    (it as TwoRegisterInstruction).registerB == 15
            },
            "the video field is not written from the parameter",
        )
        // The remembered-video call has to take the same copy, not the enabled flag.
        val remember = hook.indexOfFirst {
            it.opcode == Opcode.INVOKE_STATIC &&
                (it as ReferenceInstruction).reference.toString().contains("->rememberVideo(")
        }
        assertTrue(remember >= 0, "the video is not remembered")
        assertEquals(source, (hook[remember] as FiveRegisterInstruction).registerD, "the remembered video is not the parameter copy")
    }

    @Test
    fun `the episodes adapter pins the list getter, the sort and the closing end`() {
        val episodes = sponsorSkipSources()[EPISODES_CLASS]!!
        assertTrue(episodes.contains("Lcom/vk/dto/common/VideoFile;->k3()Ljava/util/List;"))
        assertTrue(episodes.contains("Ljava/util/Collections;->sort(Ljava/util/List;Ljava/util/Comparator;)V"))
        assertTrue(episodes.contains("iput-wide v8, v6, $SEGMENT_CLASS->endMs:J"))
        assertTrue(episodes.contains("Lcom/vk/dto/common/VideoEpisode;->a:I"))
        assertTrue(episodes.contains("if-eq v5, v13, :done"), "duplicate starts are not trimmed")
        assertTrue(episodes.contains(":no_end"), "a capped list is closed at the duration")
        assertTrue(sponsorSkipSources()[SEGMENT_ORDER_CLASS]!!.contains("Ljava/util/Comparator;"))
    }

    @Test
    fun `the rules tables mirror the Kotlin ruleset`() {
        val rules = sponsorSkipSources()[RULES_CLASS]!!
        LebedevRules.EXACT.forEach { (name, category) ->
            assertTrue(rules.contains("\"$name\""), "missing rule $name")
            val label = when (category) {
                Category.SELFPROMO -> "selfpromo"
                Category.INTRO -> "intro"
                else -> "sponsor"
            }
            val at = rules.indexOf("const-string v0, \"$name\"")
            assertTrue(at >= 0, "missing rule $name")
            val branch = rules.substring(at, minOf(rules.length, at + 200))
            assertTrue(branch.contains(":$label"), "rule $name does not branch to $label")
        }
        assertTrue(rules.contains("${LebedevRules.OWNER_ID}"))
        assertTrue(rules.contains("${LebedevRules.POSITIONAL_MAX_MS}"))
    }

    @Test
    fun `the category colors match the epic table`() {
        val category = sponsorSkipSources()[CATEGORY_CLASS]!!
        Category.COLORS.forEach { assertTrue(category.contains(it.toString()), "missing color $it") }
    }

    @Test
    fun `the toast labels mirror the RU button strings`() {
        val label = sponsorSkipSources()[CATEGORY_CLASS]!!.substringAfter(".method public static label")
        Category.LABELS.forEach { assertTrue(label.contains("\"$it\""), "missing label $it") }
        assertTrue(label.substringBefore(".end method").contains("\"unknown\""), "no unknown fallback")
    }

    @Test
    fun `auto-skip toasts once per segment with the 1200 ms floor`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        val skip = context.classDefs.first { it.type == SKIP_ENGINE_CLASS }.methods
            .first { it.name == "skip" }
        assertEquals(listOf(SKIP_STATE_CLASS, "I", "Z"), skip.parameterTypes.map { it.toString() })
        assertEquals(12, skip.implementation!!.registerCount)

        val instructions = skip.instr()
        val consumed = instructions.indexOfFirst { it.opcode == Opcode.APUT_BOOLEAN }
        val hide = instructions.indexOfLast {
            it.opcode == Opcode.INVOKE_STATIC &&
                (it as ReferenceInstruction).reference.toString().contains("SkipButton;->hide")
        }
        assertTrue(hide >= 0, "the hide call is missing")
        assertEquals(Opcode.IF_EQZ, instructions[hide + 1].opcode, "the flag is not the first check of the toast block")
        assertEquals(11, (instructions[hide + 1] as OneRegisterInstruction).registerA, "the flag is not read from p2")

        val block = instructions.subList(hide + 1, instructions.size)
        assertNullGuard(instructions, hide + 2, "view")
        assertNullGuard(instructions, hide + 5, "context")
        assertTrue(consumed < hide, "the toast block precedes the consumed flip")

        val floor = hide + 1 + block.indexOfFirst { it.opcode == Opcode.CONST_WIDE_16 }
        assertTrue(floor > hide, "no 1200 ms floor")
        assertEquals(1200L, (instructions[floor] as NarrowLiteralInstruction).narrowLiteral.toLong())

        val show = instructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_VIRTUAL &&
                (it as ReferenceInstruction).reference.toString().contains("Toast;->show")
        }
        assertTrue(show > floor, "the toast is not shown after the floor")
        val stored = instructions.indexOfFirst { it.opcode == Opcode.IPUT_WIDE }
        assertTrue(stored > show, "lastToastMs is stored before show()")
    }

    @Test
    fun `the flag splits auto skips from button skips`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        val tick = context.classDefs.first { it.type == SKIP_ENGINE_CLASS }.methods
            .first { it.name == "tick" }
        assertSkipFlag(tick, 1)
        val click = context.classDefs.first { it.type == SKIP_CLICK_CLASS }.methods
            .first { it.name == "onClick" }
        assertSkipFlag(click, 0)
    }

    @Test
    fun `the behavior store uses the frozen file and keys`() {
        val store = sponsorSkipSources()[BEHAVIOR_STORE_CLASS]!!
        assertTrue(store.contains("sponsorskip_settings"))
        assertTrue(store.contains("sponsorskip_enabled"))
        for (category in Category.SPONSOR..Category.OUTRO) {
            assertTrue(store.contains("sponsorskip_settings_behavior_${Category.name(category)}"))
        }
        assertTrue(store.contains("Lcom/vk/core/preference/Preference;->f"))
    }

    @Test
    fun `the three hooks are prepended with the parameter window registers`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)

        assertHook(sponsorSkipVideoHandoffFingerprint.getMethod(context), "onVideoChanged", 3, 4)
        assertHook(sponsorSkipViewAttachedFingerprint.getMethod(context), "onViewAttached", 2, 3)
        assertHook(sponsorSkipViewSetupFingerprint.getMethod(context), "onViewAttached", 1, 2)
        assertHook(sponsorSkipHolderFingerprint.getMethod(context), "onViewAttached", 3, 4)
        assertHook(sponsorSkipReleaseFingerprint.getMethod(context), "onRelease", 2, null)
    }

    @Test
    fun `the page hook is inserted before every return`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)

        val instructions = sponsorSkipSectionListFingerprint.getMethod(context).instr()
        val hookIndexes = instructions.withIndex()
            .filter { (it.value as? ReferenceInstruction)?.reference?.toString()?.contains("/SeekbarSections;->apply") == true }
            .map { it.index }
        assertEquals(2, hookIndexes.size, "the hook is not inserted at both returns")
        hookIndexes.forEach { hook ->
            assertEquals(Opcode.RETURN_VOID, instructions[hook + 1].opcode, "hook at $hook is not before a return")
            assertEquals(Opcode.IGET_OBJECT, instructions[hook - 3].opcode)
            assertEquals(
                "Lone/video/controls/views/seek/a;->a:Landroidx/appcompat/widget/AppCompatSeekBar;",
                (instructions[hook - 3] as ReferenceInstruction).reference.toString(),
            )
        }
    }

    @Test
    fun `the locator skips EmptyAutoPlay`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        assertEquals(VIDEO_AUTO_PLAY, sponsorSkipVideoHandoffFingerprint.matchOrNull(context)?.method?.definingClass)
        assertEquals(VIDEO_AUTO_PLAY, sponsorSkipViewAttachedFingerprint.matchOrNull(context)?.method?.definingClass)
        assertEquals(VIDEO_AUTO_PLAY, sponsorSkipReleaseFingerprint.matchOrNull(context)?.method?.definingClass)
        assertEquals(VIDEO_AUTO_PLAY, sponsorSkipViewSetupFingerprint.matchOrNull(context)?.method?.definingClass)
        assertEquals(VIDEO_AUTO_PLAY, sponsorSkipHolderFingerprint.matchOrNull(context)?.method?.definingClass)
    }

    @Test
    fun `place derives its box from the laid-out container, not from a cached measure`() {
        val place = placeBody()
        assertEquals(4, Regex("getLocationInWindow").findAll(place).count(), "place does not read the surface, the container, the bar and the parent")
        assertTrue(place.contains("invoke-virtual {v2}, Landroid/view/View;->getHeight()I"), "the container height is never read")
        assertTrue(place.contains("if-lt v9, v4, :return"), "a laid-out container bails out of the placement")
        assertTrue(
            place.contains("->anchorOf($SKIP_STATE_CLASS)Landroid/view/View;"),
            "the container is not resolved from the surface",
        )
        assertTrue(!place.contains("0x7f0b098b"), "the container is still pinned to the decor-level player view id")
        assertTrue(!place.contains("0x1020002"), "the container still falls back to the window content view")
        assertTrue(place.contains("->leftMargin:I") && place.contains("->topMargin:I"))
        assertTrue(place.contains("0x800030"), "place does not pin the gravity")
        assertTrue(place.contains("isAttachedToWindow"), "a detached surface is trusted")
        assertTrue(place.contains("if-eqz v3, :return"), "the params guard is missing")
        assertTrue(place.contains("if-eqz v4, :return"), "the layout is requested even when nothing changed")
        assertTrue(
            sponsorSkipSources()[SKIP_BUTTON_CLASS]!!.replace(";;", ";")
                .contains("invoke-static {p0}, $SKIP_BUTTON_CLASS;->place($SKIP_STATE_CLASS;)V".replace(";;", ";")),
        )
    }
    @Test
    fun `the anchor is measured from the bar top in container coordinates`() {
        val place = placeBody()
        assertTrue(
            place.contains("invoke-virtual {v2, v7}, Landroid/view/View;->getLocationInWindow([I)V"),
            "the container origin is not read first",
        )
        assertTrue(
            place.contains("invoke-virtual {v4, v6}, Landroid/view/View;->getLocationInWindow([I)V"),
            "the bar location does not use the second array",
        )
        assertTrue(place.contains("aget v9, v6, v9") && place.contains("aget v4, v7, v4"), "the bar and container tops are not the compared registers")
        assertTrue(place.contains("sub-int v9, v9, v4"), "the container origin is not subtracted from the bar")
        assertTrue(place.contains("sub-int v9, v9, v5"), "the anchor does not subtract the button height")
        assertTrue(place.contains("sub-int v9, v9, v14"), "the anchor does not subtract the edge pad")
        assertTrue(place.contains(":bottom\n    sub-int v9, v13, v5"), "the bottom fallback is not in container coordinates")
    }
    @Test
    fun `the anchor is the nearest ancestor of the surface that holds a live bar`() {
        val anchor = anchorBody()
        assertTrue(
            anchor.contains("Landroid/view/View;->getParent()Landroid/view/ViewParent;"),
            "the anchor walk does not go upward from the surface",
        )
        assertTrue(anchor.contains("instance-of v6, v1, Landroid/view/View;"), "a non-view parent is cast anyway")
        val pageBar = anchor.indexOf("0x7f0b223d")
        val cardBar = anchor.indexOf("0x7f0b2236")
        assertTrue(pageBar > 0 && cardBar > 0, "the bar ids are not both looked up")
        assertTrue(pageBar < cardBar, "the page bar is not searched first")
        assertTrue(anchor.contains("if-nez v5, :have"), "the page bar hit does not skip the card bar")
        assertTrue(anchor.contains("if-lez v6, :up"), "a zero-height bar is accepted as an anchor")
        assertTrue(anchor.contains("if-eqz v6, :up"), "a detached bar is accepted as an anchor")
        assertTrue(
            anchor.contains(":up\n    invoke-virtual {v1}, Landroid/view/View;->getParent()"),
            "a bar-less ancestor does not continue the walk",
        )
        assertTrue(
            anchor.indexOf(":up") < anchor.indexOf("->bar:Landroid/view/View;"),
            "the walk runs before the bar is recorded",
        )
    }

    @Test
    fun `a wrong-parent or unmeasurable container defers instead of parking in a corner`() {
        val place = placeBody()
        val anchorAt = place.indexOf("->anchorOf(")
        assertTrue(anchorAt > 0, "place does not resolve an anchor")
        assertTrue(
            place.substringAfter("->anchorOf(").contains("if-eqz v2, :return"),
            "a missing anchor falls through to a default corner instead of returning",
        )
        assertTrue(!place.contains("getRootView"), "the window root is still used as a placement authority")
        val anchor = anchorBody()
        assertTrue(
            anchor.contains(":fallback\n    const/4 v5, 0x0"),
            "the no-bar fallback does not clear the stale bar",
        )
        assertTrue(
            anchor.substringAfter(":fallback").contains("return-object v0"),
            "the no-bar fallback is not the surface box",
        )
        assertTrue(anchor.contains(":null\n    const/4 v5, 0x0"), "the no-surface fallback is gone")
        assertTrue(anchor.substringAfter(":null").contains("return-object v5"), "a no-surface case returns a non-null view")
        assertTrue(anchor.contains("const/16 v3, 0xa") && anchor.contains("if-ge v2, v3, :fallback"), "the walk has no depth cap")
    }

    @Test
    fun `show only reveals the chip after the anchor bound`() {
        val show = showBody()
        val placeAt = show.indexOf("->place($SKIP_STATE_CLASS)V")
        val guardAt = show.indexOf("->anchor:Landroid/view/View;", placeAt)
        val revealAt = show.lastIndexOf("setVisibility")
        assertTrue(placeAt > 0 && guardAt > placeAt, "the reveal is not guarded by a bound anchor")
        assertTrue(guardAt < revealAt, "the reveal runs before the guard")
        assertTrue(
            show.contains("if-eqz v4, :return"),
            "an unresolved anchor still reveals the chip at its default position",
        )
    }

    @Test
    fun `place records the anchor it followed`() {
        val place = placeBody()
        assertTrue(place.contains("iput-object v2, v15, $SKIP_STATE_CLASS->anchor:Landroid/view/View;"), "the anchor is not remembered")
        assertTrue(place.contains("iput v4, v15, $SKIP_STATE_CLASS->anchorX:I"), "the anchor x is not remembered")
        assertTrue(place.contains("iput v4, v15, $SKIP_STATE_CLASS->anchorY:I"), "the anchor y is not remembered")
    }

    @Test
    fun `the per-tick follow re-places only when the anchor actually moved`() {
        val follow = followBody()
        assertTrue(follow.contains("->anchor:Landroid/view/View;"), "follow ignores the bound anchor")
        assertTrue(follow.contains("isAttachedToWindow"), "follow follows a detached anchor")
        assertTrue(follow.contains("const/4 v1, 0x2") && follow.contains("new-array v1, v1, [I"), "follow allocates no origin array")
        assertTrue(follow.contains("->anchorX:I") && follow.contains("->anchorY:I"), "follow never compares the stored origin")
        assertTrue(follow.contains("if-ne v0, v3, :moved"), "a moved x does not re-place")
        assertTrue(follow.contains("if-eq v0, v3, :return"), "an unchanged y still re-places")
        assertTrue(follow.substringAfter(":moved").contains("->place($SKIP_STATE_CLASS)V"), "follow does not re-place")
        assertTrue(follow.contains("const/16 v3, 0x8"), "follow does not skip a GONE button")
        assertTrue(follow.contains("if-eq v1, v3, :return"), "follow still skips a deferred (INVISIBLE) button")
    }

    @Test
    fun `place reveals a deferred button once the geometry resolves`() {
        val place = placeBody()
        assertTrue(place.contains(":vis_done"), "the deferred reveal is gone")
        assertTrue(place.contains("const/16 v5, 0x4") && place.contains("if-ne v6, v5, :vis_done"), "the reveal ignores the VISIBLE state")
        assertTrue(place.substringAfter(":vis_done").contains("setVisibility(I)V"), "the deferred button never becomes visible")
    }
    @Test
    fun `place runs on sixteen registers with the parameter window kept last`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        val method = MutableMethod(context.classDefs.first { it.type == SKIP_BUTTON_CLASS }.methods.first { it.name == "place" })
        val source = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!.substringAfter(".method public static place")
        assertTrue(source.contains("move-object/from16 v15, p0"), "the parameter is not the window register")
        assertEquals(16, method.implementation!!.registerCount, "the register count does not match the reference map")
        assertTrue(source.contains(".registers 16"), "the source and the assembled frame disagree")
        val bound = 15
        method.instr().forEach { instruction ->
            if (instruction is OneRegisterInstruction) {
                assertTrue(instruction.registerA in 0 until bound + 1, "${instruction.opcode} reads a register past the frame")
            }
            (instruction as? TwoRegisterInstruction)?.let {
                assertTrue(it.registerA <= bound && it.registerB <= bound, "${instruction.opcode} reads a register past the frame")
            }
            // 22c field accesses only reach v15, which is why the frame is capped here.
            (instruction as? ThreeRegisterInstruction)?.let {
                assertTrue(it.registerA <= bound && it.registerB <= bound && it.registerC <= bound, "${instruction.opcode} reads past the frame")
            }
        }
    }
    @Test
    fun `place neither measures nor re-caps the label`() {
        val place = placeBody()
        assertEquals(0, Regex("->measure").findAll(place).count(), "place measures")
        assertEquals(0, Regex("->setMaxWidth").findAll(place).count(), "place caps the width")
        assertEquals(0, Regex("->setMaxHeight").findAll(place).count(), "place caps the height")
        assertEquals(0, Regex("DisplayMetrics").findAll(place).count(), "place reads the display metrics instead of the container")
    }
    @Test
    fun `an unlaid or missing bar falls back to the container bottom`() {
        val place = placeBody()
        assertTrue(place.contains(":bottom\n    sub-int v9, v13, v5"), "the fallback is not the container bottom")
        assertTrue(place.contains("if-eqz v4, :bottom"), "the missing bar is not the fallback form")
        assertTrue(place.contains("if-lez v9, :bottom"), "a zero bar height does not reach the fallback")
        assertTrue(place.contains("if-eqz v9, :bottom"), "a detached bar does not reach the fallback")
    }
    @Test
    fun `show measures the label and writes the measured size before revealing it`() {
        val show = showBody()
        assertTrue(show.contains("Landroid/view/View;->measure(II)V"), "the button is never measured")
        assertTrue(show.contains("getMeasuredWidth") && show.contains("getMeasuredHeight"), "the measurement is not read")
        assertTrue(show.contains("->width:I") && show.contains("->height:I"), "the measured size is not written")
    }

    @Test
    fun `the button stays invisible until its size is written and placed`() {
        val show = showBody()
        val invisibleAt = show.indexOf("invoke-virtual {v2, v4}, Landroid/view/View;->setVisibility(I)V")
        val measureAt = show.indexOf("->measure(II)V")
        val writeAt = show.indexOf("->width:I")
        val placeAt = show.indexOf("->place($SKIP_STATE_CLASS)V")
        val visibleAt = show.lastIndexOf("setVisibility")
        assertTrue(invisibleAt in 0 until measureAt, "the button is measured while visible")
        assertTrue(measureAt < writeAt, "the size is written before measuring")
        assertTrue(writeAt < placeAt && placeAt < visibleAt, "the reveal does not follow the write and the place")
        assertTrue(show.substringAfter(":m_params").contains("const/4 v4, 0x0"), "the reveal is not a VISIBLE flip")
    }

    @Test
    fun `the measure block runs on a reused button`() {
        val show = showBody()
        assertTrue(show.substringAfter("\n    :update").contains("->measure(II)V"), "the measure sits in the create branch")
        assertTrue(!show.substringAfter("\n    :create").substringBefore(":attach").contains("->measure(II)V"), "the create branch measures")
    }

    @Test
    fun `the params carry the measured width and are written after the attach`() {
        val show = showBody()
        assertTrue(show.contains("iput v6, v5, Landroid/view/ViewGroup\$LayoutParams;->width:I"), "the measured width is not the one written")
        assertTrue(show.contains("const/4 v4, 0x1\n    if-ge v6, v4, :m_params"), "the written width has no positive floor")
        val addAt = show.indexOf("->addView(")
        val writeAt = show.indexOf("->width:I")
        assertTrue(addAt in 0 until writeAt, "the params are written before the view is attached")
    }

    @Test
    fun `the text style is re-applied on the reused button`() {
        val update = showBody().substringAfter("\n    :update")
        assertTrue(update.contains("->setPadding(IIII)V"), "padding is not re-applied on re-show")
        assertTrue(update.contains("->setTextSize(IF)V"), "text size is not re-applied on re-show")
    }

    @Test
    fun `place prefers the freshly requested size over a stale laid-out one`() {
        val place = placeBody()
        assertTrue(place.contains("if-lez v4, :w_ok"), "a missing requested width does not fall back")
        assertTrue(place.contains("iget v4, v3, Landroid/view/ViewGroup\$LayoutParams;->width:I"), "the width is not read from the params")
        assertTrue(place.contains("if-eq v4, v8, :w_ok"), "a stale differing width is not replaced by the requested one")
        assertTrue(place.contains("if-lez v4, :h_ok"), "a missing requested height does not fall back")
        assertTrue(place.contains("iget v4, v3, Landroid/view/ViewGroup\$LayoutParams;->height:I"), "the height is not read from the params")
        assertTrue(place.contains("if-eq v4, v5, :h_ok"), "a stale differing height is not replaced by the requested one")
    }

    @Test
    fun `the clamps raise a coordinate instead of keeping the corner`() {
        val place = placeBody()
        assertTrue(place.contains("if-gt v4, v10, :bounds_ok") && place.contains("if-gt v5, v11, :bounds_set"), "an empty intersection is accepted")
        assertTrue(place.contains("if-le v4, v8, :clip_r") && place.contains("if-le v5, v9, :clip_b"), "the surface is not clipped to the container")
        assertTrue(place.contains("if-le v12, v9, :x_hi") && place.contains("if-le v9, v4, :y_hi"), "a valid coordinate is pulled down to the bottom limit")
        assertTrue(place.contains("if-ge v12, v9, :x_done") && place.contains("if-ge v9, v4, :y_done"), "a coordinate below the minimum is kept")
    }
    @Test
    fun `the margins are shifted into the button parent origin`() {
        val place = placeBody()
        assertTrue(place.contains(":no_shift") && place.contains("if-eqz v10, :no_shift"), "a detached button has no origin to shift by")
        assertTrue(place.contains("instance-of v4, v10, Landroid/view/View;"), "a non-view parent is cast anyway")
        assertTrue(place.contains("sub-int v11, v11, v4") && place.contains("add-int/2addr v12, v11"), "x is not shifted to the parent")
        assertTrue(place.contains("add-int/2addr v9, v11"), "y is not shifted to the parent")
        assertTrue(
            place.indexOf("add-int/2addr v9, v11") < place.indexOf("->leftMargin:I"),
            "the shift runs after the margins are written",
        )
    }
    @Test
    fun `the bounds only narrow, never widen`() {
        val place = placeBody()
        assertTrue(place.contains("if-eq v1, v2, :bounds_done"), "a live surface is skipped")
        assertTrue(place.contains("if-eqz v4, :bounds_done"), "a detached surface is trusted")
    }
    @Test
    fun `the measure keeps the width floor and place re-caps against the container`() {
        val button = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        assertEquals(1, Regex("\\.method public static minWidth").findAll(button).count(), "the floor helper is duplicated")
        assertTrue(showBody().contains("->minWidth(Landroid/content/Context;I)I"), "the measure ignores the floor helper")
        assertTrue(placeBody().contains("iput v8, v3, Landroid/view/ViewGroup\$LayoutParams;->width:I"), "place does not write the size back")
    }
    @Test
    fun `the floor raises the width instead of lowering it`() {
        assertTrue(
            showBody().contains("if-ge v6, v4, :m_w\n    move v6, v4"),
            "the measure keeps the smaller width when the floor is larger",
        )
    }
    @Test
    fun `the height spec is reduced by the inset and floored at one`() {
        val show = showBody()
        assertTrue(show.contains("sub-int v7, v10, v11") && show.contains("sub-int/2addr v7, v11"), "the height spec ignores the inset")
        assertTrue(show.contains("const/4 v4, 0x1\n    if-ge v7, v4, :m_h"), "the height spec has no floor")
        assertTrue(show.contains("if-le v7, v9, :m_clamp"), "the measured height is not clamped to the available one")
    }

    @Test
    fun `the layout listener follows the surface, the button and the container, then detaches`() {
        val button = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        assertTrue(
            button.replace(";;", ";")
                .contains("$SKIP_LAYOUT_CLASS;->watch($SKIP_STATE_CLASS;Landroid/view/View;Landroid/view/View;Landroid/view/View;)V".replace(";;", ";")),
            "place does not watch the container",
        )
        val layout = sponsorSkipSources()[SKIP_LAYOUT_CLASS]!!
        assertEquals(3, Regex("addOnLayoutChangeListener").findAll(layout).count(), "not all three slots are watched")
        assertTrue(layout.contains("if-nez v0, :created") && layout.contains(":created"), "the listener is not created on demand")
        assertTrue(layout.contains("if-eq v1, p1"), "the surface slot does not skip the same view")
        assertTrue(layout.contains("if-eq v1, p2"), "the button slot does not skip the same view")
        assertTrue(layout.contains("if-eq v1, p3"), "the container slot does not skip the same view")
        val stop = sponsorSkipSources()[SKIP_ENGINE_CLASS]!!.substringAfter(".method private static stop")
        assertEquals(3, Regex("removeOnLayoutChangeListener").findAll(stop).count(), "stop leaves a listener attached")
        assertTrue(stop.contains("->listenerView:Landroid/view/View;"))
        assertTrue(stop.contains("->buttonView:Landroid/view/View;"))
        assertTrue(stop.contains("->listenerContainer:Landroid/view/View;"))
    }
    @Test
    fun `every view-mutating entry point guards before its body`() {
        guardedEntries.forEach { entry ->
            val body = engineBody(entry.name, entry.parameters)
            val guard = body.indexOf("onMain()Z")
            assertTrue(guard > 0, "${entry.name} has no guard")
            val prefix = body.substring(0, guard)
            val calls = prefix.lines().map { it.trim() }.filter { it.startsWith("invoke-") }
            assertTrue(calls.dropLast(1).all { it.contains("->log(Ljava/lang/String;)V") }, "${entry.name} calls its body first: $calls")
            assertTrue(calls.isNotEmpty() && calls.last().endsWith("->"), "${entry.name} guard is not the next call: $calls")
            val defer = body.indexOf("if-nez", guard)
            assertTrue(defer in (guard + 1) until body.length, "${entry.name} does not branch on off-main")
            val bodyLabel = body.indexOf("\n    :on_main", defer)
            assertTrue(bodyLabel > defer, "${entry.name} has no body label")
            val block = body.substring(defer, bodyLabel)
            val dispatches = block.contains("->postVideo(") || block.contains("->postMain(") || (block.contains("new-instance") && block.contains(SKIP_MAIN_CLASS))
            assertTrue(dispatches, "${entry.name} does not dispatch off-main")
            if (block.contains("new-instance")) {
                assertEquals(entry.op, rangeOp(block, block.indexOf("new-instance")), "${entry.name} posts the wrong op")
            }
            assertTrue(block.contains("postMain(Ljava/lang/Runnable;)V") || block.contains("->postVideo("), "${entry.name} does not post the dispatch")
            assertTrue(block.trimEnd().endsWith("return-void"), "${entry.name} falls through after posting")
            val after = body.substring(bodyLabel)
            val contact = after.indexOf(entry.firstBody)
            val nextLabel = after.indexOf("\n    :", 1)
            assertTrue(contact > 0, "${entry.name} does not open its body with ${entry.firstBody}")
            assertTrue(nextLabel < 0 || contact < nextLabel, "${entry.name} opens its body late")
        }
    }

    @Test
    fun `stop posts its removal instead of removing inline`() {
        val stop = engineBody("stop", "$SKIP_STATE_CLASS", public = false)
        assertTrue(!stop.contains("ViewGroup;->removeView"), "stop still removes inline")
        val guard = stop.indexOf("if-eqz v5, :overlay")
        assertTrue(guard > 0, "the deferral is not on the button-non-null path")
        val block = stop.substring(guard, stop.indexOf("\n    :overlay"))
        val built = block.indexOf("new-instance v0, $SKIP_MAIN_CLASS")
        assertTrue(built >= 0, "the removal is not a SKIP_MAIN_CLASS")
        assertEquals(3, rangeOp(block, built), "the op is not the removal op")
        assertTrue(
            block.contains("iget-object v3, p0, $SKIP_STATE_CLASS->controller:Lp73/a;"),
            "the full window is not filled: controller",
        )
        assertTrue(block.contains("const/4 v4, 0x0"), "the full window is not filled: video")
        assertTrue(block.indexOf("postMain(Ljava/lang/Runnable;)V") > built, "the runnable is not posted")
        assertTrue(block.contains("const-string v1, \"stop\""), "the deferral is not traced")
        val cleared = stop.substringAfter(":overlay")
        assertTrue(cleared.contains("iput-object v0, p0, $SKIP_STATE_CLASS->view:Landroid/view/View;"), "stop drops the view")
        assertTrue(
            !cleared.contains("$SKIP_STATE_CLASS->button:Landroid/widget/TextView;"),
            "stop nulls the button before the posted op can identity-check it",
        )
    }

    @Test
    fun `the deferred removal is identity-guarded and cleans up`() {
        val run = sponsorSkipSources()[SKIP_MAIN_CLASS]!!
        val op3 = run.substringAfter("\n    :op3")
        val removeAt = op3.indexOf("->removeView(Landroid/view/View;)V")
        assertTrue(removeAt >= 0, "op 3 does not remove")
        val stateAt = op3.indexOf("->state:$SKIP_STATE_CLASS")
        assertTrue(stateAt in 0 until removeAt, "the state is read after the mutation")
        val nullButton = op3.indexOf("->button:Landroid/widget/TextView;\n    if-eqz")
        assertTrue(nullButton >= 0 && nullButton < removeAt, "a null button does not return before the mutation")
        val otherButton = op3.indexOf("if-ne v2, v3, :return")
        assertTrue(otherButton > nullButton && otherButton < removeAt, "another button does not return before the mutation")
        assertTrue(op3.indexOf("trace(") < removeAt, "the removal is not traced")
        val buttonWrite = op3.indexOf("iput-object v0, v1, $SKIP_STATE_CLASS->button:Landroid/widget/TextView;")
        val viewWrite = op3.indexOf("iput-object v0, v1, $SKIP_STATE_CLASS->view:Landroid/view/View;")
        assertTrue(buttonWrite > removeAt, "the button is nulled before the removal")
        assertTrue(viewWrite > removeAt, "the view is nulled before the removal")
    }

    @Test
    fun `show keeps the button in its current container`() {
        val show = showBody()
        val attach = show.indexOf("\n    :attach")
        val add = show.indexOf("\n    :attach_add")
        assertTrue(attach in 0 until add, "the attach/add labels moved")
        assertTrue(!show.substring(0, attach).contains("if-nez v4, :update"), "the early-out is still before the resolution")
        val block = show.substring(attach, add)
        assertTrue(block.indexOf("getRootView") in 0 until block.indexOf("check-cast v5, Landroid/view/ViewGroup;"), "the container is not resolved first")
        assertTrue(block.contains("move-result-object v4") && block.contains("getParent()Landroid/view/ViewParent;"), "the current parent is not re-read")
        val removeAt = block.indexOf("->removeView(Landroid/view/View;)V")
        assertTrue(removeAt > 0, "a stale parent is not detached")
        assertTrue(block.contains("if-eqz v4, :attach_add"), "a detached button does not fall into the add")
        val same = block.indexOf("if-eq v4, v5, :update")
        assertTrue(same in 0 until removeAt, "a same-parent button is re-attached")
        assertTrue(
            block.contains("check-cast v4, Landroid/view/ViewGroup;\n    invoke-virtual {v4, v2}, Landroid/view/ViewGroup;->removeView(Landroid/view/View;)V"),
            "the current parent is not the checked receiver",
        )
    }

    @Test
    fun `no mutator runs before the guard and every mutator keeps a declared root`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        val injected = sponsorSkipSources().keys
        val classes = context.classDefs.filter { it.type in injected }
        val methods = classes.flatMap { it.methods }

        guardedEntries.forEach { entry ->
            val method = methods.firstOrNull {
                it.name == entry.name && it.parameterTypes.size == entry.parameterCount
            } ?: error("${entry.name} missing among ${methods.map { it.name }.distinct()}")
            val instructions = method.implementation!!.instructions.toList()
            val guard = instructions.indexOfFirst {
                it.opcode == Opcode.INVOKE_STATIC &&
                    (it as ReferenceInstruction).reference.toString() == "$SKIP_ENGINE_CLASS->onMain()Z"
            }
            assertTrue(guard >= 0, "${entry.name} has no guard")
            val branch = instructions[guard + 2]
            assertEquals(Opcode.IF_NEZ, branch.opcode, "${entry.name}: the guard does not branch")
            val bodyAt = instructions.indexOfFirst {
                it.opcode == branch.opcode && (it as OffsetInstruction).codeOffset == (branch as OffsetInstruction).codeOffset
            }
            instructions.subList(0, bodyAt).forEach { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return@forEach
                val callee = classes.firstOrNull { it.type == reference.definingClass }
                    ?.methods?.firstOrNull {
                        it.name == reference.name && it.parameterTypes.joinToString("") == reference.parameterTypes.joinToString("")
                    }
                assertTrue(callee == null || !callee.mutatesStructure(), "${entry.name}: ${reference.name} mutates before the guard")
            }
        }

        val keys = methods.associateBy { it.key() }
        val mutators = methods.filter { it.mutatesStructure() }.map { it.key() }.toSet()
        assertEquals(
            setOf("$SKIP_MAIN_CLASS->run()V", "$SKIP_BUTTON_CLASS->show(${SKIP_STATE_CLASS}I)V"),
            mutators,
            "the mutator inventory moved",
        )

        val callers = mutableMapOf<String, MutableSet<String>>()
        methods.forEach { method ->
            val owner = method.key()
            method.implementation?.instructions?.forEach { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return@forEach
                val target = "${reference.definingClass}->${reference.name}(${reference.parameterTypes.joinToString("")})${reference.returnType}"
                if (target in keys) callers.getOrPut(target) { mutableSetOf() }.add(owner)
            }
        }

        val roots = mutators.flatMap { mutator ->
            val seen = mutableSetOf(mutator)
            val queue = ArrayDeque(listOf(mutator))
            val reached = mutableSetOf<String>()
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val parents = callers[node].orEmpty() - seen
                seen += parents
                queue += parents
                if (callers[node].isNullOrEmpty()) reached += node
            }
            reached
        }.toSet()

        // Each root carries why it may reach a mutation at all.
        val allowed = mapOf(
            "$SKIP_ENGINE_CLASS->onVideoChanged(Lp73/a;Lcom/vk/dto/common/VideoFile;)V" to "guarded entry, posts op 0",
            "$SKIP_ENGINE_CLASS->onRelease(Lp73/a;)V" to "guarded entry, posts op 1",
            "$SKIP_ENGINE_CLASS->onSeek(Ljava/lang/Object;J)V" to "guarded entry, posts op 2",
            "$SKIP_ENGINE_CLASS->onViewAttached(Lp73/a;Landroid/view/View;)V" to "guarded entry, posts op 4",
            "$SKIP_ENGINE_CLASS->onViewAttached(Lb83/a;Landroid/view/View;)V" to "guarded entry, posts op 4",
            "$SKIP_TICK_CLASS->run()V" to "reposted only on the handler built from getMainLooper()",
            "$SKIP_CLICK_CLASS->onClick(Landroid/view/View;)V" to "view dispatch is main",
            "$SKIP_LAYOUT_CLASS->onLayoutChange(Landroid/view/View;IIIIIIII)V" to "dispatched by the layout pass, main by construction",
            "$SKIP_MAIN_CLASS->run()V" to "drained from the main Handler",
        )
        assertTrue(roots.all { it in allowed }, "mutators lost their declared root: ${roots - allowed.keys}")
    }

    @Test
    fun `the guard handler is the main looper`() {
        val engine = sponsorSkipSources()[SKIP_ENGINE_CLASS]!!
        val clinit = engine.substringAfter(".method static constructor <clinit>").substringBefore(".end method")
        assertTrue(clinit.contains("getMainLooper()Landroid/os/Looper;"), "MAIN is not built on the main looper")
        assertTrue(clinit.contains("Landroid/os/Handler;-><init>(Landroid/os/Looper;)V"), "MAIN is not a Handler for it")
        val onMain = engineBody("onMain", "")
        assertTrue(onMain.contains("myLooper()Landroid/os/Looper;"), "onMain does not read the current looper")
        assertTrue(onMain.contains("getMainLooper()Landroid/os/Looper;"), "onMain does not compare against the main looper")
        assertTrue(onMain.contains("if-eq v0, v1, :same"), "a looper-less thread is not the false path")
    }

    @Test
    fun `the null-video teardown is deferred to main too`() {
        val body = engineBody("onVideoChanged", "Lp73/a;Lcom/vk/dto/common/VideoFile;")
        val guard = body.indexOf("onMain()Z")
        val defer = body.indexOf("if-nez", guard)
        assertTrue(defer > guard, "the off-main branch is not behind the guard")
        val bodyLabel = body.indexOf("\n    :on_main", defer)
        assertTrue(bodyLabel > defer, "the main-thread body label is missing")
        val nullCheck = body.indexOf("if-eqz v1, :v1null")
        assertTrue(nullCheck > bodyLabel, "the null-video test runs before the off-main deferral")
        val nullLabel = body.indexOf("\n    :v1null")
        assertTrue(nullLabel > nullCheck, "the null-video label is missing")
        val nullPath = body.substring(nullLabel, body.indexOf("\n    :clear", nullLabel))
        assertTrue(nullPath.contains("goto :clear"), "the null-video path does not reach the clear")
        val clear = body.substringAfter("\n    :clear").substringBefore("\n    :return")
        assertTrue(clear.contains("->clear(Lp73/a;)V"), "the null-video path no longer clears the state")
        val mainPath = body.substring(defer, bodyLabel)
        assertTrue(mainPath.trimEnd().endsWith("return-void"), "the off-main branch falls through into the body")
        assertTrue(bodyLabel < body.indexOf("getDuration"), "the video is dereferenced before the null test")
    }

    @Test
    fun `onViewAttached defers the redraw to main and hands the view over`() {
        val attach = engineBody("onViewAttached", "Lp73/a;Landroid/view/View;")
        assertTrue(attach.contains("onMain()Z"), "onViewAttached does not check the thread")
        assertTrue(attach.contains("new-instance v1, $SKIP_MAIN_CLASS"), "the off-main path does not defer")
        assertEquals(4, rangeOp(attach, attach.indexOf("new-instance")), "the redraw op is wrong")
        assertTrue(attach.contains("->attachView:Landroid/view/View;"), "the attach event does not record the view")
        assertTrue(
            sponsorSkipSources()[SKIP_ENGINE_CLASS]!!.contains(".field private static volatile LAST_VIEW:"),
            "the cross-thread view handoff is not volatile",
        )
    }
    @Test
    fun `the diagnostic reports thread and layout state`() {
        val trace = engineBody("trace", "Ljava/lang/String;Landroid/view/View;")
        assertTrue(trace.contains("Thread;->currentThread()"), "the thread is not reported")
        assertTrue(trace.contains("Thread;->getName()"), "the thread name is not reported")
        assertTrue(trace.contains("ViewGroup;->isInLayout()Z"), "the layout state is not reported")
        assertTrue(trace.contains("|inLayout=1") && trace.contains("|inLayout=0"), "both layout states are not distinguished")
        assertTrue(showBody().contains("const-string v10, \"show\"") && showBody().contains("trace(Ljava/lang/String;Landroid/view/View;)V"), "show is not traced")
        assertTrue(engineBody("stop", "$SKIP_STATE_CLASS", public = false).contains("trace("), "stop is not traced")
        assertTrue(sponsorSkipSources()[SKIP_MAIN_CLASS]!!.contains("trace("), "the removal is not traced")
    }

    @Test
    fun `the tick evaluates every pass and never gates the skip on playback`() {
        val engine = sponsorSkipSources()[SKIP_ENGINE_CLASS]!!
        val tick = engine.substring(engine.indexOf(".method public static tick"), engine.indexOf(".method private static evaluate"))
        val evaluateAt = tick.indexOf("->evaluate(")
        val skipAt = tick.indexOf("->skip(")
        assertTrue(evaluateAt >= 0, "the tick never evaluates")
        assertTrue(skipAt > evaluateAt, "the skip does not follow the scan")
        // The page player reports a playing state the position does not always follow;
        // a gate on isPlaying() is what killed autoskip.
        assertTrue(!tick.contains("->isPlaying()"), "the skip is still gated by playback")
    }

    @Test
    fun `the seek hook re-evaluates before the return`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        val instructions = sponsorSkipSeekFingerprint.getMethod(context).instr()
        val at = instructions.indexOfFirst { it.opcode == Opcode.RETURN_VOID } - 1
        assertEquals(Opcode.INVOKE_STATIC, instructions[at].opcode)
        val hook = instructions[at] as ReferenceInstruction
        assertEquals("$SKIP_ENGINE_CLASS->onSeek(Ljava/lang/Object;J)V", hook.reference.toString())
        val registers = hook as FiveRegisterInstruction
        assertEquals(3, registers.registerC)
        assertEquals(4, registers.registerD)
        assertEquals(5, registers.registerE)
    }

    @Test
    fun `the guards cover the added members`() {
        assertTrue(requiredMethods.getValue(SKIP_BUTTON_CLASS).contains("place:1"))
        assertTrue(requiredMethods.getValue(SKIP_BUTTON_CLASS).contains("textColor:0"))
        assertTrue(requiredMethods.getValue(SKIP_ENGINE_CLASS).contains("onSeek:2"))
        assertTrue(requiredMethods.getValue(SKIP_ENGINE_CLASS).contains("redraw:1"))
        assertTrue(requiredMethods.getValue(CATEGORY_CLASS).contains("label:1"))
        assertTrue(requiredFields.getValue(SKIP_STATE_CLASS).contains("lastToastMs"))
        assertTrue(requiredMethods.getValue(SKIP_LAYOUT_CLASS).containsAll(listOf("<init>:1", "onLayoutChange:9", "watch:4")))
        assertTrue(
            requiredFields.getValue(SKIP_STATE_CLASS)
                .containsAll(listOf("view", "attachView", "button", "listener", "listenerView", "buttonView", "listenerContainer", "lastIndex")),
        )
        assertTrue(requiredFields.getValue(SKIP_STATE_CLASS).containsAll(listOf("lastPosMs", "stallTicks", "frozen")))
        assertTrue(sponsorSkipSources().containsKey(SKIP_LAYOUT_CLASS))
        assertTrue(requiredMethods.getValue(SKIP_ENGINE_CLASS).containsAll(listOf("onMain:0", "mainHandler:0", "postMain:1", "trace:2")))
        assertTrue(requiredMethods.getValue(SKIP_MAIN_CLASS).containsAll(listOf("<init>:5", "run:0")))
        assertTrue(requiredFields.getValue(SKIP_ENGINE_CLASS).contains("MAIN"))
        assertTrue(requiredFields.getValue(SKIP_MAIN_CLASS).containsAll(listOf("op", "state", "controller", "video", "target")))
        assertTrue(sponsorSkipSources().containsKey(SKIP_MAIN_CLASS))
    }
    @Test
    fun `evaluate consumes and hides a segment that has ended`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        val evaluate = context.classDefs.first { it.type == SKIP_ENGINE_CLASS }.methods
            .first { it.name == "evaluate" }
        val instructions = evaluate.instr()

        val position = instructions.indexOfFirst {
            (it as? ReferenceInstruction)?.reference?.toString()?.endsWith(";->getPosition()J") == true
        }
        assertTrue(position >= 0, "evaluate does not read the position")
        assertEquals(Opcode.MOVE_RESULT_WIDE, instructions[position + 1].opcode, "the position is not kept")
        val positionRegister = (instructions[position + 1] as OneRegisterInstruction).registerA

        // The over-check is the endMs read that feeds a compare with the live position;
        // the scan's endMs read is compared against the 0x2ee window instead.
        val endRead = instructions.indices.first { index ->
            instructions[index].opcode == Opcode.IGET_WIDE &&
                (instructions[index] as ReferenceInstruction).reference.toString().contains("endMs:J") &&
                instructions.getOrNull(index + 1)?.opcode == Opcode.CMP_LONG
        }
        val compare = instructions[endRead + 1] as ThreeRegisterInstruction
        assertEquals(positionRegister, compare.registerB, "the compare does not read the position first")
        assertEquals(
            (instructions[endRead] as OneRegisterInstruction).registerA,
            compare.registerC,
            "the compare does not read the segment end second",
        )

        val guard = instructions[endRead + 2]
        assertEquals(Opcode.IF_LTZ, guard.opcode, "the end check is not a single-register if-ltz")
        assertEquals(compare.registerA, (guard as OneRegisterInstruction).registerA)
        val insideTarget = branchTarget(instructions, endRead + 2)
        assertEquals(
            Opcode.IGET_OBJECT,
            instructions[insideTarget].opcode,
            "the still-live branch does not rejoin before the button read",
        )

        val flag = instructions.indexOfFirst {
            it.opcode == Opcode.CONST_4 && (it as NarrowLiteralInstruction).narrowLiteral == 1
        }
        val aput = instructions.indexOfFirst { it.opcode == Opcode.APUT_BOOLEAN }
        val hide = instructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_STATIC &&
                (it as ReferenceInstruction).reference.toString().contains("SkipButton;->hide")
        }
        val show = instructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_STATIC &&
                (it as ReferenceInstruction).reference.toString().contains("SkipButton;->show")
        }
        assertTrue(flag in (endRead + 3) until aput, "the consumed flag is not set after the end check")
        assertTrue(aput < hide, "the segment is not consumed before the hide")
        assertTrue(hide < show, "the terminal block does not return before the show")
        assertTrue((guard as com.android.tools.smali.dexlib2.builder.BuilderInstruction).location.codeAddress > 0)
        assertTrue(insideTarget < show, "the live branch jumps past the show")

        // The reveal path clears the flag again so a re-entered segment is showable.
        assertEquals(Opcode.APUT_BOOLEAN, instructions[show - 1].opcode, "the reveal does not re-clear the flag")
        val clear = instructions[show - 2]
        assertEquals(Opcode.CONST_4, clear.opcode)
        assertEquals(0, (clear as NarrowLiteralInstruction).narrowLiteral)
    }

    @Test
    fun `a tap on a passed section does not seek`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        val skip = context.classDefs.first { it.type == SKIP_ENGINE_CLASS }.methods
            .first { it.name == "skip" }
        val instructions = skip.instr()

        val position = instructions.indexOfFirst {
            (it as? ReferenceInstruction)?.reference?.toString()?.endsWith(";->getPosition()J") == true
        }
        assertTrue(position >= 0, "skip does not read the position")
        assertEquals(Opcode.MOVE_RESULT_WIDE, instructions[position + 1].opcode, "the position is not kept")
        val positionRegister = (instructions[position + 1] as OneRegisterInstruction).registerA

        val deadCompare = instructions[position + 3]
        assertEquals(Opcode.CMP_LONG, deadCompare.opcode)
        assertEquals(positionRegister, (deadCompare as ThreeRegisterInstruction).registerB, "the -1 compare reads another value")
        val deadGuard = instructions[position + 4]
        assertEquals(Opcode.IF_EQZ, deadGuard.opcode, "a missing position does not return (no position yet)")
        // No position yet leaves the segment armed: the return must precede the aput.
        val noPositionReturn = branchTarget(instructions, position + 4)
        assertEquals(Opcode.RETURN_VOID, instructions[noPositionReturn].opcode, "the no-position arm does not return")

        val aput = instructions.indexOfFirst { it.opcode == Opcode.APUT_BOOLEAN }
        assertTrue(aput > position + 9, "the segment is consumed before the position is known")
        val seekCall = instructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_INTERFACE && (it as ReferenceInstruction).reference.toString().contains("(J)V") &&
                !(it as ReferenceInstruction).reference.toString().endsWith("getDuration()J")
        }
        assertTrue(seekCall > aput, "the seek runs before the consume")

        // after the -1 guard: the consumed guard, then the endMs compare
        assertEquals(Opcode.IGET_OBJECT, instructions[position + 5].opcode, "the consumed array is not read")
        assertEquals(Opcode.AGET_BOOLEAN, instructions[position + 6].opcode)
        assertEquals(Opcode.IF_NEZ, instructions[position + 7].opcode, "an already consumed segment is not skipped")
        val endCompare = instructions[position + 8]
        assertEquals(Opcode.CMP_LONG, endCompare.opcode, "the endMs compare does not follow the consumed guard")
        assertEquals(positionRegister, (endCompare as ThreeRegisterInstruction).registerB, "the end compare reads another value")
        val endGuard = instructions[position + 9]
        assertEquals(Opcode.IF_LTZ, endGuard.opcode, "the live arm is not `if-ltz` (position before the end)")

        // The live arm jumps to the seek, and a position at/past the end falls through
        // into the same consume-and-hide tail the seek uses.
        val seekTarget = branchTarget(instructions, position + 9)
        val seek = instructions.withIndex().indexOfFirst { (index, instruction) ->
            index >= seekTarget && instruction.opcode == Opcode.INVOKE_INTERFACE &&
                (instruction as ReferenceInstruction).reference.toString().endsWith(";->getDuration()J")
        }
        assertTrue(seek >= seekTarget, "the seek arm does not reach getDuration")
        assertTrue(
            instructions.filterIndexed { index, _ -> index > position + 6 && index < seekTarget }.any { it.opcode == Opcode.APUT_BOOLEAN },
            "the passed section is not consumed",
        )
        assertEquals(Opcode.RETURN_VOID, instructions[seekTarget - 1].opcode, "the passed section does not stop before the seek")
        assertEquals(2, instructions.count { it.opcode == Opcode.RETURN_VOID }, "skip must keep the passed tail and the shared exit")
    }

    @Test
    fun `an unattached player hides the button only after the grace`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        val tick = context.classDefs.first { it.type == SKIP_ENGINE_CLASS }.methods
            .first { it.name == "tick" }.instr()

        val attached = tick.indexOfFirst {
            (it as? ReferenceInstruction)?.reference?.toString() == "Landroid/view/View;->isAttachedToWindow()Z"
        }
        assertTrue(attached >= 0, "tick does not probe the attachment")
        assertEquals(Opcode.MOVE_RESULT, tick[attached + 1].opcode, "the attachment result is not kept")

        val position = tick.indexOfFirst {
            it.opcode == Opcode.INVOKE_INTERFACE &&
                (it as ReferenceInstruction).reference.toString().endsWith(";->getPosition()J")
        }
        assertTrue(position > attached, "the position is read before the attachment probe")
        assertEquals(Opcode.MOVE_RESULT_WIDE, tick[position + 1].opcode)
        val minusOne = tick[position + 2]
        assertEquals(Opcode.CONST_WIDE_16, minusOne.opcode)
        assertEquals(-1L, (minusOne as NarrowLiteralInstruction).narrowLiteral.toLong())
        assertEquals(Opcode.CMP_LONG, tick[position + 3].opcode)
        // A missing position no longer routes to the dead block; the live arm lands on
        // the remembered-position write instead.
        assertEquals(Opcode.IF_NEZ, tick[position + 4].opcode, "-1 does not leave the position")

        val liveArm = branchTarget(tick, position + 4)
        assertTrue(liveArm > position + 4, "the -1 arm does not jump forward")
        val readsLast = tick.subList(liveArm, minOf(liveArm + 12, tick.size)).any {
            it.opcode == Opcode.IGET_WIDE &&
                (it as ReferenceInstruction).reference.toString().contains("lastPosMs:J")
        }
        assertTrue(readsLast, "the live arm does not read the remembered position")

        val deadRead = tick.indexOfLast {
            it.opcode == Opcode.IGET &&
                (it as ReferenceInstruction).reference.toString().contains("deadTicks:I")
        }
        assertTrue(deadRead > position, "the dead counter is not read after the position")
        val deadGuard = deadRead + 1
        assertEquals(Opcode.CONST_4, tick[deadGuard].opcode, "the dead threshold is not loaded")
        assertEquals(4, (tick[deadGuard] as NarrowLiteralInstruction).narrowLiteral, "the dead grace is not four ticks")
        assertEquals(Opcode.IF_GE, tick[deadGuard + 1].opcode, "sub-threshold counts do not stay live")
        val live = branchTarget(tick, deadGuard + 1)
        val dead = tick.indexOfFirst {
            it.opcode == Opcode.INVOKE_STATIC &&
                (it as ReferenceInstruction).reference.toString().contains("SkipButton;->hide")
        }
        assertTrue(dead > deadGuard && dead < live, "the dead block does not hide inside its arm")
        assertTrue(
            tick.filterIndexed { index, _ -> index in (dead + 1) until live }.any { it.opcode == Opcode.GOTO },
            "the dead arm does not leave past the scan",
        )

        val invalidate = tick.indexOfFirst {
            (it as? ReferenceInstruction)?.reference?.toString()?.endsWith("SeekbarSections;->invalidate()V") == true
        }
        val evaluate = tick.indexOfFirst {
            (it as? ReferenceInstruction)?.reference?.toString()?.endsWith("${SKIP_ENGINE_CLASS}->evaluate($SKIP_STATE_CLASS)I") == true
        }
        assertTrue(dead > invalidate, "the dead block runs before the section refresh")
        assertTrue(dead < evaluate, "the dead block does not stand down before the scan")
    }

    @Test
    fun `an unchanged position freezes only after the time window and keeps the button`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        val engine = context.classDefs.first { it.type == SKIP_ENGINE_CLASS }

        val tick = engine.methods.first { it.name == "tick" }.instr()
        val lastPos = tick.indexOfFirst {
            it.opcode == Opcode.IGET_WIDE &&
                (it as ReferenceInstruction).reference.toString().contains("lastPosMs:J") &&
                tick[tick.indexOf(it) + 1].opcode == Opcode.CMP_LONG
        }
        assertTrue(lastPos >= 0, "tick does not compare the position with lastPosMs")
        val positionRead = tick.indexOfFirst {
            it.opcode == Opcode.INVOKE_INTERFACE &&
                (it as ReferenceInstruction).reference.toString().endsWith(";->getPosition()J")
        }
        val positionRegister = (tick[positionRead + 1] as OneRegisterInstruction).registerA
        assertEquals(positionRegister, (tick[lastPos + 1] as ThreeRegisterInstruction).registerB, "the compare does not read the position")
        assertEquals(Opcode.IF_NEZ, tick[lastPos + 2].opcode, "the compare is not a changed/unchanged split")
        val lastPosWrite = tick.indexOfFirst {
            it.opcode == Opcode.IPUT_WIDE &&
                (it as ReferenceInstruction).reference.toString().contains("lastPosMs:J")
        }
        assertTrue(branchTarget(tick, lastPos + 2) > lastPosWrite, "an unchanged position does not reach the stall arm")

        val counterRead = tick.indexOfFirst {
            it.opcode == Opcode.IGET &&
                (it as ReferenceInstruction).reference.toString().contains("stallTicks:I")
        }
        assertEquals(Opcode.ADD_INT_LIT8, tick[counterRead + 1].opcode)
        assertTrue(
            (tick[counterRead + 2] as ReferenceInstruction).reference.toString().contains("stallTicks:I"),
            "the tick counter is not stored",
        )

        // The window is wall-clock, not a tick count: `now - lastChange` against 2500 ms.
        val changeRead = tick.indexOfFirst {
            it.opcode == Opcode.IGET_WIDE &&
                (it as ReferenceInstruction).reference.toString().contains("lastChangeMs:J")
        }
        assertTrue(changeRead > counterRead, "the freeze does not read the last change time")
        assertEquals(Opcode.SUB_LONG, tick[changeRead + 1].opcode, "the elapsed window is not computed")
        assertEquals(Opcode.CONST_WIDE_16, tick[changeRead + 2].opcode, "the window length is not loaded")
        assertEquals(2500L, (tick[changeRead + 2] as NarrowLiteralInstruction).narrowLiteral.toLong(), "the stall window is not 2500 ms")
        assertEquals(Opcode.CMP_LONG, tick[changeRead + 3].opcode)
        val windowGuard = tick[changeRead + 4]
        assertEquals(Opcode.IF_GTZ, windowGuard.opcode, "sub-window stalls do not stay in the grace window")

        val frozenWrite = tick.withIndex().indexOfFirst { (index, instruction) ->
            index > changeRead && instruction.opcode == Opcode.IPUT_BOOLEAN &&
                (instruction as ReferenceInstruction).reference.toString().contains("frozen:Z")
        }
        assertTrue(frozenWrite > changeRead + 4, "the freeze does not follow the window guard")
        assertTrue(branchTarget(tick, changeRead + 4) > frozenWrite, "the sub-window branch does not skip the freeze")

        // A freeze no longer hides: the dead block is driven by the attachment probe.
        val deadHide = tick.indexOfFirst {
            it.opcode == Opcode.INVOKE_STATIC &&
                (it as ReferenceInstruction).reference.toString().contains("SkipButton;->hide")
        }
        assertTrue(frozenWrite < deadHide, "the freeze runs after the hide")

        val evaluate = engine.methods.first { it.name == "evaluate" }.instr()
        assertTrue(
            evaluate.none { it.opcode == Opcode.IGET_BOOLEAN && (it as ReferenceInstruction).reference.toString().contains("frozen:Z") },
            "the reveal still reads frozen",
        )
        val show = evaluate.indexOfFirst {
            it.opcode == Opcode.INVOKE_STATIC &&
                (it as ReferenceInstruction).reference.toString().contains("SkipButton;->show")
        }
        val reveal = evaluate.indexOfFirst { it.opcode == Opcode.APUT_BOOLEAN }
        assertTrue(show > reveal, "the reveal does not reach the show")
    }

    @Test
    fun `onSeek re-arms before evaluating`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        val engine = context.classDefs.first { it.type == SKIP_ENGINE_CLASS }

        val onSeek = engine.methods.first { it.name == "onSeek" }.instr()
        val rearm = onSeek.indexOfFirst {
            (it as? ReferenceInstruction)?.reference?.toString()?.endsWith("${SKIP_ENGINE_CLASS}->rearm($SKIP_STATE_CLASS${"J"})V") == true
        }
        val evaluate = onSeek.indexOfFirst {
            (it as? ReferenceInstruction)?.reference?.toString()?.endsWith("${SKIP_ENGINE_CLASS}->evaluate($SKIP_STATE_CLASS)I") == true
        }
        assertTrue(rearm >= 0, "onSeek does not re-arm")
        assertTrue(rearm < evaluate, "the re-arm follows the evaluation")

        val method = engine.methods.first { it.name == "rearm" }
        assertEquals(listOf(SKIP_STATE_CLASS, "J"), method.parameterTypes.map { it.toString() })
        assertEquals("V", method.returnType.toString())

        val body = method.instr()
        val aput = body.indexOfFirst { it.opcode == Opcode.APUT_BOOLEAN }
        assertTrue(aput > 0, "rearm never clears a flag")
        assertEquals(Opcode.CONST_4, body[aput - 1].opcode)
        assertEquals(0, (body[aput - 1] as NarrowLiteralInstruction).narrowLiteral)
        val startGuard = body.indexOfFirst { it.opcode == Opcode.IF_LTZ }
        val endGuard = body.indexOfFirst { it.opcode == Opcode.IF_GEZ }
        assertTrue(startGuard in 0 until endGuard, "the start range guard is missing")
        assertTrue(endGuard < aput, "the end range guard does not precede the clear")
        assertEquals(2, body.count { it.opcode == Opcode.IGET_WIDE }, "the guards must read startMs and endMs")
        // cmp-long yields sign(position - boundary): if-ltz means "below start",
        // if-gez means "at or past end", so a position inside [start, end) falls through.
        assertEquals(Opcode.CMP_LONG, body[startGuard - 1].opcode)
        assertEquals(Opcode.CMP_LONG, body[endGuard - 1].opcode)
        assertTrue(branchTarget(body, startGuard) > aput, "the start guard does not skip the clear")
        assertTrue(branchTarget(body, endGuard) > aput, "the end guard does not skip the clear")
    }

    /** Builder instructions of an interface-typed method. */
    private fun Method.instr(): List<com.android.tools.smali.dexlib2.builder.BuilderInstruction> =
        MutableMethod(this).getInstructions()

    /** Instruction index of where the branch at [index] lands, from its code-unit offset. */
    private fun branchTarget(instructions: List<com.android.tools.smali.dexlib2.builder.BuilderInstruction>, index: Int): Int {
        val branch = instructions[index]
        val address = branch.location.codeAddress + (branch as OffsetInstruction).codeOffset
        return instructions.indexOfFirst { it.location.codeAddress == address }.also {
            assertTrue(it >= 0, "branch at $index lands on no instruction")
        }
    }

    private fun List<com.android.tools.smali.dexlib2.builder.BuilderInstruction>.indexFirst(
        from: Int,
        predicate: (com.android.tools.smali.dexlib2.builder.BuilderInstruction) -> Boolean,
    ): Int {
        val at = (from until size).firstOrNull { predicate(this[it]) }
        assertTrue(at != null, "no matching instruction from $from")
        return at!!
    }

    /** The `show` body, sliced out for ordering assertions. */
    @Test
    fun `the label is light on the fixed scrim instead of computed from the category`() {
        val button = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        val color = button.substringAfter(".method public static textColor").substringBefore(".end method")
        assertTrue(color.contains("const/4 v0, -0x1"), "the text colour is not the light constant")
        val show = showBody()
        assertTrue(show.contains("->textColor()I"), "show does not use the colour helper")
        assertTrue(show.contains("const v4, -0x33ffffff"), "the scrim constant moved")
        assertTrue(!show.contains("0x12b") && !show.contains("0x24b"), "the category luminance branch survived")
        assertTrue(!show.contains("->color(I)I"), "the category colour is still resolved for the text")
    }

    @Test
    fun `the resolved view prefers the last attach event and rejects a detached one`() {
        val engine = sponsorSkipSources()[SKIP_ENGINE_CLASS]!!
        val viewOf = engine.substringAfter(".method public static viewOf").substringBefore(".end method")
        val attach = viewOf.indexOf("->attachView:Landroid/view/View;")
        val own = viewOf.indexOf("->view:Landroid/view/View;")
        val map = viewOf.indexOf("->VIEWS:Ljava/util/WeakHashMap;")
        assertTrue(attach > 0 && own > attach && map > own, "the resolution order is not attach, own, map")
        assertEquals(3, Regex("isAttachedToWindow").findAll(viewOf).count(), "a detached remembered view is not filtered")
        assertEquals(3, Regex("if-eqz v3, :").findAll(viewOf).count(), "a detached candidate is returned instead of a live one")
        assertTrue(viewOf.contains("sget-object v0, $SKIP_ENGINE_CLASS->LAST_VIEW"), "the last-view fallback is gone")
    }

    @Test
    fun `the attach event re-shows a visible button against the new surface`() {
        val redraw = engineBody("redraw", "$SKIP_STATE_CLASS").replace(";;", ";")
        assertTrue(redraw.contains("getVisibility()I"), "redraw does not check visibility")
        assertTrue(redraw.contains("->show("), "a visible button is not re-measured")
        assertTrue(redraw.contains("->place("), "a hidden button is not repositioned")
        assertTrue(redraw.contains("->lastIndex:I"), "the redraw loses the segment index")
        val show = showBody()
        assertTrue(show.contains("iput p1, p0, $SKIP_STATE_CLASS->lastIndex:I"), "show does not record the index")
    }

    @Test
    fun `the main op table carries the redraw op`() {
        val run = sponsorSkipSources()[SKIP_MAIN_CLASS]!!
        assertTrue(run.contains("const/4 v1, 0x4") && run.contains(":op4"), "op 4 is not dispatched")
        val op4 = run.substringAfter("\n    :op4").substringBefore(".end method")
        assertTrue(op4.contains("->redraw("), "op 4 does not redraw")
    }

    private fun showBody(): String = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        .substringAfter(".method public static show")
        .substringBefore(".method public static hide")

    /** The `place` body, sliced out for ordering assertions. */
    private fun placeBody(): String = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        .substringAfter(".method public static place")
        .substringBefore(".method private static anchorOf")

    /** The per-tick follow body. */
    private fun followBody(): String = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        .substringAfter(".method public static follow")
        .substringBefore(".method private static sized")

    /** The anchor resolution body. */
    private fun anchorBody(): String = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        .substringAfter(".method private static anchorOf")
        .substringBefore(".method public static follow")

    /** Asserts `fetch → move-result R → if-eqz R, :return` at [at]. */
    private fun assertNullGuard(
        instructions: List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>,
        at: Int,
        what: String,
    ) {
        assertEquals(Opcode.MOVE_RESULT_OBJECT, instructions[at + 1].opcode, "the $what result is not kept")
        val result = (instructions[at + 1] as OneRegisterInstruction).registerA
        val guard = instructions[at + 2]
        assertEquals(Opcode.IF_EQZ, guard.opcode, "the $what is not null-checked")
        assertEquals(result, (guard as OneRegisterInstruction).registerA, "the $what guard reads another register")
    }

    private fun assertSkipFlag(method: Method, value: Int) {
        val instructions = method.instr()
        val call = instructions.indexOfFirst {
            (it as? ReferenceInstruction)?.reference?.toString()?.endsWith("SkipEngine;->skip(${SKIP_STATE_CLASS}IZ)V") == true
        }
        assertTrue(call > 0, "no skip call in ${method.name}")
        val registers = instructions[call] as FiveRegisterInstruction
        val flag = registers.registerE
        val moved = instructions[call - 1]
        assertEquals(Opcode.CONST_4, moved.opcode, "the flag is not a constant")
        assertEquals(flag, (moved as OneRegisterInstruction).registerA, "the constant does not feed the flag")
        assertEquals(value, (moved as NarrowLiteralInstruction).narrowLiteral.toInt())
    }

    private fun assertHook(
        method: app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod,
        target: String,
        receiver: Int,
        argument: Int?,
    ) {
        val instruction = method.instr()[0] as ReferenceInstruction
        assertEquals(Opcode.INVOKE_STATIC, instruction.opcode)
        val reference = instruction.reference as MethodReference
        assertEquals(SKIP_ENGINE_CLASS, reference.definingClass)
        assertEquals(target, reference.name)
        val registers = method.implementation!!.instructions
        assertNotNull(registers)
        val fiveRegister = instruction as com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
        assertEquals(receiver, fiveRegister.registerC)
        if (argument != null) assertEquals(argument, fiveRegister.registerD)
    }

    private data class GuardedEntry(
        val name: String,
        val parameters: String,
        val op: Int,
        val firstBody: String,
        val parameterCount: Int,
    )

    /** The three entry hooks, each with its posted op and the first token of its body. */
    private val guardedEntries = listOf(
        GuardedEntry("onVideoChanged", "Lp73/a;Lcom/vk/dto/common/VideoFile;", 0, "->isEnabled()Z", 2),
        GuardedEntry("onRelease", "Lp73/a;", 1, "clear(Lp73/a;)V", 1),
        GuardedEntry("onSeek", "Ljava/lang/Object;J", 2, "MAP:", 2),
    )

    /** One engine method body, sliced out for source assertions. */
    private fun engineBody(name: String, parameters: String, public: Boolean = true): String {
        val visibility = if (public) "public" else "private"
        val source = sponsorSkipSources()[SKIP_ENGINE_CLASS]!!
        val header = ".method $visibility static $name($parameters)"
        require(header in source) { "no engine method header: $header" }
        return source.substringAfter(header).substringBefore(".end method")
    }

    /** The op literal loaded into the range window of a `SKIP_MAIN_CLASS` construction at [from]. */
    private fun rangeOp(block: String, from: Int): Int {
        val construction = block.substring(from)
        val range = Regex("invoke-direct/range \\{v(\\d+) .. v\\d+\\}").find(construction) ?: error("no range ctor")
        // The window is [instance, op, state, controller, video, target].
        val op = range.groupValues[1].toInt() + 1
        val literal = Regex("const/4 v$op, 0x([0-9a-f]+)").find(construction.substring(0, range.range.first))
        return literal!!.groupValues[1].toInt(16)
    }

    private fun Method.key(): String = "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

    private fun Method.mutatesStructure(): Boolean =
        implementation?.instructions?.any {
            val reference = (it as? ReferenceInstruction)?.reference?.toString().orEmpty()
            it.opcode == Opcode.INVOKE_VIRTUAL &&
                (reference.endsWith("ViewGroup;->removeView(Landroid/view/View;)V") || reference.contains("->addView("))
        } == true

    private val VIDEO_AUTO_PLAY = "Lcom/vk/libvideo/autoplay/VideoAutoPlay;"
    private val EMPTY_AUTO_PLAY = "Lcom/vk/libvideo/autoplay/e;"

    private val fixtures = PatchTestUtil.sponsorCoreFixture
}
