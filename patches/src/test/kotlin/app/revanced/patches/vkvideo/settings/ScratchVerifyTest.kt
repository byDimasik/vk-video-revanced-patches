package app.revanced.patches.vkvideo.settings

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import org.junit.jupiter.api.Test

class ScratchVerifyTest {
    @Test
    fun verify() {
        val smali = """
            .class public Lcom/example/Calc;
            .super Ljava/lang/Object;
            .method public static calc(II)I
                .registers 5
                .param p0, "a"
                .param p1, "b"
                const/4 v2, 0x2
                add-int v0, p0, p1
                add-int v0, v0, v2
                return v0
            .end method
        """.trimIndent()
        val ctx = PatchTestUtil.createBytecodeContext(smali)
        val m = MutableMethod(ctx.classDefs.first { it.type == "Lcom/example/Calc;" }.methods.first { it.name == "calc" })
        val oldCount = m.implementation!!.registerCount
        m.prependConditionalReturn("Lstub/Enabled;->isEnabled()Z", "const/4 v0, 0x0\nreturn v0")
        val newCount = m.implementation!!.registerCount
        val paramWidth = m.parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 }
        val paramStart = newCount - paramWidth
        m.implementation!!.instructions.forEachIndexed { i, ins -> println("INS $i ${ins.opcode} ${ins::class.java.simpleName}") }
        val add = m.implementation!!.instructions.firstOrNull { it.opcode == com.android.tools.smali.dexlib2.Opcode.ADD_INT } as ThreeRegisterInstruction? ?: error("OPCODES=" + m.implementation!!.instructions.joinToString { it.opcode.name })
        println("VERIFY oldCount=$oldCount newCount=$newCount paramStart=$paramStart lastParam=${newCount - 1} scratch=$oldCount addB=${add!!.registerB} addC=${add!!.registerC}")
        println("VERIFY scratchAliasesLastParam=${oldCount == newCount - 1} addReadsParams=${add!!.registerB >= paramStart && add!!.registerC >= paramStart}")
    }
}
