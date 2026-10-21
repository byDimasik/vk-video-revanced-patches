package app.revanced.patches.vkvideo.util

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.vkvideo.util.PatchTestUtil.execute
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.Opcode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class PatchTestUtilTest {

    @Test
    fun testFingerprintAndInstructionMutation() {
        val smali = """
            .class public Lcom/vk/video/TestClass;
            .super Ljava/lang/Object;

            .method public constructor <init>()V
                .registers 1
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public testMethod()V
                .registers 2
                const-string v0, "original"
                return-void
            .end method
        """.trimIndent()

        val context = PatchTestUtil.createBytecodeContext(smali)

        println("Classes in context: ${context.classDefs.map { it.type }}")

        val testFingerprint = fingerprint {
            opcodes(Opcode.CONST_STRING, Opcode.RETURN_VOID)
            strings("original")
        }

        val match = testFingerprint.matchOrNull(context)
        println("Match result: $match")
        assertNotNull(match, "Fingerprint should match testMethod")

        val method = testFingerprint.getMethod(context)
        println("Method result: $method")

        val testPatch = bytecodePatch(
            name = "test-patch",
            description = "Test instruction mutation"
        ) {
            execute {
                method.replaceInstruction(0, "const-string v0, \"modified\"")
                method.addInstructions(1, "nop")
            }
        }

        testPatch.execute(context)

        val instructions = method.getInstructions()

        assertEquals(Opcode.CONST_STRING, instructions[0].opcode)
        assertEquals(Opcode.NOP, instructions[1].opcode)
    }
}
