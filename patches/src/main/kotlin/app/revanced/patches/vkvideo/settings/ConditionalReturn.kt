package app.revanced.patches.vkvideo.settings

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.accessFlags
import app.revanced.patcher.extensions.addInstructionsWithLabels
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
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
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction45cc
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction12x
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
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction45cc
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction4rcc

/**
 * Parameter width in DEX register slots: `J` and `D` each occupy two slots,
 * every other parameter occupies one, plus the implicit receiver slot for
 * non-static methods.
 */
internal fun parameterWidth(method: MutableMethod): Int =
    method.parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 } +
        if (method.accessFlags(AccessFlags.STATIC)) 0 else 1

private fun shiftRegisters(insn: Instruction, remap: (Int) -> Int): ImmutableInstruction {
    val imm = ImmutableInstruction.of(insn)
    return when (imm) {
        is ImmutableInstruction11n -> {
            val a = remap(imm.registerA)
            if (a <= 15) ImmutableInstruction11n(imm.opcode, a, imm.narrowLiteral)
            else ImmutableInstruction21s(Opcode.CONST_16, a, imm.narrowLiteral)
        }
        is ImmutableInstruction12x -> {
            val a = remap(imm.registerA)
            val b = remap(imm.registerB)
            if (a <= 15 && b <= 15) ImmutableInstruction12x(imm.opcode, a, b)
            else ImmutableInstruction22x(wideOpcodeOf(imm.opcode), a, b)
        }
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
        is ImmutableInstruction35c -> {
            val regs = listOf(imm.registerC, imm.registerD, imm.registerE, imm.registerF, imm.registerG)
                .take(imm.registerCount).map(remap)
            if (regs.all { it <= 15 }) {
                val padded = regs + List(5 - regs.size) { 0 }
                ImmutableInstruction35c(imm.opcode, imm.registerCount, padded[0], padded[1], padded[2], padded[3], padded[4], imm.reference)
            } else {
                ImmutableInstruction3rc(
                    rangeOpcodeOf(imm.opcode),
                    imm.registerCount, regs.min(), imm.reference
                )
            }
        }
        is ImmutableInstruction3rc -> ImmutableInstruction3rc(imm.opcode, imm.registerCount, remap(imm.startRegister), imm.reference)
        is ImmutableInstruction35mi -> {
            val regs = listOf(imm.registerC, imm.registerD, imm.registerE, imm.registerF, imm.registerG)
                .take(imm.registerCount).map(remap)
            if (regs.all { it <= 15 }) {
                val padded = regs + List(5 - regs.size) { 0 }
                ImmutableInstruction35mi(imm.opcode, imm.registerCount, padded[0], padded[1], padded[2], padded[3], padded[4], imm.inlineIndex)
            } else {
                ImmutableInstruction3rmi(
                    rangeOpcodeOf(imm.opcode),
                    imm.registerCount, regs.min(), imm.inlineIndex
                )
            }
        }
        is ImmutableInstruction35ms -> {
            val regs = listOf(imm.registerC, imm.registerD, imm.registerE, imm.registerF, imm.registerG)
                .take(imm.registerCount).map(remap)
            if (regs.all { it <= 15 }) {
                val padded = regs + List(5 - regs.size) { 0 }
                ImmutableInstruction35ms(imm.opcode, imm.registerCount, padded[0], padded[1], padded[2], padded[3], padded[4], imm.vtableIndex)
            } else {
                ImmutableInstruction3rms(
                    rangeOpcodeOf(imm.opcode),
                    imm.registerCount, regs.min(), imm.vtableIndex
                )
            }
        }
        is ImmutableInstruction3rmi -> ImmutableInstruction3rmi(imm.opcode, imm.registerCount, remap(imm.startRegister), imm.inlineIndex)
        is ImmutableInstruction3rms -> ImmutableInstruction3rms(imm.opcode, imm.registerCount, remap(imm.startRegister), imm.vtableIndex)
        is ImmutableInstruction45cc -> {
            val regs = listOf(imm.registerC, imm.registerD, imm.registerE, imm.registerF, imm.registerG)
                .take(imm.registerCount).map(remap)
            if (regs.all { it <= 15 }) {
                val padded = regs + List(5 - regs.size) { 0 }
                ImmutableInstruction45cc(imm.opcode, imm.registerCount, padded[0], padded[1], padded[2], padded[3], padded[4], imm.reference, imm.reference2)
            } else {
                ImmutableInstruction4rcc(imm.opcode, imm.registerCount, regs.min(), imm.reference, imm.reference2)
            }
        }
        is ImmutableInstruction4rcc -> ImmutableInstruction4rcc(imm.opcode, imm.registerCount, remap(imm.startRegister), imm.reference, imm.reference2)
        else -> imm
    }
}

private fun Instruction.usedRegisters(): List<Int> = when (this) {
    is Instruction35c -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is Instruction35mi -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is Instruction35ms -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is Instruction45cc -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    else -> emptyList()
}

/**
 * 35c/35mi/35ms encode their registers in 4-bit nibbles. After a shift a
 * parameter operand can land above v15, so widen the instruction to its range
 * form. Range forms carry a 16-bit start register and an 8-bit count.
 */
/**
 * 4-bit invoke formats widen to their range counterpart, whose name is not a
 * uniform `_RANGE` suffix (e.g. INVOKE_VIRTUAL -> INVOKE_VIRTUAL_RANGE but
 * FILLED_NEW_ARRAY -> FILLED_NEW_ARRAY_RANGE). Map the irregular ones here.
 */
private val RANGE_OPCODES: Map<Opcode, Opcode> = mapOf(
    Opcode.INVOKE_VIRTUAL to Opcode.INVOKE_VIRTUAL_RANGE,
    Opcode.INVOKE_SUPER to Opcode.INVOKE_SUPER_RANGE,
    Opcode.INVOKE_DIRECT to Opcode.INVOKE_DIRECT_RANGE,
    Opcode.INVOKE_STATIC to Opcode.INVOKE_STATIC_RANGE,
    Opcode.INVOKE_INTERFACE to Opcode.INVOKE_INTERFACE_RANGE,
    Opcode.EXECUTE_INLINE to Opcode.EXECUTE_INLINE_RANGE,
    Opcode.INVOKE_VIRTUAL_QUICK to Opcode.INVOKE_VIRTUAL_QUICK_RANGE,
    Opcode.INVOKE_SUPER_QUICK to Opcode.INVOKE_SUPER_QUICK_RANGE,
    Opcode.INVOKE_POLYMORPHIC to Opcode.INVOKE_POLYMORPHIC_RANGE,
    Opcode.INVOKE_CUSTOM to Opcode.INVOKE_CUSTOM_RANGE,
    Opcode.FILLED_NEW_ARRAY to Opcode.FILLED_NEW_ARRAY_RANGE,
)

private fun rangeOpcodeOf(opcode: Opcode): Opcode =
    RANGE_OPCODES[opcode] ?: error("No range form for opcode $opcode")

/**
 * A remapped 12x operand can exceed the 4-bit limit; convert to the
 * move/from16 family (Format22x), which has identical dest<-src semantics.
 */
private fun wideOpcodeOf(opcode: Opcode): Opcode = when (opcode) {
    Opcode.MOVE -> Opcode.MOVE_FROM16
    Opcode.MOVE_OBJECT -> Opcode.MOVE_OBJECT_FROM16
    Opcode.MOVE_WIDE -> Opcode.MOVE_WIDE_FROM16
    else -> error("No 22x equivalent for opcode $opcode")
}

private fun toRangeFormIfNeeded(insn: ImmutableInstruction): ImmutableInstruction {
    val registers = insn.usedRegisters()
    if (registers.isEmpty() || registers.all { it <= 15 }) return insn

    val start = registers.min()
    val rangeOpcode = rangeOpcodeOf(insn.opcode)
    return when (insn) {
        is ImmutableInstruction35c -> ImmutableInstruction3rc(rangeOpcode, insn.registerCount, start, insn.reference)
        is ImmutableInstruction35mi -> ImmutableInstruction3rmi(rangeOpcode, insn.registerCount, start, insn.inlineIndex)
        is ImmutableInstruction35ms -> ImmutableInstruction3rms(rangeOpcode, insn.registerCount, start, insn.vtableIndex)
        else -> insn
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
 * rebuild with `oldCount + 1`. Parameters occupy the trailing window
 * `[oldCount - paramWidth, oldCount)`; growth moves that window up one slot,
 * so every operand that touches the old window is remapped +1. The freed
 * first slot of the old window (`oldCount - paramWidth`) becomes the scratch
 * register for the guard. Widened 4-bit invoke operands are rebuilt in range
 * form, and a scratch above v255 is rejected.
 */
internal fun MutableMethod.prependConditionalReturn(enabledMethod: String, fallbackSmali: String) {
    val oldImpl = implementation ?: error("Method has no implementation: $name")
    val oldCount = oldImpl.registerCount
    val paramWidth = parameterWidth(this)
    val oldParamStart = oldCount - paramWidth

    val remapped: List<ImmutableInstruction> = oldImpl.instructions.map { insn ->
        val shifted = if (paramWidth > 0 && usesParameterWindow(insn, oldParamStart)) {
            when (insn) {
                // Range forms are contiguous: move the whole window up one slot.
                is RegisterRangeInstruction -> shiftRegisters(insn) { it + 1 }
                // Everything else (incl. 35c-family invokes) uses independent
                // register operands, not a contiguous range: remap each
                // register conditionally — only window registers shift +1.
                else -> shiftRegisters(insn) { if (it >= oldParamStart) it + 1 else it }
            }
        } else {
            ImmutableInstruction.of(insn)
        }
        toRangeFormIfNeeded(shifted)
    }

    val grown = ImmutableMethodImplementation(
        oldCount + 1,
        remapped,
        oldImpl.tryBlocks,
        oldImpl.debugItems
    )
    setImplementation(MutableMethodImplementation(grown))

    val scratch = oldCount - paramWidth
    check(scratch <= 0xFF) {
        "Scratch register v$scratch does not fit move-result/if-eqz in $definingClass->$name"
    }

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
