package app.revanced.patches.vkvideo.e2e

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patches.vkvideo.sponsorskip.SEEK_BAR_ID
import app.revanced.patches.vkvideo.sponsorskip.SEEK_BAR_VIEW_ID
import app.revanced.patches.vkvideo.sponsorskip.autoPlayApi
import app.revanced.patches.vkvideo.sponsorskip.resolveAutoPlayApi
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipSectionListFingerprint
import app.revanced.patches.vkvideo.sponsorskip.SKIP_ENGINE_CLASS
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipSectionDrawableFingerprint
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipPatch
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipReleaseFingerprint
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipSeekFingerprint
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipVideoHandoffFingerprint
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipHolderFingerprint
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipViewAttachedFingerprint
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipViewSetupFingerprint
import app.revanced.patches.vkvideo.util.PatchTestUtil.applyPatches
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.initializeCache
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.AccessFlags
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
        // The controller interface is resolved by the patch; matching its fingerprints
        // directly here would otherwise depend on another test having run first.
        autoPlayApi = resolveAutoPlayApi(context.classDefs.toList())

        assertNotNull(sponsorSkipVideoHandoffFingerprint.matchOrNull(context), "video handoff not found")
        assertNotNull(sponsorSkipViewAttachedFingerprint.matchOrNull(context), "view attach not found")
        assertNotNull(sponsorSkipViewSetupFingerprint.matchOrNull(context), "view setup not found")
        assertNotNull(sponsorSkipHolderFingerprint.matchOrNull(context), "preview attach not found")
        assertNotNull(sponsorSkipReleaseFingerprint.matchOrNull(context), "release not found")

        val seek = sponsorSkipSeekFingerprint.matchOrNull(context)
        assertNotNull(seek, "seek entry not found")
        assertEquals(1, seek!!.method.implementation!!.instructions.count { it.opcode == Opcode.RETURN_VOID }, "the seek entry gained a return")

        applyPatches(context, sponsorSkipPatch)

        assertHook(sponsorSkipVideoHandoffFingerprint.getMethod(context), "onVideoChanged", 3, 4)
        assertHook(sponsorSkipViewAttachedFingerprint.getMethod(context), "onViewAttached", 2, 3)
        assertHook(sponsorSkipViewSetupFingerprint.getMethod(context), "onViewAttached", 1, 2)
        assertHook(sponsorSkipHolderFingerprint.getMethod(context), "onViewAttached", 3, 4)
        assertHook(sponsorSkipReleaseFingerprint.getMethod(context), "onRelease", 2, null)

        val seekInstructions = seek.method.getInstructions()
        val seekAt = seekInstructions.indexOfFirst { it.opcode == Opcode.RETURN_VOID } - 1
        val seekHook = seekInstructions[seekAt] as FiveRegisterInstruction
        assertEquals(Opcode.INVOKE_STATIC, seekHook.opcode, "the seek hook is not before the return")
        assertEquals(3, seekHook.registerC)
        assertEquals(4, seekHook.registerD)
        assertEquals(5, seekHook.registerE)
        assertEquals(Opcode.RETURN_VOID, seekInstructions[seekAt + 1].opcode)
    }

    @Test
    fun testSeekbarSurfaceOnRealApk() {
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

        val patchedDir = Files.createTempDirectory("patched-seekbar-test").toFile().apply { deleteOnExit() }
        val constructor = BytecodePatchContext::class.java.getDeclaredConstructor(File::class.java, File::class.java)
        constructor.isAccessible = true
        val context = constructor.newInstance(apkFile, patchedDir)
        context.initializeCache()

        val bar = context.classDefs.firstOrNull { it.type == "Lone/video/controls/view/seekbar/OneVideoSeekBarView;" }
        assertNotNull(bar, "OneVideoSeekBarView not found")
        assertEquals("Landroid/widget/FrameLayout;", bar!!.superclass, "the bar host is no longer a FrameLayout")

        val composite = context.classDefs.firstOrNull { it.type == "Lone/video/controls/view/seekbar/CompositeSeekBarView;" }
        assertNotNull(composite, "CompositeSeekBarView not found")
        assertEquals("Landroid/widget/FrameLayout;", composite!!.superclass)
        assertNotNull(
            composite.methods.firstOrNull {
                it.name == "getProgressSeekBarView" &&
                    it.returnType == "Lone/video/controls/view/seekbar/ProgressSeekBarView;"
            },
            "getProgressSeekBarView no longer returns the progress bar",
        )

        val seekBar = context.classDefs.firstOrNull { it.type == "Landroidx/appcompat/widget/AppCompatSeekBar;" }
        assertNotNull(seekBar, "AppCompatSeekBar not found")
        val onDraw = seekBar!!.methods.firstOrNull { it.name == "onDraw" }
        assertNotNull(onDraw, "AppCompatSeekBar.onDraw not found")
        assertTrue(
            onDraw!!.accessFlags and AccessFlags.FINAL.value != 0,
            "AppCompatSeekBar.onDraw is no longer final, a subclass override would dispatch",
        )

        java.util.zip.ZipFile(apkFile).use { zip ->
            val entry = zip.getEntry("resources.arsc")
            assertNotNull(entry, "resources.arsc missing")
            val names = zip.getInputStream(entry).readBytes().toString(Charsets.ISO_8859_1)
            assertTrue(names.contains("seek_bar_view"), "id/seek_bar_view is gone from the resource table")
            assertTrue(names.contains("seek_bar"), "id/seek_bar is gone from the resource table")

            val layouts = zip.entries().asSequence()
                .filter { it.name.startsWith("res/layout/") && it.name.endsWith(".xml") }
                .map { zip.getInputStream(it).readBytes() }
                .toList()
            listOf(SEEK_BAR_VIEW_ID, SEEK_BAR_ID).forEach { id ->
                assertTrue(layouts.any { containsId(it, id) }, "id ${Integer.toHexString(id)} is not used by any layout")
            }
            assertEquals(SEEK_BAR_VIEW_ID ushr 16, SEEK_BAR_ID ushr 16, "the two ids drifted to different types")
            assertTrue(SEEK_BAR_VIEW_ID != SEEK_BAR_ID)
        }
    }

    @Test
    fun testSectionHostsOnRealApk() {
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

        val patchedDir = Files.createTempDirectory("patched-sections-test").toFile().apply { deleteOnExit() }
        val constructor = BytecodePatchContext::class.java.getDeclaredConstructor(File::class.java, File::class.java)
        constructor.isAccessible = true
        val context = constructor.newInstance(apkFile, patchedDir)
        context.initializeCache()

        listOf("Ldp7/c;", "Lnp7/c;").forEach { descriptor ->
            val drawable = context.classDefs.firstOrNull { it.type == descriptor }
            assertNotNull(drawable, "the section drawable $descriptor is gone")
            assertEquals("Landroid/graphics/drawable/LayerDrawable;", drawable!!.superclass, "$descriptor is no longer a layer list")
            val ids = drawable.methods
                .first { it.name == "<init>" }
                .implementation!!.instructions
                .filter { it.opcode == Opcode.CONST || it.opcode == Opcode.CONST_HIGH16 }
                .mapNotNull { (it as? com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction)?.narrowLiteral }
            assertTrue(ids.contains(0x1020000), "$descriptor lost layer id 0x1020000")
        }

        val pageDrawable = context.classDefs.firstOrNull { it.type == "Lnp7/c;" }
        assertNotNull(pageDrawable, "the page section drawable is gone")
        assertNotNull(
            pageDrawable!!.methods.firstOrNull { it.name == "findDrawableByLayerId" },
            "np7/c no longer overrides findDrawableByLayerId, the id lookup shortcut is back",
        )
        val compositeDrawable = context.classDefs.firstOrNull { it.type == "Ldp7/c;" }
        assertNotNull(compositeDrawable, "the composite section drawable is gone")
        assertTrue(
            compositeDrawable!!.methods.none { it.name == "findDrawableByLayerId" },
            "dp7/c gained a findDrawableByLayerId override",
        )

        listOf("Lnp7/c;", "Ldp7/c;").forEach { descriptor ->
            val constructor = context.classDefs.first { it.type == descriptor }.methods.first { it.name == "<init>" }
            val instructions = constructor.implementation!!.instructions.toList()
            assertEquals(
                2,
                instructions.count {
                    it.opcode == Opcode.NEW_INSTANCE &&
                        (it as ReferenceInstruction).reference.toString() == "Landroid/graphics/drawable/ClipDrawable;"
                },
                "$descriptor: the clip layers moved",
            )
            val assigned = layerIds(instructions)
            assertEquals(
                listOf(0 to 0x1020000, 1 to 0x102000f, 2 to 0x102000d),
                assigned,
                "$descriptor: the fill layer is no longer index 2 with id 0x102000d",
            )
        }

        val roundDrawable = context.classDefs.first { it.type == "Lnp7/b;" }
        listOf("d" to "Ljava/util/List;", "a" to "F", "b" to "F", "e" to "I", "f" to "F").forEach { (name, type) ->
            assertTrue(
                roundDrawable.fields.any { it.name == name && it.type == type },
                "np7/b lost the reflected field $name:$type",
            )
        }
        val rectDrawable = context.classDefs.first { it.type == "Ldp7/b;" }
        listOf("b" to "Ljava/util/List;", "a" to "F").forEach { (name, type) ->
            assertTrue(rectDrawable.fields.any { it.name == name && it.type == type }, "dp7/b lost the reflected field $name:$type")
        }
        val pair = context.classDefs.first { it.type == "Lkotlin/Pair;" }
        assertTrue(pair.fields.any { it.name == "first" } && pair.fields.any { it.name == "second" }, "kotlin/Pair lost first/second")

        val page = context.classDefs.firstOrNull { it.type == "Lone/video/controls/views/seek/a;" }
        assertNotNull(page, "the page-player seek binder is gone")
        assertTrue(
            page!!.fields.any { it.name == "a" && it.type == "Landroidx/appcompat/widget/AppCompatSeekBar;" },
            "the page-player bar field moved",
        )

        assertNotNull(sponsorSkipSectionDrawableFingerprint.matchOrNull(context), "composite section install not found")
        assertNotNull(sponsorSkipSectionListFingerprint.matchOrNull(context), "page section install not found")

        applyPatches(context, sponsorSkipPatch)

        val composite = sponsorSkipSectionDrawableFingerprint.getMethod(context)
        val compositeInstructions = composite.getInstructions()
        val compositeReturns = compositeInstructions.count { it.opcode == Opcode.RETURN_VOID }
        assertEquals(1, compositeReturns, "the composite setter gained a second return")
        val compositeHook = compositeInstructions.indexOfFirst {
            (it as? ReferenceInstruction)?.reference?.toString()?.contains("/SeekbarSections;->apply") == true
        }
        assertTrue(
            compositeHook > 0 && compositeInstructions[compositeHook + 1].opcode == Opcode.RETURN_VOID,
            "the composite hook is not immediately before the return: hook=$compositeHook size=${compositeInstructions.size} " +
                "seq=${compositeInstructions.map { it.opcode }} full=${compositeInstructions.joinToString(" | ") { ins -> ins.opcode.toString() + ":" + ((ins as? ReferenceInstruction)?.reference?.toString() ?: "") }}",
        )
        assertEquals(Opcode.INVOKE_STATIC, compositeInstructions[compositeHook].opcode)
        val compositeRegisters = compositeInstructions[compositeHook] as FiveRegisterInstruction
        assertEquals(1, compositeRegisters.registerC)
        assertEquals(2, compositeRegisters.registerD)

        val pageMethod = sponsorSkipSectionListFingerprint.getMethod(context)
        val pageInstructions = pageMethod.getInstructions()
        val pageReturns = pageInstructions.count { it.opcode == Opcode.RETURN_VOID }
        assertEquals(2, pageReturns, "the page-player setter changed shape")
        val pageHook = pageInstructions.indexOfFirst {
            (it as? ReferenceInstruction)?.reference?.toString()?.contains("/SeekbarSections;->apply") == true
        }
        assertTrue(pageHook > 0 && pageInstructions[pageHook + 1].opcode == Opcode.RETURN_VOID, "the page-player hook is not immediately before its return")
        assertEquals(Opcode.INVOKE_STATIC, pageInstructions[pageHook].opcode)
        assertEquals(Opcode.MOVE_RESULT_OBJECT, pageInstructions[pageHook - 1].opcode)
        assertEquals(
            "Landroid/widget/ProgressBar;->getProgressDrawable()Landroid/graphics/drawable/Drawable;",
            (pageInstructions[pageHook - 2] as ReferenceInstruction).reference.toString(),
        )
        assertTrue(
            pageInstructions[pageHook - 3].opcode == Opcode.IGET_OBJECT &&
                (pageInstructions[pageHook - 3] as ReferenceInstruction).reference.toString() ==
                "Lone/video/controls/views/seek/a;->a:Landroidx/appcompat/widget/AppCompatSeekBar;",
            "the page hook does not read the bar field",
        )
    }

    /** `setId(index, id)` pairs of a layer constructor, resolved from the constant loads into their registers. */
    private fun layerIds(instructions: List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>): List<Pair<Int, Int>> {
        val constants = mutableMapOf<Int, Int>()
        val assigned = mutableListOf<Pair<Int, Int>>()
        instructions.forEach { instruction ->
            if (instruction.opcode in setOf(Opcode.CONST, Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST_HIGH16)) {
                val literal = instruction as com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
                constants[(literal as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction).registerA] = literal.narrowLiteral
            }
            if ((instruction as? ReferenceInstruction)?.reference?.toString()?.endsWith("LayerDrawable;->setId(II)V") != true) return@forEach
            val (indexRegister, idRegister) = when (instruction) {
                is com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction ->
                    instruction.startRegister + 1 to instruction.startRegister + 2
                is FiveRegisterInstruction -> instruction.registerD to instruction.registerE
                else -> error("unexpected setId form")
            }
            assigned += constants.getValue(indexRegister) to constants.getValue(idRegister)
        }
        return assigned
    }

    private fun containsId(bytes: ByteArray, id: Int): Boolean {
        val needle = byteArrayOf(id.toByte(), (id ushr 8).toByte(), (id ushr 16).toByte(), (id ushr 24).toByte())
        return (0..bytes.size - needle.size).any { offset ->
            needle.indices.all { bytes[offset + it] == needle[it] }
        }
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
