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
            app.revanced.patches.vkvideo.settings.signOutRowFingerprint,
            app.revanced.patches.vkvideo.settings.navOpenerFingerprint,
            app.revanced.patches.vkvideo.settings.switchListenerMethodFingerprint,
            app.revanced.patches.vkvideo.settings.toolbarChromeFingerprint,
            app.revanced.patches.vkvideo.ad.shelf.videoTopshelfBlockFingerprint,
            app.revanced.patches.vkvideo.ad.shelf.musicTopshelfFragmentFingerprint,
            app.revanced.patches.vkvideo.ad.video.instreamAdJsonParserFingerprint,
            app.revanced.patches.vkvideo.ad.video.videoFileInstreamAdFingerprint,
            app.revanced.patches.vkvideo.ad.video.instreamAdLoaderFingerprint,
            app.revanced.patches.vkvideo.ad.video.instreamAdControllerFingerprint,
            app.revanced.patches.vkvideo.ad.feed.myTargetNativeAdEntryFingerprint,
            app.revanced.patches.vkvideo.ad.feed.promoPostFingerprint,
            app.revanced.patches.vkvideo.ad.catalog.catalogAdBannerSlotIdFingerprint,
            app.revanced.patches.vkvideo.ad.catalog.catalogSectionAdBannerGetterFingerprint,
            app.revanced.patches.vkvideo.sponsorskip.sponsorSkipVideoHandoffFingerprint,
            app.revanced.patches.vkvideo.sponsorskip.sponsorSkipViewAttachedFingerprint,
            app.revanced.patches.vkvideo.sponsorskip.sponsorSkipSeekFingerprint,
            app.revanced.patches.vkvideo.sponsorskip.sponsorSkipViewSetupFingerprint,
            app.revanced.patches.vkvideo.sponsorskip.sponsorSkipHolderFingerprint,
            app.revanced.patches.vkvideo.sponsorskip.sponsorSkipReleaseFingerprint,
            app.revanced.patches.vkvideo.sponsorskip.sponsorSkipSectionDrawableFingerprint,
            app.revanced.patches.vkvideo.sponsorskip.sponsorSkipSectionListFingerprint,
            app.revanced.patches.vkvideo.sponsorskip.settings.rootContentFingerprint,
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
        val failed = mutableListOf<app.revanced.patcher.patch.PatchResult>()
        patches.toSet().apply(context, resourceContext) { result ->
            if (result.exception != null) failed += result
        }
        failed.forEach { failure ->
            throw IllegalStateException("Patch failed: ${failure.patch.name}", failure.exception)
        }
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
        .field public static final LOGOUT:Lfixture/menu/Type;
        .field public static final LOGIN:Lfixture/menu/Type;

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

            const/4 v0, 0x2
            const-string v1, "LOGOUT"
            new-instance v2, Lfixture/menu/Type;
            invoke-direct {v2, v1, v0}, Lfixture/menu/Type;-><init>(Ljava/lang/String;I)V
            sput-object v2, Lfixture/menu/Type;->LOGOUT:Lfixture/menu/Type;

            const/4 v0, 0x3
            const-string v1, "LOGIN"
            new-instance v2, Lfixture/menu/Type;
            invoke-direct {v2, v1, v0}, Lfixture/menu/Type;-><init>(Ljava/lang/String;I)V
            sput-object v2, Lfixture/menu/Type;->LOGIN:Lfixture/menu/Type;

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

    /**
     * Mirrors `yj6/r` (1.170): the row-builder class whose Sign-out method is the
     * only one referencing the LOGOUT menu-item type; `cm` calls it in both branches
     * right before handing the list to the recycler.
     */
    private val rowsBuilderFixture = """
        .class public final Lfixture/menu/Rows;
        .super Ljava/lang/Object;

        .field public e:Z

        .method public constructor <init>()V
            .registers 1

            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
        .end method

        .method public final g(Ljava/util/ArrayList;)V
            .registers 7

            sget-object v1, Lcom/vk/video/screens/profile/adapter/ProfileMenuItemType;->LOGOUT:Lcom/vk/video/screens/profile/adapter/ProfileMenuItemType;
            invoke-virtual {v1}, Lfixture/menu/Type;->ordinal()I
            move-result v1
            new-instance v2, Ljava/lang/Object;
            invoke-direct {v2}, Ljava/lang/Object;-><init>()V
            new-instance v3, Lfixture/menu/Wrapper;
            invoke-direct {v3, v1, v2}, Lfixture/menu/Wrapper;-><init>(ILjava/lang/Object;)V
            invoke-virtual {p1, v3}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
            return-void
        .end method
    """.trimIndent()

    private val rowsWrapperFixture = """
        .class public final Lfixture/menu/Wrapper;
        .super Ljava/lang/Object;

        .method public constructor <init>(ILjava/lang/Object;)V
            .registers 3

            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
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

            new-instance v0, Lfixture/menu/Rows;
            invoke-direct {v0}, Lfixture/menu/Rows;-><init>()V
            invoke-virtual {v0, v7}, Lfixture/menu/Rows;->g(Ljava/util/ArrayList;)V
            invoke-virtual {v0, v7}, Lfixture/menu/Rows;->g(Ljava/util/ArrayList;)V

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

            new-instance v0, Lfixture/menu/Rows;
            invoke-direct {v0}, Lfixture/menu/Rows;-><init>()V
            invoke-virtual {v0, v5}, Lfixture/menu/Rows;->g(Ljava/util/ArrayList;)V
            invoke-virtual {v0, v5}, Lfixture/menu/Rows;->g(Ljava/util/ArrayList;)V

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
        rowsWrapperFixture,
        rowsBuilderFixture,
        profileMenuFragmentFixture168,
    )

    val profileMenuFixture170: List<String> = listOf(
        menuEnumFixture,
        menuFnFixture,
        baseRecyclerFragmentFixture170,
        menuItemFixture170,
        menuWrapperFixture170,
        rowsWrapperFixture,
        rowsBuilderFixture,
        profileMenuFragmentFixture170,
    )

    /** `VkSwitchItem$a` — the switch-row listener interface (1.170 shape). */
    private val switchListenerInterfaceFixture = """
        .class public interface abstract Lcom/vk/core/view/components/switch/VkSwitchItem${'$'}a;
        .super Ljava/lang/Object;

        .method public abstract b(Z)V
        .end method
    """.trimIndent()

    /** `MaterialPreferenceToolbarFragment` — the chrome transcription source (1.170 shape). */
    private val toolbarChromeFragmentFixture = """
        .class public abstract Lcom/vk/prefui/fragments/MaterialPreferenceToolbarFragment;
        .super Lcom/vk/prefui/fragments/MaterialPreferenceFragment;

        .method public constructor <init>()V
            .registers 1

            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
        .end method

        .method public final onCreateView(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;
            .registers 8

            invoke-super {p0, p1, p2, p3}, Lcom/vk/prefui/fragments/MaterialPreferenceFragment;->onCreateView(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;
            move-result-object p3

            const v0, 0x7f0e0062
            const/4 v1, 0x0
            invoke-virtual {p1, v0, p2, v1}, Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;
            move-result-object p1

            const p2, 0x7f0b01a0
            invoke-virtual {p1, p2}, Landroid/view/View;->findViewById(I)Landroid/view/View;
            move-result-object p2
            check-cast p2, Landroid/view/ViewGroup;
            invoke-virtual {p2, p3}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

            const p2, 0x7f0b270c
            invoke-virtual {p1, p2}, Landroid/view/View;->findViewById(I)Landroid/view/View;
            move-result-object p2
            check-cast p2, Landroidx/appcompat/widget/Toolbar;
            if-eqz p2, :cond_revanced_t
            invoke-static {p2}, Lty1/i0;->m(Landroid/view/View;)V
            :cond_revanced_t

            const p2, 0x7f0b2292
            invoke-virtual {p1, p2}, Landroid/view/View;->findViewById(I)Landroid/view/View;
            move-result-object p2
            check-cast p2, Lcom/vk/core/view/AppBarShadowView;
            if-eqz p2, :cond_revanced_s
            invoke-static {p2}, Lty1/i0;->m(Landroid/view/View;)V
            :cond_revanced_s

            return-object p1
        .end method

        .method public final onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V
            .registers 15

            invoke-super {p0, p1, p2}, Lcom/vk/prefui/fragments/MaterialPreferenceFragment;->onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V

            new-instance p1, Lcom/vk/core/view/components/topbar/VkTopBar;
            invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->requireContext()Landroid/content/Context;
            move-result-object p2
            const/4 v0, 0x6
            const/4 v1, 0x0
            const/4 v2, 0x0
            invoke-direct {p1, p2, v2, v0, v1}, Lcom/vk/core/view/components/topbar/VkTopBar;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;II)V

            new-instance v3, Lcom/vk/core/view/components/topbar/VkTopBar${'$'}Middle${'$'}Text;
            new-instance v4, Lcom/vk/core/view/components/topbar/VkTopBar${'$'}Middle${'$'}Text${'$'}Title;
            const-string v5, "title"
            const/4 v8, 0x0
            const/16 v9, 0x1e
            const/4 v6, 0x0
            const/4 v7, 0x0
            invoke-direct/range {v4 .. v9}, Lcom/vk/core/view/components/topbar/VkTopBar${'$'}Middle${'$'}Text${'$'}Title;-><init>(Ljava/lang/String;Li12/o;Lcom/vk/core/view/components/topbar/VkTopBar${'$'}Middle${'$'}Text${'$'}Title${'$'}a${'$'}a;Lcom/vk/core/view/components/topbar/VkTopBar${'$'}Middle${'$'}Text${'$'}Title${'$'}Size;I)V

            const/16 v8, 0xe
            const/4 v5, 0x0
            invoke-direct/range {v3 .. v8}, Lcom/vk/core/view/components/topbar/VkTopBar${'$'}Middle${'$'}Text;-><init>(Lcom/vk/core/view/components/topbar/VkTopBar${'$'}Middle${'$'}Text${'$'}Title;Lcom/vk/core/view/components/topbar/VkTopBar${'$'}Middle${'$'}Text${'$'}c;Lcom/vk/core/view/components/topbar/VkTopBar${'$'}Middle${'$'}Text${'$'}a${'$'}b;Lcom/vk/core/compose/component/semantics/SemanticsConfiguration;I)V

            invoke-virtual {p1, v3}, Lcom/vk/core/view/components/topbar/VkTopBar;->setMiddle(Lcom/vk/core/view/components/topbar/VkTopBar${'$'}Middle;)V

            new-instance v4, Lcom/vk/core/view/components/topbar/VkTopBar${'$'}c;
            sget-object v0, Lpx1/a;->Companion:Lpx1/a${'$'}a;
            const v1, 0x7f140052
            invoke-static {v0, v1}, Landroidx/media3/exoplayer/hls/v;->d(Lpx1/a${'$'}a;I)Lpx1/a${'$'}f;
            move-result-object v6
            const/4 v8, 0x0
            const/16 v9, 0x1c
            const/4 v7, 0x0
            const/4 v5, 0x0
            invoke-direct/range {v4 .. v9}, Lcom/vk/core/view/components/topbar/VkTopBar${'$'}c;-><init>(Lkotlin/jvm/functions/Function0;Lpx1/a;Lp72/e;Lcom/vk/core/compose/component/semantics/a;I)V

            invoke-virtual {p1, v4}, Lcom/vk/core/view/components/topbar/VkTopBar;->setBack(Lcom/vk/core/view/components/topbar/VkTopBar${'$'}c;)V

            invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->getView()Landroid/view/View;
            move-result-object v0
            if-eqz v0, :cond_revanced_a
            const v1, 0x7f0b0191
            invoke-virtual {v0, v1}, Landroid/view/View;->findViewById(I)Landroid/view/View;
            move-result-object v0
            check-cast v0, Landroid/view/ViewGroup;
            if-eqz v0, :cond_revanced_a
            invoke-virtual {v0, p1}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V
            :cond_revanced_a

            invoke-static {p0, p1}, Lg57/a;->f(Lcom/vk/core/fragments/FragmentImpl;Lcom/vk/core/view/components/topbar/VkTopBar;)Z
            move-result v0
            if-eqz v0, :cond_revanced_b
            const/4 v2, 0x0
            invoke-virtual {p1, v2}, Lcom/vk/core/view/components/topbar/VkTopBar;->setBefore(Lcom/vk/core/view/components/topbar/VkTopBar${'$'}d;)V
            :cond_revanced_b

            return-void
        .end method
    """.trimIndent()

    /** `g57/a` — the fragment-chrome helper carrying the toolbar-back dispatch. */
    private val chromeHelperFixture = """
        .class public final Lg57/a;
        .super Ljava/lang/Object;

        .method public constructor <init>()V
            .registers 1

            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
        .end method

        .method public static final a(Lcom/vk/core/fragments/FragmentImpl;)Z
            .registers 2

            const/4 v0, 0x1
            return v0
        .end method

        .method public static final b(Lcom/vk/core/fragments/FragmentImpl;)V
            .registers 2

            return-void
        .end method

        .method public static final f(Lcom/vk/core/fragments/FragmentImpl;Lcom/vk/core/view/components/topbar/VkTopBar;)Z
            .registers 3

            const/4 v0, 0x0
            return v0
        .end method
    """.trimIndent()

    /** The General-settings click lambda shape (1.170: `oi5/d`) opening via `o54/r`. */
    private val navOpenerCallFixture = """
        .class public final Lfixture/menu/OpenerUser;
        .super Ljava/lang/Object;

        .method public click(Landroid/content/Context;)V
            .registers 5

            new-instance v0, Lfixture/menu/NavSub;
            const-class v1, Lcom/vk/video/screens/settings/main/ui/VideoUserSettingsFragment;
            const/4 v2, 0x0
            const/4 v3, 0x0
            invoke-direct {v0, v1, v2, v3}, Lfixture/menu/Nav;-><init>(Ljava/lang/Class;Ljava/lang/Class;Landroid/os/Bundle;)V
            invoke-virtual {v0, p1}, Lfixture/menu/Nav;->h(Landroid/content/Context;)V
            return-void
        .end method
    """.trimIndent()

    private val navOpenerSubclassFixture = """
        .class public final Lfixture/menu/NavSub;
        .super Lfixture/menu/Nav;

        .method public constructor <init>()V
            .registers 1

            invoke-direct {p0}, Lfixture/menu/Nav;-><init>()V
            return-void
        .end method
    """.trimIndent()

    private val navOpenerClassFixture = """
        .class public final Lfixture/menu/Nav;
        .super Ljava/lang/Object;

        .method public constructor <init>(Ljava/lang/Class;Ljava/lang/Class;Landroid/os/Bundle;)V
            .registers 4

            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
        .end method

        .method public final h(Landroid/content/Context;)V
            .registers 2

            return-void
        .end method

        .method public final i(Lcom/vk/core/fragments/FragmentImpl;)V
            .registers 2

            return-void
        .end method
    """.trimIndent()

    /** Fixture sets covering every fingerprint the settings patch resolves. */
    private val settingsExtras: List<String> = listOf(
        switchListenerInterfaceFixture,
        toolbarChromeFragmentFixture,
        chromeHelperFixture,
        navOpenerCallFixture,
        navOpenerClassFixture,
        navOpenerSubclassFixture,
    )

    val settingsScreenFixture168: List<String> = profileMenuFixture + settingsExtras

    val settingsScreenFixture170: List<String> = profileMenuFixture170 + settingsExtras


    private const val VIDEO_AUTO_PLAY = "Lcom/vk/libvideo/autoplay/VideoAutoPlay;"
    private const val EMPTY_AUTO_PLAY = "Lcom/vk/libvideo/autoplay/e;"

    /**
     * Core SponsorSkip fixture set: the `p73/a` implementations, `VideoFile`,
     * `UserId` and the preference accessor the engine fingerprints resolve.
     */
    val sponsorCoreFixture: List<String> = listOf(
        """
        .class public interface abstract Lp73/a;
        .super Ljava/lang/Object;

        .method public abstract D(Lcom/vk/dto/common/VideoFile;)V
        .end method

        .method public abstract W(Lcom/vk/libvideo/design/view/videotexture/VideoTextureView;)V
        .end method

        .method public abstract getPosition()J
        .end method

        .method public abstract getDuration()J
        .end method

        .method public abstract isPlaying()Z
        .end method

        .method public abstract release()V
        .end method

        .method public abstract s(J)V
        .end method
        """.trimIndent(),

        """
        .class public final Lcom/vk/libvideo/autoplay/VideoAutoPlay;
        .super Ljava/lang/Object;
        .implements Lp73/a;

        .field public C:Lcom/vk/dto/common/VideoFile;
        .field public K:Ljava/lang/ref/WeakReference;
        .field public W:Ljava/lang/Object;
        .field public t0:Ljava/lang/Object;
        .field public r0:Z

        .method public final D(Lcom/vk/dto/common/VideoFile;)V
            .locals 3

            iput-object p1, p0, ${VIDEO_AUTO_PLAY}->C:Lcom/vk/dto/common/VideoFile;
            invoke-virtual {p0, p1}, ${VIDEO_AUTO_PLAY}->y1(Lcom/vk/dto/common/VideoFile;)Ljava/lang/Object;
            move-result-object v0
            return-void
        .end method

        .method public final W(Lcom/vk/libvideo/design/view/videotexture/VideoTextureView;)V
            .locals 2

            iget-object v0, p0, ${VIDEO_AUTO_PLAY}->K:Ljava/lang/ref/WeakReference;
            const/4 v1, 0x0
            if-eqz v0, :cond_0
            invoke-virtual {v0, p1}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;
            :cond_0
            return-void
        .end method

        .method public final release()V
            .locals 2

            iget-object v0, p0, ${VIDEO_AUTO_PLAY}->W:Ljava/lang/Object;
            if-eqz v0, :cond_1
            const/4 v1, 0x0
            iput-object v1, p0, ${VIDEO_AUTO_PLAY}->W:Ljava/lang/Object;
            :cond_1
            return-void
        .end method

        .method public final h0(Lcom/vk/libvideo/design/view/videotexture/VideoTextureView;)Z
            .locals 1

            if-eqz p1, :cond_0
            const/4 v0, 0x1
            return v0

            :cond_0
            const/4 v0, 0x0
            return v0
        .end method

        .method public final x0(Lcom/vk/libvideo/design/view/videotexture/VideoTextureView;Lcom/vk/libvideo/autoplay/a;Landroidx/recyclerview/widget/RecyclerView${'$'}c0;)V
            .locals 3

            iget-object v0, p0, ${VIDEO_AUTO_PLAY}->K:Ljava/lang/ref/WeakReference;
            new-instance v1, Ljava/lang/ref/WeakReference;
            invoke-direct {v1, p1}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V
            iput-object v1, p0, ${VIDEO_AUTO_PLAY}->K:Ljava/lang/ref/WeakReference;
            return-void
        .end method

        .method public final getPosition()J
            .locals 2

            const-wide/16 v0, 0x0
            return-wide v0
        .end method

        .method public final getDuration()J
            .locals 2

            const-wide/16 v0, 0x0
            return-wide v0
        .end method

        .method public final isPlaying()Z
            .locals 1

            const/4 v0, 0x1
            return v0
        .end method

        .method public final s(J)V
            .locals 1

            return-void
        .end method
        """.trimIndent(),

        """
        .class public final ${EMPTY_AUTO_PLAY}
        .super Ljava/lang/Object;
        .implements Lp73/a;

        .field public a:Lcom/vk/dto/common/VideoFile;

        .method public final D(Lcom/vk/dto/common/VideoFile;)V
            .registers 2

            iput-object p1, p0, ${EMPTY_AUTO_PLAY}->a:Lcom/vk/dto/common/VideoFile;
            return-void
        .end method

        .method public final W(Lcom/vk/libvideo/design/view/videotexture/VideoTextureView;)V
            .registers 2

            return-void
        .end method

        .method public final release()V
            .registers 1

            return-void
        .end method

        .method public final getPosition()J
            .registers 2

            const-wide/16 v0, 0x0
            return-wide v0
        .end method
        """.trimIndent(),

        """
        .class public abstract Lone/video/player/BaseVideoPlayer;
        .super Ljava/lang/Object;
        .implements Lp73/a;

        .field public u0:J

        .method public final a(J)V
            .locals 3

            iput-wide p1, p0, Lone/video/player/BaseVideoPlayer;->u0:J
            invoke-virtual {p0, p1, p2}, Lone/video/player/BaseVideoPlayer;->E0(J)V
            return-void
        .end method

        .method public abstract E0(J)V
        .end method
        """.trimIndent(),

        """
        .class public interface abstract Lcom/vk/dto/common/VideoFile;
        .super Ljava/lang/Object;

        .method public abstract Y0()Ljava/lang/String;
        .end method

        .method public abstract k3()Ljava/util/List;
        .end method

        .method public abstract P0()Lcom/vk/dto/common/id/UserId;
        .end method

        .method public abstract getDuration()I
        .end method
        """.trimIndent(),

        """
        .class public final Lone/video/controls/view/seekbar/ProgressSeekBarView;
        .super Landroid/widget/ProgressBar;
        """.trimIndent(),

        """
        .class public final Lone/video/controls/view/seekbar/CompositeSeekBarView;
        .super Landroid/widget/FrameLayout;

        .field public final h:Lone/video/controls/view/seekbar/ProgressSeekBarView;

        .method public final setProgressDrawable(Landroid/graphics/drawable/Drawable;)V
            .registers 3

            iget-object v0, p0, Lone/video/controls/view/seekbar/CompositeSeekBarView;->h:Lone/video/controls/view/seekbar/ProgressSeekBarView;
            return-void
        .end method
        """.trimIndent(),

        """
        .class public final Lone/video/controls/views/seek/a;
        .super Ljava/lang/Object;

        .field public final a:Landroidx/appcompat/widget/AppCompatSeekBar;

        .method public final g(ILjava/util/ArrayList;)V
            .locals 10

            invoke-virtual {p2}, Ljava/util/ArrayList;->isEmpty()Z
            move-result v0
            if-eqz v0, :end
            return-void

            :end
            return-void
        .end method
        """.trimIndent(),

        """
        .class public final Loo7/c;
        .super Landroid/graphics/drawable/LayerDrawable;
        """.trimIndent(),

        """
        .class public final Lyo7/c;
        .super Landroid/graphics/drawable/LayerDrawable;
        """.trimIndent(),

        """
        .class public final Lcom/vk/dto/common/id/UserId;
        .super Ljava/lang/Object;

        .method public final c()J
            .registers 2

            const-wide/16 v0, 0x0
            return-wide v0
        .end method
        """.trimIndent(),

        """
        .class public final Lcom/vk/core/preference/Preference;
        .super Ljava/lang/Object;

        .method public static final f(Ljava/lang/String;)Landroid/content/SharedPreferences;
            .registers 2

            const/4 v0, 0x0
            return-object v0
        .end method
        """.trimIndent(),

    )

    /** Settings screen fixtures plus the core SponsorSkip classes its patch depends on. */
    val sponsorSettingsFixture170: List<String> = settingsScreenFixture170 + sponsorCoreFixture
}
