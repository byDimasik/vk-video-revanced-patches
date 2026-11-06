package app.revanced.patches.vkvideo.e2e

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patches.vkvideo.settings.ADS_SETTINGS_CLASS
import app.revanced.patches.vkvideo.settings.profileMenuItemsFingerprint
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class RealApkSettingsEntryTest {

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
    fun testSettingsEntryOnRealApk() {
        val apkFile = findApkFile()
        val isStrictE2e = System.getProperty("vkvideo.e2e") == "true" || System.getenv("VKVIDEO_E2E") == "true"

        if (isStrictE2e) {
            check(apkFile != null && apkFile.exists()) {
                "VK Video target APK snapshot (vkvideo-*.apk) not found in .cache"
            }
        } else {
            assumeTrue(
                apkFile != null && apkFile.exists(),
                "Skipping RealApkSettingsEntryTest: VK Video target APK snapshot not found in .cache"
            )
        }

        val realApk = apkFile!!
        val patchedDir = Files.createTempDirectory("patched-settings-test").toFile().apply { deleteOnExit() }

        val constructor = BytecodePatchContext::class.java.getDeclaredConstructor(File::class.java, File::class.java)
        constructor.isAccessible = true
        val context = constructor.newInstance(realApk, patchedDir)
        context.initializeCache()

        assertNotNull(profileMenuItemsFingerprint.matchOrNull(context))
        val menuMethod = profileMenuItemsFingerprint.getMethod(context)

        applyPatches(context, revancedSettingsPatch)

        val instructions = menuMethod.getInstructions()
        val recyclerIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                reference is MethodReference &&
                reference.definingClass == "Lme/grishka/appkit/fragments/BaseRecyclerFragment;" &&
                reference.parameterTypes == listOf("Ljava/util/List;", "Z") &&
                reference.returnType == "V"
        }
        assertTrue(recyclerIndex > 0, "recycler call not found")

        val addMenuEntry = instructions[recyclerIndex - 1] as ReferenceInstruction
        assertEquals(Opcode.INVOKE_STATIC, addMenuEntry.opcode)
        val addMenuEntryReference = addMenuEntry.reference as MethodReference
        assertEquals(ADS_SETTINGS_CLASS, addMenuEntryReference.definingClass)
        assertEquals("addMenuEntry", addMenuEntryReference.name)
        assertEquals(listOf("Ljava/util/List;"), addMenuEntryReference.parameterTypes.map { it.toString() })

        val listRegister = (instructions[recyclerIndex] as FiveRegisterInstruction).registerD
        assertEquals(listRegister, (addMenuEntry as FiveRegisterInstruction).registerC)

        assertTrue(context.classDefs.any { it.type == ADS_SETTINGS_CLASS })
        assertTrue(context.classDefs.any { it.type == "Lcom/vk/video/screens/profile/ProfileMenuFragment;" })
    }
}
