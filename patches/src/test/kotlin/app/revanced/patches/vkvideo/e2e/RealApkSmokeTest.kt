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

    private fun cacheDirs(): List<File> {
        // Gradle runs :patches:test with the patches/ project directory as the working
        // directory, while the snapshot lives in the repository root .cache/ directory.
        // Walk up towards the repository root so both layouts resolve.
        val dirs = mutableListOf<File>()
        var current: File? = File("").absoluteFile
        while (current != null) {
            dirs += File(current, ".cache")
            current = current.parentFile
        }
        dirs += File("/app/.cache")
        return dirs.distinctBy { it.absolutePath }
    }

    private fun findApkFile(): File? {
        for (dir in cacheDirs()) {
            val candidate = File(dir, TARGET_APK_NAME)
            if (candidate.isFile) return candidate
        }
        return null
    }

    @Test
    fun testPatchesOnRealApk() {
        val apkFile = findApkFile()
        if (System.getProperty(E2E_STRICT_MODE_PROPERTY) == "true") {
            check(apkFile != null) {
                "VK Video target APK snapshot $TARGET_APK_NAME not found in .cache (strict e2e mode)"
            }
        } else {
            assumeTrue(
                apkFile != null,
                "Skipping RealApkSmokeTest: VK Video target APK snapshot not found in .cache"
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
        val resourceContext = resConstructor.newInstance(realApk, tempPatchedDir, tempPatchedDir, null, null) as ResourcePatchContext

        disableVideoAdsPatch.execute(bytecodeContext, resourceContext)
        hideFeedAdsPatch.execute(bytecodeContext, resourceContext)
    }

    private companion object {
        // Exact snapshot under test: a different version must not silently stand in for it.
        const val TARGET_APK_NAME = "vkvideo-1.165.apk"

        // Set by `make test-e2e`; standard `make check` runs keep the skip behavior.
        const val E2E_STRICT_MODE_PROPERTY = "vkvideo.e2e"
    }
}
