package app.revanced.patches.vkvideo.e2e

import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Regression guard for the framework-inheritance rule: an external ancestor only
 * proves the members it actually declares. `ordinal()` lives on `Enum`, so a plain
 * `Object` subclass must not pass for it.
 */
private const val FRAGMENT_ACTIVITY_TYPE = "Landroidx/fragment/app/FragmentActivity;"

class SignatureResolutionTest {

    private fun findApkFile(): File? = listOf(File(".cache"), File("/app/.cache"), File("../.cache"))
        .asSequence()
        .filter { it.isDirectory }
        .flatMap { it.listFiles { f -> f.isFile && f.name.matches(Regex("vkvideo-\\d+\\.\\d+(-\\d+)?\\.apk")) }?.asSequence() ?: emptySequence() }
        .maxByOrNull { it.lastModified() }

    private fun loadContext(): BytecodePatchContext? {
        val apk = findApkFile() ?: return null
        val dir = Files.createTempDirectory("signature-resolution-test").toFile().apply { deleteOnExit() }
        val context = BytecodePatchContext::class.java
            .getDeclaredConstructor(File::class.java, File::class.java)
            .apply { isAccessible = true }
            .newInstance(apk, dir) as BytecodePatchContext
        val container = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault())
        for (entry in container.dexEntryNames) {
            context.classDefs.addAll(container.getEntry(entry)!!.dexFile.classes)
        }
        return context
    }

    @Test
    fun testFrameworkAncestorMustDeclareTheMember() {
        val context = loadContext()
        assumeTrue(context != null, "Skipping: APK snapshot not found in .cache")

        val enumClass = context!!.classDefs.firstOrNull { it.superclass == "Ljava/lang/Enum;" && it.type.startsWith("Lcom/vk/") }
        assertTrue(enumClass != null, "no enum in com/vk to test against")
        assertTrue(
            context.appMethodResolves(enumClass!!.type, "ordinal", emptyList(), "I"),
            "ordinal() must resolve on an Enum subclass",
        )

        val plainClass = context.classDefs.firstOrNull {
            it.superclass == "Ljava/lang/Object;" && it.type.startsWith("Lcom/vk/") &&
                it.methods.none { method -> method.name == "ordinal" }
        }
        assertTrue(plainClass != null, "no plain Object subclass without ordinal()")
        assertFalse(
            context.appMethodResolves(plainClass!!.type, "ordinal", emptyList(), "I"),
            "ordinal() must not resolve on a plain Object subclass",
        )

        // A Fragment subclass resolves the activity accessors; a FragmentActivity
        // subclass (sibling in the same package) must not.
        assertTrue(
            context.appMethodResolves(
                "Lcom/vk/core/fragments/FragmentImpl;", "requireActivity", emptyList(), "Landroidx/fragment/app/FragmentActivity;",
            ),
            "requireActivity() must resolve on a Fragment subclass",
        )
        // Neither a FragmentActivity subclass nor an unrelated class in the same
        // package declares the accessor.
        for (sibling in listOf(FRAGMENT_ACTIVITY_TYPE, "Landroidx/appcompat/app/AppCompatActivity;")) {
            assertFalse(
                context.appMethodResolves(
                    sibling, "requireActivity", emptyList(), "Landroidx/fragment/app/FragmentActivity;",
                ),
                "requireActivity() must not resolve on $sibling",
            )
        }

        // The allowance is the exact Fragment type, not the package: only Fragment
        // declares the accessor, and a sibling in its package must not stand in.
        val fragment = context.classDefs.first { it.type == "Landroidx/fragment/app/Fragment;" }
        assertTrue(fragment.methods.any { it.name == "requireActivity" }, "Fragment must declare requireActivity()")
        val activity = context.classDefs.first { it.type == "Landroidx/fragment/app/FragmentActivity;" }
        assertTrue(activity.methods.none { it.name == "requireActivity" }, "FragmentActivity must not declare it")

        // A member the class really declares always wins.
        val declared = plainClass.methods.firstOrNull { it.parameterTypes.isEmpty() && it.name != "<init>" }
        if (declared != null) {
            assertTrue(
                context.appMethodResolves(plainClass.type, declared.name, emptyList(), declared.returnType.toString()),
                "a declared method must resolve",
            )
        }
    }
}
