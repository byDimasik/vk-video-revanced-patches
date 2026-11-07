package app.revanced.patches.vkvideo.settings

import app.revanced.patches.vkvideo.util.PatchTestUtil
import app.revanced.patches.vkvideo.util.PatchTestUtil.execute
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class ReVancedSettingsPatchTest {

    private data class Fixture(
        val name: String,
        val sources: List<String>,
        val recyclerMethod: String,
        val itemClass: String,
        val wrapperClass: String,
        val icon: Int,
    )

    private val fixtures = listOf(
        Fixture(
            name = "1.168",
            sources = PatchTestUtil.settingsScreenFixture168,
            recyclerMethod = "nm",
            itemClass = "Lcom/vk/video/screens/profile/adapter/holder/i\$a;",
            wrapperClass = "Lz17/e\$a;",
            icon = 0x7f082301,
        ),
        Fixture(
            name = "1.170",
            sources = PatchTestUtil.settingsScreenFixture170,
            recyclerMethod = "hm",
            itemClass = "Lcom/vk/video/screens/profile/adapter/holder/j\$a;",
            wrapperClass = "Lj57/e\$a;",
            icon = 0x7f082302,
        ),
    )

    @TestFactory
    fun `inserts the ReVanced entry at the top of the Sign-out row builder`(): List<DynamicTest> =
        fixtures.map { fixture ->
            DynamicTest.dynamicTest(fixture.name) {
                val context = PatchTestUtil.createBytecodeContext(*fixture.sources.toTypedArray())
                assertNotNull(profileMenuItemsFingerprint.matchOrNull(context))
                val signOutMatch = assertNotNull(signOutRowFingerprint.matchOrNull(context))

                val signOutMethod = signOutRowFingerprint.getMethod(context)
                assertEquals("g", signOutMethod.name)
                assertEquals("Lfixture/menu/Rows;", signOutMethod.definingClass)
                // Snapshot the opcodes: getInstructions() is a live view over the
                // mutable implementation, so it would grow with the patch's insertion.
                val beforeOpcodes = signOutMethod.getInstructions().map { it.opcode }
                val listRegister = signOutMethod.implementation!!.registerCount -
                    app.revanced.patches.vkvideo.settings.parameterWidth(signOutMethod) + 1

                revancedSettingsPatch.execute(context)

                // addMenuEntry lands at index 0 with the builder's list parameter.
                // Re-match: patch execution re-resolves fingerprint matches against the
                // mutated context, so earlier method handles may be stale.
                assertNotNull(signOutRowFingerprint.matchOrNull(context))
                val instructions = signOutRowFingerprint.getMethod(context).getInstructions()
                assertEquals(beforeOpcodes.size + 1, instructions.size, "exactly one instruction prepended")
                val entry = instructions[0] as ReferenceInstruction
                assertEquals(Opcode.INVOKE_STATIC, entry.opcode)
                val entryReference = entry.reference as MethodReference
                assertEquals(ADS_SETTINGS_CLASS, entryReference.definingClass)
                assertEquals("addMenuEntry", entryReference.name)
                assertEquals(listOf("Ljava/util/List;"), entryReference.parameterTypes.map { it.toString() })
                assertEquals(listRegister, (instructions[0] as FiveRegisterInstruction).registerC)

                // The builder's original instructions follow unchanged (no remap: the
                // prepended invoke only reads the existing list register).
                beforeOpcodes.forEachIndexed { index, originalOpcode ->
                    assertEquals(originalOpcode, instructions[index + 1].opcode, "builder instruction $index")
                }

                // The old behavior is gone: nothing is inserted before the recycler call.
                assertNotNull(profileMenuItemsFingerprint.matchOrNull(context))
                val menuInstructions = profileMenuItemsFingerprint.getMethod(context).getInstructions()
                val recyclerIndex = menuInstructions.indexOfFirst { instruction ->
                    val reference = (instruction as? ReferenceInstruction)?.reference
                    instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                        reference is MethodReference &&
                        reference.name == fixture.recyclerMethod &&
                        reference.definingClass == "Lme/grishka/appkit/fragments/BaseRecyclerFragment;" &&
                        reference.parameterTypes == listOf("Ljava/util/List;", "Z") &&
                        reference.returnType == "V"
                }
                assertTrue(recyclerIndex > 0, "recycler call not found")
                val beforeRecycler = menuInstructions[recyclerIndex - 1]
                val beforeRecyclerRef = (beforeRecycler as? ReferenceInstruction)?.reference as? MethodReference
                assertFalse(
                    beforeRecyclerRef?.name == "addMenuEntry",
                    "addMenuEntry must not sit before the recycler call anymore",
                )

                assertTrue(context.classDefs.any { it.type == ADS_SETTINGS_CLASS })

                // The injected entry still references the resolved fixture types and icon.
                val addMenuEntryMethod = context.classDefs
                    .first { it.type == ADS_SETTINGS_CLASS }
                    .methods
                    .first { it.name == "addMenuEntry" }
                val references = addMenuEntryMethod.implementation!!.instructions
                    .filterIsInstance<ReferenceInstruction>()
                    .map { it.reference }

                assertTrue(
                    references.any {
                        it is FieldReference &&
                            it.name == "ITEM_WITH_ICON" &&
                            it.definingClass == "Lfixture/menu/Type;"
                    },
                )
                assertTrue(references.any { it is TypeReference && it.type == fixture.itemClass })
                assertTrue(references.any { it is TypeReference && it.type == fixture.wrapperClass })
                assertTrue(
                    addMenuEntryMethod.implementation!!.instructions.any {
                        it.opcode == Opcode.CONST &&
                            (it as NarrowLiteralInstruction).narrowLiteral == fixture.icon
                    },
                )
            }
        }

    @TestFactory
    fun `second apply keeps exactly one class per descriptor`(): List<DynamicTest> =
        fixtures.map { fixture ->
            DynamicTest.dynamicTest(fixture.name) {
                val context = PatchTestUtil.createBytecodeContext(*fixture.sources.toTypedArray())
                assertNotNull(profileMenuItemsFingerprint.matchOrNull(context))

                revancedSettingsPatch.execute(context)
                revancedSettingsPatch.execute(context)

                for (descriptor in expectedDescriptors) {
                    assertEquals(1, context.classDefs.count { it.type == descriptor }, descriptor)
                }
            }
        }

    private val expectedDescriptors = setOf(
        ADS_SETTINGS_CLASS,
        "Lapp/revanced/extension/vkvideo/StartReVancedSettings;",
        ROOT_FRAGMENT_CLASS,
        SETTINGS_FRAGMENT_CLASS,
        "Lapp/revanced/extension/vkvideo/ReVancedAdsGroupClick;",
        "Lapp/revanced/extension/vkvideo/ReVancedToggleListener;",
        "Lapp/revanced/extension/vkvideo/ReVancedBackClick;",
    )
}
