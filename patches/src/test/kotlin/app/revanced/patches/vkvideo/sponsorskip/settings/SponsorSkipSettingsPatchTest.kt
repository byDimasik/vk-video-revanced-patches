package app.revanced.patches.vkvideo.sponsorskip.settings

import app.revanced.patches.vkvideo.settings.extractToolbarChrome
import app.revanced.patches.vkvideo.settings.navOpenerFingerprint
import app.revanced.patches.vkvideo.settings.resolveNavOpener
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
import app.revanced.patches.vkvideo.settings.switchListenerMethodFingerprint
import app.revanced.patches.vkvideo.settings.toolbarChromeFingerprint
import app.revanced.patches.vkvideo.sponsorskip.BEHAVIOR_CLASS
import app.revanced.patches.vkvideo.sponsorskip.BEHAVIOR_STORE_CLASS
import app.revanced.patches.vkvideo.sponsorskip.Behavior
import app.revanced.patches.vkvideo.sponsorskip.Category
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipPatch
import app.revanced.patches.vkvideo.util.PatchTestUtil.createBytecodeContext
import app.revanced.patches.vkvideo.util.PatchTestUtil.execute
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SponsorSkipSettingsPatchTest {

    private class Fixture {
        val context = createBytecodeContext(*app.revanced.patches.vkvideo.util.PatchTestUtil.sponsorSettingsFixture170.toTypedArray())

        val sources: Map<String, String>
            get() {
                val chromeMethod = toolbarChromeFingerprint.getMethod(context)
                val chrome = extractToolbarChrome(context.classDefs.first { it.type == chromeMethod.definingClass })
                val opener = resolveNavOpener(navOpenerFingerprint.getMethod(context), context.classDefs.toList())
                return settingsSources(chrome, opener, switchListenerMethodFingerprint.getMethod(context).name)
            }

        fun apply() {
            revancedSettingsPatch.execute(context)
            sponsorSkipPatch.execute(context)
            sponsorSkipSettingsPatch.execute(context)
        }
    }

    private val descriptors = listOf(
        SETTINGS_CLASS, FRAGMENT_CLASS, GROUP_CLICK_CLASS, ROOT_ROW_CLASS,
        TOGGLE_CLASS, DIALOG_CLASS, DIALOG_CLICK_CLASS, ROWS_CLASS,
    )

    @Test
    fun `the patch injects every class once`() {
        val fixture = Fixture()
        assertNotNull(toolbarChromeFingerprint.matchOrNull(fixture.context))

        fixture.apply()

        for (descriptor in descriptors) {
            assertEquals(1, fixture.context.classDefs.count { it.type == descriptor }, descriptor)
        }
    }

    @Test
    fun `the screen never spells a store key and only uses the store contract`() {
        val sources = Fixture().sources.values

        for (source in sources) {
            assertFalse(source.contains("sponsorskip_"), "a pref key literal leaked into the screen")
            assertFalse(Regex("KEY_[A-Z]+").containsMatchIn(source), "a KEY_* field is referenced directly")
            assertFalse(source.contains("BehaviorStore;->key(I)"), "key(I) is called directly")
        }

        val storeMethods = sources
            .flatMap { source ->
                source.split("$BEHAVIOR_STORE_CLASS->").drop(1)
                    .map { usage -> usage.takeWhile { c -> c.isLetterOrDigit() || c == '_' } }
            }
            .toSet()
        assertEquals(setOf("isEnabled", "getBehavior", "setEnabled", "setBehavior"), storeMethods)

        assertTrue(
            sources.count { it.contains("$BEHAVIOR_CLASS->literal(") } >= 1,
            "behavior literals come from Behavior.literal",
        )
        assertTrue(
            sources.any { it.contains("invoke-static {p2}, $BEHAVIOR_CLASS->literal(I)Ljava/lang/String;") },
            "the written literal comes from Behavior.literal",
        )
    }

    @Test
    fun `behavior labels mirror the enum and the item order is fixed`() {
        assertEquals("SKIP_AUTOMATICALLY", Behavior.literal(Behavior.SKIP_AUTOMATICALLY))
        assertEquals("SHOW_SKIP_BUTTON", Behavior.literal(Behavior.SHOW_SKIP_BUTTON))
        assertEquals("DISABLED", Behavior.literal(Behavior.DISABLED))

        val settings = Fixture().sources[SETTINGS_CLASS]!!
        for (label in listOf("\"Skip automatically\"", "\"Show skip button\"", "\"Disabled\"")) {
            assertTrue(settings.contains(label), "missing popup label $label")
        }

        val dialog = Fixture().sources[DIALOG_CLASS]!!
        val arrayBlock = dialog.substringAfter("new-array v1, v1, [Ljava/lang/CharSequence;")
            .substringBefore(";->v([Ljava/lang/CharSequence;")
        val indexes = Regex("const/4 v4, 0x(\\d)").findAll(arrayBlock).map { it.groupValues[1] }.toList()
        assertEquals(listOf("0", "1", "2"), indexes, "items are built in Behavior order")
        assertEquals(3, Regex("aput-object v3, v1, v4").findAll(arrayBlock).count())
    }

    @Test
    fun `the root row is appended once right after the root addView`() {
        val fixture = Fixture()
        fixture.apply()

        val root = rootContentFingerprint.getMethod(fixture.context)
        val instructions = root.getInstructions()
        val inserts = instructions.withIndex().filter { (_, instruction) ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            instruction.opcode == Opcode.INVOKE_STATIC &&
                reference?.definingClass == ROOT_ROW_CLASS &&
                reference.name == "add"
        }
        assertEquals(1, inserts.size, "exactly one root-row insert")
        val (index, insert) = inserts.single()
        assertEquals(Opcode.INVOKE_VIRTUAL, instructions[index - 1].opcode)
        assertEquals("addView", ((instructions[index - 1] as ReferenceInstruction).reference as MethodReference).name)
        val registers = insert as FiveRegisterInstruction
        assertEquals(listOf(9, 10, 11), listOf(registers.registerC, registers.registerD, registers.registerE))

        val row = fixture.sources[ROOT_ROW_CLASS]!!
        assertTrue(fixture.sources[ROWS_CLASS]!!.contains("Lcom/vk/core/view/components/cell/VkCell${'$'}Right${'$'}d;"))
        assertTrue(row.contains("0x7f082020"), "SponsorSkip icon")
        assertTrue(row.contains("$ROWS_CLASS->right("), "chevron comes from the shared right-slot helper")
    }

    @Test
    fun `the row click opens the SponsorSkip screen and the back handler finishes`() {
        val sources = Fixture().sources

        val click = sources[GROUP_CLICK_CLASS]!!
        assertTrue(click.contains("const-class v1, $FRAGMENT_CLASS"))
        assertTrue(click.contains("Lcom/vk/core/fragments/FragmentImpl;"))
        assertTrue(sources[FRAGMENT_CLASS]!!.contains("$SETTINGS_CLASS->buildSponsorSkipContent"))
        assertTrue(sources[FRAGMENT_CLASS]!!.contains("Lcom/vk/core/view/components/topbar/VkTopBar"))
    }

    @Test
    fun `the switch reads isEnabled and writes through setEnabled`() {
        val sources = Fixture().sources

        assertTrue(sources[SETTINGS_CLASS]!!.contains("$BEHAVIOR_STORE_CLASS->isEnabled()Z"))
        val listener = sources[TOGGLE_CLASS]!!
        assertTrue(listener.contains("$BEHAVIOR_STORE_CLASS->setEnabled(Z)V"))
        assertTrue(sources[ROWS_CLASS]!!.contains("$BEHAVIOR_STORE_CLASS->getBehavior(I)I"))
    }

    @Test
    fun `the dialog uses the app builder, the current value and dismisses`() {
        val sources = Fixture().sources
        val dialog = sources[DIALOG_CLASS]!!

        assertTrue(dialog.contains("new-instance v0, Lhy1/c${'$'}a;"))
        assertTrue(dialog.contains("->x(Ljava/lang/CharSequence;)Lhy1/c${'$'}a;"))
        assertTrue(dialog.contains("invoke-static {v2}, $BEHAVIOR_STORE_CLASS->getBehavior(I)I"), "checked index is the stored value")
        assertTrue(dialog.contains("->v([Ljava/lang/CharSequence;ILandroid/content/DialogInterface${'$'}OnClickListener;)"))

        val click = sources[DIALOG_CLICK_CLASS]!!
        assertTrue(click.contains("$BEHAVIOR_STORE_CLASS->setBehavior(ILjava/lang/String;)V"))
        assertTrue(click.contains("$SETTINGS_CLASS->setRowValue(Lcom/vk/core/view/components/cell/VkCell;II)V"))
        assertTrue(click.contains("Landroid/content/DialogInterface;->dismiss()V"))
    }

    @Test
    fun `category labels are the exact English titles`() {
        val settings = Fixture().sources[SETTINGS_CLASS]!!
        for (label in listOf("Sponsor", "Self-promo", "Intro", "Outro")) {
            assertTrue(settings.contains("\"$label\""), "missing label $label")
        }
        assertEquals(4, Category.COUNT)
    }

    @Test
    fun `the middle masks keep the title and the subtitle text`() {
        val rows = Fixture().sources[ROWS_CLASS]!!
        assertTrue(rows.contains("0x2e"), "subtitle defaults null everything but the text")
        assertTrue(rows.contains("const/4 v6, 0x4"), "the factory drops the counter only")
        assertTrue(rows.contains("0x3e"), "title keeps only the title")
    }

    @Test
    fun `the store setters write the frozen keys`() {
        val generated = app.revanced.patches.vkvideo.sponsorskip.sponsorSkipSources()[BEHAVIOR_STORE_CLASS]!!
        assertTrue(generated.contains("setEnabled(Z)V"))
        assertTrue(generated.contains("setBehavior(ILjava/lang/String;)V"))
        assertTrue(generated.contains("sponsorskip_enabled"))
        for (category in Category.SPONSOR..Category.OUTRO) {
            assertTrue(generated.contains("sponsorskip_settings_behavior_${Category.name(category)}"))
        }
        val literal = app.revanced.patches.vkvideo.sponsorskip.sponsorSkipSources()[BEHAVIOR_CLASS]!!
        for (behavior in 0..2) {
            assertTrue(literal.contains(Behavior.literal(behavior)))
        }
    }
}
