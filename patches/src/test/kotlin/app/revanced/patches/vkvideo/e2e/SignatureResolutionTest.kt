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
