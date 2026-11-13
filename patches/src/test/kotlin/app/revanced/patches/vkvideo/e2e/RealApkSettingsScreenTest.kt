package app.revanced.patches.vkvideo.e2e

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patches.vkvideo.settings.ADS_SETTINGS_CLASS
import app.revanced.patches.vkvideo.settings.ROOT_FRAGMENT_CLASS
import app.revanced.patches.vkvideo.settings.SETTINGS_FRAGMENT_CLASS
import app.revanced.patches.vkvideo.settings.extractToolbarChrome
import app.revanced.patches.vkvideo.settings.remapAppTypes
import app.revanced.patches.vkvideo.settings.navOpenerFingerprint
import app.revanced.patches.vkvideo.settings.profileMenuItemsFingerprint
import app.revanced.patches.vkvideo.settings.resolveNavOpener
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
import app.revanced.patches.vkvideo.settings.switchListenerMethodFingerprint
import app.revanced.patches.vkvideo.settings.toolbarChromeFingerprint
import app.revanced.patches.vkvideo.util.PatchTestUtil.applyPatches
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.initializeCache
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Real-APK structural coverage for the native settings screen: proves every
 * interpolated anchor (chrome ids, hide helper, VkTopBar constructor chain,
 * opener type, switch-listener method) resolves in the shipped 1.170 dexes and
 * that the five classes inject against the real app. Visual/theme behavior and
 * persistence round-trips are the device gate.
 */
class RealApkSettingsScreenTest {

    private fun findApkFile(): File? {
        val candidateDirs = listOf(File(".cache"), File("/app/.cache"), File("../.cache"))
        return candidateDirs.asSequence()
            .filter { it.isDirectory }
            .flatMap { it.listFiles { f -> f.isFile && f.name.matches(Regex("vkvideo-\\d+\\.\\d+(-\\d+)?\\.apk")) }?.asSequence() ?: emptySequence() }
            .maxByOrNull { it.lastModified() }
    }

    @Test
    fun testSettingsScreenOnRealApk() {
        val apkFile = findApkFile()
        val isStrictE2e = System.getProperty("vkvideo.e2e") == "true" || System.getenv("VKVIDEO_E2E") == "true"
        if (isStrictE2e) {
            check(apkFile != null && apkFile.exists()) { "VK Video target APK snapshot not found in .cache" }
        } else {
            assumeTrue(apkFile != null && apkFile.exists(), "Skipping: APK snapshot not found in .cache")
        }

        val realApk = apkFile!!
        val patchedDir = Files.createTempDirectory("patched-screen-test").toFile().apply { deleteOnExit() }
        val context = BytecodePatchContext::class.java
            .getDeclaredConstructor(File::class.java, File::class.java)
            .apply { isAccessible = true }
            .newInstance(realApk, patchedDir) as BytecodePatchContext
        val dexContainer = DexFileFactory.loadDexContainer(realApk, Opcodes.getDefault())
        for (dexEntryName in dexContainer.dexEntryNames) {
            context.classDefs.addAll(dexContainer.getEntry(dexEntryName)!!.dexFile.classes)
        }
        context.initializeCache()

        assertNotNull(profileMenuItemsFingerprint.matchOrNull(context))
        assertNotNull(toolbarChromeFingerprint.matchOrNull(context))
        assertNotNull(navOpenerFingerprint.matchOrNull(context))
        assertNotNull(switchListenerMethodFingerprint.matchOrNull(context))

        // Chrome ids pinned to the 1.171 res/values/public.xml values.
        val chrome = extractToolbarChrome(
            context.classDefs.first { it.type == "Lcom/vk/prefui/fragments/MaterialPreferenceToolbarFragment;" },
        )
        assertEquals(0x7f0e0062, chrome.chromeLayoutId, "appkit_toolbar_fragment_hidable_with_coordinator")
        assertEquals(0x7f0b01a3, chrome.contentId, "appkit_content")
        assertEquals(0x7f0b2731, chrome.legacyToolbarId, "legacy toolbar")
        assertEquals(0x7f0b22a4, chrome.shadowId, "app bar shadow")
        assertEquals(0x7f0b0194, chrome.appBarLayoutId, "app_bar_layout")
        assertEquals(0x7f140052, chrome.accessibilityBackId, "accessibility_back")
        assertEquals("Lxy1/j0;->m(Landroid/view/View;)V", chrome.hideHelperRef)
        assertEquals("Lv57/a;->f(Lcom/vk/core/fragments/FragmentImpl;Lcom/vk/core/view/components/topbar/VkTopBar;)Z", chrome.chromeCheckRef)
        assertEquals(6, chrome.topBarStyleInt)
        assertEquals(30, chrome.titleStyleInt)
        assertEquals(14, chrome.textStyleInt)
        assertEquals(28, chrome.backStyleInt)

        // The switch listener method name (obfuscated; 1.170: b) and interface.
        val listenerMethod = switchListenerMethodFingerprint.getMethod(context)
        assertEquals("Lcom/vk/core/view/components/switch/VkSwitchItem\$a;", listenerMethod.definingClass)

        // The opener resolves to real classes present in the shipped dexes.
        val opener = resolveNavOpener(navOpenerFingerprint.getMethod(context), context.classDefs.toList())
        assertTrue(context.classDefs.any { it.type == opener.instanceType }, "opener instance class must exist")
        assertTrue(context.classDefs.any { it.type == opener.ctorOwner }, "opener ctor owner must exist")
        assertTrue(context.classDefs.any { it.type == opener.hOwner }, "opener h owner must exist")
        assertTrue(context.classDefs.any { it.type == opener.iOwner }, "opener i owner must exist")

        applyPatches(context, revancedSettingsPatch)

        // All seven classes inject; both fragments are reflectively instantiable.
        for (descriptor in listOf(
            ADS_SETTINGS_CLASS,
            "Lapp/revanced/extension/vkvideo/StartReVancedSettings;",
            ROOT_FRAGMENT_CLASS,
            SETTINGS_FRAGMENT_CLASS,
            "Lapp/revanced/extension/vkvideo/ReVancedAdsGroupClick;",
            "Lapp/revanced/extension/vkvideo/ReVancedToggleListener;",
            "Lapp/revanced/extension/vkvideo/ReVancedBackClick;",
        )) {
            assertTrue(context.classDefs.any { it.type == descriptor }, "$descriptor must be injected")
        }
        for (descriptor in listOf(ROOT_FRAGMENT_CLASS, SETTINGS_FRAGMENT_CLASS)) {
            val fragment = context.classDefs.first { it.type == descriptor }
            assertEquals("Lcom/vk/core/fragments/FragmentImpl;", fragment.superclass)
            val ctor = fragment.methods.first { it.name == "<init>" }
            assertTrue(ctor.parameterTypes.isEmpty(), "reflective FragmentEntry.u8() instantiation needs the no-arg ctor")
            assertTrue(com.android.tools.smali.dexlib2.AccessFlags.PUBLIC.isSet(ctor.accessFlags))
        }

        // Every method the injected UI calls must exist in the real APK with the
        // emitted owner/name/signature — a transcribed class the 1.170 dexes do not
        // actually carry (or a renamed overload) fails here, not on device.
        /**
         * Asserts the APK carries `owner->name(params)` so a stale transcription fails
         * in CI. The signature is remapped first: the latency is written against one
         * version and R8 renames the obfuscated types in it.
         */
        val resolvedRefs = mutableListOf<String>()
        fun assertResolves(owner: String, name: String, params: List<String>) {
            resolvedRefs += "$owner->$name(${params.joinToString("")})V"
            // Remap the accumulated refs together: a package move is only learnable
            // from a neighbouring type, never from a single obstinate ref.
            val remapped = remapAppTypes(context, resolvedRefs.joinToString("\n")).lines().last()
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
                def!!.methods.firstOrNull { it.name == name && it.parameterTypes.toList() == types },
                "referenced method $mappedOwner->$name(${types.joinToString("")}) must exist",
            )
        }
        assertResolves("Lcom/vk/core/view/components/cell/VkCell;", "<init>", listOf(
            "Landroid/content/Context;", "Landroid/util/AttributeSet;", "I", "I",
        ))
        assertResolves("Lcom/vk/core/view/components/cell/VkCell;", "setLeft", listOf(
            "Lcom/vk/core/view/components/cell/VkCell\$Left\$b;",
        ))
        assertResolves("Lcom/vk/core/view/components/cell/VkCell;", "setMiddle", listOf(
            "Lcom/vk/core/view/components/cell/VkCell\$Middle\$b;",
        ))
        assertResolves("Lcom/vk/core/view/components/cell/VkCell;", "setRight", listOf(
            "Lcom/vk/core/view/components/cell/VkCell\$Right\$d;",
        ))
        assertResolves("Lcom/vk/core/view/components/cell/VkCell\$Left\$Main\$c;", "<init>", listOf(
            "Lp72/g;", "Lcom/vk/core/view/components/cell/VkCell\$Left\$Main\$Size;", "Lp72/r;",
        ))
        assertResolves("Lcom/vk/core/view/components/cell/VkCell\$Middle\$e;", "<init>", listOf(
            "Lpx1/a;", "I", "Landroid/text/TextUtils\$TruncateAt;",
            "Lcom/vk/core/view/components/cell/VkCell\$Middle\$e\$b\$b;",
            "Lcom/vk/core/view/components/cell/VkCell\$Middle\$e\$b;", "I",
        ))
        assertResolves("Lcom/vk/core/view/components/cell/VkCell\$Right\$d;", "<init>", listOf(
            "Lcom/vk/core/view/components/cell/VkCell\$Right\$a;",
            "Lcom/vk/core/view/components/cell/VkCell\$Right\$e;",
            "Lcom/vk/core/view/components/cell/VkCell\$Right\$ExtraAction\$a;",
            "Lcom/vk/core/view/components/cell/VkCell\$Right\$b;", "I",
        ))
        assertResolves("Lp72/j;", "<init>", listOf("I"))
        assertResolves("Lp72/b;", "<init>", listOf("I"))
        assertResolves(opener.iOwner, opener.openFragmentMethod, listOf("Lcom/vk/core/fragments/FragmentImpl;"))

        // StartReVancedSettings opens the screen via the resolved opener and returns null.
        val start = context.classDefs.first { it.type == "Lapp/revanced/extension/vkvideo/StartReVancedSettings;" }
        val invoke = start.methods.first { it.name == "invoke" }
        val instructions = invoke.implementation!!.instructions.toList()
        val openerTypes = instructions.filterIsInstance<ReferenceInstruction>()
            .mapNotNull { (it.reference as? TypeReference)?.type }
        assertTrue(openerTypes.contains(opener.instanceType), "opener must be instantiated")
        val lastReturn = instructions.last()
        val nullConst = instructions[instructions.size - 2]
        assertEquals(com.android.tools.smali.dexlib2.Opcode.CONST_4, nullConst.opcode)
        assertEquals(0, (nullConst as com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction).narrowLiteral)
        assertEquals(
            (lastReturn as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction).registerA,
            (nullConst as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction).registerA,
            "the returned register must hold the null constant",
        )
    }
}
