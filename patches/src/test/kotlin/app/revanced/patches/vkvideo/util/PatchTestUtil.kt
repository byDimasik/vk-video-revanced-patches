package app.revanced.patches.vkvideo.util

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.Fingerprint
import app.revanced.patcher.Match
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.Patch
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import com.android.tools.smali.smali.SmaliTestUtils
import java.io.File
import java.nio.file.Files

object PatchTestUtil {
    fun createBytecodeContext(vararg smaliSources: String): BytecodePatchContext {
        val tempApk = Files.createTempFile("test-app", ".apk").toFile().apply { deleteOnExit() }
        val tempPatchedDir = Files.createTempDirectory("test-patched").toFile().apply { deleteOnExit() }

        val compiledClassDefs = smaliSources.map { SmaliTestUtils.compileSmali(it) }.toSet()
        val dexFile = object : DexFile {
            override fun getClasses() = compiledClassDefs
            override fun getOpcodes() = Opcodes.getDefault()
        }
        DexPool.writeTo(tempApk.absolutePath, dexFile)

        val constructor = BytecodePatchContext::class.java.getDeclaredConstructor(File::class.java, File::class.java)
        constructor.isAccessible = true
        val context = constructor.newInstance(tempApk, tempPatchedDir)

        println("Directly after constructor, context.classDefs size=${context.classDefs.size}")
        for (c in context.classDefs) {
            println("ClassDef in context.classDefs: type=${c.type}, class=${c.javaClass.name}")
        }
        println("context.classDefs.get('Lcom/vk/video/TestClass;'): ${context.classDefs.get("Lcom/vk/video/TestClass;")}")

        return context
    }

    fun Patch.execute(context: BytecodePatchContext) {
        val method = Patch::class.java.methods.first { it.name.startsWith("getApply") }
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val applyFunc = method.invoke(this) as kotlin.jvm.functions.Function2<BytecodePatchContext, Any?, Unit>
        applyFunc.invoke(context, null)
    }

    fun Fingerprint.matchOrNull(context: BytecodePatchContext): Match? {
        val method = Fingerprint::class.java.methods.firstOrNull { it.name.startsWith("matchOrNull") && it.parameterCount == 1 }
        method?.isAccessible = true
        return method?.invoke(this, context) as? Match
    }

    fun Fingerprint.getMethod(context: BytecodePatchContext): MutableMethod {
        val match = matchOrNull(context) ?: error("Fingerprint did not match")
        return match.method
    }

    fun MutableMethod.getInstructions(): List<BuilderInstruction> {
        val methodKtClass = Class.forName("app.revanced.patcher.extensions.MethodKt")
        val method = methodKtClass.methods.first {
            it.name == "getInstructions" && it.parameterCount == 1 && it.parameterTypes[0] == MutableMethod::class.java
        }
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, this) as List<BuilderInstruction>
    }
}
