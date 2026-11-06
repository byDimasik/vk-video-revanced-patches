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
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
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
    fun `35c invoke arg below the window stays in sync with its const-string writer`() {
        // Replicates com.vk.dto.common.InstreamAd$b.a(Lorg/json/JSONObject;)Z:
        // instance method, 1 object param, .registers 15 (v13 = p0, v14 = p1),
        // invoke-virtual with the receiver inside the param window and a String
        // arg BELOW the window written by const-string. Regression test for the
        // ART VerifyError where the whole 35c operand set was shifted +1 while
        // the const-string writer was not, leaving the invoke reading an
        // Undefined register.
        val smali = """
            .class public Lcom/example/Json;
            .super Ljava/lang/Object;

            .method public a(Lorg/json/JSONObject;)Z
                .registers 15

                const-string v0, "slot_id"
                invoke-virtual {v14, v0}, Lorg/json/JSONObject;->optInt(Ljava/lang/String;)I
                move-result v1
                if-lez v1, :cond_0
                const/4 v2, 0x1
                return v2
                :cond_0
                const/4 v2, 0x0
                return v2
            .end method
        """.trimIndent()

        val context = PatchTestUtil.createBytecodeContext(smali)
        val method = method(context, "Lcom/example/Json;", "a")

        method.prependConditionalReturn(enabledMethod, "const/4 v0, 0x0\nreturn v0")

        val instructions = method.getInstructions()
        // Guard: invoke-static, move-result v13, if-eqz v13 (scratch = 15 - 2).
        assertEquals(Opcode.INVOKE_STATIC, instructions[0].opcode)
        assertEquals(13, (instructions[1] as OneRegisterInstruction).registerA)
        assertEquals(13, (instructions[2] as OneRegisterInstruction).registerA)

        // Original body starts at index 5 (guard = 5 instructions).
        // const-string arg register is a local below the window: unchanged at v0.
        assertEquals(Opcode.CONST_STRING, instructions[5].opcode)
        assertEquals(0, (instructions[5] as OneRegisterInstruction).registerA)

        // invoke-virtual stays a 35c: receiver v14 (p1) shifts +1 -> v15, while
        // the out-of-window arg register v0 must NOT shift — the same register
        // the preceding const-string writes.
        val invoke = instructions[6] as Instruction35c
        assertEquals(Opcode.INVOKE_VIRTUAL, invoke.opcode)
        assertEquals(2, invoke.registerCount)
        assertEquals(15, invoke.registerC)
        assertEquals((instructions[5] as OneRegisterInstruction).registerA, invoke.registerD)

        assertEquals(16, method.implementation!!.registerCount)
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

    @Test
    fun `contiguous overflow invoke widens to a correct range form`() {
        // Remapped operands {v15, v16} overflow the 4-bit fields but are
        // contiguous, so the invoke widens to invoke-virtual/range with
        // startRegister = 15, registerCount = 2 (ctor order: start, count).
        val smali = """
            .class public Lcom/example/Appender;
            .super Ljava/lang/Object;

            .method public build(Ljava/lang/StringBuilder;)V
                .registers 16
                .param p1, "sb"

                const-string v0, "x"
                invoke-virtual {v14, v15}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                return-void
            .end method
        """.trimIndent()

        val context = PatchTestUtil.createBytecodeContext(smali)
        val method = method(context, "Lcom/example/Appender;", "build")

        method.prependConditionalReturn(enabledMethod, "return-void")

        val instructions = method.getInstructions()
        // Guard scratch = 16 - (receiver + 1 param) = v14; frame grows by 1.
        assertEquals(14, (instructions[1] as OneRegisterInstruction).registerA)
        assertEquals(17, method.implementation!!.registerCount)

        // Body: const-string v0, widened invoke, return-void.
        assertEquals(Opcode.CONST_STRING, instructions[4].opcode)
        val invoke = instructions[5] as Instruction3rc
        assertEquals(Opcode.INVOKE_VIRTUAL_RANGE, invoke.opcode)
        assertEquals(15, invoke.startRegister)
        assertEquals(2, invoke.registerCount)
        invoke.reference.let {
            it as MethodReference
            assertEquals("Ljava/lang/StringBuilder;", it.definingClass)
            assertEquals("append", it.name)
        }
        assertEquals(Opcode.RETURN_VOID, instructions[6].opcode)
    }

    @Test
    fun `single-register overflow invoke widens to a one-register range`() {
        // {v15} remaps to {v16}: one register above the nibble limit, trivially
        // contiguous, so it widens to invoke-virtual/range {v16}.
        val smali = """
            .class public Lcom/example/Thrower;
            .super Ljava/lang/Object;

            .method public describe(Ljava/lang/Throwable;)Ljava/lang/String;
                .registers 16
                .param p1, "t"

                invoke-virtual {v15}, Ljava/lang/Throwable;->getMessage()Ljava/lang/String;
                move-result-object v0
                return-object v0
            .end method
        """.trimIndent()

        val context = PatchTestUtil.createBytecodeContext(smali)
        val method = method(context, "Lcom/example/Thrower;", "describe")

        method.prependConditionalReturn(enabledMethod, "return-void")

        val instructions = method.getInstructions()
        assertEquals(17, method.implementation!!.registerCount)

        // Body: widened invoke at index 4, move-result-object, return-object.
        val invoke = instructions[4] as Instruction3rc
        assertEquals(Opcode.INVOKE_VIRTUAL_RANGE, invoke.opcode)
        assertEquals(16, invoke.startRegister)
        assertEquals(1, invoke.registerCount)
        (invoke.reference as MethodReference).let {
            assertEquals("Ljava/lang/Throwable;", it.definingClass)
            assertEquals("getMessage", it.name)
        }
    }

    @Test
    fun `descending overflow invoke operands are spilled instead of widened`() {
        // Remapped operands {v16, v15} (receiver p0 -> v16, arg p1 -> v15) are
        // 'contiguous' by max-min+1 but DESCENDING: a range form starting at v15
        // would read (v15, v16) and swap the argument order. The invoke must be
        // spilled with order-preserving gather moves, not widened directly.
        val smali = """
            .class public Lcom/example/Swapper;
            .super Ljava/lang/Object;

            .method public swap(Lcom/example/A;)V
                .registers 16
                .param p1, "a"

                invoke-virtual {v15, v14}, Lcom/example/A;->use(Lcom/example/B;)V
                return-void
            .end method
        """.trimIndent()

        val context = PatchTestUtil.createBytecodeContext(smali)
        val method = method(context, "Lcom/example/Swapper;", "swap")

        method.prependConditionalReturn(enabledMethod, "return-void")

        val instructions = method.getInstructions()
        // paramWidth = receiver + 1 param = 2; K = 2 spill slots.
        // newCount = 16 + 2 + 1 + 2 = 21; block = v19..v20 (top of frame).
        assertEquals(21, method.implementation!!.registerCount)

        // Guard scratch = oldCount - paramWidth = v14.
        assertEquals(Opcode.INVOKE_STATIC, instructions[0].opcode)
        assertEquals(14, (instructions[1] as OneRegisterInstruction).registerA)
        assertEquals(14, (instructions[2] as OneRegisterInstruction).registerA)

        // Body starts at index 4 (guard + fallback return-void). Two gather
        // moves in OPERAND order: v19 <- v16 (receiver), then v20 <- v15 (arg).
        val moves = instructions.drop(4).take(2).map { it as TwoRegisterInstruction }
        assertEquals(Opcode.MOVE_OBJECT_FROM16, moves[0].opcode)
        assertEquals(19 to 16, moves[0].registerA to moves[0].registerB)
        assertEquals(Opcode.MOVE_OBJECT_FROM16, moves[1].opcode)
        assertEquals(20 to 15, moves[1].registerA to moves[1].registerB)

        // The invoke is a range form against the spill block, NOT {v15..v16}.
        val invoke = instructions[6] as Instruction3rc
        assertEquals(Opcode.INVOKE_VIRTUAL_RANGE, invoke.opcode)
        assertEquals(19, invoke.startRegister)
        assertEquals(2, invoke.registerCount)
        (invoke.reference as MethodReference).let {
            assertEquals("Lcom/example/A;", it.definingClass)
            assertEquals("use", it.name)
        }

        assertEquals(Opcode.RETURN_VOID, instructions[7].opcode)
        assertEquals(8, instructions.size)
    }

    @Test
    fun `scattered overflow invoke is spilled into a contiguous range block`() {
        // Replicates qo.w0.g: non-static, 2 object params, .registers 16. The
        // invoke's operands {v0,v1,v2,v14,v15} remap to {v0,v1,v2,v15,v16} —
        // overflowing v15 but NOT contiguous, so no legal range form exists.
        // The operands must be gathered, in argument order, into a fresh
        // contiguous scratch block at the top of the grown frame, and the
        // invoke rewritten as a range form against the block.
        val smali = """
            .class public Lqo/w0;
            .super Ljava/lang/Object;

            .method public g(Lqo/k;Lqo/hb;)V
                .registers 16

                const/4 v0, 0x0
                const/4 v1, 0x1
                const/4 v2, 0x2
                invoke-static {v0, v1, v2, v14, v15}, Lg0/a;->a(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
                return-void
            .end method
        """.trimIndent()

        val context = PatchTestUtil.createBytecodeContext(smali)
        val method = method(context, "Lqo/w0;", "g")

        method.prependConditionalReturn(enabledMethod, "return-void")

        val instructions = method.getInstructions()
        // paramWidth = receiver + 2 params = 3; K = 5 spill slots.
        // newCount = 16 + 3 + 1 + 5 = 25; block = v20..v24 (top of frame).
        assertEquals(25, method.implementation!!.registerCount)

        // Guard scratch stays at oldCount - paramWidth = v13.
        assertEquals(Opcode.INVOKE_STATIC, instructions[0].opcode)
        assertEquals(13, (instructions[1] as OneRegisterInstruction).registerA)
        assertEquals(13, (instructions[2] as OneRegisterInstruction).registerA)

        // Body: 3 const, then the spill moves (5) directly before the invoke,
        // then return-void. Guard = 4 instructions, so body starts at 4.
        assertEquals(Opcode.CONST_4, instructions[4].opcode)
        assertEquals(Opcode.CONST_4, instructions[5].opcode)
        assertEquals(Opcode.CONST_4, instructions[6].opcode)

        val moves = instructions.drop(7).take(5).map { it as TwoRegisterInstruction }
        assertEquals(5, moves.size)
        moves.forEach { assertEquals(Opcode.MOVE_OBJECT_FROM16, it.opcode) }
        // Argument order preserved: block regs 20..24 gather v0,v1,v2,v15,v16.
        assertEquals(20 to 0, moves[0].registerA to moves[0].registerB)
        assertEquals(21 to 1, moves[1].registerA to moves[1].registerB)
        assertEquals(22 to 2, moves[2].registerA to moves[2].registerB)
        assertEquals(23 to 15, moves[3].registerA to moves[3].registerB)
        assertEquals(24 to 16, moves[4].registerA to moves[4].registerB)

        val invoke = instructions[12] as Instruction3rc
        assertEquals(Opcode.INVOKE_STATIC_RANGE, invoke.opcode)
        assertEquals(20, invoke.startRegister)
        assertEquals(5, invoke.registerCount)
        (invoke.reference as MethodReference).let {
            assertEquals("Lg0/a;", it.definingClass)
            assertEquals("a", it.name)
        }

        assertEquals(Opcode.RETURN_VOID, instructions[13].opcode)
        assertEquals(14, instructions.size)
    }
}
