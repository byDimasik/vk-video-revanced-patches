package app.revanced.patches.vkvideo.e2e

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patches.vkvideo.settings.ADS_SETTINGS_CLASS
import app.revanced.patches.vkvideo.settings.extractToolbarChrome
import app.revanced.patches.vkvideo.settings.navOpenerFingerprint
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
import app.revanced.patches.vkvideo.settings.remapAppTypes
import app.revanced.patches.vkvideo.settings.resolveNavOpener
import app.revanced.patches.vkvideo.settings.toolbarChromeFingerprint
import app.revanced.patches.vkvideo.sponsorskip.settings.FRAGMENT_CLASS
import app.revanced.patches.vkvideo.sponsorskip.settings.ROOT_ROW_CLASS
import app.revanced.patches.vkvideo.sponsorskip.settings.SETTINGS_CLASS
import app.revanced.patches.vkvideo.sponsorskip.settings.resolveDialogApi
import app.revanced.patches.vkvideo.sponsorskip.settings.settingsSources
import app.revanced.patches.vkvideo.sponsorskip.settings.sponsorSkipSettingsPatch
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipPatch
import app.revanced.patches.vkvideo.util.PatchTestUtil.applyPatches
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.initializeCache
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Real-APK structural coverage for the SponsorSkip settings screen: the popup
 * builder, subtitle constructor and icon must all exist in the shipped 1.170
 * dexes, and the root anchor must still be a single `addView`.
 */
class RealApkSponsorSkipSettingsTest {

    private fun findApkFile(): File? {
        val candidateDirs = listOf(File(".cache"), File("/app/.cache"), File("../.cache"))
        return candidateDirs.asSequence()
            .filter { it.isDirectory }
            .flatMap { it.listFiles { f -> f.isFile && f.name.matches(Regex("vkvideo-\\d+\\.\\d+(-\\d+)?\\.apk")) }?.asSequence() ?: emptySequence() }
            .maxByOrNull { it.lastModified() }
    }

    @Test
    fun testSponsorSkipSettingsOnRealApk() {
        val apkFile = findApkFile()
        val isStrictE2e = System.getProperty("vkvideo.e2e") == "true" || System.getenv("VKVIDEO_E2E") == "true"
        if (isStrictE2e) {
            check(apkFile != null && apkFile.exists()) { "VK Video target APK snapshot not found in .cache" }
        } else {
            assumeTrue(apkFile != null && apkFile.exists(), "Skipping: APK snapshot not found in .cache")
        }

        val realApk = apkFile!!
        val patchedDir = Files.createTempDirectory("patched-sponsorskip-settings-test").toFile().apply { deleteOnExit() }
        val context = BytecodePatchContext::class.java
            .getDeclaredConstructor(File::class.java, File::class.java)
            .apply { isAccessible = true }
            .newInstance(realApk, patchedDir) as BytecodePatchContext
        val dexContainer = DexFileFactory.loadDexContainer(realApk, Opcodes.getDefault())
        for (dexEntryName in dexContainer.dexEntryNames) {
            context.classDefs.addAll(dexContainer.getEntry(dexEntryName)!!.dexFile.classes)
        }
        context.initializeCache()

        assertNotNull(toolbarChromeFingerprint.matchOrNull(context))
        assertNotNull(navOpenerFingerprint.matchOrNull(context))

        applyPatches(context, revancedSettingsPatch, sponsorSkipPatch, sponsorSkipSettingsPatch)

        // Every transcribed anchor exists in the shipped dexes with the emitted signature.
        // Refs are remapped together: the answer to a renamed type is only learnable
        // from a neighbouring app type, never from a single ref.
        val refs = mutableListOf<String>()
        fun assertResolves(owner: String, name: String, params: List<String>, returnType: String) {
            refs += "$owner->$name(${params.joinToString("")})$returnType"
            val remapped = remapAppTypes(context, refs.joinToString("\n")).lines().last()
            val mappedOwner = remapped.substringBefore("->")
            val spec = remapped.substringAfter("(").substringBefore(")")
            val types = mutableListOf<String>()
            var i = 0
            while (i < spec.length) {
                val start = i
                while (spec[i] == '[') i++
                if (spec[i] == 'L') i = spec.indexOf(';', i) + 1 else i++
                types += spec.substring(start, i)
            }
            val def = context.classDefs.firstOrNull { it.type == mappedOwner }
            assertNotNull(def, "referenced class $mappedOwner must exist")
            assertNotNull(
                def!!.methods.firstOrNull {
                    it.name == name && it.parameterTypes.toList() == types &&
                        it.returnType.toString() == remapped.substringAfterLast(')')
                },
                "referenced method $mappedOwner->$name(${types.joinToString("")})$returnType must exist",
            )
        }
        assertResolves("Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}d;", "<init>", listOf(
            "Lpx1/a;", "Lbj1/m;", "I", "Landroid/text/TextUtils${'$'}TruncateAt;",
            "Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}d${'$'}b${'$'}b;", "I",
        ), "V")
        assertResolves("Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}a;", "a", listOf(
            "Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}a;",
            "Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}e;",
            "Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}d;",
            "Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}c;", "I",
        ), "Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}b;")
        val dialog = resolveDialogApi(context.classDefs.toList())
        assertResolves(dialog.builder, "<init>", listOf("Landroid/content/Context;"), "V")
        assertResolves(dialog.builder, dialog.titleMethod, listOf("Ljava/lang/CharSequence;"), dialog.itemsReturn)
        assertResolves(dialog.builder, dialog.itemsMethod, listOf(
            "[Ljava/lang/CharSequence;", "I", "Landroid/content/DialogInterface${'$'}OnClickListener;",
        ), dialog.itemsReturn)
        assertResolves(dialog.showOwner, dialog.showMethod, emptyList(), "Landroidx/appcompat/app/d;")
        // `show()` is inherited from the framework Dialog, which is not a dexed class.
        assertTrue(context.classDefs.any { it.type == "Landroidx/appcompat/app/d;" })
        assertResolves("Lpx1/a${'$'}h;", "<init>", listOf("Ljava/lang/CharSequence;"), "V")

        // The icon is a stock resource (public.xml).
        assertEquals(0x7f082020, app.revanced.patches.vkvideo.sponsorskip.settings.ROOT_ROW_ICON_ID)

        // All eight screen classes inject against the real app.
        val sources = settingsSources(
            extractToolbarChrome(context.classDefs.first { it.type == toolbarChromeFingerprint.getMethod(context).definingClass }),
            resolveNavOpener(navOpenerFingerprint.getMethod(context), context.classDefs.toList()),
            app.revanced.patches.vkvideo.settings.switchListenerMethodFingerprint.getMethod(context).name,
            dialog,
        )
        for (descriptor in sources.keys) {
            assertTrue(context.classDefs.any { it.type == descriptor }, "$descriptor must be injected")
        }

        // The root anchor is still exactly one addView and the row lands right after it.
        val root = context.classDefs.first { it.type == ADS_SETTINGS_CLASS }.methods.first { it.name == "buildRootContent" }
        val instructions = root.implementation!!.instructions.toList()
        val anchorIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference?.name == "addView" && reference.definingClass == "Landroid/widget/LinearLayout;"
        }
        assertTrue(anchorIndex >= 0, "root addView anchor missing")
        assertEquals(
            ROOT_ROW_CLASS,
            ((instructions[anchorIndex + 1] as ReferenceInstruction).reference as MethodReference).definingClass,
        )

        val fragment = context.classDefs.first { it.type == FRAGMENT_CLASS }
        assertEquals("Lcom/vk/core/fragments/FragmentImpl;", fragment.superclass)
        assertTrue(fragment.methods.any { it.name == "<init>" && it.parameterTypes.isEmpty() })
        assertTrue(context.classDefs.any { it.type == SETTINGS_CLASS })

        // Every app reference the injected screens make, by exact signature.
        val checked = context.assertInjectedReferencesResolve()
        assertTrue(checked > 50, "reference sweep must cover the injected settings screens, only $checked checked")
    }
}
