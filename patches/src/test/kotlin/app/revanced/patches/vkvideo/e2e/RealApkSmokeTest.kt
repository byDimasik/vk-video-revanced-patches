package app.revanced.patches.vkvideo.e2e

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.ResourcePatchContext
import app.revanced.patches.vkvideo.ad.catalog.catalogAdBannerSlotIdFingerprint
import app.revanced.patches.vkvideo.ad.catalog.catalogSectionAdBannerGetterFingerprint
import app.revanced.patches.vkvideo.ad.catalog.hideCatalogBannersPatch
import app.revanced.patches.vkvideo.ad.feed.hideFeedAdsPatch
import app.revanced.patches.vkvideo.ad.feed.myTargetNativeAdEntryFingerprint
import app.revanced.patches.vkvideo.ad.feed.promoPostFingerprint
import app.revanced.patches.vkvideo.ad.shelf.suppressTopShelfPatch
import app.revanced.patches.vkvideo.ad.video.disableVideoAdsPatch
import app.revanced.patches.vkvideo.ad.video.instreamAdControllerFingerprint
import app.revanced.patches.vkvideo.ad.video.instreamAdJsonParserFingerprint
import app.revanced.patches.vkvideo.ad.video.instreamAdLoaderFingerprint
import app.revanced.patches.vkvideo.ad.video.videoFileInstreamAdFingerprint
import app.revanced.patches.vkvideo.settings.ADS_SETTINGS_CLASS
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
import app.revanced.patches.vkvideo.util.PatchTestUtil.applyPatches
import app.revanced.patches.vkvideo.util.PatchTestUtil.execute
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.initializeCache
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class RealApkSmokeTest {

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
    fun testPatchesOnRealApk() {
        val apkFile = findApkFile()
        val isStrictE2e = System.getProperty("vkvideo.e2e") == "true" || System.getenv("VKVIDEO_E2E") == "true"

        if (isStrictE2e) {
            check(apkFile != null && apkFile.exists()) {
                "VK Video target APK snapshot (vkvideo-*.apk) not found in .cache"
            }
        } else {
            assumeTrue(
                apkFile != null && apkFile.exists(),
                "Skipping RealApkSmokeTest: VK Video target APK snapshot (vkvideo-*.apk) not found in .cache"
            )
        }

        val realApk = apkFile!!
        val tempPatchedDir = Files.createTempDirectory("patched-apk-test").toFile().apply { deleteOnExit() }

        val bytecodeConstructor = BytecodePatchContext::class.java.getDeclaredConstructor(File::class.java, File::class.java)
        bytecodeConstructor.isAccessible = true
        val bytecodeContext = bytecodeConstructor.newInstance(realApk, tempPatchedDir)

        val dexContainer = DexFileFactory.loadDexContainer(realApk, Opcodes.getDefault())
        for (dexEntryName in dexContainer.dexEntryNames) {
            val dexFile = dexContainer.getEntry(dexEntryName)!!.dexFile
            bytecodeContext.classDefs.addAll(dexFile.classes)
        }
        bytecodeContext.initializeCache()

        val resConstructor = ResourcePatchContext::class.java.declaredConstructors.first()
        resConstructor.isAccessible = true
        val frameworkDir = System.getenv("APKTOOL_FRAMEWORK_DIR")
        val resourceContext =
            resConstructor.newInstance(realApk, tempPatchedDir, tempPatchedDir, null, frameworkDir) as ResourcePatchContext

        val adPatches = listOf(disableVideoAdsPatch, hideFeedAdsPatch, hideCatalogBannersPatch)
        for (patch in adPatches) {
            assertTrue(
                patch.dependencies.contains(revancedSettingsPatch),
                "${patch.name} must depend on revancedSettingsPatch"
            )
        }

        applyPatches(bytecodeContext, *adPatches.toTypedArray())

        for (classDef in bytecodeContext.classDefs) {
            for (m in classDef.methods) {
                val hit = m.implementation?.instructions?.withIndex()?.firstOrNull { (_, insn) ->
                    ((insn as? ReferenceInstruction)?.reference as? MethodReference)
                        ?.let { it.definingClass == ADS_SETTINGS_CLASS && it.name == "isVideoAdsEnabled" } == true
                }
                if (hit != null) System.err.println("GETTERHIT ${classDef.type}->${m.name}${m.parameterTypes} idx=${hit.index} count=${m.implementation!!.instructions.toList().size}")
            }
        }

        assertTrue(
            bytecodeContext.classDefs.any { it.type == ADS_SETTINGS_CLASS },
            "revancedSettingsPatch dependency must inject AdsSettings"
        )

        val adMethods = listOf(
            "instreamAdJsonParser" to instreamAdJsonParserFingerprint,
            "videoFileInstreamAd" to videoFileInstreamAdFingerprint,
            "instreamAdLoader" to instreamAdLoaderFingerprint,
            "instreamAdController" to instreamAdControllerFingerprint,
            "myTargetNativeAdEntry" to myTargetNativeAdEntryFingerprint,
            "promoPost" to promoPostFingerprint,
            "catalogAdBannerSlotId" to catalogAdBannerSlotIdFingerprint,
            "catalogSectionAdBannerGetter" to catalogSectionAdBannerGetterFingerprint,
        ).map { (label, fingerprint) -> label to fingerprint.getMethod(bytecodeContext) }
        for ((label, method) in adMethods) {
            val insns = method.getInstructions()
            val hasInvoke = insns.any { it.opcode == Opcode.INVOKE_STATIC }
            System.err.println("ADM $label ${method.definingClass}->${method.name}${method.parameterTypes} insns=${insns.size} first=${insns.firstOrNull()?.opcode} hasInvoke=$hasInvoke")
            assertConditionalReturn(insns, label)
        }

        // Resource patches need decoded resources; resourcePatch decoding is lazy until first use.
        ResourcePatchContext::class.java.getMethod("decodeResources\$patcher").invoke(resourceContext)
        suppressTopShelfPatch.execute(bytecodeContext, resourceContext)

        for (layout in listOf("catalog_video_topshelf.xml", "catalog_video_music_topshelf.xml")) {
            val xml = File(tempPatchedDir, "res/layout/$layout").readText()
            check("android:layout_height=\"0dp\"" in xml) { "$layout root height not collapsed" }
            check("android:visibility=\"gone\"" in xml) { "$layout root not hidden" }
        }
    }

    private fun assertConditionalReturn(instructions: List<BuilderInstruction>, label: String) {
        val firstReturn = instructions.indexOfFirst {
            it.opcode == Opcode.RETURN_OBJECT || it.opcode == Opcode.RETURN_VOID
        }
        assertTrue(firstReturn >= 0, "method has no return instruction")
        val ifEqz = instructions.indexOfFirst { it.opcode == Opcode.IF_EQZ }
        instructions.forEachIndexed { i, insn -> System.err.println("DUMP $label $i ${insn.opcode} ${(insn as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)?.reference ?: ""}") }
        assertTrue(ifEqz in 0 until firstReturn, "IF_EQZ must precede the first return")

        val branch = instructions[ifEqz]
        val targetAddress = branch.location.codeAddress + (branch as OffsetInstruction).codeOffset
        val target = instructions.indexOfFirst { it.location.codeAddress == targetAddress }
        assertTrue(target > firstReturn, "branch must jump past the fallback")

        val getterCall = instructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_STATIC &&
                ((it as? ReferenceInstruction)?.reference as? MethodReference)?.definingClass == ADS_SETTINGS_CLASS
        }
        assertTrue(getterCall in 0 until firstReturn, "$label: AdsSettings getter must precede the first return")
        assertEquals(Opcode.MOVE_RESULT, instructions[getterCall + 1].opcode, "$label: getter result must be consumed")
    }
}
