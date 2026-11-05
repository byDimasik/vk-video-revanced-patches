package app.revanced.patches.vkvideo.e2e

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.ResourcePatchContext
import app.revanced.patches.vkvideo.ad.catalog.hideCatalogBannersPatch
import app.revanced.patches.vkvideo.ad.feed.hideFeedAdsPatch
import app.revanced.patches.vkvideo.ad.shelf.suppressTopShelfPatch
import app.revanced.patches.vkvideo.ad.video.disableVideoAdsPatch
import app.revanced.patches.vkvideo.util.PatchTestUtil.execute
import app.revanced.patches.vkvideo.util.PatchTestUtil.initializeCache
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class RealApkSmokeTest {

    private fun findApkFile(): File? {
        val candidatePaths = listOf(
            File(".cache/vkvideo-1.168.apk"),
            File("/app/.cache/vkvideo-1.168.apk"),
            File("../.cache/vkvideo-1.168.apk")
        )
        return candidatePaths.firstOrNull { it.exists() && it.isFile }
    }

    @Test
    fun testPatchesOnRealApk() {
        val apkFile = findApkFile()
        val isStrictE2e = System.getProperty("vkvideo.e2e") == "true" || System.getenv("VKVIDEO_E2E") == "true"

        if (isStrictE2e) {
            check(apkFile != null && apkFile.exists()) {
                "VK Video target APK snapshot (vkvideo-1.168.apk) not found in .cache"
            }
        } else {
            assumeTrue(
                apkFile != null && apkFile.exists(),
                "Skipping RealApkSmokeTest: VK Video target APK snapshot (vkvideo-1.168.apk) not found in .cache"
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

        disableVideoAdsPatch.execute(bytecodeContext, resourceContext)
        hideFeedAdsPatch.execute(bytecodeContext, resourceContext)
        hideCatalogBannersPatch.execute(bytecodeContext, resourceContext)

        // Resource patches need decoded resources; resourcePatch decoding is lazy until first use.
        ResourcePatchContext::class.java.getMethod("decodeResources\$patcher").invoke(resourceContext)
        suppressTopShelfPatch.execute(bytecodeContext, resourceContext)

        for (layout in listOf("catalog_video_topshelf.xml", "catalog_video_music_topshelf.xml")) {
            val xml = File(tempPatchedDir, "res/layout/$layout").readText()
            check("android:layout_height=\"0dp\"" in xml) { "$layout root height not collapsed" }
            check("android:visibility=\"gone\"" in xml) { "$layout root not hidden" }
        }
    }
}
