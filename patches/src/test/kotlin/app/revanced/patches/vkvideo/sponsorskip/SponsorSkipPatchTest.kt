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

        val instructions = skip.implementation!!.instructions.toList()
        val consumed = instructions.indexOfFirst { it.opcode == Opcode.APUT_BOOLEAN }
        val hide = instructions.indexOfFirst {
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

        val instructions = sponsorSkipSectionListFingerprint.getMethod(context).implementation!!.instructions.toList()
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
    fun `the button clamps inside the player surface`() {
        val button = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        val place = button.substring(button.indexOf(".method public static place"))
        assertEquals(3, Regex("getLocationInWindow").findAll(place).count(), "place does not read the surface, the parent and the bar")
        assertTrue(place.contains("->leftMargin:I") && place.contains("->topMargin:I"))
        assertTrue(place.contains("0x800030"), "place does not pin the gravity")
        assertTrue(place.contains("if-eqz v1, :fallback"), "a null surface does not route to the fallback")
        assertEquals(3, Regex("if-eqz v1, :frame").findAll(place).count(), "the frame is not the terminal fallback")
        assertTrue(place.contains("setMaxWidth") && place.contains("setMaxHeight"), "the button is not capped to the surface")
        assertTrue(place.contains("if-eqz v3, :return"), "the layout is requested even when nothing changed")
        assertTrue(button.replace(";;", ";").contains("invoke-static {p0}, $SKIP_BUTTON_CLASS;->place($SKIP_STATE_CLASS;)V".replace(";;", ";")))
    }

    @Test
    fun `the raised anchor is measured from the bar bottom in the parent frame`() {
        val place = placeBody()
        assertTrue(
            place.contains("invoke-virtual {v5, v6}, Landroid/view/View;->getLocationInWindow([I)V"),
            "the bar location reuses another array than the parent one",
        )
        assertTrue(place.contains("aget v15, v6, v4"), "the bar top is not read from the bar array")
        assertTrue(place.contains("aget v4, v7, v4"), "the parent top is not read from the parent array")
        assertTrue(place.contains("sub-int/2addr v15, v4"), "the parent origin is not subtracted")
        assertTrue(place.contains("sub-int/2addr v15, v4\n    if-lez v12, :zero_bar"), "the anchor is not the bar top in the parent frame")
        assertTrue(place.contains("sub-int/2addr v15, v6"), "the page anchor does not subtract the 8 dp gap")
        assertTrue(place.contains("sub-int v12, v15, v13"), "the anchor does not subtract the button height")
    }

    @Test
    fun `the bar is looked up page id first, scoped to the content view with a decor fallback`() {
        val place = placeBody()
        val contentView = place.indexOf("const v5, 0x7f0b098b")
        val pageBar = place.indexOf("const v4, 0x7f0b223d")
        val cardBar = place.indexOf("const v4, 0x7f0b2236")
        val decor = place.indexOf("const v5, 0x1020002", contentView)
        assertTrue(contentView > 0 && pageBar > 0 && cardBar > 0, "the ids are not all looked up")
        assertTrue(contentView < pageBar, "the bar is searched before the content view")
        assertTrue(decor in (contentView + 1) until pageBar, "the decor fallback does not follow a missing content view")
        assertTrue(pageBar < cardBar, "the page bar is not searched first")
        assertTrue(place.contains("if-eqz v5, :bar_first"), "the page bar hit does not skip the card bar")
        assertTrue(place.contains("if-eqz v5, :bar_first\n    const/4 v13, 0x0"), "the page host is not the guarded form")
        assertTrue(place.contains("if-eqz v5, :no_bar\n    const/4 v13, 0x1"), "the card host is not recorded as the bar form")
    }

    @Test
    fun `place runs on seventeen registers with the parameter window kept last`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        val method = MutableMethod(context.classDefs.first { it.type == SKIP_BUTTON_CLASS }.methods.first { it.name == "place" })
        val source = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!.substringAfter(".method public static place")
        assertTrue(source.contains("move-object/from16 v15, p0"), "the parameter is not copied into a low register")
        assertEquals(17, method.implementation!!.registerCount, "the register count does not match the reference map")
        assertTrue(source.contains(".registers 17"), "the source and the assembled frame disagree")
        val bound = 16
        method.getInstructions().forEach { instruction ->
            if (instruction is OneRegisterInstruction) {
                assertTrue(instruction.registerA in 0 until bound + 1, "${instruction.opcode} reads a register past the frame")
            }
            (instruction as? TwoRegisterInstruction)?.let {
                assertTrue(it.registerA <= bound && it.registerB <= bound, "${instruction.opcode} reads a register past the frame")
            }
        }
    }

    @Test
    fun `the raise adds no measure or size calls to place`() {
        val place = placeBody()
        assertEquals(0, Regex("->measure").findAll(place).count(), "place measures")
        assertEquals(1, Regex("->setMaxWidth").findAll(place).count(), "the width cap is set more than once")
        assertEquals(1, Regex("->setMaxHeight").findAll(place).count(), "the height cap is set more than once")
    }

    @Test
    fun `an unlaid or missing bar falls back to the fixed offset`() {
        val place = placeBody()
        assertTrue(place.contains("const/16 v5, 0x1e"), "the fallback offset does not clear the bar")
        assertTrue(place.contains(":target_fallback\n    const/16 v5, 0x1e"), "the fallback is not the anchor form")
        assertTrue(place.contains("sub-int v15, v11, v5"), "the fallback does not lift the surface bottom by the offset")
        assertTrue(place.contains("if-eqz v1, :no_bar"), "a missing container does not reach the fallback")
        assertTrue(place.contains("if-lez v12, :zero_bar"), "a zero bar height does not reach the fallback")
        assertTrue(place.contains(":zero_bar\n    const/4 v13, 0x1"), "a zero bar height does not take the same form as an unlaid alternate bar")
        assertTrue(place.contains(":no_bar\n    const/4 v13, 0x2"), "the missing bar is not the fallback form")
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
    fun `place falls back to the layout params when the button has no extent`() {
        val place = placeBody()
        assertTrue(place.contains("if-lez v12, :w_measured"), "a zero width does not fall back")
        assertTrue(place.contains("iget v12, v2, Landroid/widget/FrameLayout\$LayoutParams;->width:I"), "the width fallback reads another field")
        assertTrue(place.contains("if-lez v13, :h_measured"), "a zero height does not fall back")
        assertTrue(place.contains("iget v13, v2, Landroid/widget/FrameLayout\$LayoutParams;->height:I"), "the height fallback reads another field")
    }

    @Test
    fun `measure and place share one width floor`() {
        val button = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        assertEquals(1, Regex("\\.method public static minWidth").findAll(button).count(), "the floor helper is duplicated")
        assertTrue(showBody().contains("->minWidth(Landroid/content/Context;I)I"), "the measure ignores the floor helper")
        assertTrue(placeBody().contains("->minWidth(Landroid/content/Context;I)I"), "the place ignores the floor helper")
    }

    @Test
    fun `the floor raises the width instead of lowering it`() {
        assertTrue(
            showBody().contains("if-ge v6, v4, :m_w\n    move v6, v4"),
            "the measure keeps the smaller width when the floor is larger",
        )
        assertTrue(
            placeBody().contains("if-ge v4, v14, :w_max\n    move v4, v14"),
            "the place keeps the smaller cap when the floor is larger",
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
    fun `the layout listener follows the surface and the button, then detaches`() {
        val button = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        assertTrue(
            button.replace(";;", ";")
                .contains("$SKIP_LAYOUT_CLASS;->watch($SKIP_STATE_CLASS;Landroid/view/View;Landroid/view/View;)V".replace(";;", ";")),
        )
        val layout = sponsorSkipSources()[SKIP_LAYOUT_CLASS]!!
        assertEquals(2, Regex("addOnLayoutChangeListener").findAll(layout).count(), "both slots are not watched")
        assertTrue(layout.contains("if-nez v0, :created") && layout.contains(":created"), "the listener is not created on demand")
        assertTrue(layout.contains("if-eq v1, p1"), "the surface slot does not skip the same view")
        assertTrue(layout.contains("if-eq v1, p2"), "the button slot does not skip the same view")
        val stop = sponsorSkipSources()[SKIP_ENGINE_CLASS]!!.substringAfter(".method private static stop")
        assertEquals(2, Regex("removeOnLayoutChangeListener").findAll(stop).count(), "stop leaves a listener attached")
        assertTrue(stop.contains("->listenerView:Landroid/view/View;"))
        assertTrue(stop.contains("->buttonView:Landroid/view/View;"))
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
            val built = block.indexOf("new-instance")
            assertTrue(built > 0 && block.contains(SKIP_MAIN_CLASS), "${entry.name} does not build a dispatch")
            assertEquals(entry.op, rangeOp(block, built), "${entry.name} posts the wrong op")
            assertTrue(block.contains("postMain(Ljava/lang/Runnable;)V"), "${entry.name} does not post the dispatch")
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
        val same = block.indexOf("if-ne v4, v5, :update")
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
    fun `onViewAttached stays inline`() {
        val attach = engineBody("onViewAttached", "Landroid/view/View;Landroid/view/View;")
        assertTrue(!attach.contains("onMain()Z"), "onViewAttached was deferred")
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
    fun `the tick evaluates while paused and gates only the skip`() {
        val engine = sponsorSkipSources()[SKIP_ENGINE_CLASS]!!
        val tick = engine.substring(engine.indexOf(".method public static tick"), engine.indexOf(".method private static evaluate"))
        val evaluateAt = tick.indexOf("->evaluate(")
        val playingAt = tick.indexOf("->isPlaying()")
        val skipAt = tick.indexOf("->skip(")
        assertTrue(evaluateAt >= 0 && evaluateAt < playingAt, "the scan is still gated by playback")
        assertTrue(skipAt > playingAt, "skip is not gated by playback")
        assertTrue(tick.substring(playingAt, skipAt).contains("if-eqz v8, :repost"), "the playing result does not guard the skip")
    }

    @Test
    fun `the seek hook re-evaluates before the return`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        val instructions = sponsorSkipSeekFingerprint.getMethod(context).implementation!!.instructions.toList()
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
        assertTrue(requiredMethods.getValue(SKIP_BUTTON_CLASS).contains("minWidth:2"))
        assertTrue(requiredMethods.getValue(SKIP_ENGINE_CLASS).contains("onSeek:2"))
        assertTrue(requiredMethods.getValue(SKIP_ENGINE_CLASS).contains("skip:3"))
        assertTrue(requiredMethods.getValue(CATEGORY_CLASS).contains("label:1"))
        assertTrue(requiredFields.getValue(SKIP_STATE_CLASS).contains("lastToastMs"))
        assertTrue(requiredMethods.getValue(SKIP_LAYOUT_CLASS).containsAll(listOf("<init>:1", "onLayoutChange:9", "watch:3")))
        assertTrue(requiredFields.getValue(SKIP_STATE_CLASS).containsAll(listOf("view", "button", "listener", "listenerView", "buttonView")))
        assertTrue(sponsorSkipSources().containsKey(SKIP_LAYOUT_CLASS))
        assertTrue(requiredMethods.getValue(SKIP_ENGINE_CLASS).containsAll(listOf("onMain:0", "mainHandler:0", "postMain:1", "trace:2")))
        assertTrue(requiredMethods.getValue(SKIP_MAIN_CLASS).containsAll(listOf("<init>:5", "run:0")))
        assertTrue(requiredFields.getValue(SKIP_ENGINE_CLASS).contains("MAIN"))
        assertTrue(requiredFields.getValue(SKIP_MAIN_CLASS).containsAll(listOf("op", "state", "controller", "video", "target")))
        assertTrue(sponsorSkipSources().containsKey(SKIP_MAIN_CLASS))
    }

    /** The `show` body, sliced out for ordering assertions. */
    private fun showBody(): String = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        .substringAfter(".method public static show")
        .substringBefore(".method public static hide")

    /** The `place` body, sliced out for ordering assertions. */
    private fun placeBody(): String = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        .substringAfter(".method public static place")
        .substringBefore(".method private static sized")

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
        val instructions = method.implementation!!.instructions.toList()
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
        val instruction = method.getInstructions()[0] as ReferenceInstruction
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
        return sponsorSkipSources()[SKIP_ENGINE_CLASS]!!
            .substringAfter(".method $visibility static $name($parameters)")
            .substringBefore(".end method")
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
