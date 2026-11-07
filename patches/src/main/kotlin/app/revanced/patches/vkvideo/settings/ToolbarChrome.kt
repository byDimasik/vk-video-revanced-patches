package app.revanced.patches.vkvideo.settings

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private val CONST_OPS = setOf(Opcode.CONST, Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST_HIGH16)
private val DIRECT_OPS = setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE)
private val VIRTUAL_OPS = setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE)

private fun Method.instructionList(): List<Instruction> =
    implementation?.instructions?.toList()
        ?: error("No implementation in ${definingClass}->${methodSignature()}")

private fun Method.methodSignature() = "$name($parameterTypes)$returnType"

/** Register operands of an invoke, in argument order (receiver first). */
private fun invokeArgRegs(instruction: Instruction): List<Int> = when (instruction) {
    is FiveRegisterInstruction ->
        listOf(instruction.registerC, instruction.registerD, instruction.registerE, instruction.registerF, instruction.registerG)
            .take(instruction.registerCount)
    is RegisterRangeInstruction ->
        (instruction.startRegister until instruction.startRegister + instruction.registerCount).toList()
    else -> error("Not an invoke: ${instruction.opcode}")
}

/** Literal constant feeding [register], scanned backwards from [index]. */
private fun List<Instruction>.constFeeding(index: Int, register: Int): Int {
    for (i in index downTo 0) {
        val instruction = this[i]
        val reg = (instruction as? OneRegisterInstruction)?.registerA
        if (instruction is NarrowLiteralInstruction && instruction.opcode in CONST_OPS && reg == register) {
            return instruction.narrowLiteral
        }
    }
    error("No constant found feeding v$register before instruction $index")
}

private fun List<Instruction>.methodRef(index: Int): MethodReference =
    (this[index] as ReferenceInstruction).reference as MethodReference

/**
 * Finds the constructor invoke of a `new-instance` of [type] and returns the
 * constructor reference with its argument registers (receiver first).
 */
private fun List<Instruction>.findConstructor(type: String): Triple<MethodReference, List<Int>, Int> {
    for (i in indices) {
        val instruction = this[i]
        if (instruction.opcode != Opcode.NEW_INSTANCE) continue
        val register = (instruction as OneRegisterInstruction).registerA
        val typeRef = (instruction as ReferenceInstruction).reference as TypeReference
        if (typeRef.type != type) continue

        for (j in i + 1 until size) {
            val call = this[j]
            if (call.opcode !in DIRECT_OPS) continue
            val reference = (call as ReferenceInstruction).reference as? MethodReference ?: continue
            if (reference.definingClass != type) continue
            val regs = invokeArgRegs(call)
            if (regs.firstOrNull() == register) return Triple(reference, regs, j)
        }
    }
    error("Constructor invoke of $type not found")
}

private fun methodRefString(reference: MethodReference): String =
    "${reference.definingClass}->${reference.name}(${reference.parameterTypes.joinToString("")})${reference.returnType}"

/**
 * Extracts every chrome reference the injected settings screen needs from
 * `MaterialPreferenceToolbarFragment`'s own onCreateView/onViewCreated — the
 * transcription source verified in the 1.170 APK.
 */
internal fun extractToolbarChrome(classDef: ClassDef, allClasses: List<ClassDef>): ToolbarChrome {
    val onCreateView = classDef.methods.first { it.name == "onCreateView" }
        .instructionList()
    val onViewCreated = classDef.methods.first { it.name == "onViewCreated" }
        .instructionList()

    // onCreateView: inflate(chrome layout) -> findViewById(appkit_content) -> addView
    //   -> findViewById(legacy toolbar) -> hide -> findViewById(shadow) -> hide.
    val inflateIndex = onCreateView.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference
        instruction.opcode in VIRTUAL_OPS &&
            reference is MethodReference &&
            reference.name == "inflate" &&
            reference.parameterTypes == listOf("I", "Landroid/view/ViewGroup;", "Z")
    }
    if (inflateIndex < 0) error("chrome onCreateView: inflate not found")
    val chromeLayoutId = onCreateView.constFeeding(inflateIndex, invokeArgRegs(onCreateView[inflateIndex])[1])

    fun findViewByIdConsts(instructions: List<Instruction>): List<Pair<Int, Int>> =
        instructions.mapIndexedNotNull { index, instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            if (instruction.opcode in VIRTUAL_OPS &&
                reference is MethodReference &&
                reference.name == "findViewById" &&
                reference.parameterTypes == listOf("I")
            ) {
                val regs = invokeArgRegs(instruction)
                index to instructions.constFeeding(index, regs[1])
            } else {
                null
            }
        }

    val onCreateViewFinds = findViewByIdConsts(onCreateView)
    if (onCreateViewFinds.size < 3) error("chrome onCreateView: expected content/toolbar/shadow findViewById calls")
    val contentId = onCreateViewFinds[0].second

    fun checkCastType(instructions: List<Instruction>, fromIndex: Int): String? {
        for (i in fromIndex + 1 until instructions.size) {
            val instruction = instructions[i]
            if (instruction.opcode == Opcode.CHECK_CAST) {
                return ((instruction as ReferenceInstruction).reference as TypeReference).type
            }
            if (instruction.opcode == Opcode.MOVE_RESULT_OBJECT || instruction.opcode == Opcode.IF_EQZ) continue
            break
        }
        return null
    }

    val legacyToolbarId = onCreateViewFinds
        .firstOrNull { checkCastType(onCreateView, it.first) == "Landroidx/appcompat/widget/Toolbar;" }
        ?.second ?: error("chrome onCreateView: legacy toolbar findViewById not found")
    val shadowId = onCreateViewFinds
        .firstOrNull { checkCastType(onCreateView, it.first) == "Lcom/vk/core/view/AppBarShadowView;" }
        ?.second ?: error("chrome onCreateView: app bar shadow findViewById not found")

    val hideHelperRef = onCreateView
        .mapIndexedNotNull { index, instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            if (instruction.opcode == Opcode.INVOKE_STATIC &&
                reference is MethodReference &&
                reference.parameterTypes == listOf("Landroid/view/View;") &&
                reference.returnType == "V"
            ) {
                methodRefString(reference)
            } else {
                null
            }
        }
        .firstOrNull() ?: error("chrome onCreateView: toolbar hide helper not found")

    // onViewCreated: VkTopBar ctor chain with default-argument constants, the
    // accessibility-back resource helper, the app bar addView and the chrome check.
    val topBarBase = "Lcom/vk/core/view/components/topbar/VkTopBar"
    val topBarType = "${topBarBase};"
    val (topBarCtor, topBarRegs, topBarCtorIndex) = onViewCreated.findConstructor(topBarType)
    // ctor args: [receiver, Context, AttributeSet, styleInt, flagsInt]
    val topBarStyleInt = onViewCreated.constFeeding(topBarCtorIndex, topBarRegs[3])
    val topBarFlagsInt = onViewCreated.constFeeding(topBarCtorIndex, topBarRegs[4])

    val titleType = "${topBarBase}\$Middle\$Text\$Title;"
    val (titleCtor, titleRegs, titleCtorIndex) = onViewCreated.findConstructor(titleType)
    val titleStyleInt = onViewCreated.constFeeding(titleCtorIndex, titleRegs.last())

    val textType = "${topBarBase}\$Middle\$Text;"
    val (textCtor, textRegs, textCtorIndex) = onViewCreated.findConstructor(textType)
    val textStyleInt = onViewCreated.constFeeding(textCtorIndex, textRegs.last())

    val backTokenType = "${topBarBase}\$c;"
    val (backCtor, backRegs, backCtorIndex) = onViewCreated.findConstructor(backTokenType)
    val backStyleInt = onViewCreated.constFeeding(backCtorIndex, backRegs.last())

    val companionIndex = onViewCreated.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference
        instruction.opcode == Opcode.SGET_OBJECT &&
            reference is FieldReference &&
            reference.name == "Companion" &&
            reference.definingClass == "Lpx1/a;"
    }
    if (companionIndex < 0) error("chrome onViewCreated: px1/a Companion not found")
    val companionFieldRef = (onViewCreated[companionIndex] as ReferenceInstruction)
        .reference.let { it as FieldReference }
        .let { "${it.definingClass}->${it.name}:${it.type}" }
    val companionReg = (onViewCreated[companionIndex] as OneRegisterInstruction).registerA

    val resHelperIndex = onViewCreated.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference
        instruction.opcode == Opcode.INVOKE_STATIC &&
            reference is MethodReference &&
            reference.returnType == "Lpx1/a\$f;" &&
            invokeArgRegs(instruction).firstOrNull() == companionReg
    }
    if (resHelperIndex < 0) error("chrome onViewCreated: accessibility-back resource helper not found")
    val resHelperRef = methodRefString(onViewCreated.methodRef(resHelperIndex))
    val accessibilityBackId =
        onViewCreated.constFeeding(resHelperIndex, invokeArgRegs(onViewCreated[resHelperIndex])[1])

    val appBarLayoutId = findViewByIdConsts(onViewCreated)
        .firstOrNull()?.second ?: error("chrome onViewCreated: app bar layout id not found")

    val chromeCheckRef = onViewCreated
        .mapIndexedNotNull { _, instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            if (instruction.opcode == Opcode.INVOKE_STATIC &&
                reference is MethodReference &&
                reference.parameterTypes == listOf("Lcom/vk/core/fragments/FragmentImpl;", topBarType) &&
                reference.returnType == "Z"
            ) {
                methodRefString(reference)
            } else {
                null
            }
        }
        .firstOrNull() ?: error("chrome onViewCreated: top-bar chrome check not found")

    // The app's own toolbar-back dispatch (transcribed from the R8-collapsed
    // CallableReference body): g57/a.b(FragmentImpl) routes through the nav-host
    // container pop (o54/p.J) and falls back to finish(). Resolved as the unique
    // `(FragmentImpl)V` method of the same helper class that carries the chrome
    // check — the obfuscated method name must never be hardcoded.
    val chromeCheckClassType = chromeCheckRef.substringBefore("->")
    val backHandlerClass = allClasses.firstOrNull { it.type == chromeCheckClassType }
    val backHandlerRef = backHandlerClass?.methods
        ?.filter { method ->
            method.parameterTypes == listOf("Lcom/vk/core/fragments/FragmentImpl;") &&
                method.returnType == "V" &&
                !method.name.startsWith("<")
        }
        ?.map { "${chromeCheckClassType}->${it.name}(Lcom/vk/core/fragments/FragmentImpl;)V" }
        ?.singleOrNull() ?: error("chrome: toolbar-back dispatch method not found on $chromeCheckClassType")

    return ToolbarChrome(
        chromeLayoutId = chromeLayoutId,
        contentId = contentId,
        legacyToolbarId = legacyToolbarId,
        shadowId = shadowId,
        hideHelperRef = hideHelperRef,
        appBarLayoutId = appBarLayoutId,
        topBarCtor = methodRefString(topBarCtor),
        topBarStyleInt = topBarStyleInt,
        topBarFlagsInt = topBarFlagsInt,
        titleCtor = methodRefString(titleCtor),
        titleStyleInt = titleStyleInt,
        textCtor = methodRefString(textCtor),
        textStyleInt = textStyleInt,
        backCtor = methodRefString(backCtor),
        backStyleInt = backStyleInt,
        companionFieldRef = companionFieldRef,
        backHandlerRef = backHandlerRef,
        resHelperRef = resHelperRef,
        accessibilityBackId = accessibilityBackId,
        chromeCheckRef = chromeCheckRef,
    )
}

/**
 * Resolves the fragment-opener class from the matched General-settings click
 * lambda: the type of the `new-instance` feeding the
 * `(Class, Class, Bundle)` constructor whose `h(Context)` opens the screen.
 */
internal data class NavOpener(
    /** The instantiated opener type (may be a subclass of the method owners). */
    val instanceType: String,
    /** Defining class of the `(Class, Class, Bundle)` constructor. */
    val ctorOwner: String,
    /** Defining class of the `h(Context)` opener. */
    val hOwner: String,
)

internal fun resolveNavOpener(method: Method): NavOpener {
    val instructions = method.instructionList()

    // Anchor: the const-class of the app's General settings screen feeds the
    // opener constructor's fragment-class argument.
    val targetClassIndex = instructions.indexOfFirst { instruction ->
        instruction.opcode == Opcode.CONST_CLASS &&
            ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type == NAV_OPENER_TARGET_CLASS
    }
    if (targetClassIndex < 0) error("nav opener: ${NAV_OPENER_TARGET_CLASS} const-class not found")
    val targetClassReg = (instructions[targetClassIndex] as OneRegisterInstruction).registerA

    val ctorIndex = instructions.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference
        instruction.opcode in DIRECT_OPS &&
            reference is MethodReference &&
            reference.parameterTypes == listOf("Ljava/lang/Class;", "Ljava/lang/Class;", "Landroid/os/Bundle;") &&
            reference.returnType == "V" &&
            invokeArgRegs(instruction).getOrNull(1) == targetClassReg
    }
    if (ctorIndex < 0) error("nav opener: (Class, Class, Bundle) constructor receiving the target class not found")
    val ctorOwner = ((instructions[ctorIndex] as ReferenceInstruction).reference as MethodReference).definingClass

    val receiverReg = invokeArgRegs(instructions[ctorIndex]).first()
    val instanceType = ((ctorIndex downTo 0).firstNotNullOfOrNull { index ->
        val instruction = instructions[index]
        val register = (instruction as? OneRegisterInstruction)?.registerA
        if (instruction.opcode == Opcode.NEW_INSTANCE && register == receiverReg) {
            (instruction as ReferenceInstruction).reference.let { it as TypeReference }.type
        } else {
            null
        }
    } ?: error("nav opener: new-instance not found for the opener"))

    // h(Context) dispatched on the same instance — the method may be declared on a
    // superclass of the instantiated opener (R8 subclasses openers), so match by
    // receiver register, not by defining class.
    val hIndex = instructions.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference
        instruction.opcode in VIRTUAL_OPS &&
            reference is MethodReference &&
            reference.name == "h" &&
            reference.parameterTypes == listOf("Landroid/content/Context;") &&
            invokeArgRegs(instruction).firstOrNull() == receiverReg
    }
    if (hIndex < 0) error("nav opener: h(Context) not found on the opener instance")
    val hOwner = ((instructions[hIndex] as ReferenceInstruction).reference as MethodReference).definingClass

    return NavOpener(instanceType, ctorOwner, hOwner)
}
