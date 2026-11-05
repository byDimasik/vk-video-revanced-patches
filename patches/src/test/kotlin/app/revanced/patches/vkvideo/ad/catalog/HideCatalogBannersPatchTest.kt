package app.revanced.patches.vkvideo.ad.catalog

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

class HideCatalogBannersPatchTest {

    private val catalogAdBannerDtoSmali = """
        .class public final Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;
        .super Ljava/lang/Object;

        # interfaces
        .implements Landroid/os/Parcelable;

        # instance fields
        .field private final slotId:Ljava/lang/Long;
            .annotation runtime Lhn/b;
                value = "slot_id"
            .end annotation
        .end field

        .method public final b()Ljava/lang/Long;
            .registers 2

            iget-object v0, p0, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;->slotId:Ljava/lang/Long;

            return-object v0
        .end method
    """.trimIndent()

    private val catalogSectionDtoSmali = """
        .class public final Lcom/vk/api/generated/catalog/dto/CatalogSectionDto;
        .super Ljava/lang/Object;

        # interfaces
        .implements Landroid/os/Parcelable;

        # instance fields
        .field private final adBanner:Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;
        .end field

        .method public final d()Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;
            .registers 2

            iget-object v0, p0, Lcom/vk/api/generated/catalog/dto/CatalogSectionDto;->adBanner:Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;

            return-object v0
        .end method

        .method public final getTitle()Ljava/lang/String;
            .registers 2

            const-string v0, "title"

            return-object v0
        .end method
    """.trimIndent()

    @Test
    fun testHideCatalogBannersPatchMatchesAndMutates() {
        val context = PatchTestUtil.createBytecodeContext(
            catalogAdBannerDtoSmali,
            catalogSectionDtoSmali
        )

        val slotIdMatch = catalogAdBannerSlotIdFingerprint.matchOrNull(context)
        assertNotNull(slotIdMatch, "catalogAdBannerSlotIdFingerprint should match fixture")

        val sectionAdBannerMatch = catalogSectionAdBannerGetterFingerprint.matchOrNull(context)
        assertNotNull(
            sectionAdBannerMatch,
            "catalogSectionAdBannerGetterFingerprint should match fixture"
        )

        hideCatalogBannersPatch.execute(context)

        val slotIdMethod = catalogAdBannerSlotIdFingerprint.getMethod(context)
        val slotIdInstructions = slotIdMethod.getInstructions()
        assertEquals(Opcode.CONST_4, slotIdInstructions[0].opcode)
        assertEquals(0, (slotIdInstructions[0] as OneRegisterInstruction).registerA)
        assertEquals(0, (slotIdInstructions[0] as NarrowLiteralInstruction).narrowLiteral)
        assertEquals(Opcode.RETURN_OBJECT, slotIdInstructions[1].opcode)
        assertEquals(0, (slotIdInstructions[1] as OneRegisterInstruction).registerA)

        val sectionAdBannerMethod = catalogSectionAdBannerGetterFingerprint.getMethod(context)
        val sectionAdBannerInstructions = sectionAdBannerMethod.getInstructions()
        assertEquals(Opcode.CONST_4, sectionAdBannerInstructions[0].opcode)
        assertEquals(0, (sectionAdBannerInstructions[0] as OneRegisterInstruction).registerA)
        assertEquals(0, (sectionAdBannerInstructions[0] as NarrowLiteralInstruction).narrowLiteral)
        assertEquals(Opcode.RETURN_OBJECT, sectionAdBannerInstructions[1].opcode)
        assertEquals(0, (sectionAdBannerInstructions[1] as OneRegisterInstruction).registerA)
    }
}
