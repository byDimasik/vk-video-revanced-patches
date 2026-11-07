package app.revanced.patches.vkvideo.e2e

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patches.vkvideo.ad.shelf.conditionalTopShelfPatch
import app.revanced.patches.vkvideo.ad.shelf.musicTopshelfFragmentFingerprint
import app.revanced.patches.vkvideo.ad.shelf.videoTopshelfBlockFingerprint
import app.revanced.patches.vkvideo.settings.ADS_SETTINGS_CLASS
import app.revanced.patches.vkvideo.util.PatchTestUtil.applyPatches
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.initializeCache
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Real-APK structural coverage for the runtime top-shelf conditional: proves the
 * anchors and resolved references exist in the shipped 1.170 dexes. ART behavior
 * (the actual collapse, no VerifyError) is the device gate.
 */
class RealApkTopShelfConditionalTest {

    private fun findApkFile(): File? {
        val candidateDirs = listOf(File(".cache"), File("/app/.cache"), File("../.cache"))
        return candidateDirs.asSequence()
            .filter { it.isDirectory }
            .flatMap { it.listFiles { f -> f.isFile && f.name.matches(Regex("vkvideo-\\d+\\.\\d+(-\\d+)?\\.apk")) }?.asSequence() ?: emptySequence() }
            .maxByOrNull { it.lastModified() }
    }

    @Test
    fun testTopShelfConditionalOnRealApk() {
        val apkFile = findApkFile()
        val isStrictE2e = System.getProperty("vkvideo.e2e") == "true" || System.getenv("VKVIDEO_E2E") == "true"
        if (isStrictE2e) {
            check(apkFile != null && apkFile.exists()) { "VK Video target APK snapshot not found in .cache" }
        } else {
            assumeTrue(apkFile != null && apkFile.exists(), "Skipping: APK snapshot not found in .cache")
        }

        val realApk = apkFile!!
        val patchedDir = Files.createTempDirectory("patched-topshelf-test").toFile().apply { deleteOnExit() }
        val context = BytecodePatchContext::class.java
            .getDeclaredConstructor(File::class.java, File::class.java)
            .apply { isAccessible = true }
            .newInstance(realApk, patchedDir) as BytecodePatchContext
        val dexContainer = DexFileFactory.loadDexContainer(realApk, Opcodes.getDefault())
        for (dexEntryName in dexContainer.dexEntryNames) {
            context.classDefs.addAll(dexContainer.getEntry(dexEntryName)!!.dexFile.classes)
        }
        context.initializeCache()

        // 1.170 pinned layout ids (res/values/public.xml: catalog_video_topshelf,
        // catalog_video_music_topshelf).
        val expectedLayoutIds = mapOf(
            videoTopshelfBlockFingerprint to 0x7f0e029c,
            musicTopshelfFragmentFingerprint to 0x7f0e027e,
        )

        for ((fingerprint, expectedLayoutId) in expectedLayoutIds) {
            assertNotNull(fingerprint.matchOrNull(context), "$fingerprint must match the real APK")
            val method = fingerprint.getMethod(context)
            val instructions = method.getInstructions()

            val inflateIndex = instructions.indexOfFirst { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference
                instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                    reference is MethodReference &&
                    reference.name == "inflate" &&
                    reference.parameterTypes == listOf("I", "Landroid/view/ViewGroup;", "Z")
            }
            assertTrue(inflateIndex >= 0)
            val layoutReg = (instructions[inflateIndex] as FiveRegisterInstruction).registerD
            val layoutConst = (inflateIndex downTo 0).firstNotNullOfOrNull { i ->
                val insn = instructions[i]
                val reg = (insn as? OneRegisterInstruction)?.registerA
                if (insn is NarrowLiteralInstruction &&
                    insn.opcode in setOf(Opcode.CONST, Opcode.CONST_4, Opcode.CONST_16) &&
                    reg == layoutReg
                ) {
                    insn.narrowLiteral
                } else {
                    null
                }
            }
            assertEquals(expectedLayoutId, layoutConst, "resolved layout id must equal the public.xml id")
        }

        applyPatches(context, conditionalTopShelfPatch)

        for ((fingerprint, _) in expectedLayoutIds) {
            val method = fingerprint.getMethod(context)
            val insns = method.getInstructions()

            val guardIndex = insns.indexOfFirst { insn ->
                insn.opcode == Opcode.INVOKE_STATIC &&
                    ((insn as? ReferenceInstruction)?.reference as? MethodReference)?.name == "isTopShelfAdsEnabled"
            }
            assertTrue(guardIndex >= 0, "${method.definingClass}->${method.name}: conditional not appended")

            assertEquals(Opcode.MOVE_RESULT, insns[guardIndex + 1].opcode)
            // ON (blocking enabled) falls through to the collapse; OFF skips it.
            assertEquals(Opcode.IF_EQZ, insns[guardIndex + 2].opcode)
            assertEquals(Opcode.INVOKE_STATIC_RANGE, insns[guardIndex + 3].opcode, "collapse invoke must be the range form")
            val collapseRef = (insns[guardIndex + 3] as ReferenceInstruction).reference as MethodReference
            assertEquals(ADS_SETTINGS_CLASS, collapseRef.definingClass)
            assertEquals("collapseTopShelfView", collapseRef.name)

            // The branch must land past the collapse, on the original code.
            val branch = insns[guardIndex + 2] as OffsetInstruction
            val targetAddress = insns[guardIndex + 2].location.codeAddress + branch.codeOffset
            val targetIndex = insns.indexOfFirst { it.location.codeAddress == targetAddress }
            assertEquals(guardIndex + 4, targetIndex, "branch must skip exactly the collapse invoke")

            // No non-range 35c invoke may carry a register operand >= 16.
            for (insn in insns) {
                if (insn is FiveRegisterInstruction) {
                    val regs = listOf(insn.registerC, insn.registerD, insn.registerE, insn.registerF, insn.registerG)
                        .take(insn.registerCount)
                    assertTrue(regs.all { it <= 15 }, "35c ${insn.opcode} with register >= 16 in ${method.definingClass}")
                }
            }
        }

        assertTrue(context.classDefs.any { it.type == ADS_SETTINGS_CLASS })
    }
}
