package app.revanced.patches.vkvideo.settings

import app.revanced.patches.vkvideo.util.PatchTestUtil
import app.revanced.patches.vkvideo.util.PatchTestUtil.execute
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import org.junit.jupiter.api.Assertions.assertEquals
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
            sources = PatchTestUtil.profileMenuFixture,
            recyclerMethod = "nm",
            itemClass = "Lcom/vk/video/screens/profile/adapter/holder/i\$a;",
            wrapperClass = "Lz17/e\$a;",
            icon = 0x7f082301,
        ),
        Fixture(
            name = "1.170",
            sources = PatchTestUtil.profileMenuFixture170,
            recyclerMethod = "hm",
            itemClass = "Lcom/vk/video/screens/profile/adapter/holder/j\$a;",
            wrapperClass = "Lj57/e\$a;",
            icon = 0x7f082302,
        ),
    )

    @TestFactory
    fun `inserts the ReVanced entry before the recycler call`(): List<DynamicTest> =
        fixtures.map { fixture ->
            DynamicTest.dynamicTest(fixture.name) {
                val context = PatchTestUtil.createBytecodeContext(*fixture.sources.toTypedArray())
                assertNotNull(profileMenuItemsFingerprint.matchOrNull(context))

                revancedSettingsPatch.execute(context)

                val instructions = profileMenuItemsFingerprint.getMethod(context).getInstructions()
                val recyclerIndex = instructions.indexOfFirst { instruction ->
                    val reference = (instruction as? ReferenceInstruction)?.reference
                    instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                        reference is MethodReference &&
                        reference.name == fixture.recyclerMethod &&
                        reference.definingClass == "Lme/grishka/appkit/fragments/BaseRecyclerFragment;" &&
                        reference.parameterTypes == listOf("Ljava/util/List;", "Z") &&
                        reference.returnType == "V"
                }
                assertTrue(recyclerIndex > 0, "recycler call not found")

                val addMenuEntry = instructions[recyclerIndex - 1] as ReferenceInstruction
                assertEquals(Opcode.INVOKE_STATIC, addMenuEntry.opcode)
                val addMenuEntryReference = addMenuEntry.reference as MethodReference
                assertEquals(ADS_SETTINGS_CLASS, addMenuEntryReference.definingClass)
                assertEquals("addMenuEntry", addMenuEntryReference.name)
                assertEquals(listOf("Ljava/util/List;"), addMenuEntryReference.parameterTypes.map { it.toString() })

                val listRegister = (instructions[recyclerIndex] as FiveRegisterInstruction).registerD
                assertEquals(listRegister, (addMenuEntry as FiveRegisterInstruction).registerC)

                assertTrue(context.classDefs.any { it.type == ADS_SETTINGS_CLASS })

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
        "Lapp/revanced/extension/vkvideo/AdsSettings;",
        "Lapp/revanced/extension/vkvideo/StartReVancedSettings;",
        "Lapp/revanced/extension/vkvideo/ReVancedSettingsDialog;",
        "Lapp/revanced/extension/vkvideo/ToggleListener;",
    )
}
