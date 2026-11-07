package app.revanced.patches.vkvideo.settings

import app.revanced.patches.vkvideo.util.PatchTestUtil
import app.revanced.patches.vkvideo.util.PatchTestUtil.applyPatches
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Structural tests for the five injected classes: the settings helper, the
 * Function1 screen opener, the native full-screen fragment (themed chrome +
 * VkTopBar + VkSwitchItem rows), the switch listener and the back handler.
 *
 * Everything the templates interpolate (chrome ids/refs, opener type, listener
 * method name) comes from the app's own classes via the fingerprints — asserted
 * here against the 1.170-shaped fixtures and against the real APK in the e2e
 * suite. Visual/theme behavior and persistence round-trips are device-gated.
 */
class InjectedClassesTest {

    private val rows = listOf(
        Row(
            key = "revanced_ads_video",
            label = "Block video ads",
            getter = "isVideoAdsEnabled",
        ),
        Row(
            key = "revanced_ads_feed",
            label = "Block feed ads",
            getter = "isFeedAdsEnabled",
        ),
        Row(
            key = "revanced_ads_catalog",
            label = "Block catalog banners",
            getter = "isCatalogAdsEnabled",
        ),
        Row(
            key = "revanced_ads_topshelf",
            label = "Block top shelf promotions",
            getter = "isTopShelfAdsEnabled",
        ),
    )

    private data class Row(val key: String, val label: String, val getter: String)

    private val expectedDescriptors = setOf(
        ADS_SETTINGS_CLASS,
        "Lapp/revanced/extension/vkvideo/StartReVancedSettings;",
        SETTINGS_FRAGMENT_CLASS,
        "Lapp/revanced/extension/vkvideo/ReVancedToggleListener;",
        "Lapp/revanced/extension/vkvideo/ReVancedBackClick;",
    )

    private val dialogEraDescriptors = setOf(
        "Lapp/revanced/extension/vkvideo/ReVancedSettingsDialog;",
        "Lapp/revanced/extension/vkvideo/ToggleListener;",
    )

    private lateinit var context: app.revanced.patcher.patch.BytecodePatchContext

    @BeforeEach
    fun patchContext() {
        context = PatchTestUtil.createBytecodeContext(*PatchTestUtil.settingsScreenFixture170.toTypedArray())
        assertNotNull(profileMenuItemsFingerprint.matchOrNull(context))
        applyPatches(context, revancedSettingsPatch)
    }

    private fun injectedClass(descriptor: String) =
        context.classDefs.first { it.type == descriptor }

    private fun method(descriptor: String, name: String): List<Instruction> =
        injectedClass(descriptor).methods.first { it.name == name }.implementation!!.instructions.toList()

    @Test
    fun `injects exactly the five classes, idempotently, without the dialog era`() {
        val types = context.classDefs.map { it.type }.toSet()
        assertTrue(types.containsAll(expectedDescriptors))
        assertFalse(types.any { it in dialogEraDescriptors }, "dialog-era classes must be gone")

        applyPatches(context, revancedSettingsPatch)
        for (descriptor in expectedDescriptors) {
            assertEquals(1, context.classDefs.count { it.type == descriptor }, descriptor)
        }

        // buildContentView is deleted with its only caller (the removed dialog).
        val adsSettingsMethods = injectedClass(ADS_SETTINGS_CLASS).methods.map { it.name }
        assertFalse(adsSettingsMethods.contains("buildContentView"))
    }

    @Test
    fun `fragment extends FragmentImpl with a public no-arg constructor`() {
        val fragment = injectedClass(SETTINGS_FRAGMENT_CLASS)
        assertEquals("Lcom/vk/core/fragments/FragmentImpl;", fragment.superclass)

        val ctor = fragment.methods.first { it.name == "<init>" }
        assertEquals("()V", ctor.let { "(${it.parameterTypes.joinToString("")})${it.returnType}" })
        assertTrue(com.android.tools.smali.dexlib2.AccessFlags.PUBLIC.isSet(ctor.accessFlags))
    }

    @Test
    fun `onCreateView inflates the chrome, embeds the content, hides the appkit bar`() {
        val chrome = extractToolbarChrome(
            context.classDefs.first { it.type == "Lcom/vk/prefui/fragments/MaterialPreferenceToolbarFragment;" },
            context.classDefs.toList(),
        )
        val instructions = method(SETTINGS_FRAGMENT_CLASS, "onCreateView")

        // inflate(chrome layout, container, false) -> coordinator root
        val inflateIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                reference is MethodReference &&
                reference.name == "inflate" &&
                reference.parameterTypes == listOf("I", "Landroid/view/ViewGroup;", "Z")
        }
        assertTrue(inflateIndex >= 0, "onCreateView must inflate the chrome layout")
        val layoutReg = (instructions[inflateIndex] as FiveRegisterInstruction).registerD
        val layoutConst = (0..inflateIndex).reversed()
            .first { i -> instructions[i] is NarrowLiteralInstruction && (instructions[i] as OneRegisterInstruction).registerA == layoutReg }
        assertEquals(chrome.chromeLayoutId, (instructions[layoutConst] as NarrowLiteralInstruction).narrowLiteral)

        // content built by AdsSettings.buildAdsContent(requireContext())
        val contentIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference && reference.name == "buildAdsContent"
        }
        assertTrue(contentIndex > inflateIndex, "content must be built after the chrome root")
        val requireContextIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference && reference.name == "requireContext"
        }
        assertTrue(requireContextIndex in 0 until contentIndex)

        // addView into the appkit_content findViewById result
        val findViewByIdIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference && reference.name == "findViewById"
        }
        assertTrue(findViewByIdIndex > contentIndex)
        val contentIdReg = (instructions[findViewByIdIndex] as FiveRegisterInstruction).registerD
        val contentIdConst = (0..findViewByIdIndex).reversed()
            .first { i -> instructions[i] is NarrowLiteralInstruction && (instructions[i] as OneRegisterInstruction).registerA == contentIdReg }
        assertEquals(chrome.contentId, (instructions[contentIdConst] as NarrowLiteralInstruction).narrowLiteral)

        val addViewIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference && reference.name == "addView"
        }
        assertTrue(addViewIndex > findViewByIdIndex)

        // hide helper (NOT inline setVisibility(8)) on both the legacy toolbar and shadow
        val hideHelper = (chrome.hideHelperRef.split("->")[0] to chrome.hideHelperRef.substringAfter("->"))
        val hideHelperName = hideHelper.second.substringBefore("(")
        val hideCalls = instructions.filterIndexed { _, instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference &&
                reference.definingClass == hideHelper.first &&
                reference.name == hideHelperName
        }
        assertEquals(2, hideCalls.size, "toolbar and shadow must both go through the hide helper")
        assertTrue(
            instructions.none { instruction ->
                instruction.opcode == Opcode.CONST_16 &&
                    (instruction as NarrowLiteralInstruction).narrowLiteral == 0x8
            },
            "onCreateView must not inline setVisibility(8)",
        )

        val returnIndex = instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
        assertTrue(returnIndex > addViewIndex)
    }

    @Test
    fun `onViewCreated builds the VkTopBar chrome with a direct finish back handler`() {
        val chrome = extractToolbarChrome(
            context.classDefs.first { it.type == "Lcom/vk/prefui/fragments/MaterialPreferenceToolbarFragment;" },
            context.classDefs.toList(),
        )
        val instructions = method(SETTINGS_FRAGMENT_CLASS, "onViewCreated")

        fun ctorIndexOf(type: String): Int = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference &&
                instruction.opcode in setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE) &&
                reference.definingClass == type
        }

        val topBarIndex = ctorIndexOf("Lcom/vk/core/view/components/topbar/VkTopBar;")
        assertTrue(topBarIndex >= 0)
        assertEquals(chrome.topBarCtor, methodRefString(instructions, topBarIndex))
        assertEquals(chrome.topBarStyleInt, literalArg(instructions, topBarIndex, 3))
        assertEquals(chrome.topBarFlagsInt, literalArg(instructions, topBarIndex, 4))

        val titleIndex = ctorIndexOf("Lcom/vk/core/view/components/topbar/VkTopBar\$Middle\$Text\$Title;")
        assertTrue(titleIndex > topBarIndex)
        assertEquals(chrome.titleCtor, methodRefString(instructions, titleIndex))
        assertEquals(chrome.titleStyleInt, literalArg(instructions, titleIndex, 5))
        val titleString = instructions.subList(0, titleIndex)
            .filterIsInstance<ReferenceInstruction>()
            .mapNotNull { (it.reference as? StringReference)?.string }
        assertTrue(titleString.contains("ReVanced"), "title must be the ReVanced string")

        val textIndex = ctorIndexOf("Lcom/vk/core/view/components/topbar/VkTopBar\$Middle\$Text;")
        assertTrue(textIndex > titleIndex)
        assertEquals(chrome.textCtor, methodRefString(instructions, textIndex))
        assertEquals(chrome.textStyleInt, literalArg(instructions, textIndex, 5))

        val setMiddleIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference && reference.name == "setMiddle"
        }
        assertTrue(setMiddleIndex > textIndex)

        val backIndex = ctorIndexOf("Lcom/vk/core/view/components/topbar/VkTopBar\$c;")
        assertTrue(backIndex > setMiddleIndex)
        assertEquals(chrome.backCtor, methodRefString(instructions, backIndex))
        assertEquals(chrome.backStyleInt, literalArg(instructions, backIndex, 5))

        // Back handler: ReVancedBackClick constructed with this fragment; the
        // accessibility label via the app's own resource helper.
        val backClickCtorIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference &&
                reference.definingClass == "Lapp/revanced/extension/vkvideo/ReVancedBackClick;" &&
                reference.name == "<init>"
        }
        assertTrue(backClickCtorIndex in 0 until backIndex, "back click must be constructed before the back token")
        val backClickRef = (instructions[backClickCtorIndex] as ReferenceInstruction).reference as MethodReference
        assertEquals("Lcom/vk/core/fragments/FragmentImpl;", backClickRef.parameterTypes.single().toString())
        assertTrue(
            instructions.subList(0, backIndex).any { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference
                reference is MethodReference && reference.returnType == "Lpx1/a\$f;"
            },
            "accessibility-back label must come from the app's resource helper",
        )

        val setBackIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference && reference.name == "setBack"
        }
        assertTrue(setBackIndex > backIndex)

        val appBarIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference && reference.name == "setBefore"
        }
        assertTrue(appBarIndex > setBackIndex)

        // The back Function0 routes through the app's own back dispatch
        // (g57/a.b -> nav-container pop), like the app's own toolbar screens.
        val backClick = injectedClass("Lapp/revanced/extension/vkvideo/ReVancedBackClick;")
        val invoke = backClick.methods.first { it.name == "invoke" }
        val invokeInstructions = invoke.implementation!!.instructions.toList()
        val backDispatchIndex = invokeInstructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference && reference.definingClass == chrome.backHandlerRef.substringBefore("->")
        }
        assertTrue(backDispatchIndex >= 0, "back click must call the app's back dispatch ${chrome.backHandlerRef}")
    }

    @Test
    fun `buildAdsContent assembles four VkSwitchItem rows in bind order`() {
        val instructions = method(ADS_SETTINGS_CLASS, "buildAdsContent")

        val containerIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference &&
                reference.definingClass == "Landroid/widget/LinearLayout;" &&
                reference.name == "<init>"
        }
        assertTrue(containerIndex >= 0)
        val orientationIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference && reference.name == "setOrientation"
        }
        assertTrue(orientationIndex > containerIndex)

        val switchType = "Lcom/vk/core/view/components/switch/VkSwitchItem;"
        var cursor = orientationIndex

        fun findFrom(start: Int, predicate: (Instruction) -> Boolean): Int =
            ((start + 1) until instructions.size).firstOrNull { predicate(instructions[it]) } ?: -1

        fun refName(instruction: Instruction): String? =
            ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.name

        fun refClass(instruction: Instruction): MethodReference? =
            (instruction as? ReferenceInstruction)?.reference as? MethodReference

        for (row in rows) {
            val initIndex = findFrom(cursor) { instruction ->
                instruction.opcode == Opcode.INVOKE_DIRECT &&
                    refClass(instruction)?.definingClass == switchType &&
                    refName(instruction) == "<init>"
            }
            assertTrue(initIndex > cursor, "row ${row.key} must be constructed after the previous row")
            val init = instructions[initIndex] as FiveRegisterInstruction
            val initRef = (instructions[initIndex] as ReferenceInstruction).reference as MethodReference
            assertEquals(listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;"), initRef.parameterTypes)

            // setTitle(new px1/a$h(label))
            val setTitleIndex = findFrom(initIndex) { instruction ->
                refClass(instruction)?.definingClass == switchType && refName(instruction) == "setTitle"
            }
            assertTrue(setTitleIndex > initIndex, "setTitle must follow the row constructor for ${row.key}")
            val wrapperCtor = instructions[setTitleIndex - 1] as ReferenceInstruction
            val wrapperRef = wrapperCtor.reference as MethodReference
            assertEquals("Lpx1/a\$h;", wrapperRef.definingClass)
            assertEquals("Ljava/lang/CharSequence;", wrapperRef.parameterTypes.single().toString())
            val label = instructions[setTitleIndex - 2] as ReferenceInstruction
            assertEquals(row.label, (label.reference as StringReference).string, "row label")

            // setChecked(getter())
            val setCheckedIndex = findFrom(setTitleIndex) { instruction ->
                refClass(instruction)?.definingClass == switchType && refName(instruction) == "setChecked"
            }
            assertTrue(setCheckedIndex > setTitleIndex, "setChecked must follow setTitle for ${row.key}")
            // setChecked consumes the getter's move-result: the invoke sits one further back.
            val getterCall = instructions[setCheckedIndex - 2] as ReferenceInstruction
            val getterRef = getterCall.reference as MethodReference
            assertEquals(ADS_SETTINGS_CLASS, getterRef.definingClass)
            assertEquals(row.getter, getterRef.name)

            // setListener(new ReVancedToggleListener(key))
            val setListenerIndex = findFrom(setCheckedIndex) { instruction ->
                refClass(instruction)?.definingClass == switchType && refName(instruction) == "setListener"
            }
            assertTrue(setListenerIndex > setCheckedIndex, "setListener must follow setChecked for ${row.key}")
            val listenerCtor = instructions[setListenerIndex - 1] as ReferenceInstruction
            val listenerRef = listenerCtor.reference as MethodReference
            assertEquals("Lapp/revanced/extension/vkvideo/ReVancedToggleListener;", listenerRef.definingClass)
            assertEquals("Ljava/lang/String;", listenerRef.parameterTypes.single().toString())
            val key = instructions[setListenerIndex - 2] as ReferenceInstruction
            assertEquals(row.key, (key.reference as StringReference).string, "listener key")

            // setAlignSwitchToEnd(true)
            val alignIndex = findFrom(setListenerIndex) { instruction ->
                refClass(instruction)?.definingClass == switchType && refName(instruction) == "setAlignSwitchToEnd"
            }
            assertTrue(alignIndex > setListenerIndex, "setAlignSwitchToEnd must follow setListener for ${row.key}")

            cursor = alignIndex
        }

        // 6dip vertical margins, density-scaled at runtime.
        val densityIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is FieldReference && reference.name == "density"
        }
        assertTrue(densityIndex >= 0, "margins must be density-scaled")
        val sixFloat = instructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.CONST_HIGH16 &&
                (instruction as NarrowLiteralInstruction).narrowLiteral == 0x40c00000.toInt()
        }
        assertTrue(sixFloat > containerIndex, "the 6.0f row margin literal must be density-multiplied, never applied raw")
        val setMargins = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference && reference.name == "setMargins"
        }
        assertTrue(setMargins > sixFloat)
    }

    @Test
    fun `injected smali has no literal colors or style references`() {
        for (descriptor in expectedDescriptors) {
            for (m in injectedClass(descriptor).methods) {
                for (instruction in m.implementation!!.instructions) {
                    val literal = (instruction as? NarrowLiteralInstruction)?.narrowLiteral ?: continue
                    if ((instruction as Instruction).opcode == Opcode.CONST_HIGH16 ||
                        instruction.opcode == Opcode.CONST
                    ) {
                        assertFalse(
                            literal in 0xff000000..0xffffffffL,
                            "$descriptor->${m.name}: literal color 0x${literal.toString(16)}",
                        )
                    }
                    assertFalse(
                        literal in 0x7f120000..0x7f13ffff,
                        "$descriptor->${m.name}: literal style reference 0x${literal.toString(16)}",
                    )
                }
            }
        }
    }

    @Test
    fun `toggle listener persists through AdsSettings setEnabled`() {
        val listenerMethod = switchListenerMethodFingerprint.getMethod(context).name
        val listener = injectedClass("Lapp/revanced/extension/vkvideo/ReVancedToggleListener;")
        val callback = listener.methods.first { it.name == listenerMethod }

        val instructions = callback.implementation!!.instructions.toList()
        val setEnabledIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference && reference.name == "setEnabled"
        }
        assertTrue(setEnabledIndex >= 0, "listener must persist via AdsSettings.setEnabled")
        val setEnabled = instructions[setEnabledIndex] as FiveRegisterInstruction
        val reference = (instructions[setEnabledIndex] as ReferenceInstruction).reference as MethodReference
        assertEquals(ADS_SETTINGS_CLASS, reference.definingClass)
        assertEquals(listOf("Ljava/lang/String;", "Z"), reference.parameterTypes.map { it.toString() })
        assertEquals(callback.implementation!!.registerCount - 1, setEnabled.registerD, "checked value must be passed through")
    }

    @Test
    fun `opener opens the fragment through the app's nav builder and returns null`() {
        val opener = resolveNavOpener(navOpenerFingerprint.getMethod(context))

        val instructions = method("Lapp/revanced/extension/vkvideo/StartReVancedSettings;", "invoke")
        val castIndex = instructions.indexOfFirst { it.opcode == Opcode.CHECK_CAST }
        assertTrue(castIndex >= 0, "argument must be cast to Context")

        val ctorIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference &&
                reference.definingClass == opener.ctorOwner &&
                reference.name == "<init>"
        }
        assertTrue(ctorIndex > castIndex, "opener must be constructed after the cast")
        val ctor = instructions[ctorIndex] as FiveRegisterInstruction
        val ctorRef = (instructions[ctorIndex] as ReferenceInstruction).reference as MethodReference
        assertEquals(
            listOf("Ljava/lang/Class;", "Ljava/lang/Class;", "Landroid/os/Bundle;"),
            ctorRef.parameterTypes.map { it.toString() },
        )
        val fragmentClassConst = instructions.subList(0, ctorIndex)
            .filterIsInstance<ReferenceInstruction>()
            .mapNotNull { (it.reference as? TypeReference)?.type }
        assertTrue(
            fragmentClassConst.contains(SETTINGS_FRAGMENT_CLASS),
            "opener must receive the ReVanced settings fragment class",
        )

        val openIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is MethodReference &&
                reference.definingClass == opener.ctorOwner &&
                reference.name == "h" &&
                reference.parameterTypes == listOf("Landroid/content/Context;")
        }
        assertTrue(openIndex > ctorIndex, "h(Context) must open the screen")

        val returnObject = instructions.last()
        assertEquals(Opcode.RETURN_OBJECT, returnObject.opcode)
        val nullConst = instructions[instructions.size - 2]
        assertEquals(Opcode.CONST_4, nullConst.opcode)
        assertEquals(0, (nullConst as NarrowLiteralInstruction).narrowLiteral)
    }

    private fun methodRefString(instructions: List<Instruction>, index: Int): String {
        val reference = (instructions[index] as ReferenceInstruction).reference as MethodReference
        return "${reference.definingClass}->${reference.name}(${reference.parameterTypes.joinToString("")})${reference.returnType}"
    }

    /** The literal feeding invoke argument slot [slot] (0 = receiver) at [index]. */
    private fun literalArg(instructions: List<Instruction>, index: Int, slot: Int): Int {
        val invoke = instructions[index]
        val register = when (invoke) {
            is FiveRegisterInstruction -> listOf(invoke.registerC, invoke.registerD, invoke.registerE, invoke.registerF, invoke.registerG)[slot]
            is com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction ->
                invoke.startRegister + slot
            else -> error("not an invoke")
        }
        return (0..index).reversed()
            .first { i ->
                instructions[i] is NarrowLiteralInstruction &&
                    (instructions[i] as OneRegisterInstruction).registerA == register
            }
            .let { (instructions[it] as NarrowLiteralInstruction).narrowLiteral }
    }
}
