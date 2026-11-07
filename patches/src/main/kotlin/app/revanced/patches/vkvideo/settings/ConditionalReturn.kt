package app.revanced.patches.vkvideo.settings

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.accessFlags
import app.revanced.patcher.extensions.addInstructionsWithLabels
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction12x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction32x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction4rcc
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction12x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21s
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22b
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22s
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction23x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31t
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
        // Range formats carry (startRegister, registerCount) in that ctor order.
        is ImmutableInstruction3rc -> ImmutableInstruction3rc(imm.opcode, remap(imm.startRegister), imm.registerCount, imm.reference)
        is ImmutableInstruction3rmi -> ImmutableInstruction3rmi(imm.opcode, remap(imm.startRegister), imm.registerCount, imm.inlineIndex)
        is ImmutableInstruction3rms -> ImmutableInstruction3rms(imm.opcode, remap(imm.startRegister), imm.registerCount, imm.vtableIndex)
        is ImmutableInstruction4rcc -> ImmutableInstruction4rcc(imm.opcode, remap(imm.startRegister), imm.registerCount, imm.reference, imm.reference2)
        // 4-bit invoke formats (35c family) are remapped by [remapForFrameGrow],
        // which decides between keeping the 4-bit form, widening to a contiguous
        // range form, or spilling; they never reach here.
        else -> imm
    }
}

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

private fun operandRegisters(insn: FiveRegisterInstruction): List<Int> =
    listOf(insn.registerC, insn.registerD, insn.registerE, insn.registerF, insn.registerG)
        .take(insn.registerCount)

/** Outcome of remapping a 4-bit invoke's operands. */
private sealed interface InvokeRemap {
    /** All remapped operands still fit the 4-bit nibble fields. */
    data class Fits(val regs: List<Int>) : InvokeRemap

    /** Remapped operands overflow v15 but are exactly ascending-contiguous:
     * widen to the range form. */
    data class Range(val start: Int) : InvokeRemap

    /**
     * Remapped operands overflow v15 and are NOT contiguous: no legal range
     * form exists (a range would read the in-between registers as extra
     * arguments). The invoke must be spilled into a contiguous scratch block.
     */
    data class Spill(val regs: List<Int>) : InvokeRemap
}

private fun classifyInvoke(insn: FiveRegisterInstruction, remap: (Int) -> Int): InvokeRemap {
    val regs = operandRegisters(insn).map(remap)
    return when {
        regs.all { it <= 15 } -> InvokeRemap.Fits(regs)
        // A range form reads (start, start+1, ...) in ascending slot order, so
        // widening is only legal when the remapped operand list is EXACTLY
        // ascending-contiguous. A descending list like {v16, v15} passes the
        // old max-min+1 check but would swap the argument semantics; it (and
        // any gapped list) must go through the order-preserving spill path.
        regs.indices.all { regs[it] == regs.min() + it } -> InvokeRemap.Range(regs.min())
        else -> InvokeRemap.Spill(regs)
    }
}

/**
 * Result of remapping one instruction while growing the frame.
 */
private sealed interface Remapped {
    /** The replacement instructions, in order (may be empty-neutral single). */
    data class Insns(val insns: List<Instruction>) : Remapped

    /** A 4-bit invoke that needs its operands spilled into a contiguous block. */
    data class SpillInvoke(
        val rangeOpcode: Opcode,
        val registerCount: Int,
        val registers: List<Int>,
        val reference: Reference,
        val reference2: Reference?,
    ) : Remapped
}

private fun remapForFrameGrow(insn: Instruction, oldParamStart: Int, delta: Int): Remapped {
    val imm = ImmutableInstruction.of(insn)
    if (!usesParameterWindow(insn, oldParamStart)) return Remapped.Insns(listOf(imm))

    // Range forms are contiguous: move the whole window up by the frame growth.
    // Everything else (incl. 35c-family invokes) uses independent register
    // operands: remap each register conditionally — only window registers shift.
    val remap: (Int) -> Int = when (imm) {
        is RegisterRangeInstruction -> { r -> r + delta }
        else -> { r -> if (r >= oldParamStart) r + delta else r }
    }

    return when (imm) {
        is ImmutableInstruction35c -> when (val c = classifyInvoke(imm, remap)) {
            is InvokeRemap.Fits -> {
                val padded = c.regs + List(5 - c.regs.size) { 0 }
                Remapped.Insns(
                    listOf(ImmutableInstruction35c(imm.opcode, imm.registerCount, padded[0], padded[1], padded[2], padded[3], padded[4], imm.reference))
                )
            }
            is InvokeRemap.Range -> Remapped.Insns(
                listOf(ImmutableInstruction3rc(rangeOpcodeOf(imm.opcode), c.start, imm.registerCount, imm.reference))
            )
            is InvokeRemap.Spill -> Remapped.SpillInvoke(rangeOpcodeOf(imm.opcode), imm.registerCount, c.regs, imm.reference, null)
        }
        is ImmutableInstruction35mi -> when (val c = classifyInvoke(imm, remap)) {
            is InvokeRemap.Fits -> {
                val padded = c.regs + List(5 - c.regs.size) { 0 }
                Remapped.Insns(
                    listOf(ImmutableInstruction35mi(imm.opcode, imm.registerCount, padded[0], padded[1], padded[2], padded[3], padded[4], imm.inlineIndex))
                )
            }
            is InvokeRemap.Range -> Remapped.Insns(
                listOf(ImmutableInstruction3rmi(rangeOpcodeOf(imm.opcode), c.start, imm.registerCount, imm.inlineIndex))
            )
            // Quickened/inline invokes carry no method reference, so argument
            // types are unknown; they are spilled with plain moves.
            is InvokeRemap.Spill -> Remapped.SpillInvoke(rangeOpcodeOf(imm.opcode), imm.registerCount, c.regs, NoReference, null)
        }
        is ImmutableInstruction35ms -> when (val c = classifyInvoke(imm, remap)) {
            is InvokeRemap.Fits -> {
                val padded = c.regs + List(5 - c.regs.size) { 0 }
                Remapped.Insns(
                    listOf(ImmutableInstruction35ms(imm.opcode, imm.registerCount, padded[0], padded[1], padded[2], padded[3], padded[4], imm.vtableIndex))
                )
            }
            is InvokeRemap.Range -> Remapped.Insns(
                listOf(ImmutableInstruction3rms(rangeOpcodeOf(imm.opcode), c.start, imm.registerCount, imm.vtableIndex))
            )
            is InvokeRemap.Spill -> Remapped.SpillInvoke(rangeOpcodeOf(imm.opcode), imm.registerCount, c.regs, NoReference, null)
        }
        is ImmutableInstruction45cc -> when (val c = classifyInvoke(imm, remap)) {
            is InvokeRemap.Fits -> {
                val padded = c.regs + List(5 - c.regs.size) { 0 }
                Remapped.Insns(
                    listOf(ImmutableInstruction45cc(imm.opcode, imm.registerCount, padded[0], padded[1], padded[2], padded[3], padded[4], imm.reference, imm.reference2))
                )
            }
            is InvokeRemap.Range -> Remapped.Insns(
                listOf(ImmutableInstruction4rcc(rangeOpcodeOf(imm.opcode), c.start, imm.registerCount, imm.reference, imm.reference2))
            )
            is InvokeRemap.Spill -> Remapped.SpillInvoke(rangeOpcodeOf(imm.opcode), imm.registerCount, c.regs, imm.reference, imm.reference2)
        }
        is ImmutableInstruction11x -> narrow11x(imm, remap, oldParamStart)
        is ImmutableInstruction22c -> narrow22c(imm, remap, oldParamStart)
        is ImmutableInstruction12x -> narrow12x(imm, remap, oldParamStart)
        else -> Remapped.Insns(listOf(shiftRegisters(insn, remap)))
    }
}

/**
 * Formats with 4-bit register fields (`11x`, `12x`, `22c`, plus `11n` which is
 * widened to 21s) cannot address a parameter once the window has moved above
 * v15. The freed low window slots (`oldParamStart` and the one after it) are
 * always below v16 whenever such an instruction references the window: the
 * original opcode could only name registers up to v15 and parameters are the
 * only registers that shift. High operands are therefore snapshotted into
 * those slots (and a high destination copied back) around the instruction.
 */
private fun moveKindOf(opcode: Opcode): MoveKind = when {
    opcode.name.contains("wide") -> MoveKind.WIDE
    opcode.name.contains("object") -> MoveKind.OBJECT
    else -> MoveKind.INT
}

private fun copyFromHigh(kind: MoveKind, dest: Int, src: Int): BuilderInstruction = when {
    dest <= 15 && src <= 15 -> error("copyFromHigh on two low registers")
    else -> BuilderInstruction22x(kind.op22x, dest, src)
}

private fun narrow11x(imm: ImmutableInstruction11x, remap: (Int) -> Int, temp: Int): Remapped {
    val a = remap(imm.registerA)
    val kind = moveKindOf(imm.opcode)
    val copy = if (a <= 15) null else copyFromHigh(kind, temp, a)
    // move-result* must stay adjacent to its invoke: the copy-back goes AFTER.
    return if (imm.opcode.name.startsWith("move-result")) {
        // Writes `a`: run into the temp and copy back.
        if (a <= 15) return Remapped.Insns(listOf(ImmutableInstruction11x(imm.opcode, a)))
        Remapped.Insns(listOf<Instruction>(ImmutableInstruction11x(imm.opcode, temp), BuilderInstruction22x(kind.op22x, a, temp)))
    } else {
        val target = if (copy == null) a else temp
        Remapped.Insns(listOfNotNull<Instruction>(copy, ImmutableInstruction11x(imm.opcode, target)))
    }
}

private fun narrow12x(imm: ImmutableInstruction12x, remap: (Int) -> Int, temp: Int): Remapped {
    val a = remap(imm.registerA)
    val b = remap(imm.registerB)
    if (a <= 15 && b <= 15) return Remapped.Insns(listOf(ImmutableInstruction12x(imm.opcode, a, b)))
    // The move family has a wide (22x) equivalent with an 8-bit destination.
    when (imm.opcode) {
        Opcode.MOVE, Opcode.MOVE_OBJECT, Opcode.MOVE_WIDE ->
            return Remapped.Insns(listOf(ImmutableInstruction22x(wideOpcodeOf(imm.opcode), a, b)))
        else -> Unit
    }
    val kind = moveKindOf(imm.opcode)
    // registerB is read, registerA is written.
    val copyIn = if (b > 15) copyFromHigh(kind, temp, b) else null
    val source = if (copyIn == null) b else temp
    val dest = if (a <= 15) a else temp
    val out = mutableListOf<Instruction>()
    copyIn?.let { out += it }
    out += ImmutableInstruction12x(imm.opcode, dest, source)
    if (a > 15) out += BuilderInstruction22x(kind.op22x, a, temp)
    return Remapped.Insns(out)
}

private fun narrow22c(imm: ImmutableInstruction22c, remap: (Int) -> Int, temp: Int): Remapped {
    val a = remap(imm.registerA)
    val b = remap(imm.registerB)
    if (a <= 15 && b <= 15) return Remapped.Insns(listOf(ImmutableInstruction22c(imm.opcode, a, b, imm.reference)))
    val kind = moveKindOf(imm.opcode)
    val out = mutableListOf<Instruction>()
    // iput* stores registerA as the data and registerB as the object; iget*
    // reads the object from registerB into registerA. Both orientations are
    // snapshotted before the instruction runs.
    if (imm.opcode.name.startsWith("iput")) {
        if (a > 15 && b > 15) {
            // Copy the object away first: the data slot's copy destination may
            // be the object's own register (temp2 == oldParamStart + 1).
            out += copyFromHigh(kind, temp, b)
            out += copyFromHigh(kind, temp + 1, a)
            out += ImmutableInstruction22c(imm.opcode, temp + 1, temp, imm.reference)
            return Remapped.Insns(out)
        }
        if (b > 15) {
            out += copyFromHigh(kind, temp, b)
            out += ImmutableInstruction22c(imm.opcode, a, temp, imm.reference)
            return Remapped.Insns(out)
        }
        out += copyFromHigh(kind, temp, a)
        out += ImmutableInstruction22c(imm.opcode, temp, b, imm.reference)
        return Remapped.Insns(out)
    }
    if (b > 15) out += copyFromHigh(kind, temp, b)
    val objectReg = if (b > 15) temp else b
    if (a > 15) {
        out += ImmutableInstruction22c(imm.opcode, temp, objectReg, imm.reference)
        out += BuilderInstruction22x(kind.op22x, a, temp)
    } else {
        out += ImmutableInstruction22c(imm.opcode, a, objectReg, imm.reference)
    }
    return Remapped.Insns(out)
}

/** Placeholder reference for spilled invokes without a method reference (35mi/35ms). */
private val NoReference: Reference = object : Reference {
    override fun validateReference() {}
}

/**
 * Argument slot kinds for a spilled invoke, derived from the callee's method
 * reference. Invoke arguments occupy the same slot layout as parameters
 * (receiver first for non-static callees, `J`/`D` two slots).
 */
private fun spillMoveKinds(registerCount: Int, reference: Reference?): List<MoveKind> {
    val methodRef = reference as? MethodReference ?: return List(registerCount) { MoveKind.INT }
    val params = methodRef.parameterTypes
    val paramSlots = params.sumOf { if (it == "J" || it == "D") 2 else 1 }
    val kinds = buildList {
        // registerCount == paramSlots <=> static call; otherwise a receiver
        // (always an object) occupies the first slot.
        if (registerCount != paramSlots) add(MoveKind.OBJECT)
        for (p in params) {
            when (p.firstOrNull()) {
                'J', 'D' -> add(MoveKind.WIDE)
                'L', '[' -> add(MoveKind.OBJECT)
                else -> add(MoveKind.INT)
            }
        }
    }
    return if (kinds.sumOf { it.width } == registerCount) kinds else List(registerCount) { MoveKind.INT }
}

private enum class MoveKind(val width: Int, val op12: Opcode, val op22x: Opcode, val op32x: Opcode) {
    OBJECT(1, Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16),
    INT(1, Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16),
    WIDE(2, Opcode.MOVE_WIDE, Opcode.MOVE_WIDE_FROM16, Opcode.MOVE_WIDE_16),
}

private fun moveInstruction(kind: MoveKind, dest: Int, src: Int): BuilderInstruction = when {
    dest <= 15 && src <= 15 -> BuilderInstruction12x(kind.op12, dest, src)
    dest <= 0xFF -> BuilderInstruction22x(kind.op22x, dest, src)
    else -> BuilderInstruction32x(kind.op32x, dest, src)
}

/**
 * Builds the spill moves: one move per argument slot-group, gathering the
 * invoke's operands (in argument order) into the contiguous block starting at
 * [blockStart].
 */
private fun buildSpillMoves(kinds: List<MoveKind>, regs: List<Int>, blockStart: Int): List<BuilderInstruction> {
    val moves = mutableListOf<BuilderInstruction>()
    var slot = 0
    for (kind in kinds) {
        moves += moveInstruction(kind, blockStart + slot, regs[slot])
        slot += kind.width
    }
    check(slot == regs.size) {
        "Spill kinds cover $slot slots but the invoke has ${regs.size} operand slots"
    }
    return moves
}

/**
 * Applies every spill plan against the mutable implementation: inserts the
 * gather moves right before the invoke and rewrites the invoke as a range form
 * against the scratch block. [MutableMethodImplementation] keeps builder
 * labels, so branch targets and try-block ranges stay correct across the
 * insertions — no hand-patched code offsets.
 */
private fun applySpills(
    impl: MutableMethodImplementation,
    spills: List<Pair<Int, Remapped.SpillInvoke>>,
    blockStart: Int,
) {
    var offset = 0
    for ((index, spill) in spills) {
        val at = index + offset
        val kinds = spillMoveKinds(spill.registerCount, spill.reference)
        val moves = buildSpillMoves(kinds, spill.registers, blockStart)
        moves.forEachIndexed { j, move -> impl.addInstruction(at + j, move) }
        val rangeInsn: BuilderInstruction = if (spill.reference2 != null) {
            BuilderInstruction4rcc(spill.rangeOpcode, blockStart, spill.registerCount, spill.reference, spill.reference2)
        } else {
            BuilderInstruction3rc(spill.rangeOpcode, blockStart, spill.registerCount, spill.reference)
        }
        impl.replaceInstruction(at + moves.size, rangeInsn)
        offset += moves.size
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
 * rebuild. Parameters occupy the trailing window `[oldCount - paramWidth,
 * oldCount)`; growth moves that window up one slot, so every operand that
 * touches the old window is remapped +1. The freed first slot of the old
 * window (`oldCount - paramWidth`) becomes the scratch register for the guard.
 *
 * Widened 4-bit invoke operands are rebuilt in range form — `3rc`/`4rcc`
 * constructors take `(opcode, startRegister, registerCount, ...)` — but only
 * when the remapped operands are exactly ascending-contiguous
 * (`regs[i] == regs.min() + i` for every i; a descending operand order would
 * Non-contiguous overflow invokes are spilled: gather moves copy each operand,
 * in argument order, into a fresh contiguous scratch block at the top of the
 * grown frame, and the invoke is rewritten as a range form against that block.
 * The frame then grows by `paramWidth + 1 + K`, where `K` is the widest spill
 * (in register slots) across the method's spill invokes; the block sits above
 * every register the remap can touch, so it is never aliased. The insertions
 * go through [MutableMethodImplementation], whose builder labels keep branch
 * targets and try ranges correct; a scratch above v255 is rejected.
 */
internal fun MutableMethod.prependConditionalReturn(enabledMethod: String, fallbackSmali: String) {
    val scratch = growFrameWithScratch()

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

/**
 * Grows the frame by the guard slot (plus the widest spill block, if the body
 * has to spill) and remaps the original instructions so the incoming parameter
 * window is re-emitted at the tail of the new frame. DEX places the arguments
 * in the LAST `paramWidth` registers, so the window has to move by the FULL
 * frame growth — guard + spill slots — not just by the guard: remapping by the
 * guard alone leaves every parameter `paramWidth`… read too low (the ART
 * `Undefined register` VerifyError class, issue #46).
 *
 * The register freed by the window shift (`oldCount - paramWidth`, the old
 * first parameter slot) is returned as scratch; it and the slot above it double
 * as low temp registers for the 4-bit formats that cannot address the moved
 * window ([narrow11x]/[narrow12x]/[narrow22c]). Window-touching operands are
 * remapped per-register with the same 35c/range/spill discipline as
 * [prependConditionalReturn]; the spill block is placed immediately below the
 * re-emitted window, above every remapped register.
 */
internal fun MutableMethod.growFrameWithScratch(): Int {
    val oldImpl = implementation ?: error("Method has no implementation: $name")
    val oldCount = oldImpl.registerCount
    val paramWidth = parameterWidth(this)
    val oldParamStart = oldCount - paramWidth

    // The spill set depends on how far the window moves (a larger shift can
    // push a formerly fitting operand over the nibble limit) while the
    // required shift depends on the widest spill: iterate to the fixed point.
    // Every round only grows the shift, so this settles immediately in practice.
    var delta = 1
    var remapped: List<Remapped>
    var spills: List<Pair<Int, Remapped.SpillInvoke>>
    var maxSpillWidth: Int
    var rounds = 0
    while (true) {
        remapped = oldImpl.instructions.map { remapForFrameGrow(it, oldParamStart, delta) }
        spills = remapped.mapIndexedNotNull { index, r ->
            (r as? Remapped.SpillInvoke)?.let { index to it }
        }
        maxSpillWidth = spills.maxOfOrNull { it.second.registerCount } ?: 0
        val needed = if (spills.isEmpty()) 1 else paramWidth + 1 + maxSpillWidth
        if (needed <= delta) break
        check(++rounds < 8) { "Frame growth did not settle in $definingClass->$name" }
        delta = needed
    }

    val newCount = oldCount + delta
    check(newCount <= 0xFFFF) { "Frame of $newCount registers is not addressable in $definingClass->$name" }
    val newParamStart = newCount - paramWidth

    // Flatten the remapped body; spilled invokes are parked as NOPs at their
    // final position and rewritten in place by applySpills once the mutable
    // implementation (and its builder labels) exists.
    val flat = ArrayList<Instruction>(oldImpl.instructions.size)
    val spillPositions = ArrayList<Pair<Int, Remapped.SpillInvoke>>(spills.size)
    for (r in remapped) {
        when (r) {
            is Remapped.Insns -> flat += r.insns
            is Remapped.SpillInvoke -> {
                spillPositions += flat.size to r
                flat += ImmutableInstruction10x(Opcode.NOP)
            }
        }
    }

    val grown = ImmutableMethodImplementation(newCount, flat, oldImpl.tryBlocks, oldImpl.debugItems)
    setImplementation(MutableMethodImplementation(grown))

    if (spillPositions.isNotEmpty()) {
        applySpills(implementation!!, spillPositions, newParamStart - maxSpillWidth)
    }

    val scratch = oldCount - paramWidth
    check(scratch in 0..0xFF) {
        "Scratch register v$scratch does not fit move-result/if-eqz in $definingClass->$name"
    }
    return scratch
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
