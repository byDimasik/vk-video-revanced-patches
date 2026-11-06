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
}
