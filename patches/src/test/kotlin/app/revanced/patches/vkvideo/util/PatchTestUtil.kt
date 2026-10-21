package app.revanced.patches.vkvideo.util

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.Fingerprint
import app.revanced.patcher.Match
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patch.ResourcePatchContext
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

        for (classDef in compiledClassDefs) {
            context.classDefs.add(classDef)
        }
        context.initializeCache()

        return context
    }

    private fun BytecodePatchContext.getApkFile(): File {
        val method = BytecodePatchContext::class.java.methods.first { it.name.startsWith("getApkFile") }
        method.isAccessible = true
        return method.invoke(this) as File
    }

    private fun BytecodePatchContext.getPatchedFilesPath(): File {
        val method = BytecodePatchContext::class.java.methods.first { it.name.startsWith("getPatchedFilesPath") }
        method.isAccessible = true
        return method.invoke(this) as File
    }

    private fun createDummyResourceContext(apkFile: File, patchedDir: File): ResourcePatchContext {
        val constructor = ResourcePatchContext::class.java.declaredConstructors.first()
        constructor.isAccessible = true
        return constructor.newInstance(apkFile, patchedDir, patchedDir, null, null) as ResourcePatchContext
    }

    fun BytecodePatchContext.initializeCache() {
        val method = this.classDefs.javaClass.methods.firstOrNull { it.name.startsWith("initializeCache") }
            ?: error("initializeCache method not found in ClassDefs")
        method.isAccessible = true
        method.invoke(this.classDefs)
    }

    fun Patch.execute(context: BytecodePatchContext, resourceContext: ResourcePatchContext? = null) {
        val method = Patch::class.java.methods.firstOrNull { it.name.startsWith("getApply") }
            ?: error("getApply method not found on Patch")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val applyFunc = method.invoke(this) as kotlin.jvm.functions.Function2<BytecodePatchContext, ResourcePatchContext, Unit>
        val resCtx = resourceContext ?: createDummyResourceContext(context.getApkFile(), context.getPatchedFilesPath())
        applyFunc.invoke(context, resCtx)
    }

    fun Fingerprint.matchOrNull(context: BytecodePatchContext): Match? {
        val method = Fingerprint::class.java.methods.firstOrNull { it.name.startsWith("matchOrNull") && it.parameterCount == 1 }
            ?: error("matchOrNull method not found on Fingerprint")
        method.isAccessible = true
        return method.invoke(this, context) as? Match
    }

    fun Fingerprint.getMethod(context: BytecodePatchContext): MutableMethod {
        val match = matchOrNull(context) ?: error("Fingerprint did not match")
        return match.method
    }

    fun MutableMethod.getInstructions(): List<BuilderInstruction> {
        return this.implementation!!.instructions
    }
}
