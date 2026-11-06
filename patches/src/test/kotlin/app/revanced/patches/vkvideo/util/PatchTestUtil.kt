package app.revanced.patches.vkvideo.util

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.Fingerprint
import app.revanced.patcher.apply
import app.revanced.patcher.Match
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patch.ResourcePatchContext
import app.revanced.patches.vkvideo.settings.profileMenuItemsFingerprint
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import com.android.tools.smali.smali.SmaliTestUtils
import java.io.File
import java.nio.file.Files

object PatchTestUtil {
    fun createBytecodeContext(vararg smaliSources: String): BytecodePatchContext {
        // Fingerprint matches are cached per-instance objects; reset them so each
        // fresh context re-matches instead of observing an earlier context's match.
        listOf(
            profileMenuItemsFingerprint,
            app.revanced.patches.vkvideo.ad.video.instreamAdJsonParserFingerprint,
            app.revanced.patches.vkvideo.ad.video.videoFileInstreamAdFingerprint,
            app.revanced.patches.vkvideo.ad.video.instreamAdLoaderFingerprint,
            app.revanced.patches.vkvideo.ad.video.instreamAdControllerFingerprint,
            app.revanced.patches.vkvideo.ad.feed.myTargetNativeAdEntryFingerprint,
            app.revanced.patches.vkvideo.ad.feed.promoPostFingerprint,
            app.revanced.patches.vkvideo.ad.catalog.catalogAdBannerSlotIdFingerprint,
            app.revanced.patches.vkvideo.ad.catalog.catalogSectionAdBannerGetterFingerprint,
        ).forEach { fingerprintMatchField.set(it, null) }
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

    fun applyPatches(context: BytecodePatchContext, vararg patches: Patch) {
        val resourceContext = createDummyResourceContext(context.getApkFile(), context.getPatchedFilesPath())
        patches.toSet().apply(context, resourceContext) {}
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

    private val fingerprintMatchField = Fingerprint::class.java
        .getDeclaredField("_matchOrNull")
        .apply { isAccessible = true }

    fun Fingerprint.matchOrNull(context: BytecodePatchContext): Match? {
        fingerprintMatchField.set(this, null)
        val method = Fingerprint::class.java.methods.firstOrNull { it.name.startsWith("matchOrNull") && it.parameterCount == 1 }
            ?: error("matchOrNull method not found on Fingerprint")
        method.isAccessible = true
        return method.invoke(this, context) as? Match
    }

    fun Fingerprint.getMethod(context: BytecodePatchContext): MutableMethod {
        val match = (fingerprintMatchField.get(this) as? Match)
            ?: matchOrNull(context)
            ?: error("Fingerprint did not match")
        return match.method
    }

    fun MutableMethod.getInstructions(): List<BuilderInstruction> {
        return this.implementation!!.instructions
    }

    private val menuEnumFixture = """
        .class public final enum Lfixture/menu/Type;
        .super Ljava/lang/Enum;

        .field public static final ITEM_WITH_ICON:Lfixture/menu/Type;
        .field public static final PLAYER_SETTINGS:Lfixture/menu/Type;

        .method static constructor <clinit>()V
            .registers 4

            const/4 v0, 0x0
            const-string v1, "PLAYER_SETTINGS"
            new-instance v2, Lfixture/menu/Type;
            invoke-direct {v2, v1, v0}, Lfixture/menu/Type;-><init>(Ljava/lang/String;I)V
            sput-object v2, Lfixture/menu/Type;->PLAYER_SETTINGS:Lfixture/menu/Type;

            const/4 v0, 0x1
            const-string v1, "ITEM_WITH_ICON"
            new-instance v2, Lfixture/menu/Type;
            invoke-direct {v2, v1, v0}, Lfixture/menu/Type;-><init>(Ljava/lang/String;I)V
            sput-object v2, Lfixture/menu/Type;->ITEM_WITH_ICON:Lfixture/menu/Type;

            return-void
        .end method

        .method private constructor <init>(Ljava/lang/String;I)V
            .registers 3

            invoke-direct {p0, p1, p2}, Ljava/lang/Enum;-><init>(Ljava/lang/String;I)V
            return-void
        .end method
    """.trimIndent()

    private val menuFnFixture = """
        .class public final Lfixture/menu/Fn;
        .super Ljava/lang/Object;
        .implements Lkotlin/jvm/functions/Function1;

        .method public constructor <init>()V
            .registers 1

            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
        .end method

        .method public invoke(Ljava/lang/Object;)Ljava/lang/Object;
            .registers 2

            const/4 v0, 0x0
            return-object v0
        .end method
    """.trimIndent()

    private val baseRecyclerFragmentFixture168 = """
        .class public Lme/grishka/appkit/fragments/BaseRecyclerFragment;
        .super Ljava/lang/Object;

        .method public nm(Ljava/util/List;Z)V
            .registers 4

            return-void
        .end method
    """.trimIndent()

    private val baseRecyclerFragmentFixture170 = """
        .class public Lme/grishka/appkit/fragments/BaseRecyclerFragment;
        .super Ljava/lang/Object;

        .method public hm(Ljava/util/List;Z)V
            .registers 4

            return-void
        .end method
    """.trimIndent()

    private val menuItemFixture168 = """
        .class public final Lcom/vk/video/screens/profile/adapter/holder/i${'$'}a;
        .super Ljava/lang/Object;

        .method public constructor <init>(IILkotlin/jvm/functions/Function1;Z)V
            .registers 5

            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
        .end method

        .method public constructor <init>(ILjava/lang/String;Lkotlin/jvm/functions/Function1;Z)V
            .registers 5

            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
        .end method
    """.trimIndent()

    private val menuItemFixture170 = """
        .class public final Lcom/vk/video/screens/profile/adapter/holder/j${'$'}a;
        .super Ljava/lang/Object;

        .method public constructor <init>(IILkotlin/jvm/functions/Function1;Z)V
            .registers 5

            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
        .end method

        .method public constructor <init>(ILjava/lang/String;Lkotlin/jvm/functions/Function1;Z)V
            .registers 5

            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
        .end method
    """.trimIndent()

    private val menuWrapperFixture168 = """
        .class public final Lz17/e${'$'}a;
        .super Ljava/lang/Object;

        .method public constructor <init>(ILjava/lang/Object;)V
            .registers 3

            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
        .end method
    """.trimIndent()

    private val menuWrapperFixture170 = """
        .class public final Lj57/e${'$'}a;
        .super Ljava/lang/Object;

        .method public constructor <init>(ILjava/lang/Object;)V
            .registers 3

            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
        .end method
    """.trimIndent()

    private val profileMenuFragmentFixture168 = """
        .class public final Lcom/vk/video/screens/profile/ProfileMenuFragment;
        .super Lme/grishka/appkit/fragments/BaseRecyclerFragment;

        .method public final im(II)V
            .registers 8
            .param p1, "a"
            .param p2, "items"

            new-instance v0, Lcom/vk/video/screens/profile/adapter/holder/i${'$'}a;
            const v2, 0x7f141f4f
            const v3, 0x7f082301
            const/4 v4, 0x1
            new-instance v1, Lfixture/menu/Fn;
            invoke-direct {v1}, Lfixture/menu/Fn;-><init>()V
            invoke-direct {v0, v2, v3, v1, v4}, Lcom/vk/video/screens/profile/adapter/holder/i${'$'}a;-><init>(IILkotlin/jvm/functions/Function1;Z)V

            sget-object v1, Lfixture/menu/Type;->PLAYER_SETTINGS:Lfixture/menu/Type;
            invoke-virtual {v1}, Lfixture/menu/Type;->ordinal()I
            move-result v1
            new-instance v2, Lz17/e${'$'}a;
            invoke-direct {v2, v1, v0}, Lz17/e${'$'}a;-><init>(ILjava/lang/Object;)V
            invoke-virtual {v7, v2}, Ljava/util/List;->add(Ljava/lang/Object;)Z

            const/4 v0, 0x0
            invoke-virtual {p0, v7, v0}, Lme/grishka/appkit/fragments/BaseRecyclerFragment;->nm(Ljava/util/List;Z)V
            return-void
        .end method
    """.trimIndent()

    private val profileMenuFragmentFixture170 = """
        .class public final Lcom/vk/video/screens/profile/ProfileMenuFragment;
        .super Lme/grishka/appkit/fragments/BaseRecyclerFragment;

        .method public final cm(II)V
            .registers 9
            .param p1, "a"
            .param p2, "items"

            new-instance v0, Lcom/vk/video/screens/profile/adapter/holder/j${'$'}a;
            const v2, 0x7f141f4f
            const v3, 0x7f082302
            const/4 v4, 0x1
            new-instance v1, Lfixture/menu/Fn;
            invoke-direct {v1}, Lfixture/menu/Fn;-><init>()V
            invoke-direct {v0, v2, v3, v1, v4}, Lcom/vk/video/screens/profile/adapter/holder/j${'$'}a;-><init>(IILkotlin/jvm/functions/Function1;Z)V

            sget-object v1, Lfixture/menu/Type;->PLAYER_SETTINGS:Lfixture/menu/Type;
            invoke-virtual {v1}, Lfixture/menu/Type;->ordinal()I
            move-result v1
            new-instance v2, Lj57/e${'$'}a;
            invoke-direct {v2, v1, v0}, Lj57/e${'$'}a;-><init>(ILjava/lang/Object;)V
            move-object v5, p2
            invoke-virtual {v5, v2}, Ljava/util/List;->add(Ljava/lang/Object;)Z

            const/4 v0, 0x0
            invoke-virtual {p0, v5, v0}, Lme/grishka/appkit/fragments/BaseRecyclerFragment;->hm(Ljava/util/List;Z)V
            return-void
        .end method
    """.trimIndent()

    val profileMenuFixture: List<String> = listOf(
        menuEnumFixture,
        menuFnFixture,
        baseRecyclerFragmentFixture168,
        menuItemFixture168,
        menuWrapperFixture168,
        profileMenuFragmentFixture168,
    )

    val profileMenuFixture170: List<String> = listOf(
        menuEnumFixture,
        menuFnFixture,
        baseRecyclerFragmentFixture170,
        menuItemFixture170,
        menuWrapperFixture170,
        profileMenuFragmentFixture170,
    )

}
