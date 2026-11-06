package app.revanced.patches.vkvideo.ad.catalog

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

    private val adFixtures = listOf(catalogAdBannerDtoSmali, catalogSectionDtoSmali)

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

                val slotIdMethod = catalogAdBannerSlotIdFingerprint.getMethod(context)
                val sectionAdBannerMethod = catalogSectionAdBannerGetterFingerprint.getMethod(context)

                val slotIdFirst = slotIdMethod.getInstructions().first().opcode
                val sectionAdBannerFirst = sectionAdBannerMethod.getInstructions().first().opcode

                PatchTestUtil.applyPatches(context, hideCatalogBannersPatch)

                assertTrue(
                    hideCatalogBannersPatch.dependencies.contains(revancedSettingsPatch),
                    "hideCatalogBannersPatch must depend on revancedSettingsPatch"
                )
                assertTrue(
                    context.classDefs.any { it.type == ADS_SETTINGS_CLASS },
                    "revancedSettingsPatch dependency must inject AdsSettings"
                )

                assertObjectConditional(slotIdMethod, "isCatalogAdsEnabled", slotIdFirst)
                assertObjectConditional(sectionAdBannerMethod, "isCatalogAdsEnabled", sectionAdBannerFirst)
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
                assertNotNull(catalogAdBannerSlotIdFingerprint.matchOrNull(context))
                assertNotNull(catalogSectionAdBannerGetterFingerprint.matchOrNull(context))
            }
        }
}
