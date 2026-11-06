package app.revanced.patches.vkvideo.settings

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.addInstructionsWithLabels
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction

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
 * rebuild with `oldCount + 1`. DEX parameter registers are always the last
 * registers of the frame, so growing only appends a fresh register after the
 * parameter window: parameters keep their exact registers and no original
 * instruction needs remapping. The appended register (`oldCount`) is used as
 * the scratch register, and try blocks/debug items stay valid.
 */
internal fun MutableMethod.prependConditionalReturn(enabledMethod: String, fallbackSmali: String) {
    val oldImpl = implementation ?: error("Method has no implementation: $name")
    val oldCount = oldImpl.registerCount

    val grown = ImmutableMethodImplementation(
        oldCount + 1,
        oldImpl.instructions.map { ImmutableInstruction.of(it) },
        oldImpl.tryBlocks,
        oldImpl.debugItems
    )
    setImplementation(MutableMethodImplementation(grown))

    val scratch = oldCount
    // Branch target = the first instruction of the original body.
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

    val inserted = implementation!!.instructions.first()
    check(inserted.opcode == Opcode.INVOKE_STATIC) {
        "prepend no-op on $definingClass->$name"
    }
}
