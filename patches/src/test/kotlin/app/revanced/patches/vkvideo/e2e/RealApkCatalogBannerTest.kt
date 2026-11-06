package app.revanced.patches.vkvideo.e2e

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patches.vkvideo.ad.catalog.catalogAdBannerSlotIdFingerprint
import app.revanced.patches.vkvideo.ad.catalog.catalogSectionAdBannerGetterFingerprint
import app.revanced.patches.vkvideo.ad.catalog.hideCatalogBannersPatch
import app.revanced.patches.vkvideo.settings.ADS_SETTINGS_CLASS
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
import app.revanced.patches.vkvideo.util.PatchTestUtil.applyPatches
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.initializeCache
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    private fun findApkFile(): File? {
        val candidateDirs = listOf(
            File(".cache"),
            File("/app/.cache"),
            File("../.cache")
        )
        // Version-agnostic: pick the newest vkvideo-<version>.apk snapshot in the cache.
        return candidateDirs.asSequence()
            .filter { it.isDirectory }
            .flatMap { it.listFiles { f -> f.isFile && f.name.matches(Regex("vkvideo-\\d+\\.\\d+(-\\d+)?\\.apk")) }?.asSequence() ?: emptySequence() }
            .maxByOrNull { it.lastModified() }
    }

    @Test
    fun testCatalogBannerFingerprintsOnRealApk() {
        val apkFile = findApkFile()
        val isStrictE2e = System.getProperty("vkvideo.e2e") == "true" || System.getenv("VKVIDEO_E2E") == "true"

        if (isStrictE2e) {
            check(apkFile != null && apkFile.exists()) {
                "VK Video target APK snapshot (vkvideo-*.apk) not found in .cache"
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

        assertNotNull(catalogAdBannerSlotIdFingerprint.matchOrNull(context))
        assertNotNull(catalogSectionAdBannerGetterFingerprint.matchOrNull(context))

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

        assertTrue(
            hideCatalogBannersPatch.dependencies.contains(revancedSettingsPatch),
            "hideCatalogBannersPatch must depend on revancedSettingsPatch"
        )

        applyPatches(context, hideCatalogBannersPatch)

        assertTrue(
            context.classDefs.any { it.type == ADS_SETTINGS_CLASS },
            "revancedSettingsPatch dependency must inject AdsSettings"
        )

        for (method in listOf(slotIdFingerprintMethod, sectionGetterMethod)) {
            assertConditionalReturn(method.getInstructions())
        }
    }

    private fun assertConditionalReturn(instructions: List<BuilderInstruction>) {
        assertEquals(Opcode.INVOKE_STATIC, instructions[0].opcode)
        assertEquals(Opcode.MOVE_RESULT, instructions[1].opcode)
        assertEquals(Opcode.IF_EQZ, instructions[2].opcode)
        assertEquals(Opcode.CONST_4, instructions[3].opcode)
        assertEquals(Opcode.RETURN_OBJECT, instructions[4].opcode)

        val firstReturn = instructions.indexOfFirst { it.opcode == Opcode.RETURN_OBJECT }
        assertEquals(4, firstReturn, "fallback must be the first return")

        val branch = instructions[2]
        val targetAddress = branch.location.codeAddress + (branch as OffsetInstruction).codeOffset
        val target = instructions.indexOfFirst { it.location.codeAddress == targetAddress }
        assertTrue(target > firstReturn, "branch must jump past the fallback")
    }
}
