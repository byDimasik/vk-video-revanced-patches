package app.revanced.patches.vkvideo.ad.video

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patches.vkvideo.settings.ADS_SETTINGS_CLASS
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
import app.revanced.patches.vkvideo.util.PatchTestUtil
import app.revanced.patches.vkvideo.util.PatchTestUtil.applyPatches
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

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

        .field public h:Ljava/lang/String;

        .method public final f(Lmo/d5;Lmo/c1;)V
            .registers 13

            iget-object v0, p0, Lmo/p6;->h:Ljava/lang/String;
            if-eqz v0, :cond_0
            const-string v3, "version"
            const-string v4, "2.5"
            const-string v2, "s2s"
            const-string v0, "InstreamAdFactory: invalid json-data, error: "
            :cond_0
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

    private val adFixtures = listOf(
        instreamAdJsonParserSmali,
        videoFileInstreamAdSmali,
        instreamAdLoaderSmali,
        instreamAdControllerSmali,
    )

    private data class MenuFixture(val name: String, val sources: List<String>)

    private val menuFixtures = listOf(
        MenuFixture("1.168", PatchTestUtil.settingsScreenFixture168),
        MenuFixture("1.170", PatchTestUtil.settingsScreenFixture170),
    )

    @TestFactory
    fun `blocks through the toggle unless disabled`(): List<DynamicTest> =
        menuFixtures.map { menu ->
            DynamicTest.dynamicTest(menu.name) {
                val context = PatchTestUtil.createBytecodeContext(
                    *(menu.sources + adFixtures).toTypedArray()
                )

                val parserMethod = instreamAdJsonParserFingerprint.getMethod(context)
                val videoFileMethod = videoFileInstreamAdFingerprint.getMethod(context)
                val loaderMethod = instreamAdLoaderFingerprint.getMethod(context)
                val controllerMethod = instreamAdControllerFingerprint.getMethod(context)

                val parserFirst = parserMethod.getInstructions().first().opcode
                val videoFileFirst = videoFileMethod.getInstructions().first().opcode
                val loaderFirst = loaderMethod.getInstructions().first().opcode
                val controllerFirst = controllerMethod.getInstructions().first().opcode

                PatchTestUtil.applyPatches(context, disableVideoAdsPatch)

                assertTrue(
                    disableVideoAdsPatch.dependencies.contains(revancedSettingsPatch),
                    "disableVideoAdsPatch must depend on revancedSettingsPatch"
                )
                assertTrue(
                    context.classDefs.any { it.type == ADS_SETTINGS_CLASS },
                    "revancedSettingsPatch dependency must inject AdsSettings"
                )

                assertObjectConditional(parserMethod, "isVideoAdsEnabled", parserFirst)
                assertObjectConditional(videoFileMethod, "isVideoAdsEnabled", videoFileFirst)
                assertVoidConditional(loaderMethod, "isVideoAdsEnabled", loaderFirst)
                assertVoidConditional(controllerMethod, "isVideoAdsEnabled", controllerFirst)
            }
        }

    private fun assertObjectConditional(method: MutableMethod, enabledMethod: String, originalFirst: Opcode) {
        val instructions = method.getInstructions()
        assertEnabledInvoke(instructions[0], enabledMethod)
        assertEquals(Opcode.MOVE_RESULT, instructions[1].opcode)
        assertBranchTargets(instructions, index = 2, target = 5, originalFirst = originalFirst)
        assertEquals(Opcode.CONST_4, instructions[3].opcode)
        assertEquals(0, (instructions[3] as NarrowLiteralInstruction).narrowLiteral)
        assertEquals(Opcode.RETURN_OBJECT, instructions[4].opcode)
        assertEquals(originalFirst, instructions[5].opcode)
    }

    private fun assertVoidConditional(method: MutableMethod, enabledMethod: String, originalFirst: Opcode) {
        val instructions = method.getInstructions()
        assertEnabledInvoke(instructions[0], enabledMethod)
        assertEquals(Opcode.MOVE_RESULT, instructions[1].opcode)
        assertBranchTargets(instructions, index = 2, target = 4, originalFirst = originalFirst)
        assertEquals(Opcode.RETURN_VOID, instructions[3].opcode)
        assertEquals(originalFirst, instructions[4].opcode)
    }

    private fun assertBranchTargets(
        instructions: List<com.android.tools.smali.dexlib2.builder.BuilderInstruction>,
        index: Int,
        target: Int,
        originalFirst: Opcode,
    ) {
        val branch = instructions[index]
        assertEquals(Opcode.IF_EQZ, branch.opcode)
        val targetAddress = branch.location.codeAddress + (branch as OffsetInstruction).codeOffset
        val resolved = instructions.indexOfFirst { it.location.codeAddress == targetAddress }
        assertEquals(target, resolved, "branch must jump over the fallback")
        assertEquals(originalFirst, instructions[target].opcode)
    }

    private fun assertEnabledInvoke(
        instruction: com.android.tools.smali.dexlib2.builder.BuilderInstruction,
        enabledMethod: String,
    ) {
        assertEquals(Opcode.INVOKE_STATIC, instruction.opcode)
        val reference = (instruction as ReferenceInstruction).reference as MethodReference
        assertEquals(ADS_SETTINGS_CLASS, reference.definingClass)
        assertEquals(enabledMethod, reference.name)
    }

    @TestFactory
    fun `fingerprints match the fixtures`(): List<DynamicTest> =
        menuFixtures.map { menu ->
            DynamicTest.dynamicTest(menu.name) {
                val context = PatchTestUtil.createBytecodeContext(
                    *(menu.sources + adFixtures).toTypedArray()
                )
                assertNotNull(instreamAdJsonParserFingerprint.matchOrNull(context))
                assertNotNull(videoFileInstreamAdFingerprint.matchOrNull(context))
                assertNotNull(instreamAdLoaderFingerprint.matchOrNull(context))
                assertNotNull(instreamAdControllerFingerprint.matchOrNull(context))
            }
        }
}
