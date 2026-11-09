package app.revanced.patches.vkvideo.sponsorskip

import app.revanced.patches.vkvideo.util.PatchTestUtil
import app.revanced.patches.vkvideo.util.PatchTestUtil.createBytecodeContext
import app.revanced.patches.vkvideo.util.PatchTestUtil.execute
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SponsorSkipPatchTest {

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
        assertEquals(2, Regex("getLocationInWindow").findAll(place).count(), "place does not read both locations")
        assertTrue(place.contains("->leftMargin:I") && place.contains("->topMargin:I"))
        assertTrue(place.contains("0x800030"), "place does not pin the gravity")
        assertTrue(button.replace(";;", ";").contains("invoke-static {p0}, $SKIP_BUTTON_CLASS;->place($SKIP_STATE_CLASS;)V".replace(";;", ";")))
    }

    @Test
    fun `the layout listener follows the view and detaches on stop`() {
        val button = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        assertTrue(button.replace(";;", ";").contains("$SKIP_LAYOUT_CLASS;->watch($SKIP_STATE_CLASS;Landroid/view/View;)V".replace(";;", ";")))
        assertTrue(sponsorSkipSources()[SKIP_LAYOUT_CLASS]!!.contains("removeOnLayoutChangeListener"))
        assertTrue(sponsorSkipSources()[SKIP_ENGINE_CLASS]!!.contains("removeOnLayoutChangeListener"))
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
        assertTrue(requiredMethods.getValue(SKIP_ENGINE_CLASS).contains("onSeek:2"))
        assertTrue(requiredMethods.getValue(SKIP_LAYOUT_CLASS).containsAll(listOf("<init>:1", "onLayoutChange:9")))
        assertTrue(requiredFields.getValue(SKIP_STATE_CLASS).containsAll(listOf("listener", "listenerView")))
        assertTrue(sponsorSkipSources().containsKey(SKIP_LAYOUT_CLASS))
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

    private val VIDEO_AUTO_PLAY = "Lcom/vk/libvideo/autoplay/VideoAutoPlay;"
    private val EMPTY_AUTO_PLAY = "Lcom/vk/libvideo/autoplay/e;"

    private val fixtures = PatchTestUtil.sponsorCoreFixture
}
