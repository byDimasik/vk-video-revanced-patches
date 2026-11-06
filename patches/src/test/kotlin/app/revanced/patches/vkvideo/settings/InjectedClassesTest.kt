package app.revanced.patches.vkvideo.settings

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InjectedClassesTest {

    private val menuTypes = SettingsMenuTypes(
        menuItemType = "Lobf/e;",
        menuItemWrapper = "Lobf/w\$a;",
        menuItemClass = "Lobf/h\$a;",
        iconResourceId = 0x7f08230a,
    )

    private val expectedDescriptors = setOf(
        "Lapp/revanced/extension/vkvideo/AdsSettings;",
        "Lapp/revanced/extension/vkvideo/StartReVancedSettings;",
        "Lapp/revanced/extension/vkvideo/ReVancedSettingsDialog;",
        "Lapp/revanced/extension/vkvideo/ToggleListener;",
    )

    private val listenerClass = "Lapp/revanced/extension/vkvideo/ToggleListener;"

    private val rows = listOf(
        Row("revanced_ads_video", "isVideoAdsEnabled"),
        Row("revanced_ads_feed", "isFeedAdsEnabled"),
        Row("revanced_ads_catalog", "isCatalogAdsEnabled"),
    )

    private data class Row(val key: String, val enabledMethod: String)

    private fun createContext() = PatchTestUtil.createBytecodeContext(
        """
        .class public Lcom/example/Dummy;
        .super Ljava/lang/Object;
        """.trimIndent(),
    )

    private fun injectedSettings(context: app.revanced.patcher.patch.BytecodePatchContext) =
        context.classDefs.first { it.type == ADS_SETTINGS_CLASS }

    private fun method(
        context: app.revanced.patcher.patch.BytecodePatchContext,
        name: String,
    ): MutableMethod = MutableMethod(injectedSettings(context).methods.first { it.name == name })

    private fun indexOfInvoke(
        instructions: List<com.android.tools.smali.dexlib2.builder.BuilderInstruction>,
        methodName: String,
        start: Int = 0,
    ): Int {
        val index = (start until instructions.size).firstOrNull { index ->
            val reference = (instructions[index] as? ReferenceInstruction)?.reference
            reference is MethodReference && reference.name == methodName
        }
        assertNotNull(index, "expected invoke of $methodName after index $start")
        return index!!
    }

    @Test
    fun `injects every helper class`() {
        val context = createContext()
        val before = context.classDefs.map { it.type }.toSet()
        assertTrue(expectedDescriptors.none { it in before })

        injectSettingsClasses(context, menuTypes)

        val types = context.classDefs.map { it.type }.toSet()
        assertTrue(types.containsAll(expectedDescriptors))
    }

    @Test
    fun `second injection keeps exactly one class per descriptor`() {
        val context = createContext()
        injectSettingsClasses(context, menuTypes)
        injectSettingsClasses(context, menuTypes)

        for (descriptor in expectedDescriptors) {
            assertEquals(1, context.classDefs.count { it.type == descriptor }, descriptor)
        }
    }

    @Test
    fun `getBoolean defaults a missing preference to true`() {
        val context = createContext()
        injectSettingsClasses(context, menuTypes)

        val defaultInstr = method(context, "getBoolean").getInstructions()
            .first { it.opcode == Opcode.CONST_4 }
        assertEquals(1, (defaultInstr as NarrowLiteralInstruction).narrowLiteral)
    }

    @Test
    fun `setEnabled writes the parameter to the preference before apply`() {
        val context = createContext()
        injectSettingsClasses(context, menuTypes)

        val instructions = method(context, "setEnabled").getInstructions()
        val putBooleanIndex = indexOfInvoke(instructions, "putBoolean")
        val putBoolean = instructions[putBooleanIndex] as FiveRegisterInstruction
        val putBooleanReference = (putBoolean as ReferenceInstruction).reference as MethodReference
        assertEquals("Z", putBooleanReference.parameterTypes.last())
        assertEquals(2, putBoolean.registerE)

        val applyIndex = indexOfInvoke(instructions, "apply")
        assertTrue(applyIndex > putBooleanIndex)
    }

    @Test
    fun `addMenuEntry references fixture types and icon`() {
        val context = createContext()
        injectSettingsClasses(context, menuTypes)

        val instructions = method(context, "addMenuEntry").getInstructions()
        val references = instructions
            .filterIsInstance<ReferenceInstruction>()
            .map { it.reference }

        assertTrue(references.any { it is FieldReference && it.name == "ITEM_WITH_ICON" && it.definingClass == menuTypes.menuItemType })
        assertTrue(references.any { it is TypeReference && it.type == menuTypes.menuItemWrapper })
        assertTrue(references.any { it is TypeReference && it.type == menuTypes.menuItemClass })
        assertTrue(instructions.any {
            it.opcode == Opcode.CONST &&
                (it as NarrowLiteralInstruction).narrowLiteral == menuTypes.iconResourceId
        })
    }

    @Test
    fun `buildContentView carries the top shelf note`() {
        val context = createContext()
        injectSettingsClasses(context, menuTypes)

        val instructions = method(context, "buildContentView").getInstructions()
        assertTrue(
            instructions.any {
                it.opcode == Opcode.CONST_STRING &&
                    (it as ReferenceInstruction).reference.let {
                        it is StringReference && it.string == "Top shelf promotions are always blocked."
                    }
            },
        )
    }

    @Test
    fun `each row initializes from prefs before registering its listener`() {
        val context = createContext()
        injectSettingsClasses(context, menuTypes)

        val instructions = method(context, "buildContentView").getInstructions()

        for (row in rows) {
            val enabledIndex = indexOfInvoke(instructions, row.enabledMethod)

            val checkedIndex = indexOfInvoke(instructions, "setChecked", enabledIndex)
            assertTrue(checkedIndex > enabledIndex, row.key)

            val between = instructions.subList(enabledIndex + 1, checkedIndex)
            assertFalse(
                between.any { it.opcode in setOf(Opcode.CONST, Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST_HIGH16) },
                "setChecked must consume the persisted read for ${row.key}, not a constant",
            )

            val listenerIndex = indexOfInvoke(instructions, "setOnCheckedChangeListener", checkedIndex)
            val toggleIndex = indexOfInvoke(instructions, "<init>", checkedIndex)
            assertTrue(toggleIndex in (checkedIndex + 1) until listenerIndex)

            val toggle = instructions[toggleIndex] as ReferenceInstruction
            assertEquals(listenerClass, (toggle.reference as MethodReference).definingClass)
            assertEquals(listOf("Ljava/lang/String;"), (toggle.reference as MethodReference).parameterTypes)

            val key = (instructions[toggleIndex - 1] as ReferenceInstruction).reference
            assertTrue(key is StringReference && key.string == row.key)
        }
    }
}
