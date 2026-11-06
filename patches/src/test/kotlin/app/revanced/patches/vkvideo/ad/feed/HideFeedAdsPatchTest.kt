package app.revanced.patches.vkvideo.ad.feed

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
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class HideFeedAdsPatchTest {

    private val myTargetSmali = """
        .class public final Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry${"$"}a;
        .super Ljava/lang/Object;

        .method public static a(Lorg/json/JSONObject;Lap/d;)Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry;
            .registers 48

            move-object/from16 v0, p1
            const-string v1, "ads"
            move-object/from16 v2, p0
            invoke-virtual {v2, v1}, Lorg/json/JSONObject;->getJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;
            move-result-object v1
            const/4 v2, 0x0
            invoke-virtual {v1, v2}, Lorg/json/JSONArray;->getJSONObject(I)Lorg/json/JSONObject;
            move-result-object v1
            const-string v3, "bannerID"
            invoke-virtual {v1, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v5
            const-string v3, "pattern_type"
            invoke-virtual {v1, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v6
            const-string v3, "template_id"
            invoke-virtual {v1, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v7
            const/4 v0, 0x0
            return-object v0
        .end method
    """.trimIndent()

    private val promoPostSmali = """
        .class public final Lcom/vk/dto/newsfeed/entries/PromoPost${"$"}a;
        .super Ljava/lang/Object;

        .method public static a(Lorg/json/JSONObject;Landroid/util/ArrayMap;Landroid/util/SparseArray;Ljava/util/Map;)Lcom/vk/dto/newsfeed/entries/PromoPost;
            .registers 34

            move-object/from16 v0, p0
            move-object/from16 v1, p3
            const-string v2, "ads"
            invoke-virtual {v0, v2}, Lorg/json/JSONObject;->getJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;
            move-result-object v2
            const-string v3, "ads_id1"
            invoke-virtual {v0, v3}, Lorg/json/JSONObject;->getInt(Ljava/lang/String;)I
            move-result v6
            const-string v3, "ads_id2"
            invoke-virtual {v0, v3}, Lorg/json/JSONObject;->getInt(Ljava/lang/String;)I
            move-result v7
            const-string v3, "ads_title"
            invoke-virtual {v0, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v8
            const/16 v0, 0x0
            return-object v0
        .end method
    """.trimIndent()

    private val adFixtures = listOf(myTargetSmali, promoPostSmali)

    private data class MenuFixture(val name: String, val sources: List<String>)

    private val menuFixtures = listOf(
        MenuFixture("1.168", PatchTestUtil.profileMenuFixture),
        MenuFixture("1.170", PatchTestUtil.profileMenuFixture170),
    )

    @TestFactory
    fun `blocks through the toggle unless disabled`(): List<DynamicTest> =
        menuFixtures.map { menu ->
            DynamicTest.dynamicTest(menu.name) {
                val context = PatchTestUtil.createBytecodeContext(
                    *(menu.sources + adFixtures).toTypedArray()
                )

                val myTargetMethod = myTargetNativeAdEntryFingerprint.getMethod(context)
                val promoPostMethod = promoPostFingerprint.getMethod(context)

                val myTargetFirst = myTargetMethod.getInstructions().first().opcode
                val promoPostFirst = promoPostMethod.getInstructions().first().opcode

                PatchTestUtil.applyPatches(context, hideFeedAdsPatch)

                assertTrue(
                    hideFeedAdsPatch.dependencies.contains(revancedSettingsPatch),
                    "hideFeedAdsPatch must depend on revancedSettingsPatch"
                )
                assertTrue(
                    context.classDefs.any { it.type == ADS_SETTINGS_CLASS },
                    "revancedSettingsPatch dependency must inject AdsSettings"
                )

                assertObjectConditional(myTargetMethod, "isFeedAdsEnabled", myTargetFirst)
                assertObjectConditional(promoPostMethod, "isFeedAdsEnabled", promoPostFirst)
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
                assertNotNull(myTargetNativeAdEntryFingerprint.matchOrNull(context))
                assertNotNull(promoPostFingerprint.matchOrNull(context))
            }
        }
}
