package app.revanced.patches.vkvideo.ad.feed

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

    @Test
    fun testHideFeedAdsPatchMatchesAndMutates() {
        val context = PatchTestUtil.createBytecodeContext(myTargetSmali, promoPostSmali)

        val myTargetMatch = myTargetNativeAdEntryFingerprint.matchOrNull(context)
        assertNotNull(myTargetMatch, "myTargetNativeAdEntryFingerprint should match fixture")

        val promoPostMatch = promoPostFingerprint.matchOrNull(context)
        assertNotNull(promoPostMatch, "promoPostFingerprint should match fixture")

        hideFeedAdsPatch.execute(context)

        val myTargetMethod = myTargetNativeAdEntryFingerprint.getMethod(context)
        val myTargetInstructions = myTargetMethod.getInstructions()
        assertEquals(Opcode.CONST_4, myTargetInstructions[0].opcode)
        assertEquals(0, (myTargetInstructions[0] as OneRegisterInstruction).registerA)
        assertEquals(0, (myTargetInstructions[0] as NarrowLiteralInstruction).narrowLiteral)
        assertEquals(Opcode.RETURN_OBJECT, myTargetInstructions[1].opcode)
        assertEquals(0, (myTargetInstructions[1] as OneRegisterInstruction).registerA)

        val promoPostMethod = promoPostFingerprint.getMethod(context)
        val promoPostInstructions = promoPostMethod.getInstructions()
        assertEquals(Opcode.CONST_4, promoPostInstructions[0].opcode)
        assertEquals(0, (promoPostInstructions[0] as OneRegisterInstruction).registerA)
        assertEquals(0, (promoPostInstructions[0] as NarrowLiteralInstruction).narrowLiteral)
        assertEquals(Opcode.RETURN_OBJECT, promoPostInstructions[1].opcode)
        assertEquals(0, (promoPostInstructions[1] as OneRegisterInstruction).registerA)
    }
}
