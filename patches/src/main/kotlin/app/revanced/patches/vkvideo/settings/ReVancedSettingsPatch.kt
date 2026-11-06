package app.revanced.patches.vkvideo.settings

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val ITEM_CONSTRUCTOR_TYPES = "Lkotlin/jvm/functions/Function1;"

val revancedSettingsPatch = bytecodePatch(
    name = "ReVanced settings",
    description = "Adds a ReVanced entry to the profile settings menu with Ads toggles.",
) {
    compatibleWith("com.vk.vkvideo")

    apply {
        val menuMethod = profileMenuItemsFingerprint.method
        val types = resolveTypes(menuMethod.implementation!!.instructions)

        injectSettingsClasses(this, types)

        // The ReVanced entry is appended by the Sign-out row builder before it appends
        // Sign out / Login, so the entry renders directly above it in both cm(II) branches.
        val signOutMethod = signOutRowFingerprint.method
        val listRegister = signOutMethod.implementation!!.registerCount - parameterWidth(signOutMethod) + 1
        val call = if (listRegister > 15) {
            "invoke-static/range {v$listRegister .. v$listRegister}, $ADS_SETTINGS_CLASS->addMenuEntry(Ljava/util/List;)V"
        } else {
            "invoke-static {v$listRegister}, $ADS_SETTINGS_CLASS->addMenuEntry(Ljava/util/List;)V"
        }
        signOutMethod.addInstruction(0, call)
    }
}

internal fun resolveTypes(instructions: List<BuilderInstruction>): SettingsMenuTypes {
    val playerSettingsIndex = instructions.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference
        reference is FieldReference && reference.name == "PLAYER_SETTINGS"
    }
    if (playerSettingsIndex < 0) error("PLAYER_SETTINGS field reference not found")

    val enumType = ((instructions[playerSettingsIndex] as ReferenceInstruction).reference as FieldReference).definingClass

    val itemConstructorIndex = (playerSettingsIndex - 1 downTo 0).firstOrNull { index ->
        val instruction = instructions[index]
        val reference = (instruction as? ReferenceInstruction)?.reference
        instruction.opcode == Opcode.INVOKE_DIRECT &&
            reference is MethodReference &&
            reference.returnType == "V" &&
            reference.parameterTypes.size == 4 &&
            reference.parameterTypes[0] == "I" &&
            reference.parameterTypes[1] in setOf("I", "Ljava/lang/String;") &&
            reference.parameterTypes[2] == ITEM_CONSTRUCTOR_TYPES &&
            reference.parameterTypes[3] == "Z"
    } ?: error("Menu item constructor not found before PLAYER_SETTINGS")

    val itemConstructor = instructions[itemConstructorIndex] as FiveRegisterInstruction
    val itemClass = newInstanceTypeBefore(instructions, itemConstructorIndex, itemConstructor.registerC)
        ?: error("Menu item class not found")

    val iconRegister = itemConstructor.registerE
    val icon = (itemConstructorIndex - 1 downTo 0).firstNotNullOfOrNull { index ->
        val instruction = instructions[index]
        val register = (instruction as? OneRegisterInstruction)?.registerA
        if (instruction is NarrowLiteralInstruction &&
            register == iconRegister &&
            instruction.opcode in setOf(Opcode.CONST, Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST_HIGH16)
        ) {
            instruction.narrowLiteral
        } else {
            null
        }
    } ?: error("Menu item icon not found")

    val wrapperConstructorIndex = (playerSettingsIndex + 1 until instructions.size).firstOrNull { index ->
        val instruction = instructions[index]
        val reference = (instruction as? ReferenceInstruction)?.reference
        instruction.opcode == Opcode.INVOKE_DIRECT &&
            reference is MethodReference &&
            reference.returnType == "V" &&
            reference.parameterTypes == listOf("I", "Ljava/lang/Object;")
    } ?: error("Menu item wrapper constructor not found after PLAYER_SETTINGS")

    val wrapperConstructor = instructions[wrapperConstructorIndex] as FiveRegisterInstruction
    val wrapperType = newInstanceTypeAfter(
        instructions,
        playerSettingsIndex,
        wrapperConstructorIndex,
        wrapperConstructor.registerC,
    ) ?: error("Menu item wrapper class not found")

    return SettingsMenuTypes(
        menuItemType = enumType,
        menuItemWrapper = wrapperType,
        menuItemClass = itemClass,
        iconResourceId = icon,
    )
}

private fun newInstanceTypeBefore(
    instructions: List<BuilderInstruction>,
    beforeIndex: Int,
    register: Int,
): String? {
    for (index in beforeIndex - 1 downTo 0) {
        val instruction = instructions[index]
        if (instruction.opcode == Opcode.NEW_INSTANCE && (instruction as OneRegisterInstruction).registerA == register) {
            return ((instruction as ReferenceInstruction).reference as TypeReference).type
        }
    }
    return null
}

private fun newInstanceTypeAfter(
    instructions: List<BuilderInstruction>,
    afterIndex: Int,
    beforeIndex: Int,
    register: Int,
): String? {
    for (index in afterIndex + 1 until beforeIndex) {
        val instruction = instructions[index]
        if (instruction.opcode == Opcode.NEW_INSTANCE && (instruction as OneRegisterInstruction).registerA == register) {
            return ((instruction as ReferenceInstruction).reference as TypeReference).type
        }
    }
    return null
}
