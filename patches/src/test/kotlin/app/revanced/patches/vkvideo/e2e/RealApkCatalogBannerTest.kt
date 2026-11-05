package app.revanced.patches.vkvideo.e2e

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patches.vkvideo.ad.catalog.catalogAdBannerSlotIdFingerprint
import app.revanced.patches.vkvideo.ad.catalog.catalogSectionAdBannerGetterFingerprint
import app.revanced.patches.vkvideo.ad.catalog.hideCatalogBannersPatch
import app.revanced.patches.vkvideo.util.PatchTestUtil.execute
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.initializeCache
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Resolves `hide-catalog-banners` fingerprints against the real VK Video APK snapshot.
 *
 * Scoped to this patch so it still exercises real bytecode when another patch's fingerprints drift.
 */
class RealApkCatalogBannerTest {

    private fun findApkFile(): File? = listOf(
        File(".cache/vkvideo-1.168.apk"),
        File("/app/.cache/vkvideo-1.168.apk"),
        File("../.cache/vkvideo-1.168.apk")
    ).firstOrNull { it.exists() && it.isFile }

    @Test
    fun testCatalogBannerFingerprintsOnRealApk() {
        val apkFile = findApkFile()
        val isStrictE2e = System.getProperty("vkvideo.e2e") == "true" || System.getenv("VKVIDEO_E2E") == "true"

        if (isStrictE2e) {
            check(apkFile != null && apkFile.exists()) {
                "VK Video target APK snapshot (vkvideo-1.168.apk) not found in .cache"
            }
        } else {
            assumeTrue(
                apkFile != null && apkFile.exists(),
                "Skipping RealApkCatalogBannerTest: VK Video target APK snapshot not found in .cache"
            )
        }

        val realApk = apkFile!!
        val patchedDir = Files.createTempDirectory("patched-catalog-test").toFile().apply { deleteOnExit() }

        val constructor = BytecodePatchContext::class.java.getDeclaredConstructor(File::class.java, File::class.java)
        constructor.isAccessible = true
        val context = constructor.newInstance(realApk, patchedDir)

        val dexContainer = DexFileFactory.loadDexContainer(realApk, Opcodes.getDefault())
        for (dexEntryName in dexContainer.dexEntryNames) {
            val dexFile = dexContainer.getEntry(dexEntryName)?.dexFile ?: continue
            context.classDefs.addAll(dexFile.classes)
        }
        context.initializeCache()

        val slotIdFingerprintMethod = catalogAdBannerSlotIdFingerprint.getMethod(context)
        val sectionGetterMethod = catalogSectionAdBannerGetterFingerprint.getMethod(context)

        assertEquals(
            "Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;",
            slotIdFingerprintMethod.definingClass
        )
        assertEquals(
            "Lcom/vk/api/generated/catalog/dto/CatalogSectionDto;",
            sectionGetterMethod.definingClass
        )
        assertNotNull(catalogAdBannerSlotIdFingerprint.matchOrNull(context))
        assertNotNull(catalogSectionAdBannerGetterFingerprint.matchOrNull(context))

        hideCatalogBannersPatch.execute(context)

        for (method in listOf(slotIdFingerprintMethod, sectionGetterMethod)) {
            val instructions = method.getInstructions()
            assertEquals(Opcode.CONST_4, instructions[0].opcode)
            assertEquals(0, (instructions[0] as NarrowLiteralInstruction).narrowLiteral)
            assertEquals(Opcode.RETURN_OBJECT, instructions[1].opcode)
            assertEquals(0, (instructions[1] as OneRegisterInstruction).registerA)
        }
    }
}
