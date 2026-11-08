package app.revanced.patches.vkvideo.e2e

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patches.vkvideo.sponsorskip.SKIP_ENGINE_CLASS
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipPatch
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipReleaseFingerprint
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipVideoHandoffFingerprint
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipHolderFingerprint
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipViewAttachedFingerprint
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipViewSetupFingerprint
import app.revanced.patches.vkvideo.util.PatchTestUtil.applyPatches
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.initializeCache
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class RealApkSponsorSkipTest {

    private fun findApkFile(): File? {
        val candidateDirs = listOf(
            File(".cache"),
            File("/app/.cache"),
            File("../.cache"),
        )
        return candidateDirs.asSequence()
            .filter { it.isDirectory }
            .flatMap { it.listFiles { f -> f.isFile && f.name.matches(Regex("vkvideo-\\d+\\.\\d+(-\\d+)?\\.apk")) }?.asSequence() ?: emptySequence() }
            .maxByOrNull { it.lastModified() }
    }

    @Test
    fun testHooksOnRealApk() {
        val apkFile = findApkFile()
        val isStrictE2e = System.getProperty("vkvideo.e2e") == "true" || System.getenv("VKVIDEO_E2E") == "true"

        if (isStrictE2e) {
            check(apkFile != null && apkFile.exists()) {
                "VK Video target APK snapshot (vkvideo-*.apk) not found in .cache"
            }
        } else {
            assumeTrue(
                apkFile != null && apkFile.exists(),
                "Skipping RealApkSponsorSkipTest: VK Video target APK snapshot not found in .cache",
            )
        }

        val patchedDir = Files.createTempDirectory("patched-sponsorskip-test").toFile().apply { deleteOnExit() }
        val constructor = BytecodePatchContext::class.java.getDeclaredConstructor(File::class.java, File::class.java)
        constructor.isAccessible = true
        val context = constructor.newInstance(apkFile, patchedDir)
        context.initializeCache()

        assertNotNull(sponsorSkipVideoHandoffFingerprint.matchOrNull(context), "video handoff not found")
        assertNotNull(sponsorSkipViewAttachedFingerprint.matchOrNull(context), "view attach not found")
        assertNotNull(sponsorSkipViewSetupFingerprint.matchOrNull(context), "view setup not found")
        assertNotNull(sponsorSkipHolderFingerprint.matchOrNull(context), "preview attach not found")
        assertNotNull(sponsorSkipReleaseFingerprint.matchOrNull(context), "release not found")

        applyPatches(context, sponsorSkipPatch)

        assertHook(sponsorSkipVideoHandoffFingerprint.getMethod(context), "onVideoChanged", 3, 4)
        assertHook(sponsorSkipViewAttachedFingerprint.getMethod(context), "onViewAttached", 2, 3)
        assertHook(sponsorSkipViewSetupFingerprint.getMethod(context), "onViewAttached", 1, 2)
        assertHook(sponsorSkipHolderFingerprint.getMethod(context), "onViewAttached", 3, 4)
        assertHook(sponsorSkipReleaseFingerprint.getMethod(context), "onRelease", 2, null)
    }

    private fun assertHook(
        method: app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod,
        target: String,
        receiver: Int,
        argument: Int?,
    ) {
        val instructions = method.getInstructions()
        val instruction = instructions[0] as ReferenceInstruction
        assertEquals(Opcode.INVOKE_STATIC, instruction.opcode, "first instruction must be the hook")
        val reference = instruction.reference as MethodReference
        assertEquals(SKIP_ENGINE_CLASS, reference.definingClass)
        assertEquals(target, reference.name)
        val registers = instruction as FiveRegisterInstruction
        assertEquals(receiver, registers.registerC)
        if (argument != null) assertEquals(argument, registers.registerD)
    }
}
