package app.revanced.patches.vkvideo.sponsorskip

import app.revanced.patches.vkvideo.util.PatchTestUtil
import app.revanced.patches.vkvideo.util.PatchTestUtil.createBytecodeContext
import app.revanced.patches.vkvideo.util.PatchTestUtil.execute
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.Opcode
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
