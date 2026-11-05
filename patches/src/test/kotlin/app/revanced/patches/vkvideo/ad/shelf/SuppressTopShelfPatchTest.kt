package app.revanced.patches.vkvideo.ad.shelf

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patch.ResourcePatchContext
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import com.android.tools.smali.smali.SmaliTestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory

class SuppressTopShelfPatchTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun layoutFixture(id: String) = """
        <?xml version="1.0" encoding="utf-8"?>
        <androidx.constraintlayout.widget.ConstraintLayout
            xmlns:android="$androidNs"
            android:id="@+id/$id"
            android:layout_width="match_parent"
            android:layout_height="wrap_content">

            <TextView
                android:id="@+id/video_topshelf_title"
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />
        </androidx.constraintlayout.widget.ConstraintLayout>
    """.trimIndent()

    private fun writeDummyApk(apkFile: File) {
        // BytecodePatchContext eagerly reads a DEX container from the APK, so the
        // fixture APK needs at least one valid DEX even for a resource-only patch.
        val classDefs = setOf(
            SmaliTestUtils.compileSmali(
                """
                .class public Ltest/Dummy;
                .super Ljava/lang/Object;
                """.trimIndent()
            )
        )
        val dexFile = object : DexFile {
            override fun getClasses() = classDefs
            override fun getOpcodes() = Opcodes.getDefault()
        }
        DexPool.writeTo(apkFile.absolutePath, dexFile)
    }

    private fun ResourcePatchContext.applyPatch(apkFile: File) {
        val method = Patch::class.java.methods.firstOrNull { it.name.startsWith("getApply") }
            ?: error("getApply not found on Patch")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val applyFunc =
            method.invoke(suppressTopShelfPatch) as kotlin.jvm.functions.Function2<BytecodePatchContext, ResourcePatchContext, Unit>

        val bytecodeContext = BytecodePatchContext::class.java
            .getDeclaredConstructor(File::class.java, File::class.java)
            .apply { isAccessible = true }
            .newInstance(apkFile, Files.createTempDirectory("suppress-topshelf-patched").toFile())

        applyFunc.invoke(bytecodeContext, this)
    }

    private fun readRoot(file: File) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement

    @Test
    fun collapsesBothTopShelfLayouts() {
        val apkDir = Files.createTempDirectory("suppress-topshelf").toFile().apply { deleteOnExit() }
        val layoutDir = File(apkDir, "res/layout").apply { mkdirs() }
        File(layoutDir, "catalog_video_topshelf.xml").writeText(layoutFixture("topshelf_a"))
        File(layoutDir, "catalog_video_music_topshelf.xml").writeText(layoutFixture("topshelf_b"))
        val dummyApk = File(apkDir, "app.apk")
        writeDummyApk(dummyApk)

        val constructor = ResourcePatchContext::class.java.declaredConstructors.first()
        constructor.isAccessible = true
        val resourceContext =
            constructor.newInstance(dummyApk, apkDir, apkDir, null, null) as ResourcePatchContext

        resourceContext.applyPatch(dummyApk)

        for (name in listOf("catalog_video_topshelf.xml", "catalog_video_music_topshelf.xml")) {
            val file = File(layoutDir, name)
            val root = readRoot(file)

            assertEquals("0dp", root.getAttribute("android:layout_height"), "$name root height")
            assertEquals("gone", root.getAttribute("android:visibility"), "$name root visibility")
            assertEquals("match_parent", root.getAttribute("android:layout_width"), "$name width untouched")

            val child = root.firstChild.let { first ->
                generateSequence(first) { it.nextSibling }.first { it.nodeName == "TextView" }
            }
            assertEquals("wrap_content", child.attributes.getNamedItem("android:layout_height").nodeValue)
        }
    }
}
