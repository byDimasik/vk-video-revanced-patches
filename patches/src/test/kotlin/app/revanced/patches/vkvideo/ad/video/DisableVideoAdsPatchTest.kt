package app.revanced.patches.vkvideo.ad.video

import app.revanced.patches.vkvideo.util.PatchTestUtil
import app.revanced.patches.vkvideo.util.PatchTestUtil.execute
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DisableVideoAdsPatchTest {

    private val instreamAdJsonParserSmali = """
        .class public final Lcom/vk/dto/common/InstreamAd${"$"}b;
        .super Ljava/lang/Object;

        .method public final a(Lorg/json/JSONObject;)Ljava/lang/Object;
            .registers 15

            const-string v0, "slot_id"
            const-string v0, "timeout"
            const-string v0, "can_play"
            const-string v0, "midroll_percents"
            const-string v0, "params"
            const-string v0, "sections"
            const-string v0, "autoplay_preroll"
            const/4 v0, 0x0
            return-object v0
        .end method
    """.trimIndent()

    private val videoFileInstreamAdSmali = """
        .class public final Lcom/vk/dto/common/VideoFileOld;
        .super Ljava/lang/Object;

        .field public b0:Lcom/vk/dto/common/InstreamAd;

        .method public final x3()Lcom/vk/dto/common/InstreamAd;
            .registers 2

            iget-object v0, p0, Lcom/vk/dto/common/VideoFileOld;->b0:Lcom/vk/dto/common/InstreamAd;
            return-object v0
        .end method
    """.trimIndent()

    private val instreamAdLoaderSmali = """
        .class public final Lmo/p6;
        .super Ljava/lang/Object;

        .method public final f(Lmo/d5;Lmo/c1;)V
            .registers 13

            const-string v0, "version"
            const-string v0, "2.5"
            const-string v0, "s2s"
            const-string v0, "InstreamAdFactory: invalid json-data, error: "
            return-void
        .end method
    """.trimIndent()

    private val instreamAdControllerSmali = """
        .class public final Lwo/b;
        .super Ljava/lang/Object;

        .method public final c(Ljava/lang/String;)V
            .registers 3

            const-string v0, "InstreamAd: Unable to start ad - not loaded yet"
            const-string v0, "InstreamAd: Unable to start ad - player has not set"
            const-string v0, "InstreamAdEngine: No section with name "
            return-void
        .end method
    """.trimIndent()

    @Test
    fun testDisableVideoAdsPatchMatchesAndMutates() {
        val context = PatchTestUtil.createBytecodeContext(
            instreamAdJsonParserSmali,
            videoFileInstreamAdSmali,
            instreamAdLoaderSmali,
            instreamAdControllerSmali
        )

        assertNotNull(
            instreamAdJsonParserFingerprint.matchOrNull(context),
            "instreamAdJsonParserFingerprint should match fixture"
        )
        assertNotNull(
            videoFileInstreamAdFingerprint.matchOrNull(context),
            "videoFileInstreamAdFingerprint should match fixture"
        )
        assertNotNull(
            instreamAdLoaderFingerprint.matchOrNull(context),
            "instreamAdLoaderFingerprint should match fixture"
        )
        assertNotNull(
            instreamAdControllerFingerprint.matchOrNull(context),
            "instreamAdControllerFingerprint should match fixture"
        )

        disableVideoAdsPatch.execute(context)

        val parserInstructions = instreamAdJsonParserFingerprint.getMethod(context).getInstructions()
        assertEquals(Opcode.CONST_4, parserInstructions[0].opcode)
        assertEquals(0, (parserInstructions[0] as OneRegisterInstruction).registerA)
        assertEquals(0, (parserInstructions[0] as NarrowLiteralInstruction).narrowLiteral)
        assertEquals(Opcode.RETURN_OBJECT, parserInstructions[1].opcode)
        assertEquals(0, (parserInstructions[1] as OneRegisterInstruction).registerA)

        val videoFileInstructions = videoFileInstreamAdFingerprint.getMethod(context).getInstructions()
        assertEquals(Opcode.CONST_4, videoFileInstructions[0].opcode)
        assertEquals(0, (videoFileInstructions[0] as OneRegisterInstruction).registerA)
        assertEquals(0, (videoFileInstructions[0] as NarrowLiteralInstruction).narrowLiteral)
        assertEquals(Opcode.RETURN_OBJECT, videoFileInstructions[1].opcode)
        assertEquals(0, (videoFileInstructions[1] as OneRegisterInstruction).registerA)

        val loaderInstructions = instreamAdLoaderFingerprint.getMethod(context).getInstructions()
        assertEquals(Opcode.RETURN_VOID, loaderInstructions[0].opcode)

        val controllerInstructions = instreamAdControllerFingerprint.getMethod(context).getInstructions()
        assertEquals(Opcode.RETURN_VOID, controllerInstructions[0].opcode)
    }
}
