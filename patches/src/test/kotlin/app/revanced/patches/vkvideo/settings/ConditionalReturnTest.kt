package app.revanced.patches.vkvideo.settings

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21t
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConditionalReturnTest {

    private val enabledMethod = "Lstub/Enabled;->isEnabled()Z"

    private fun method(context: app.revanced.patcher.patch.BytecodePatchContext, type: String, name: String): MutableMethod =
        MutableMethod(context.classDefs.first { it.type == type }.methods.first { it.name == name })

    @Test
    fun `prepends conditional return and appends a scratch register`() {
        val smali = """
            .class public Lcom/example/Calc;
            .super Ljava/lang/Object;

            .method public static calc(II)I
                .registers 5
                .param p0, "a"
                .param p1, "b"
                .local v2, "last":I

                const/4 v2, 0x2
                add-int v0, p0, p1
                add-int v0, v0, v2
                return v0
            .end method
        """.trimIndent()

        val context = PatchTestUtil.createBytecodeContext(smali)
        val method = method(context, "Lcom/example/Calc;", "calc")
        val originalCount = method.implementation!!.registerCount
        val originalParameters = method.parameterTypes
        val originalTail = method.getInstructions().drop(0).map { it.opcode }

        method.prependConditionalReturn(enabledMethod, "const/4 v0, 0x0\nreturn v0")

        val instructions = method.getInstructions()
        val scratch = originalCount - 2

        assertEquals(Opcode.INVOKE_STATIC, instructions[0].opcode)
        val invoke = instructions[0] as Instruction35c
        (invoke.reference as MethodReference).let {
            assertEquals("Lstub/Enabled;", it.definingClass)
            assertEquals("isEnabled", it.name)
            assertEquals("Z", it.returnType)
        }
        assertEquals(Opcode.MOVE_RESULT, instructions[1].opcode)
        assertEquals(scratch, (instructions[1] as OneRegisterInstruction).registerA)
        assertEquals(Opcode.IF_EQZ, instructions[2].opcode)
        assertEquals(scratch, (instructions[2] as OneRegisterInstruction).registerA)
        assertEquals(4, (instructions[2] as Instruction21t).codeOffset)

        assertEquals(Opcode.CONST_4, instructions[3].opcode)
        assertEquals(0, (instructions[3] as NarrowLiteralInstruction).narrowLiteral)
        assertEquals(Opcode.RETURN, instructions[4].opcode)

        assertEquals(originalCount + 1, method.implementation!!.registerCount)
        assertEquals(originalParameters, method.parameterTypes)
        assertEquals(originalTail.size + 5, instructions.size)

        assertEquals(Opcode.CONST_4, instructions[5].opcode)
        assertEquals(2, (instructions[5] as OneRegisterInstruction).registerA)
        assertEquals(2, (instructions[5] as NarrowLiteralInstruction).narrowLiteral)

        assertEquals(Opcode.ADD_INT, instructions[6].opcode)
        instructions[6].let {
            it as ThreeRegisterInstruction
            assertEquals(0, it.registerA)
            assertEquals(4, it.registerB)
            assertEquals(5, it.registerC)
        }

        assertEquals(Opcode.ADD_INT, instructions[7].opcode)
        instructions[7].let {
            it as ThreeRegisterInstruction
            assertEquals(0, it.registerA)
            assertEquals(0, it.registerB)
            assertEquals(2, it.registerC)
        }

        assertEquals(Opcode.RETURN, instructions[8].opcode)
        assertEquals(0, (instructions[8] as OneRegisterInstruction).registerA)
    }

    @Test
    fun `off path still reads its last parameter after the branch`() {
        val smali = """
            .class public Lcom/example/High;
            .super Ljava/lang/Object;

            .method public static high(II)I
                .registers 6
                .param p0, "a"
                .param p1, "b"

                const/4 v0, 0x0
                add-int v0, p0, p1
                return v0
            .end method
        """.trimIndent()

        val context = PatchTestUtil.createBytecodeContext(smali)
        val method = method(context, "Lcom/example/High;", "high")

        method.prependConditionalReturn(enabledMethod, "const/4 v0, 0x0\nreturn v0")

        val instructions = method.getInstructions()
        assertEquals(8, instructions.size)
        assertEquals(Opcode.IF_EQZ, instructions[2].opcode)
        assertEquals(4, (instructions[2] as Instruction21t).codeOffset)

        assertEquals(Opcode.ADD_INT, instructions[6].opcode)
        instructions[6].let {
            it as ThreeRegisterInstruction
            assertEquals(0, it.registerA)
            assertEquals(5, it.registerB)
            assertEquals(6, it.registerC)
        }
    }

    @Test
    fun `instance method receiver is part of the parameter window`() {
        val smali = """
            .class public Lcom/example/Instance;
            .super Ljava/lang/Object;

            .method public run(I)V
                .registers 4
                .param p1, "value"

                return-void
            .end method
        """.trimIndent()

        val context = PatchTestUtil.createBytecodeContext(smali)
        val method = method(context, "Lcom/example/Instance;", "run")

        method.prependConditionalReturn(enabledMethod, "return-void")

        val instructions = method.getInstructions()
        assertEquals(5, instructions.size)
        assertEquals(2, (instructions[1] as OneRegisterInstruction).registerA)
        assertEquals(2, (instructions[2] as OneRegisterInstruction).registerA)
        assertEquals(3, (instructions[2] as Instruction21t).codeOffset)
        assertEquals(Opcode.RETURN_VOID, instructions[3].opcode)
        assertEquals(5, method.implementation!!.registerCount)
        assertEquals(Opcode.RETURN_VOID, instructions.last().opcode)
    }

    @Test
    fun `wide parameter occupies two slots and the whole window shifts`() {
        val smali = """
            .class public Lcom/example/Wide;
            .super Ljava/lang/Object;

            .method public static wide(IJ)J
                .registers 7
                .param p0, "x"
                .param p1, "y"
                .local v4, "lo":I

                const/4 v4, 0x1
                return-wide v1
            .end method
        """.trimIndent()

        val context = PatchTestUtil.createBytecodeContext(smali)
        val method = method(context, "Lcom/example/Wide;", "wide")

        // (I J) -> 1 + 2 = 3 param slots; scratch = 7 - 3 = v4
        method.prependConditionalReturn(enabledMethod, "const/4 v0, 0x0\nreturn v0")

        val instructions = method.getInstructions()
        // guard: invoke, move-result v4, if-eqz v4
        assertEquals(4, (instructions[1] as OneRegisterInstruction).registerA)
        assertEquals(4, (instructions[2] as OneRegisterInstruction).registerA)
        // const/4 v4 was a local at old v4 == old scratch boundary? v4 < oldParamStart(4)? No: oldParamStart=4,
        // so v4 was the FIRST param slot (p1 lo) and must be remapped to v5.
        assertEquals(Opcode.CONST_4, instructions[5].opcode)
        assertEquals(5, (instructions[5] as OneRegisterInstruction).registerA)
        assertEquals(8, method.implementation!!.registerCount)
    }

    @Test
    fun `12x instruction reading a parameter is remapped`() {
        val smali = """
            .class public Lcom/example/Twelve;
            .super Ljava/lang/Object;

            .method public static calc(I)I
                .registers 3
                .param p0, "a"

                move-object v0, p0
                invoke-static {v0}, Lstub/Use;->use(Ljava/lang/Object;)I
                move-result v0
                return v0
            .end method
        """.trimIndent()

        val context = PatchTestUtil.createBytecodeContext(smali)
        val method = method(context, "Lcom/example/Twelve;", "calc")

        method.prependConditionalReturn(enabledMethod, "const/4 v0, 0x0\nreturn v0")

        val instructions = method.getInstructions()
        // Static method, .registers 3: v0 local, v2 = p0 (params occupy the tail).
        // After growth (count 4): p0 -> v3, scratch = 3-1 = v2.
        // move-object v0, p0 becomes move-object v0, v3 (still 12x, both <= 15).
        val move = instructions.first { it.opcode == Opcode.MOVE_OBJECT } as TwoRegisterInstruction
        assertEquals(0, move.registerA)
        assertEquals(3, move.registerB)
    }

    @Test
    fun `12x instruction overflowing the nibble limit widens to 22x`() {
        val smali = """
            .class public Lcom/example/Nib;
            .super Ljava/lang/Object;

            .method public static calc(I)V
                .registers 16
                .param p0, "a"

                move-object v0, p0
                return-void
            .end method
        """.trimIndent()

        val context = PatchTestUtil.createBytecodeContext(smali)
        val method = method(context, "Lcom/example/Nib;", "calc")

        method.prependConditionalReturn(enabledMethod, "return-void")

        val instructions = method.getInstructions()
        // Static method, .registers 16: v15 = p0. Growth to 17: p0 -> v16, which a
        // 12x nibble cannot hold; the instruction widens to move-object/from16 (22x).
        val move = instructions.first { it.opcode == Opcode.MOVE_OBJECT_FROM16 } as TwoRegisterInstruction
        assertEquals(0, move.registerA)
        assertEquals(16, move.registerB)
    }
}
