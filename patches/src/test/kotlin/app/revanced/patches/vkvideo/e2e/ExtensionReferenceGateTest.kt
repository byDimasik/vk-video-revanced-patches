package app.revanced.patches.vkvideo.e2e

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patches.vkvideo.settings.assertAssignable
import app.revanced.patches.vkvideo.settings.auditAssignability
import app.revanced.patches.vkvideo.settings.auditExtensionClass
import app.revanced.patches.vkvideo.settings.compileExtensionClass
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
import app.revanced.patches.vkvideo.sponsorskip.autoPlayApi
import app.revanced.patches.vkvideo.sponsorskip.resolveAutoPlayApi
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipPatch
import app.revanced.patches.vkvideo.sponsorskip.settings.DIALOG_CLICK_CLASS
import app.revanced.patches.vkvideo.sponsorskip.settings.resolveDialogApi
import app.revanced.patches.vkvideo.sponsorskip.settings.settingsSources
import app.revanced.patches.vkvideo.settings.extractToolbarChrome
import app.revanced.patches.vkvideo.settings.resolveNavOpener
import app.revanced.patches.vkvideo.settings.switchListenerMethodFingerprint
import app.revanced.patches.vkvideo.settings.toolbarChromeFingerprint
import app.revanced.patches.vkvideo.settings.navOpenerFingerprint
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.applyPatches
import app.revanced.patches.vkvideo.util.PatchTestUtil.initializeCache
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * The build-time gate that `compileExtensionClass` puts in front of every
 * injected class (#78): a reference the injected dex makes must resolve against
 * the *current* dexes, not merely compile.
 */
class ExtensionReferenceGateTest {

    private fun findApkFile(): File? {
        val candidateDirs = listOf(File(".cache"), File("/app/.cache"), File("../.cache"))
        return candidateDirs.asSequence()
            .filter { it.isDirectory }
            .flatMap { dir ->
                dir.listFiles { f -> f.isFile && f.name.matches(Regex("vkvideo-\\d+\\.\\d+(-\\d+)?\\.apk")) }?.asSequence()
                    ?: emptySequence()
            }
            .maxByOrNull { it.lastModified() }
    }

    private fun loadContext(): Pair<BytecodePatchContext, Unit> {
        val apkFile = findApkFile()
        val isStrictE2e = System.getProperty("vkvideo.e2e") == "true" || System.getenv("VKVIDEO_E2E") == "true"
        if (isStrictE2e) {
            check(apkFile != null && apkFile.exists()) { "VK Video target APK snapshot (vkvideo-*.apk) not found in .cache" }
        } else {
            assumeTrue(apkFile != null && apkFile.exists(), "Skipping ExtensionReferenceGateTest: target APK not in .cache")
        }
        val patchedDir = Files.createTempDirectory("patched-gate-test").toFile().apply { deleteOnExit() }
        val constructor = BytecodePatchContext::class.java.getDeclaredConstructor(File::class.java, File::class.java)
        constructor.isAccessible = true
        val context = constructor.newInstance(apkFile, patchedDir)
        context.initializeCache()
        return context to Unit
    }

    /**
     * A class carrying the stale 1.170 shape must come out of the gate calling
     * the 1.171 shape: `VideoFile.P0()` -> `O0()`.
     */
    @Test
    fun testStaleReferenceIsCorrected() {
        val (context, _) = loadContext()
        val source = """
            .class public final Lapp/revanced/extension/probe/Probe;
            .super Ljava/lang/Object;

            .method public static idOf(Lcom/vk/dto/common/VideoFile;)J
                .registers 3
                invoke-interface {p0}, Lcom/vk/dto/common/VideoFile;->P0()Lcom/vk/dto/common/id/UserId;
                move-result-object v0
                invoke-virtual {v0}, Lcom/vk/dto/common/id/UserId;->c()J
                move-result-wide v0
                return-wide v0
            .end method
        """.trimIndent()

        val probe = compileExtensionClass(context, "Lapp/revanced/extension/probe/Probe;", source)
        val calls = probe.methods.flatMap { it.implementation?.instructions?.toList().orEmpty() }
            .mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }
            .filter { it.definingClass == "Lcom/vk/dto/common/VideoFile;" }
            .map { it.name }
        assertTrue(calls.contains("O0"), "the gate must re-point the stale UserId getter, saw $calls")
        assertTrue(calls.none { it == "P0" }, "stale P0 survived the gate")
        assertTrue(auditExtensionClass(context, probe).all { it.resolved }, "corrected probe must audit clean")
    }

    /**
     * An array of a drifted class must be corrected as an array, not flattened to
     * the element type: the prefix survives and `check-cast`/`new-array` stay valid.
     */
    @Test
    fun testArrayReferenceKeepsItsPrefix() {
        val (context, _) = loadContext()
        // Array-only fixture: no scalar occurrence of the stale type may exist, or the
        // scalar return-type row would fix the array text too and the test would pass
        // even without the prefix being preserved.
        val source = """
            .class public final Lapp/revanced/extension/probe/ArrayProbe;
            .super Ljava/lang/Object;

            .method public static count([Lq72/VideoFile;)I
                .registers 2
                array-length v0, p0
                return v0
            .end method
        """.trimIndent()

        val probe = compileExtensionClass(context, "Lapp/revanced/extension/probe/ArrayProbe;", source)
        val parameter = probe.methods.single().parameterTypes.first().toString()
        assertEquals("[Lcom/vk/dto/common/VideoFile;", parameter, "array parameter must stay an array of the corrected class")
        val rows = auditExtensionClass(context, probe)
        assertTrue(rows.all { it.resolved }, "corrected probe must audit clean")
        val typeRow = rows.single { it.reference.endsWith("VideoFile;") }
        assertEquals("[Lcom/vk/dto/common/VideoFile;", typeRow.reference, "the corrected descriptor must itself still be an array")
    }

/** An unresolvable reference must fail the build, not ship. */
    @Test
    fun testUnresolvableReferenceFails() {
        val (context, _) = loadContext()
        val source = """
            .class public final Lapp/revanced/extension/probe/Broken;
            .super Ljava/lang/Object;

            .method public static idOf(Lcom/vk/dto/common/Nope;)I
                .registers 2
                invoke-virtual {p0}, Lcom/vk/dto/common/Nope;->zzz(IIIII)I
                move-result v0
                return v0
            .end method
        """.trimIndent()

        val failure = assertThrows(IllegalStateException::class.java) {
            compileExtensionClass(context, "Lapp/revanced/extension/probe/Broken;", source)
        }
        assertTrue(failure.message!!.contains("do not resolve"), failure.message)
        assertTrue(failure.message!!.contains("Nope"), failure.message)
    }

    /**
     * Existence is not assignability (#83): the listener here implements only
     * `DialogInterface$OnClickListener`, which resolves fine, but the app's
     * preference dialog hands it to `androidx.preference.c$a` and `check-cast`s
     * it. That must fail the build.
     */
    @Test
    fun testUnassignableCastTargetFails() {
        val (context, _) = loadContext()
        val loose = """
            .class public final Lapp/revanced/extension/probe/Loose;
            .super Ljava/lang/Object;
            .implements Landroid/content/DialogInterface${'$'}OnClickListener;

            .method public constructor <init>()V
                .registers 1
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public onClick(Landroid/content/DialogInterface;I)V
                .registers 3
                return-void
            .end method

            .method public static hand()V
                .registers 2
                new-instance v0, Lapp/revanced/extension/probe/Loose;
                invoke-direct {v0}, Lapp/revanced/extension/probe/Loose;-><init>()V
                check-cast v0, Landroidx/preference/c${'$'}a;
                return-void
            .end method
        """.trimIndent()

        val probe = compileExtensionClass(context, "Lapp/revanced/extension/probe/Loose;", loose)
        val violations = auditAssignability(context, probe)
        assertTrue(violations.any { it.target == "Landroidx/preference/c${'$'}a;" }, "the impossible cast went unreported: $violations")
        val failure = assertThrows(IllegalStateException::class.java) { assertAssignable(context, listOf(probe)) }
        assertTrue(failure.message!!.contains("cannot accept"), failure.message)
    }

    /** The same listener with the narrowed superclass is accepted. */
    @Test
    fun testAssignableCastTargetPasses() {
        val (context, _) = loadContext()
        val tight = """
            .class public final Lapp/revanced/extension/probe/Tight;
            .super Landroidx/preference/c${'$'}a;

            .method public constructor <init>(Landroidx/preference/c;)V
                .registers 2
                invoke-direct {p0, p1}, Landroidx/preference/c${'$'}a;-><init>(Landroidx/preference/c;)V
                return-void
            .end method

            .method public static hand()V
                .registers 2
                new-instance v0, Lapp/revanced/extension/probe/Tight;
                invoke-direct {v0}, Lapp/revanced/extension/probe/Tight;-><init>(Landroidx/preference/c;)V
                check-cast v0, Landroidx/preference/c${'$'}a;
                return-void
            .end method
        """.trimIndent()

        val probe = compileExtensionClass(context, "Lapp/revanced/extension/probe/Tight;", tight)
        assertEquals(emptyList<String>(), auditAssignability(context, probe).map { it.toString() })
        assertAssignable(context, listOf(probe))
    }

    /**
     * Every app reference in every compiled extension dex resolves, and the audit
     * actually saw the references (an empty walk would pass vacuously).
     */
    @Test
    fun testCompiledExtensionDexesAuditClean() {
        val (context, _) = loadContext()
        autoPlayApi = resolveAutoPlayApi(context.classDefs.toList())
        applyPatches(context, revancedSettingsPatch, sponsorSkipPatch)

        val injected = context.classDefs.filter { it.type.startsWith(INJECTED_PREFIX) }
        assertTrue(injected.size > 10, "expected the patches to inject classes, got ${injected.size}")
        val rows = injected.flatMap { auditExtensionClass(context, it) }
        val unresolved = rows.filter { !it.resolved }
        assertEquals(emptyList<String>(), unresolved.map { it.reference }, "unresolved extension references")
        assertTrue(rows.size > 50, "the audit must cover the extension dexes, only ${rows.size} reference(s)")
        assertTrue(rows.any { it.reference.startsWith("Lcom/vk/") }, "no com.vk reference audited")

        // And the #83 shape itself: the popup listener must be an instance of the
        // type the builder's narrowed items setter declares.
        val dialog = resolveDialogApi(context.classDefs.toList())
        val sources = settingsSources(
            extractToolbarChrome(context.classDefs.first { it.type == toolbarChromeFingerprint.getMethod(context).definingClass }),
            resolveNavOpener(navOpenerFingerprint.getMethod(context), context.classDefs.toList()),
            switchListenerMethodFingerprint.getMethod(context).name,
            dialog,
        )
        val click = compileExtensionClass(context, DIALOG_CLICK_CLASS, sources.getValue(DIALOG_CLICK_CLASS))
        assertEquals("Landroidx/preference/c\$a;", click.superclass, "the popup listener must extend the narrowed listener type")
        assertEquals(emptyList<String>(), auditAssignability(context, click).map { it.toString() })
    }
}
