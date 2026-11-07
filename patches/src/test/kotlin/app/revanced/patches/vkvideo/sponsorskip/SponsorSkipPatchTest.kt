package app.revanced.patches.vkvideo.sponsorskip

import app.revanced.patches.vkvideo.util.PatchTestUtil.createBytecodeContext
import app.revanced.patches.vkvideo.util.PatchTestUtil.execute
import app.revanced.patches.vkvideo.util.PatchTestUtil.getInstructions
import app.revanced.patches.vkvideo.util.PatchTestUtil.getMethod
import app.revanced.patches.vkvideo.util.PatchTestUtil.matchOrNull
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SponsorSkipPatchTest {

    @Test
    fun `every injected class compiles`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)
        sponsorSkipSources().keys.forEach { descriptor ->
            assertTrue(context.classDefs.any { it.type == descriptor }, "missing $descriptor")
        }
    }

    @Test
    fun `the episodes adapter pins the list getter, the sort and the closing end`() {
        val episodes = sponsorSkipSources()[EPISODES_CLASS]!!
        assertTrue(episodes.contains("Lcom/vk/dto/common/VideoFile;->k3()Ljava/util/List;"))
        assertTrue(episodes.contains("Ljava/util/Collections;->sort(Ljava/util/List;Ljava/util/Comparator;)V"))
        assertTrue(episodes.contains("iput-wide v8, v6, $SEGMENT_CLASS->endMs:J"))
        assertTrue(episodes.contains("Lcom/vk/dto/common/VideoEpisode;->a:I"))
        assertTrue(sponsorSkipSources()[SEGMENT_ORDER_CLASS]!!.contains("Ljava/util/Comparator;"))
    }

    @Test
    fun `the rules tables mirror the Kotlin ruleset`() {
        val rules = sponsorSkipSources()[RULES_CLASS]!!
        LebedevRules.EXACT.forEach { (name, category) ->
            assertTrue(rules.contains("\"$name\""), "missing rule $name")
            val label = when (category) {
                Category.SELFPROMO -> "selfpromo"
                Category.INTRO -> "intro"
                else -> "sponsor"
            }
            assertTrue(rules.contains(":$label"), "missing branch $label")
        }
        assertTrue(rules.contains("${LebedevRules.OWNER_ID}"))
        assertTrue(rules.contains("${LebedevRules.POSITIONAL_MAX_MS}"))
    }

    @Test
    fun `the category colors match the epic table`() {
        val category = sponsorSkipSources()[CATEGORY_CLASS]!!
        Category.COLORS.forEach { assertTrue(category.contains(it.toString()), "missing color $it") }
    }

    @Test
    fun `the behavior store uses the frozen file and keys`() {
        val store = sponsorSkipSources()[BEHAVIOR_STORE_CLASS]!!
        assertTrue(store.contains("sponsorskip_settings"))
        assertTrue(store.contains("sponsorskip_enabled"))
        for (category in Category.SPONSOR..Category.OUTRO) {
            assertTrue(store.contains("sponsorskip_settings_behavior_${Category.name(category)}"))
        }
        assertTrue(store.contains("Lcom/vk/core/preference/Preference;->f"))
    }

    @Test
    fun `the three hooks are prepended with the parameter window registers`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        sponsorSkipPatch.execute(context)

        assertHook(sponsorSkipVideoHandoffFingerprint.getMethod(context), "onVideoChanged", 3, 4)
        assertHook(sponsorSkipViewAttachedFingerprint.getMethod(context), "onViewAttached", 2, 3)
        assertHook(sponsorSkipReleaseFingerprint.getMethod(context), "onRelease", 2, null)
    }

    @Test
    fun `the locator skips EmptyAutoPlay`() {
        val context = createBytecodeContext(*fixtures.toTypedArray())
        assertEquals(VIDEO_AUTO_PLAY, sponsorSkipVideoHandoffFingerprint.matchOrNull(context)?.method?.definingClass)
        assertEquals(VIDEO_AUTO_PLAY, sponsorSkipViewAttachedFingerprint.matchOrNull(context)?.method?.definingClass)
        assertEquals(VIDEO_AUTO_PLAY, sponsorSkipReleaseFingerprint.matchOrNull(context)?.method?.definingClass)
    }

    private fun assertHook(
        method: app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod,
        target: String,
        receiver: Int,
        argument: Int?,
    ) {
        val instruction = method.getInstructions()[0] as ReferenceInstruction
        assertEquals(Opcode.INVOKE_STATIC, instruction.opcode)
        val reference = instruction.reference as MethodReference
        assertEquals(SKIP_ENGINE_CLASS, reference.definingClass)
        assertEquals(target, reference.name)
        val registers = method.implementation!!.instructions
        assertNotNull(registers)
        val fiveRegister = instruction as com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
        assertEquals(receiver, fiveRegister.registerC)
        if (argument != null) assertEquals(argument, fiveRegister.registerD)
    }

    private val VIDEO_AUTO_PLAY = "Lcom/vk/libvideo/autoplay/VideoAutoPlay;"
    private val EMPTY_AUTO_PLAY = "Lcom/vk/libvideo/autoplay/e;"

    private val fixtures = listOf(
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

            iput-object p1, p0, $VIDEO_AUTO_PLAY->C:Lcom/vk/dto/common/VideoFile;
            invoke-virtual {p0, p1}, $VIDEO_AUTO_PLAY->y1(Lcom/vk/dto/common/VideoFile;)Ljava/lang/Object;
            move-result-object v0
            return-void
        .end method

        .method public final W(Lcom/vk/libvideo/design/view/videotexture/VideoTextureView;)V
            .locals 2

            iget-object v0, p0, $VIDEO_AUTO_PLAY->K:Ljava/lang/ref/WeakReference;
            const/4 v1, 0x0
            if-eqz v0, :cond_0
            invoke-virtual {v0, p1}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;
            :cond_0
            return-void
        .end method

        .method public final release()V
            .locals 2

            iget-object v0, p0, $VIDEO_AUTO_PLAY->W:Ljava/lang/Object;
            if-eqz v0, :cond_1
            const/4 v1, 0x0
            iput-object v1, p0, $VIDEO_AUTO_PLAY->W:Ljava/lang/Object;
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

            iget-object v0, p0, $VIDEO_AUTO_PLAY->K:Ljava/lang/ref/WeakReference;
            new-instance v1, Ljava/lang/ref/WeakReference;
            invoke-direct {v1, p1}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V
            iput-object v1, p0, $VIDEO_AUTO_PLAY->K:Ljava/lang/ref/WeakReference;
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
        .class public final $EMPTY_AUTO_PLAY
        .super Ljava/lang/Object;
        .implements Lp73/a;

        .field public a:Lcom/vk/dto/common/VideoFile;

        .method public final D(Lcom/vk/dto/common/VideoFile;)V
            .registers 2

            iput-object p1, p0, $EMPTY_AUTO_PLAY->a:Lcom/vk/dto/common/VideoFile;
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
}
