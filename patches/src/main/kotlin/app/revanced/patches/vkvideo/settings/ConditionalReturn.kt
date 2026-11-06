package app.revanced.patches.vkvideo.settings

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.accessFlags
import app.revanced.patcher.extensions.addInstructionsWithLabels
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11x
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21s
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21t
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31t
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22b
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22s
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22t
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22x
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction23x
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction32x
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35mi
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35ms
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rmi
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rms
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21s
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22b
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22s
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction23x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction32x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35mi
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35ms
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction3rc
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction3rmi
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction3rms

/**
 * Parameter width in DEX register slots: one slot per declared parameter,
 * plus the implicit receiver slot for non-static methods.
 */
internal fun parameterWidth(method: MutableMethod): Int =
    method.parameters.size + if (method.accessFlags(AccessFlags.STATIC)) 0 else 1

private inline fun <reified T> shift(insn: T, remap: (Int) -> Int): T = insn

private fun shiftRegisters(insn: Instruction, remap: (Int) -> Int): ImmutableInstruction {
    val imm = ImmutableInstruction.of(insn)
    return when (imm) {
        is ImmutableInstruction11x -> ImmutableInstruction11x(imm.opcode, remap(imm.registerA))
        is ImmutableInstruction21t -> ImmutableInstruction21t(imm.opcode, remap(imm.registerA), imm.codeOffset)
        is ImmutableInstruction21s -> ImmutableInstruction21s(imm.opcode, remap(imm.registerA), imm.narrowLiteral)
        is ImmutableInstruction21c -> ImmutableInstruction21c(imm.opcode, remap(imm.registerA), imm.reference)
        is ImmutableInstruction22x -> ImmutableInstruction22x(imm.opcode, remap(imm.registerA), remap(imm.registerB))
        is ImmutableInstruction22b -> ImmutableInstruction22b(imm.opcode, remap(imm.registerA), remap(imm.registerB), imm.narrowLiteral)
        is ImmutableInstruction22s -> ImmutableInstruction22s(imm.opcode, remap(imm.registerA), remap(imm.registerB), imm.narrowLiteral)
        is ImmutableInstruction22t -> ImmutableInstruction22t(imm.opcode, remap(imm.registerA), remap(imm.registerB), imm.codeOffset)
        is ImmutableInstruction22c -> ImmutableInstruction22c(imm.opcode, remap(imm.registerA), remap(imm.registerB), imm.reference)
        is ImmutableInstruction23x -> ImmutableInstruction23x(imm.opcode, remap(imm.registerA), remap(imm.registerB), remap(imm.registerC))
        is ImmutableInstruction32x -> ImmutableInstruction32x(imm.opcode, remap(imm.registerA), remap(imm.registerB))
        is ImmutableInstruction31t -> ImmutableInstruction31t(imm.opcode, remap(imm.registerA), imm.codeOffset)
        is ImmutableInstruction35c -> ImmutableInstruction35c(
            imm.opcode, imm.registerCount, remap(imm.registerC), remap(imm.registerD),
            remap(imm.registerE), remap(imm.registerF), remap(imm.registerG), imm.reference
        )
        is ImmutableInstruction3rc -> ImmutableInstruction3rc(imm.opcode, imm.registerCount, remap(imm.startRegister), imm.reference)
        is ImmutableInstruction35mi -> ImmutableInstruction35mi(
            imm.opcode, imm.registerCount, remap(imm.registerC), remap(imm.registerD),
            remap(imm.registerE), remap(imm.registerF), remap(imm.registerG), imm.inlineIndex
        )
        is ImmutableInstruction35ms -> ImmutableInstruction35ms(
            imm.opcode, imm.registerCount, remap(imm.registerC), remap(imm.registerD),
            remap(imm.registerE), remap(imm.registerF), remap(imm.registerG), imm.vtableIndex
        )
        is ImmutableInstruction3rmi -> ImmutableInstruction3rmi(imm.opcode, imm.registerCount, remap(imm.startRegister), imm.inlineIndex)
        is ImmutableInstruction3rms -> ImmutableInstruction3rms(imm.opcode, imm.registerCount, remap(imm.startRegister), imm.vtableIndex)
        else -> imm
    }
}

/**
 * Prepends a conditional early-return block at index 0:
 *
 * ```
 * invoke-static {}, <enabledMethod>
 * move-result v<scratch>
 * if-eqz v<scratch>, :revanced_continue
 * <fallbackSmali>
 * :revanced_continue -> first original instruction (via ExternalLabel)
 * ```
 *
 * dexlib2's `MutableMethodImplementation.registerCount` is immutable after
 * construction, so the frame is grown via an [ImmutableMethodImplementation]
 * rebuild with `oldCount + 1`. Parameters conventionally occupy the window
 * `[registerCount - paramWidth, registerCount)`; after growth that window
 * moves up one slot, so every original operand register in the OLD window is
 * remapped +1 and the freed first old slot becomes the scratch register
 * (`oldCount - paramWidth`). Non-parameter operands are untouched.
 */
internal fun MutableMethod.prependConditionalReturn(enabledMethod: String, fallbackSmali: String) {
    val oldImpl = implementation ?: error("Method has no implementation: $name")
    val oldCount = oldImpl.registerCount
    val paramWidth = parameterWidth(this)
    val oldParamStart = oldCount - paramWidth

    val remapped: List<ImmutableInstruction> = oldImpl.instructions.map { insn ->
        if (paramWidth > 0 && usesParameterWindow(insn, oldParamStart)) {
            shiftRegisters(insn) { if (it >= oldParamStart) it + 1 else it }
        } else {
            ImmutableInstruction.of(insn)
        }
    }

    remapped.forEach { r ->
            val regs = when (r) {
                is com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction -> "a=" + r.registerA + " b=" + r.registerB + " c=" + r.registerC
                is com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction -> "a=" + r.registerA
                else -> ""
            }
            System.err.println("CRDBG start=" + oldParamStart + " w=" + paramWidth + " " + r.opcode + " " + regs)
        }
    val grown = ImmutableMethodImplementation(
        oldCount + 1,
        remapped,
        oldImpl.tryBlocks,
        oldImpl.debugItems
    )
    setImplementation(MutableMethodImplementation(grown))

    val scratch = oldCount - paramWidth
    // Branch target = the first instruction of the (remapped) original body.
    // The patcher's addInstructionsWithLabels resolves trailing labels against
    // ExternalLabel entries, so the continue label points at the first original
    // instruction instead of a trailing internal label.
    val continueTarget = implementation!!.instructions.first()
    addInstructionsWithLabels(
        0,
        """
        invoke-static {}, $enabledMethod
        move-result v$scratch
        if-eqz v$scratch, :revanced_continue
        $fallbackSmali
        """.trimIndent(),
        app.revanced.patcher.extensions.ExternalLabel("revanced_continue", continueTarget)
    )
}

private fun usesParameterWindow(insn: Instruction, oldParamStart: Int): Boolean = when (insn) {
    // Most-specific first: OneRegisterInstruction is a supertype of the
    // two-/three-/five-register interfaces, so it must be checked last.
    is RegisterRangeInstruction -> insn.startRegister + insn.registerCount > oldParamStart
    is FiveRegisterInstruction -> insn.registerC >= oldParamStart || insn.registerD >= oldParamStart ||
        insn.registerE >= oldParamStart || insn.registerF >= oldParamStart || insn.registerG >= oldParamStart
    is ThreeRegisterInstruction -> insn.registerA >= oldParamStart || insn.registerB >= oldParamStart || insn.registerC >= oldParamStart
    is TwoRegisterInstruction -> insn.registerA >= oldParamStart || insn.registerB >= oldParamStart
    is OneRegisterInstruction -> insn.registerA >= oldParamStart
    else -> false
}
