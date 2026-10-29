package app.revanced.patches.vkvideo.e2e

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.ResourcePatchContext
import app.revanced.patches.vkvideo.ad.feed.hideFeedAdsPatch
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
            File(".cache/vkvideo-1.165.apk"),
            File("/app/.cache/vkvideo-1.165.apk")
        )
        for (file in candidatePaths) {
            if (file.exists() && file.isFile) return file
        }

        val candidateDirs = listOf(File(".cache"), File("/app/.cache"))
        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                val apkMatch = dir.listFiles { _, name ->
                    name.startsWith("vkvideo-") && name.endsWith(".apk")
                }?.firstOrNull()
                if (apkMatch != null) return apkMatch
            }
        }
        return null
    }

    @Test
    fun testPatchesOnRealApk() {
        val apkFile = findApkFile()
        assumeTrue(
            apkFile != null && apkFile.exists(),
            "Skipping RealApkSmokeTest: VK Video target APK snapshot not found in .cache"
        )

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
        val resourceContext = resConstructor.newInstance(realApk, tempPatchedDir, tempPatchedDir, null, null) as ResourcePatchContext

        disableVideoAdsPatch.execute(bytecodeContext, resourceContext)
        hideFeedAdsPatch.execute(bytecodeContext, resourceContext)
    }
}
