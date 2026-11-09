package app.revanced.patches.vkvideo.sponsorskip

import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.smali.SmaliTestUtils

internal const val SPONSOR_SKIP_PACKAGE = "Lapp/revanced/extension/sponsorskip/"

internal const val CATEGORY_CLASS = "${SPONSOR_SKIP_PACKAGE}Category;"
internal const val SEGMENT_CLASS = "${SPONSOR_SKIP_PACKAGE}Segment;"
internal const val SEGMENT_ARRAY = "[$SEGMENT_CLASS"
internal const val SEGMENT_ORDER_CLASS = "${SPONSOR_SKIP_PACKAGE}SegmentOrder;"
internal const val EPISODES_CLASS = "${SPONSOR_SKIP_PACKAGE}Episodes;"
internal const val RULES_CLASS = "${SPONSOR_SKIP_PACKAGE}Rules;"
internal const val BEHAVIOR_CLASS = "${SPONSOR_SKIP_PACKAGE}Behavior;"
internal const val BEHAVIOR_STORE_CLASS = "${SPONSOR_SKIP_PACKAGE}BehaviorStore;"
internal const val SKIP_ENGINE_CLASS = "${SPONSOR_SKIP_PACKAGE}SkipEngine;"
internal const val SKIP_STATE_CLASS = "${SPONSOR_SKIP_PACKAGE}SkipEngine\$State;"
internal const val SKIP_TICK_CLASS = "${SPONSOR_SKIP_PACKAGE}SkipEngine\$Tick;"
internal const val SKIP_BUTTON_CLASS = "${SPONSOR_SKIP_PACKAGE}SkipButton;"
internal const val SKIP_CLICK_CLASS = "${SPONSOR_SKIP_PACKAGE}SkipButton\$Click;"
internal const val SKIP_LAYOUT_CLASS = "${SPONSOR_SKIP_PACKAGE}SkipButton\$Layout;"
internal const val BAND_CLASS = "${SPONSOR_SKIP_PACKAGE}Band;"
internal const val BAND_ARRAY = "[$BAND_CLASS"
internal const val RANGES_CLASS = "${SPONSOR_SKIP_PACKAGE}Ranges;"
internal const val OVERLAY_CLASS = "${SPONSOR_SKIP_PACKAGE}SeekbarOverlay;"
internal const val SECTIONS_DRAWABLE_CLASS = "${SPONSOR_SKIP_PACKAGE}SectionsDrawable;"
internal const val SEEK_SECTIONS_CLASS = "${SPONSOR_SKIP_PACKAGE}SeekbarSections;"

/** The `OneVideoSeekBarView` id from `one_video_simple_controls_view.xml`. */
internal const val SEEK_BAR_VIEW_ID = 0x7f0b222b

/** The plain `AppCompatSeekBar` inside `VideoSeekViewOld`, used by the page player. */
internal const val SEEK_BAR_ID = 0x7f0b2224

private const val PREF_FILE = "sponsorskip_settings"
private const val KEY_ENABLED = "sponsorskip_enabled"

/** Forces one behavior for every category when >= 0; -1 reads the store. */
private const val AUTO_SKIP_OVERRIDE = -1

/** Density-independent button margin, in dp, from the player surface corner. */
private const val BUTTON_MARGIN_DP = 16

/** Minimum inset, in dp, keeping the button off the surface edge. */
private const val BUTTON_PAD_DP = 8

/**
 * Injects the SponsorSkip extension classes into the app dex.
 *
 * Same contract as the settings injector: an existing descriptor is skipped, but
 * a stale generation missing methods is rejected instead of silently kept.
 */
internal fun injectSponsorSkipClasses(context: BytecodePatchContext) {
    sponsorSkipSources().forEach { (descriptor, source) ->
        val existing = context.classDefs.firstOrNull { it.type == descriptor }
        if (existing != null) {
            val missing = requiredMethods[descriptor].orEmpty().filter { required ->
                val (name, params) = required.split(':')
                existing.methods.none { it.name == name && it.parameterTypes.size == params.toInt() }
            }
            val missingFields = requiredFields[descriptor].orEmpty().filter { name ->
                existing.fields.none { it.name == name }
            }
            check(missing.isEmpty() && missingFields.isEmpty()) {
                "Stale injected class $descriptor is missing methods $missing, fields $missingFields; " +
                    "re-patch from the stock APK"
            }
            return@forEach
        }
        context.classDefs.add(
            try {
                SmaliTestUtils.compileSmali(source)
            } catch (e: RuntimeException) {
                throw IllegalStateException("$descriptor does not compile: ${e.cause?.message}", e)
            },
        )
    }
}

/** The injected class sources, keyed by descriptor; exposed for structural tests. */
internal fun sponsorSkipSources(): Map<String, String> = linkedMapOf(
    CATEGORY_CLASS to categorySmali(),
    SEGMENT_CLASS to segmentSmali(),
    SEGMENT_ORDER_CLASS to segmentOrderSmali(),
    EPISODES_CLASS to episodesSmali(),
    RULES_CLASS to rulesSmali(),
    BEHAVIOR_CLASS to behaviorSmali(),
    BEHAVIOR_STORE_CLASS to behaviorStoreSmali(),
    SKIP_STATE_CLASS to skipStateSmali(),
    SKIP_TICK_CLASS to skipTickSmali(),
    SKIP_ENGINE_CLASS to skipEngineSmali(),
    SKIP_BUTTON_CLASS to skipButtonSmali(),
    SKIP_CLICK_CLASS to skipClickSmali(),
    SKIP_LAYOUT_CLASS to skipLayoutSmali(),
    BAND_CLASS to bandSmali(),
    RANGES_CLASS to rangesSmali(),
    SECTIONS_DRAWABLE_CLASS to sectionsDrawableSmali(),
    SEEK_SECTIONS_CLASS to seekbarSectionsSmali(),
).mapValues { (_, source) -> source.replace(";;", ";") }

internal val requiredMethods = mapOf(
    CATEGORY_CLASS to listOf("color:1", "name:1", "label:1"),
    SEGMENT_CLASS to listOf("<init>:4"),
    SEGMENT_ORDER_CLASS to listOf("compare:2"),
    EPISODES_CLASS to listOf("read:2"),
    RULES_CLASS to listOf("classify:5"),
    BEHAVIOR_CLASS to listOf("parse:2", "literal:1"),
    BEHAVIOR_STORE_CLASS to listOf("isEnabled:0", "getBehavior:1", "key:1", "setEnabled:1", "setBehavior:2"),
    SKIP_STATE_CLASS to listOf("<init>:5"),
    SKIP_TICK_CLASS to listOf("<init>:1", "run:0"),
    SKIP_ENGINE_CLASS to listOf(
        "onVideoChanged:2", "onViewAttached:2", "onRelease:1", "tick:1", "onSeek:2", "skip:3",
        "rememberView:2", "viewOf:1", "stateOf:1", "put:2", "clear:1", "stop:1", "active:0",
    ),
    SKIP_BUTTON_CLASS to listOf("show:2", "hide:1", "dp:2", "place:1"),
    SKIP_CLICK_CLASS to listOf("<init>:2", "onClick:1"),
    SKIP_LAYOUT_CLASS to listOf("<init>:1", "onLayoutChange:9", "watch:3"),
    BAND_CLASS to listOf("<init>:4"),
    RANGES_CLASS to listOf("build:2"),
    SECTIONS_DRAWABLE_CLASS to listOf("<init>:0", "onDraw:1", "setAlpha:1", "setColorFilter:1", "getOpacity:0"),
    SEEK_SECTIONS_CLASS to listOf("apply:2", "invalidate:0"),
)

/** Added fields that a stale generation of the class can miss; methods alone do not catch them. */
internal val requiredFields = mapOf(
    SKIP_STATE_CLASS to listOf("view", "button", "listener", "listenerView", "buttonView", "lastToastMs"),
)

private fun categorySmali(): String = """
    .class public final $CATEGORY_CLASS
    .super Ljava/lang/Object;

    .field public static final SPONSOR:I = 0x0
    .field public static final SELFPROMO:I = 0x1
    .field public static final INTRO:I = 0x2
    .field public static final OUTRO:I = 0x3
    .field public static final COUNT:I = 0x4

    .method public static colors()[I
        .registers 1

        const/4 v0, 0x4
        new-array v0, v0, [I
        fill-array-data v0, :colors
        return-object v0

        :colors
        .array-data 4
            ${Category.COLORS[0]}
            ${Category.COLORS[1]}
            ${Category.COLORS[2]}
            ${Category.COLORS[3]}
        .end array-data
    .end method

    .method public static color(I)I
        .registers 3

        invoke-static {}, $CATEGORY_CLASS->colors()[I
        move-result-object v0
        if-gez p0, :non_negative
        const/4 p0, 0x0
        :non_negative
        array-length v1, v0
        if-ge p0, v1, :in_range
        const/4 p0, 0x0
        :in_range
        aget v0, v0, p0
        return v0
    .end method

    .method public static name(I)Ljava/lang/String;
        .registers 1

        packed-switch p0, :table
        const-string v0, "unknown"
        return-object v0

        :sponsor
        const-string v0, "sponsor"
        return-object v0

        :selfpromo
        const-string v0, "selfpromo"
        return-object v0

        :intro
        const-string v0, "intro"
        return-object v0

        :outro
        const-string v0, "outro"
        return-object v0

        :table
        .packed-switch 0x0
            :sponsor
            :selfpromo
            :intro
            :outro
        .end packed-switch
    .end method

    .method public static label(I)Ljava/lang/String;
        .registers 1

        packed-switch p0, :table
        const-string v0, "unknown"
        return-object v0

        :sponsor
        const-string v0, "Спонсор"
        return-object v0

        :selfpromo
        const-string v0, "Самореклама"
        return-object v0

        :intro
        const-string v0, "Интро"
        return-object v0

        :outro
        const-string v0, "Аутро"
        return-object v0

        :table
        .packed-switch 0x0
            :sponsor
            :selfpromo
            :intro
            :outro
        .end packed-switch
    .end method
""".trimIndent()

private fun segmentSmali(): String = """
    .class public final $SEGMENT_CLASS
    .super Ljava/lang/Object;

    .field public final startMs:J
    .field public endMs:J
    .field public final name:Ljava/lang/String;
    .field public category:I

    .method public constructor <init>(JJLjava/lang/String;I)V
        .registers 7

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        iput-wide p1, p0, $SEGMENT_CLASS->startMs:J
        iput-wide p3, p0, $SEGMENT_CLASS->endMs:J
        iput-object p5, p0, $SEGMENT_CLASS->name:Ljava/lang/String;
        iput p6, p0, $SEGMENT_CLASS->category:I
        return-void
    .end method
""".trimIndent()

private fun segmentOrderSmali(): String = """
    .class public final $SEGMENT_ORDER_CLASS
    .super Ljava/lang/Object;
    .implements Ljava/util/Comparator;

    .method public constructor <init>()V
        .registers 1

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        return-void
    .end method

    .method public compare(Ljava/lang/Object;Ljava/lang/Object;)I
        .registers 6
        .param p1, "left"
        .param p2, "right"

        check-cast p1, $SEGMENT_CLASS
        check-cast p2, $SEGMENT_CLASS
        iget-wide v0, p1, $SEGMENT_CLASS->startMs:J
        iget-wide v2, p2, $SEGMENT_CLASS->startMs:J
        cmp-long v4, v0, v2
        if-ltz v4, :less
        if-gtz v4, :greater
        const/4 v0, 0x0
        return v0

        :less
        const/4 v0, -0x1
        return v0

        :greater
        const/4 v0, 0x1
        return v0
    .end method
""".trimIndent()

private fun episodesSmali(): String = """
    .class public final $EPISODES_CLASS
    .super Ljava/lang/Object;

    .method public static read(Lcom/vk/dto/common/VideoFile;J)$SEGMENT_ARRAY
        .registers 18
        .param p0, "videoFile"
        .param p1, "durationMs"

        if-eqz p0, :empty
        invoke-interface {p0}, Lcom/vk/dto/common/VideoFile;->k3()Ljava/util/List;
        move-result-object v0
        if-eqz v0, :empty
        invoke-interface {v0}, Ljava/util/List;->isEmpty()Z
        move-result v1
        if-nez v1, :empty

        new-instance v2, Ljava/util/ArrayList;
        invoke-direct {v2}, Ljava/util/ArrayList;-><init>()V
        const/4 v3, 0x0

        :loop
        invoke-interface {v0}, Ljava/util/List;->size()I
        move-result v4
        if-ge v3, v4, :build
        invoke-virtual {v2}, Ljava/util/ArrayList;->size()I
        move-result v4
        const/16 v5, 0x1f5
        if-ge v4, v5, :build

        invoke-interface {v0, v3}, Ljava/util/List;->get(I)Ljava/lang/Object;
        move-result-object v4
        if-eqz v4, :advance
        check-cast v4, Lcom/vk/dto/common/VideoEpisode;
        iget v5, v4, Lcom/vk/dto/common/VideoEpisode;->a:I
        if-ltz v5, :advance
        iget-object v6, v4, Lcom/vk/dto/common/VideoEpisode;->b:Ljava/lang/String;
        if-eqz v6, :advance
        invoke-virtual {v6}, Ljava/lang/String;->trim()Ljava/lang/String;
        move-result-object v6
        invoke-virtual {v6}, Ljava/lang/String;->length()I
        move-result v7
        if-eqz v7, :advance
        int-to-long v8, v5
        const-wide/16 v10, 0x3e8
        mul-long/2addr v8, v10
        cmp-long v7, v8, p1
        if-gez v7, :advance

        new-instance v7, $SEGMENT_CLASS
        const-wide/16 v10, 0x0
        move-object v12, v6
        const/4 v13, -0x1
        invoke-direct/range {v7 .. v13}, $SEGMENT_CLASS-><init>(JJLjava/lang/String;I)V
        invoke-virtual {v2, v7}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

        :advance
        add-int/lit8 v3, v3, 0x1
        goto :loop

        :build
        invoke-virtual {v2}, Ljava/util/ArrayList;->size()I
        move-result v3
        if-eqz v3, :empty
        new-instance v13, $SEGMENT_ORDER_CLASS
        invoke-direct {v13}, $SEGMENT_ORDER_CLASS-><init>()V
        invoke-static {v2, v13}, Ljava/util/Collections;->sort(Ljava/util/List;Ljava/util/Comparator;)V

        const/16 v10, 0x1f4
        move v13, v3
        if-lt v3, v10, :sized
        move v13, v10

        :sized
        new-array v4, v13, $SEGMENT_ARRAY
        const/4 v5, 0x0
        const/4 v6, 0x0
        const-wide/16 v7, -0x1

        :close
        if-ge v6, v3, :closed
        invoke-virtual {v2, v6}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;
        move-result-object v9
        check-cast v9, $SEGMENT_CLASS
        iget-wide v10, v9, $SEGMENT_CLASS->startMs:J
        cmp-long v12, v10, v7
        if-nez v12, :append
        add-int/lit8 v12, v5, -0x1
        aput-object v9, v4, v12
        goto :next

        :append
        if-eqz v5, :store
        add-int/lit8 v12, v5, -0x1
        aget-object v12, v4, v12
        iput-wide v10, v12, $SEGMENT_CLASS->endMs:J

        :store
        move-wide v7, v10
        if-ge v5, v13, :next
        aput-object v9, v4, v5
        add-int/lit8 v5, v5, 0x1

        :next
        add-int/lit8 v6, v6, 0x1
        goto :close

        :closed
        const/16 v10, 0x1f4
        if-gt v3, v10, :no_end
        move-wide/from16 v8, p1
        add-int/lit8 v6, v5, -0x1
        aget-object v6, v4, v6
        iput-wide v8, v6, $SEGMENT_CLASS->endMs:J

        :no_end
        if-eq v5, v13, :done
        new-array v6, v5, $SEGMENT_ARRAY
        const/4 v7, 0x0
        invoke-static {v4, v7, v6, v7, v5}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V
        return-object v6

        :done
        return-object v4

        :empty
        const/4 v0, 0x0
        new-array v1, v0, $SEGMENT_ARRAY
        return-object v1
    .end method
""".trimIndent()

private fun rulesSmali(): String = """
    .class public final $RULES_CLASS
    .super Ljava/lang/Object;

    .field public static final OWNER_ID:J = ${LebedevRules.OWNER_ID}L
    .field public static final POSITIONAL_MAX_MS:J = ${LebedevRules.POSITIONAL_MAX_MS}L

    .method public static classify(JLjava/lang/String;IJJ)I
        .registers 14
        .param p2, "name"

        const-wide/32 v0, ${LebedevRules.OWNER_ID}
        cmp-long v2, p0, v0
        if-eqz v2, :ours
        const/4 v0, -0x1
        return v0

        :ours
        if-eqz p2, :positional
        invoke-virtual {p2}, Ljava/lang/String;->trim()Ljava/lang/String;
        move-result-object p2
${exactMatches()}
        const-string v0, "${LebedevRules.SPONSOR_PREFIX}"
        invoke-virtual {p2, v0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
        move-result v0
        if-nez v0, :sponsor

        :positional
        if-nez p3, :unmatched
        const-wide/32 v0, ${LebedevRules.POSITIONAL_MAX_MS}
        sub-long v2, p6, p4
        cmp-long v4, v2, v0
        if-gez v4, :unmatched
        const-wide/16 v0, 0x0
        cmp-long v2, p4, v0
        if-eqz v2, :sponsor

        :unmatched
        const/4 v0, -0x1
        return v0

        :sponsor
        const/4 v0, 0x0
        return v0

        :selfpromo
        const/4 v0, 0x1
        return v0

        :intro
        const/4 v0, 0x2
        return v0

        :outro
        const/4 v0, 0x3
        return v0
    .end method
""".trimIndent()

private fun exactMatches(): String = LebedevRules.EXACT.entries.joinToString("\n") { (name, category) ->
    val label = when (category) {
        Category.SELFPROMO -> "selfpromo"
        Category.INTRO -> "intro"
        Category.OUTRO -> "outro"
        else -> "sponsor"
    }
    """
        const-string v0, "$name"
        invoke-virtual {p2, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
        move-result v0
        if-nez v0, :$label
    """
}.trimEnd()

private fun behaviorSmali(): String = """
    .class public final $BEHAVIOR_CLASS
    .super Ljava/lang/Object;

    .field public static final SKIP_AUTOMATICALLY:I = 0x0
    .field public static final SHOW_SKIP_BUTTON:I = 0x1
    .field public static final DISABLED:I = 0x2

    .method public static parse(Ljava/lang/String;I)I
        .registers 3
        .param p0, "value"
        .param p1, "fallback"

        if-eqz p0, :fallback
        const-string v0, "SKIP_AUTOMATICALLY"
        invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
        move-result v0
        if-nez v0, :skip
        const-string v0, "SHOW_SKIP_BUTTON"
        invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
        move-result v0
        if-nez v0, :button
        const-string v0, "DISABLED"
        invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
        move-result v0
        if-nez v0, :disabled

        :fallback
        return p1

        :skip
        const/4 v0, 0x0
        return v0

        :button
        const/4 v0, 0x1
        return v0

        :disabled
        const/4 v0, 0x2
        return v0
    .end method

    .method public static literal(I)Ljava/lang/String;
        .registers 2

        packed-switch p0, :table
        const-string v0, "SHOW_SKIP_BUTTON"
        return-object v0

        :skip
        const-string v0, "SKIP_AUTOMATICALLY"
        return-object v0

        :button
        const-string v0, "SHOW_SKIP_BUTTON"
        return-object v0

        :disabled
        const-string v0, "DISABLED"
        return-object v0

        :table
        .packed-switch 0x0
            :skip
            :button
            :disabled
        .end packed-switch
    .end method
""".trimIndent()

private fun behaviorStoreSmali(): String = """
    .class public final $BEHAVIOR_STORE_CLASS
    .super Ljava/lang/Object;

    .field public static final KEY_ENABLED:Ljava/lang/String; = "$KEY_ENABLED"
    .field public static final KEY_SPONSOR:Ljava/lang/String; = "${PREF_FILE}_behavior_sponsor"
    .field public static final KEY_SELFPROMO:Ljava/lang/String; = "${PREF_FILE}_behavior_selfpromo"
    .field public static final KEY_INTRO:Ljava/lang/String; = "${PREF_FILE}_behavior_intro"
    .field public static final KEY_OUTRO:Ljava/lang/String; = "${PREF_FILE}_behavior_outro"

    .method private static prefs()Landroid/content/SharedPreferences;
        .registers 2

        const-string v0, "$PREF_FILE"
        invoke-static {v0}, Lcom/vk/core/preference/Preference;->f(Ljava/lang/String;)Landroid/content/SharedPreferences;
        move-result-object v1
        return-object v1
    .end method

    .method public static isEnabled()Z
        .registers 4

        invoke-static {}, $BEHAVIOR_STORE_CLASS->prefs()Landroid/content/SharedPreferences;
        move-result-object v0
        const-string v1, "$KEY_ENABLED"
        invoke-interface {v0, v1}, Landroid/content/SharedPreferences;->contains(Ljava/lang/String;)Z
        move-result v2
        if-eqz v2, :default
        const/4 v2, 0x0
        invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
        move-result v0
        return v0

        :default
        const/4 v0, 0x1
        return v0
    .end method

    .method public static key(I)Ljava/lang/String;
        .registers 1

        packed-switch p0, :table
        sget-object v0, $BEHAVIOR_STORE_CLASS->KEY_SPONSOR:Ljava/lang/String;
        return-object v0

        :sponsor
        sget-object v0, $BEHAVIOR_STORE_CLASS->KEY_SPONSOR:Ljava/lang/String;
        return-object v0

        :selfpromo
        sget-object v0, $BEHAVIOR_STORE_CLASS->KEY_SELFPROMO:Ljava/lang/String;
        return-object v0

        :intro
        sget-object v0, $BEHAVIOR_STORE_CLASS->KEY_INTRO:Ljava/lang/String;
        return-object v0

        :outro
        sget-object v0, $BEHAVIOR_STORE_CLASS->KEY_OUTRO:Ljava/lang/String;
        return-object v0

        :table
        .packed-switch 0x0
            :sponsor
            :selfpromo
            :intro
            :outro
        .end packed-switch
    .end method

    .method public static getBehavior(I)I
        .registers 5

        const/4 v3, $AUTO_SKIP_OVERRIDE
        if-gez v3, :forced
        invoke-static {p0}, $BEHAVIOR_STORE_CLASS->key(I)Ljava/lang/String;
        move-result-object v0
        invoke-static {}, $BEHAVIOR_STORE_CLASS->prefs()Landroid/content/SharedPreferences;
        move-result-object v1
        const/4 v2, 0x0
        invoke-interface {v1, v0, v2}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
        move-result-object v0
        const/4 v1, 0x1
        invoke-static {v0, v1}, $BEHAVIOR_CLASS;->parse(Ljava/lang/String;I)I
        move-result v0
        return v0

        :forced
        return v3
    .end method

    .method public static setEnabled(Z)V
        .registers 4
        .param p0, "enabled"

        invoke-static {}, $BEHAVIOR_STORE_CLASS->prefs()Landroid/content/SharedPreferences;
        move-result-object v0
        invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences${'$'}Editor;
        move-result-object v0
        const-string v1, "$KEY_ENABLED"
        invoke-interface {v0, v1, p0}, Landroid/content/SharedPreferences${'$'}Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences${'$'}Editor;
        move-result-object v0
        invoke-interface {v0}, Landroid/content/SharedPreferences${'$'}Editor;->apply()V
        return-void
    .end method

    .method public static setBehavior(ILjava/lang/String;)V
        .registers 6
        .param p0, "category"
        .param p1, "behavior"

        invoke-static {p0}, $BEHAVIOR_STORE_CLASS->key(I)Ljava/lang/String;
        move-result-object v0
        invoke-static {}, $BEHAVIOR_STORE_CLASS->prefs()Landroid/content/SharedPreferences;
        move-result-object v1
        invoke-interface {v1}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences${'$'}Editor;
        move-result-object v1
        invoke-interface {v1, v0, p1}, Landroid/content/SharedPreferences${'$'}Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences${'$'}Editor;
        move-result-object v0
        invoke-interface {v0}, Landroid/content/SharedPreferences${'$'}Editor;->apply()V
        return-void
    .end method
""".trimIndent()

private fun skipStateSmali(): String = """
    .class public final $SKIP_STATE_CLASS
    .super Ljava/lang/Object;

    .field public final controller:Lp73/a;
    .field public final segments:$SEGMENT_ARRAY
    .field public final consumed:[Z
    .field public final handler:Landroid/os/Handler;
    .field public tick:Ljava/lang/Runnable;
    .field public button:Landroid/widget/TextView;
    .field public view:Landroid/view/View;
    .field public listener:Landroid/view/View${'$'}OnLayoutChangeListener;
    .field public listenerView:Landroid/view/View;
    .field public buttonView:Landroid/view/View;
    .field public lastToastMs:J

    .method public constructor <init>(Lp73/a;$SEGMENT_ARRAY[ZLandroid/os/Handler;Ljava/lang/Runnable;)V
        .registers 7

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        iput-object p1, p0, $SKIP_STATE_CLASS->controller:Lp73/a;
        iput-object p2, p0, $SKIP_STATE_CLASS->segments:$SEGMENT_ARRAY
        iput-object p3, p0, $SKIP_STATE_CLASS->consumed:[Z
        iput-object p4, p0, $SKIP_STATE_CLASS->handler:Landroid/os/Handler;
        iput-object p5, p0, $SKIP_STATE_CLASS->tick:Ljava/lang/Runnable;
        const-wide/16 p1, 0x0
        iput-wide p1, p0, $SKIP_STATE_CLASS->lastToastMs:J
        return-void
    .end method
""".trimIndent()

private fun skipTickSmali(): String = """
    .class public final $SKIP_TICK_CLASS
    .super Ljava/lang/Object;
    .implements Ljava/lang/Runnable;

    .field private final state:$SKIP_STATE_CLASS;

    .method public constructor <init>($SKIP_STATE_CLASS;)V
        .registers 2

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        iput-object p1, p0, $SKIP_TICK_CLASS->state:$SKIP_STATE_CLASS;
        return-void
    .end method

    .method public run()V
        .registers 1

        iget-object v0, p0, $SKIP_TICK_CLASS->state:$SKIP_STATE_CLASS;
        invoke-static {v0}, $SKIP_ENGINE_CLASS->tick($SKIP_STATE_CLASS;)V
        return-void
    .end method
""".trimIndent()

/**
 * `onVideoChanged` guards a null video: `D(VideoFile)` has several invoking bodies —
 * the autoplay and catalog delegates, the compose lazy delegate, the live view and
 * `gg1/v` — none of which is audited for null.
 */
private fun skipEngineSmali(): String = """
    .class public final $SKIP_ENGINE_CLASS
    .super Ljava/lang/Object;

    .field private static final MAP:Ljava/util/WeakHashMap;
    .field private static final VIEWS:Ljava/util/WeakHashMap;
    .field private static LAST_VIEW:Landroid/view/View;
    .field private static ACTIVE:$SKIP_STATE_CLASS;

    .method static constructor <clinit>()V
        .registers 1

        new-instance v0, Ljava/util/WeakHashMap;
        invoke-direct {v0}, Ljava/util/WeakHashMap;-><init>()V
        sput-object v0, $SKIP_ENGINE_CLASS->MAP:Ljava/util/WeakHashMap;
        new-instance v0, Ljava/util/WeakHashMap;
        invoke-direct {v0}, Ljava/util/WeakHashMap;-><init>()V
        sput-object v0, $SKIP_ENGINE_CLASS->VIEWS:Ljava/util/WeakHashMap;
        return-void
    .end method

    .method public static active()$SKIP_STATE_CLASS;
        .registers 1

        sget-object v0, $SKIP_ENGINE_CLASS->ACTIVE:$SKIP_STATE_CLASS;
        return-object v0
    .end method

    .method public static log(Ljava/lang/String;)V
        .registers 2
        .param p0, "message"

        const-string v0, "SponsorSkip"
        invoke-static {v0, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
        return-void
    .end method

    .method public static onVideoChanged(Lp73/a;Lcom/vk/dto/common/VideoFile;)V
        .registers 16
        .param p0, "controller"
        .param p1, "videoFile"

        move-object/from16 v0, p0
        move-object/from16 v1, p1
        const-string v10, "hook"
        invoke-static {v10}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        if-eqz v0, :v0null
        if-eqz v1, :v1null
        invoke-static {}, $BEHAVIOR_STORE_CLASS;->isEnabled()Z
        move-result v2
        new-instance v10, Ljava/lang/StringBuilder;
        const-string v11, "isEnabled="
        invoke-direct {v10, v11}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
        invoke-virtual {v10, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
        invoke-virtual {v10}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
        move-result-object v10
        invoke-static {v10}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        const-string v10, "enabled"
        invoke-static {v10}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        invoke-interface {v1}, Lcom/vk/dto/common/VideoFile;->getDuration()I
        move-result v3
        new-instance v10, Ljava/lang/StringBuilder;
        const-string v11, "dur="
        invoke-direct {v10, v11}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
        invoke-virtual {v10, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
        invoke-virtual {v10}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
        move-result-object v10
        invoke-static {v10}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        invoke-interface {v1}, Lcom/vk/dto/common/VideoFile;->k3()Ljava/util/List;
        move-result-object v10
        new-instance v11, Ljava/lang/StringBuilder;
        const-string v12, "episodes="
        invoke-direct {v11, v12}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
        invoke-virtual {v11, v10}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;
        invoke-virtual {v11}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
        move-result-object v11
        invoke-static {v11}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        int-to-long v3, v3
        const-wide/16 v6, 0x3e8
        mul-long/2addr v3, v6
        invoke-static {v1, v3, v4}, $EPISODES_CLASS;->read(Lcom/vk/dto/common/VideoFile;J)$SEGMENT_ARRAY
        move-result-object v5
        array-length v6, v5
        new-instance v10, Ljava/lang/StringBuilder;
        const-string v11, "segments="
        invoke-direct {v10, v11}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
        invoke-virtual {v10, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
        invoke-virtual {v10}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
        move-result-object v10
        invoke-static {v10}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        if-eqz v6, :clear

        invoke-interface {v1}, Lcom/vk/dto/common/VideoFile;->P0()Lcom/vk/dto/common/id/UserId;
        move-result-object v7
        if-eqz v7, :clear
        invoke-virtual {v7}, Lcom/vk/dto/common/id/UserId;->c()J
        move-result-wide v8

        const/4 v2, 0x0
        const/4 v1, 0x0
        const/4 v11, 0x0

        :classify
        if-ge v11, v6, :classified
        aget-object v7, v5, v11
        iget-object v10, v7, $SEGMENT_CLASS->name:Ljava/lang/String;
        iget-wide v12, v7, $SEGMENT_CLASS->startMs:J
        iget-wide v14, v7, $SEGMENT_CLASS->endMs:J
        invoke-static/range {v8 .. v15}, $RULES_CLASS;->classify(JLjava/lang/String;IJJ)I
        move-result v10
        if-ltz v10, :next
        iput v10, v7, $SEGMENT_CLASS->category:I
        add-int/lit8 v2, v2, 0x1
        invoke-static {v10}, $BEHAVIOR_STORE_CLASS;->getBehavior(I)I
        move-result v7
        const/4 v14, 0x2
        if-eq v7, v14, :next
        const/4 v1, 0x1

        :next
        add-int/lit8 v11, v11, 0x1
        goto :classify

        :classified
        new-instance v10, Ljava/lang/StringBuilder;
        const-string v11, "kept="
        invoke-direct {v10, v11}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
        invoke-virtual {v10, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
        const-string v11, " enabled="
        invoke-virtual {v10, v11}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
        invoke-virtual {v10, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
        invoke-virtual {v10}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
        move-result-object v10
        invoke-static {v10}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        if-eqz v2, :clear

        new-instance v2, Landroid/os/Handler;
        invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;
        move-result-object v3
        invoke-direct {v2, v3}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V
        new-array v3, v6, [Z
        new-instance v10, $SKIP_STATE_CLASS;
        move-object v11, v0
        move-object v12, v5
        move-object v13, v3
        move-object v14, v2
        const/4 v15, 0x0
        invoke-direct/range {v10 .. v15}, $SKIP_STATE_CLASS;-><init>(Lp73/a;$SEGMENT_ARRAY[ZLandroid/os/Handler;Ljava/lang/Runnable;)V
        new-instance v11, $SKIP_TICK_CLASS;
        invoke-direct {v11, v10}, $SKIP_TICK_CLASS;-><init>($SKIP_STATE_CLASS;)V
        iput-object v11, v10, $SKIP_STATE_CLASS->tick:Ljava/lang/Runnable;
        check-cast v0, Lcom/vk/libvideo/autoplay/VideoAutoPlay;
        iget-object v12, v0, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->K:Ljava/lang/ref/WeakReference;
        if-eqz v12, :attached
        invoke-virtual {v12}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
        move-result-object v12
        check-cast v12, Landroid/view/View;
        if-eqz v12, :attached
        invoke-static {v0, v12}, $SKIP_ENGINE_CLASS;->rememberView(Lp73/a;Landroid/view/View;)V

        :attached
        invoke-static {v0, v10}, $SKIP_ENGINE_CLASS;->put(Lp73/a;$SKIP_STATE_CLASS;)V
        const-wide/16 v4, 0xfa
        invoke-virtual {v2, v11, v4, v5}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z
        return-void

        :v0null
        const-string v10, "v0null"
        invoke-static {v10}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        goto :return

        :v1null
        const-string v10, "v1null"
        invoke-static {v10}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        goto :clear

        :clear
        const-string v10, "clear"
        invoke-static {v10}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        invoke-static {v0}, $SKIP_ENGINE_CLASS;->clear(Lp73/a;)V

        :return
        const-string v10, "return"
        invoke-static {v10}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        return-void
    .end method

    .method public static onViewAttached(Lp73/a;Landroid/view/View;)V
        .registers 5
        .param p0, "controller"
        .param p1, "view"

        if-eqz p0, :return
        if-eqz p1, :return
        invoke-static {p0}, $SKIP_ENGINE_CLASS;->stateOf(Lp73/a;)$SKIP_STATE_CLASS;
        move-result-object v0
        invoke-static {p0, p1}, $SKIP_ENGINE_CLASS;->rememberView(Lp73/a;Landroid/view/View;)V
        if-eqz v0, :return
        iput-object p1, v0, $SKIP_STATE_CLASS->view:Landroid/view/View;

        :return
        return-void
    .end method

    .method private static rememberView(Lp73/a;Landroid/view/View;)V
        .registers 4

        if-eqz p0, :return
        if-eqz p1, :return
        sget-object v0, $SKIP_ENGINE_CLASS->VIEWS:Ljava/util/WeakHashMap;
        monitor-enter v0
        invoke-virtual {v0, p0, p1}, Ljava/util/WeakHashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
        monitor-exit v0
        sput-object p1, $SKIP_ENGINE_CLASS->LAST_VIEW:Landroid/view/View;

        :return
        return-void
    .end method

    .method public static viewOf($SKIP_STATE_CLASS;)Landroid/view/View;
        .registers 4

        iget-object v0, p0, $SKIP_STATE_CLASS->view:Landroid/view/View;
        if-eqz v0, :done
        iget-object v1, p0, $SKIP_STATE_CLASS->controller:Lp73/a;
        if-eqz v1, :last
        sget-object v2, $SKIP_ENGINE_CLASS->VIEWS:Ljava/util/WeakHashMap;
        monitor-enter v2
        invoke-virtual {v2, v1}, Ljava/util/WeakHashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object v0
        check-cast v0, Landroid/view/View;
        monitor-exit v2
        if-eqz v0, :done
        sget-object v0, $SKIP_ENGINE_CLASS->LAST_VIEW:Landroid/view/View;
        goto :done

        :last
        sget-object v0, $SKIP_ENGINE_CLASS->LAST_VIEW:Landroid/view/View;

        :done
        return-object v0
    .end method

    .method public static onRelease(Lp73/a;)V
        .registers 1
        .param p0, "controller"

        invoke-static {p0}, $SKIP_ENGINE_CLASS;->clear(Lp73/a;)V
        return-void
    .end method

    .method public static tick($SKIP_STATE_CLASS;)V
        .registers 16
        .param p0, "state"

        if-eqz p0, :return
        iget-object v0, p0, $SKIP_STATE_CLASS->controller:Lp73/a;
        if-eqz v0, :return
        check-cast v0, Lcom/vk/libvideo/autoplay/VideoAutoPlay;
        iget-object v2, v0, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->K:Ljava/lang/ref/WeakReference;
        if-eqz v2, :viewed
        invoke-virtual {v2}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
        move-result-object v2
        check-cast v2, Landroid/view/View;
        if-eqz v2, :viewed
        iput-object v2, p0, $SKIP_STATE_CLASS->view:Landroid/view/View;

        :viewed
        invoke-static {}, $SEEK_SECTIONS_CLASS;->invalidate()V
        invoke-static {}, $BEHAVIOR_STORE_CLASS;->isEnabled()Z
        move-result v3
        if-eqz v3, :hide
        invoke-static {p0}, $SKIP_ENGINE_CLASS;->evaluate($SKIP_STATE_CLASS;)I
        move-result v7
        if-ltz v7, :repost
        invoke-interface {v0}, Lp73/a;->isPlaying()Z
        move-result v8
        if-eqz v8, :repost
        const/4 v14, 0x1
        invoke-static {p0, v7, v14}, $SKIP_ENGINE_CLASS;->skip($SKIP_STATE_CLASS;IZ)V
        goto :repost

        :hide
        invoke-static {p0}, $SKIP_BUTTON_CLASS;->hide($SKIP_STATE_CLASS;)V

        :repost
        iget-object v0, p0, $SKIP_STATE_CLASS->handler:Landroid/os/Handler;
        if-eqz v0, :return
        iget-object v1, p0, $SKIP_STATE_CLASS->tick:Ljava/lang/Runnable;
        if-eqz v1, :return
        const-wide/16 v2, 0xfa
        invoke-virtual {v0, v1, v2, v3}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z

        :return
        return-void
    .end method

    .method private static evaluate($SKIP_STATE_CLASS;)I
        .registers 16
        .param p0, "state"

        const/4 v14, -0x1
        if-eqz p0, :hidden
        iget-object v0, p0, $SKIP_STATE_CLASS->controller:Lp73/a;
        if-eqz v0, :hidden
        invoke-static {}, $BEHAVIOR_STORE_CLASS;->isEnabled()Z
        move-result v3
        if-eqz v3, :hidden
        invoke-interface {v0}, Lp73/a;->getPosition()J
        move-result-wide v1
        iget-object v3, p0, $SKIP_STATE_CLASS->segments:$SEGMENT_ARRAY
        array-length v4, v3
        iget-object v5, p0, $SKIP_STATE_CLASS->consumed:[Z
        const/4 v6, 0x0
        const/4 v7, -0x1

        :scan
        if-ge v6, v4, :scanned
        aget-boolean v8, v5, v6
        if-nez v8, :next
        aget-object v9, v3, v6
        iget-wide v10, v9, $SEGMENT_CLASS->startMs:J
        const-wide/16 v12, 0x2ee
        add-long v12, v1, v12
        cmp-long v8, v12, v10
        if-ltz v8, :next
        iget-wide v10, v9, $SEGMENT_CLASS->endMs:J
        const-wide/16 v12, 0x2ee
        sub-long v12, v1, v12
        cmp-long v8, v12, v10
        if-lez v8, :found

        :next
        add-int/lit8 v6, v6, 0x1
        goto :scan

        :found
        move v7, v6

        :scanned
        if-ltz v7, :hidden
        aget-object v9, v3, v7
        iget v8, v9, $SEGMENT_CLASS->category:I
        if-ltz v8, :hidden
        invoke-static {v8}, $BEHAVIOR_STORE_CLASS;->getBehavior(I)I
        move-result v8
        if-eqz v8, :auto
        const/4 v10, 0x2
        if-eq v8, v10, :hidden
        invoke-static {p0, v7}, $SKIP_BUTTON_CLASS;->show($SKIP_STATE_CLASS;I)V
        return v14

        :auto
        invoke-static {p0}, $SKIP_BUTTON_CLASS;->hide($SKIP_STATE_CLASS;)V
        return v7

        :hidden
        invoke-static {p0}, $SKIP_BUTTON_CLASS;->hide($SKIP_STATE_CLASS;)V
        return v14
    .end method

    .method public static onSeek(Ljava/lang/Object;J)V
        .registers 9
        .param p0, "player"
        .param p1, "position"

        sget-object v0, $SKIP_ENGINE_CLASS;->MAP:Ljava/util/WeakHashMap;
        monitor-enter v0
        invoke-virtual {v0}, Ljava/util/WeakHashMap;->values()Ljava/util/Collection;
        move-result-object v1
        new-instance v2, Ljava/util/ArrayList;
        invoke-direct {v2, v1}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V
        monitor-exit v0
        invoke-virtual {v2}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;
        move-result-object v3

        :loop
        invoke-interface {v3}, Ljava/util/Iterator;->hasNext()Z
        move-result v4
        if-eqz v4, :return
        invoke-interface {v3}, Ljava/util/Iterator;->next()Ljava/lang/Object;
        move-result-object v4
        check-cast v4, $SKIP_STATE_CLASS;
        invoke-static {v4}, $SKIP_ENGINE_CLASS;->evaluate($SKIP_STATE_CLASS;)I
        move-result v5
        goto :loop

        :return
        return-void
    .end method

    .method public static skip($SKIP_STATE_CLASS;IZ)V
        .registers 12
        .param p0, "state"
        .param p1, "index"
        .param p2, "toast"

        if-eqz p0, :return
        iget-object v0, p0, $SKIP_STATE_CLASS->segments:$SEGMENT_ARRAY
        array-length v1, v0
        if-ge p1, v1, :return
        if-ltz p1, :return
        iget-object v1, p0, $SKIP_STATE_CLASS->consumed:[Z
        aget-boolean v2, v1, p1
        if-nez v2, :return
        const/4 v2, 0x1
        aput-boolean v2, v1, p1
        aget-object v0, v0, p1
        iget-wide v2, v0, $SEGMENT_CLASS->endMs:J
        iget-object v0, p0, $SKIP_STATE_CLASS->controller:Lp73/a;
        invoke-interface {v0}, Lp73/a;->getDuration()J
        move-result-wide v4
        const-wide/16 v6, 0x0
        cmp-long v7, v4, v6
        if-lez v7, :seek
        cmp-long v7, v4, v2
        if-gez v7, :seek
        move-wide v2, v4
        :seek
        invoke-interface {v0, v2, v3}, Lp73/a;->s(J)V
        invoke-static {p0}, $SKIP_BUTTON_CLASS;->hide($SKIP_STATE_CLASS;)V

        if-eqz p2, :return
        invoke-static {p0}, $SKIP_ENGINE_CLASS;->viewOf($SKIP_STATE_CLASS;)Landroid/view/View;
        move-result-object v0
        if-eqz v0, :return
        invoke-virtual {v0}, Landroid/view/View;->getContext()Landroid/content/Context;
        move-result-object v1
        if-eqz v1, :return
        iget-object v2, p0, $SKIP_STATE_CLASS->segments:$SEGMENT_ARRAY
        aget-object v2, v2, p1
        iget v2, v2, $SEGMENT_CLASS->category:I
        invoke-static {v2}, $CATEGORY_CLASS;->label(I)Ljava/lang/String;
        move-result-object v3
        const-string v4, "Пропущено: "
        invoke-virtual {v4, v3}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
        move-result-object v3
        invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
        move-result-wide v4
        iget-wide v6, p0, $SKIP_STATE_CLASS->lastToastMs:J
        sub-long v6, v4, v6
        const-wide/16 v4, 0x4b0
        cmp-long v8, v6, v4
        if-gtz v8, :toast_go
        goto :return

        :toast_go
        invoke-static {v3}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        const/4 v2, 0x0
        invoke-static {v1, v3, v2}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;
        move-result-object v3
        invoke-virtual {v3}, Landroid/widget/Toast;->show()V
        invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
        move-result-wide v6
        iput-wide v6, p0, $SKIP_STATE_CLASS->lastToastMs:J

        :return
        return-void
    .end method

    .method private static stateOf(Lp73/a;)$SKIP_STATE_CLASS;
        .registers 4

        sget-object v0, $SKIP_ENGINE_CLASS->MAP:Ljava/util/WeakHashMap;
        monitor-enter v0
        invoke-virtual {v0, p0}, Ljava/util/WeakHashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object v1
        check-cast v1, $SKIP_STATE_CLASS;
        monitor-exit v0
        return-object v1
    .end method

    .method private static put(Lp73/a;$SKIP_STATE_CLASS;)V
        .registers 5

        sget-object v0, $SKIP_ENGINE_CLASS->MAP:Ljava/util/WeakHashMap;
        monitor-enter v0
        invoke-virtual {v0, p0}, Ljava/util/WeakHashMap;->remove(Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object v1
        check-cast v1, $SKIP_STATE_CLASS;
        if-eqz v1, :store
        invoke-static {v1}, $SKIP_ENGINE_CLASS;->stop($SKIP_STATE_CLASS;)V

        :store
        invoke-virtual {v0, p0, p1}, Ljava/util/WeakHashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
        monitor-exit v0
        sput-object p1, $SKIP_ENGINE_CLASS->ACTIVE:$SKIP_STATE_CLASS;
        invoke-static {p1}, $SKIP_ENGINE_CLASS;->viewOf($SKIP_STATE_CLASS;)Landroid/view/View;
        move-result-object v0
        if-eqz v0, :return
        iput-object v0, p1, $SKIP_STATE_CLASS->view:Landroid/view/View;

        :return
        return-void
    .end method

    .method private static clear(Lp73/a;)V
        .registers 4

        if-eqz p0, :return
        sget-object v0, $SKIP_ENGINE_CLASS->MAP:Ljava/util/WeakHashMap;
        monitor-enter v0
        invoke-virtual {v0, p0}, Ljava/util/WeakHashMap;->remove(Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object v1
        check-cast v1, $SKIP_STATE_CLASS;
        monitor-exit v0
        if-eqz v1, :return
        invoke-static {v1}, $SKIP_ENGINE_CLASS;->stop($SKIP_STATE_CLASS;)V
        sget-object v2, $SKIP_ENGINE_CLASS->ACTIVE:$SKIP_STATE_CLASS;
        if-ne v2, v1, :return
        const/4 v1, 0x0
        sput-object v1, $SKIP_ENGINE_CLASS->ACTIVE:$SKIP_STATE_CLASS;

        :return
        return-void
    .end method

    .method private static stop($SKIP_STATE_CLASS;)V
        .registers 4

        iget-object v0, p0, $SKIP_STATE_CLASS->handler:Landroid/os/Handler;
        if-eqz v0, :button
        iget-object v1, p0, $SKIP_STATE_CLASS->tick:Ljava/lang/Runnable;
        if-eqz v1, :button
        invoke-virtual {v0, v1}, Landroid/os/Handler;->removeCallbacks(Ljava/lang/Runnable;)V

        :button
        iget-object v2, p0, $SKIP_STATE_CLASS->listener:Landroid/view/View${'$'}OnLayoutChangeListener;
        if-eqz v2, :detach
        iget-object v0, p0, $SKIP_STATE_CLASS->listenerView:Landroid/view/View;
        if-eqz v0, :button_detach
        invoke-virtual {v0, v2}, Landroid/view/View;->removeOnLayoutChangeListener(Landroid/view/View${'$'}OnLayoutChangeListener;)V

        :button_detach
        iget-object v0, p0, $SKIP_STATE_CLASS->buttonView:Landroid/view/View;
        if-eqz v0, :detach
        invoke-virtual {v0, v2}, Landroid/view/View;->removeOnLayoutChangeListener(Landroid/view/View${'$'}OnLayoutChangeListener;)V

        :detach
        const/4 v0, 0x0
        iput-object v0, p0, $SKIP_STATE_CLASS->listenerView:Landroid/view/View;
        iput-object v0, p0, $SKIP_STATE_CLASS->buttonView:Landroid/view/View;
        iget-object v0, p0, $SKIP_STATE_CLASS->button:Landroid/widget/TextView;
        if-eqz v0, :overlay
        invoke-virtual {v0}, Landroid/view/View;->getParent()Landroid/view/ViewParent;
        move-result-object v1
        if-eqz v1, :overlay
        check-cast v1, Landroid/view/ViewGroup;
        invoke-virtual {v1, v0}, Landroid/view/ViewGroup;->removeView(Landroid/view/View;)V

        :overlay
        const/4 v0, 0x0
        iput-object v0, p0, $SKIP_STATE_CLASS->view:Landroid/view/View;

        :return
        return-void
    .end method
""".trimIndent()

private fun bandSmali(): String = """
    .class public final $BAND_CLASS
    .super Ljava/lang/Object;

    .field public final xStart:F
    .field public final xEnd:F
    .field public final lane:I
    .field public final category:I

    .method public constructor <init>(FFII)V
        .registers 5

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        iput p1, p0, $BAND_CLASS->xStart:F
        iput p2, p0, $BAND_CLASS->xEnd:F
        iput p3, p0, $BAND_CLASS->lane:I
        iput p4, p0, $BAND_CLASS->category:I
        return-void
    .end method
""".trimIndent()

private fun rangesSmali(): String = """
    .class public final $RANGES_CLASS
    .super Ljava/lang/Object;

    .method public static build($SEGMENT_ARRAY;J)$BAND_ARRAY
        .registers 26
        .param p0, "segments"
        .param p1, "durationMs"

        move-object/from16 v0, p0
        move-wide/from16 v1, p1
        sget-object v16, $SECTIONS_DRAWABLE_CLASS;->BEHAVIORS:[I

        if-eqz v0, :empty
        array-length v3, v0
        if-eqz v3, :empty
        const-wide/16 v4, 0x0
        cmp-long v18, v1, v4
        if-lez v18, :empty

        new-instance v4, Ljava/util/ArrayList;
        invoke-direct {v4}, Ljava/util/ArrayList;-><init>()V
        const/4 v15, 0x3
        new-array v5, v15, [F
        const/high16 v10, -0x3f800000
        const/4 v6, 0x0

        :init
        if-ge v6, v15, :inited
        aput v10, v5, v6
        add-int/lit8 v6, v6, 0x1
        goto :init

        :inited
        const v19, 0x3a83126f
        const v20, 0x3f800000
        long-to-float v13, v1
        move/from16 v21, v13
        const/4 v7, 0x0

        :loop
        if-ge v7, v3, :done
        aget-object v8, v0, v7
        iget v9, v8, $SEGMENT_CLASS->category:I
        if-ltz v9, :next
        aget v6, v16, v9
        const/4 v10, 0x2
        if-eq v6, v10, :next

        iget-wide v13, v8, $SEGMENT_CLASS->startMs:J
        long-to-float v13, v13
        div-float v11, v13, v21
        iget-wide v13, v8, $SEGMENT_CLASS->endMs:J
        long-to-float v13, v13
        div-float v12, v13, v21
        new-instance v13, Ljava/lang/StringBuilder;
        const-string v14, "frac "
        invoke-direct {v13, v14}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
        invoke-virtual {v13, v11}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;
        const-string v14, " / "
        invoke-virtual {v13, v14}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
        invoke-virtual {v13, v12}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;
        const-string v14, " d="
        invoke-virtual {v13, v14}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
        move/from16 v14, v21
        invoke-virtual {v13, v14}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;
        invoke-virtual {v13}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
        move-result-object v13
        invoke-static/range {v13}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        const/4 v13, 0x0
        cmpl-float v14, v11, v13
        if-gez v14, :start_pos
        const/4 v11, 0x0

        :start_pos
        cmpg-float v14, v11, v20
        if-lez v14, :start_max
        move/from16 v11, v20

        :start_max
        cmpg-float v14, v12, v20
        if-lez v14, :end_max
        move/from16 v12, v20

        :end_max
        cmpl-float v14, v12, v11
        if-gtz v14, :width_ok
        add-float v12, v11, v19
        cmpg-float v14, v12, v20
        if-lez v14, :width_ok
        move/from16 v12, v20

        :width_ok
        const/4 v10, -0x1
        const/4 v6, 0x0

        :lane
        if-lt v6, v15, :lane_body
        goto :laned

        :lane_body
        aget v13, v5, v6
        cmpl-float v14, v13, v11
        if-ltz v14, :accept
        add-int/lit8 v6, v6, 0x1
        goto :lane

        :accept
        move v10, v6

        :laned
        if-gez v10, :lane_ok
        const/4 v10, 0x2

        :lane_ok
        aget v13, v5, v10
        add-float v13, v13, v19
        cmpl-float v14, v11, v13
        if-gez v14, :gap
        move v11, v13

        :gap
        cmpg-float v14, v12, v20
        if-lez v14, :end_ok
        move/from16 v12, v20

        :end_ok
        cmpl-float v14, v12, v11
        if-gtz v14, :gap_ok
        add-float v12, v11, v19
        cmpg-float v14, v12, v20
        if-lez v14, :min_ok
        move/from16 v12, v20

        :min_ok
        sub-float v11, v12, v19

        :gap_ok
        new-instance v13, Ljava/lang/StringBuilder;
        const-string v14, "pre "
        invoke-direct {v13, v14}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
        invoke-virtual {v13, v11}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;
        const-string v14, " "
        invoke-virtual {v13, v14}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
        invoke-virtual {v13, v12}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;
        invoke-virtual {v13}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
        move-result-object v13
        invoke-static/range {v13}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        aput v12, v5, v10
        new-instance v13, $BAND_CLASS;
        invoke-direct {v13, v11, v12, v10, v9}, $BAND_CLASS;-><init>(FFII)V
        invoke-virtual {v4, v13}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

        :next
        add-int/lit8 v7, v7, 0x1
        goto :loop

        :done
        invoke-virtual {v4}, Ljava/util/ArrayList;->size()I
        move-result v3
        new-array v5, v3, $BAND_ARRAY
        invoke-virtual {v4, v5}, Ljava/util/ArrayList;->toArray([Ljava/lang/Object;)[Ljava/lang/Object;
        move-result-object v4
        check-cast v4, $BAND_ARRAY
        return-object v4

        :empty
        const/4 v3, 0x0
        new-array v3, v3, $BAND_ARRAY
        return-object v3
    .end method
""".trimIndent()

private fun sectionsDrawableSmali(): String = """
    .class public final $SECTIONS_DRAWABLE_CLASS
    .super Landroid/graphics/drawable/Drawable;

    .field private paint:Landroid/graphics/Paint;
    .field private alpha:I
    .field private static CACHE_STAMP:I
    .field public static BEHAVIORS:[I
    .field private static CACHE_BANDS:$BAND_ARRAY

    .method public constructor <init>()V
        .registers 4

        invoke-direct {p0}, Landroid/graphics/drawable/Drawable;-><init>()V
        const/16 v0, 0xff
        iput v0, p0, $SECTIONS_DRAWABLE_CLASS;->alpha:I
        new-instance v0, Landroid/graphics/Paint;
        invoke-direct {v0}, Landroid/graphics/Paint;-><init>()V
        const/4 v1, 0x0
        invoke-virtual {v0, v1}, Landroid/graphics/Paint;->setAntiAlias(Z)V
        sget-object v1, Landroid/graphics/Paint${'$'}Style;->FILL:Landroid/graphics/Paint${'$'}Style;
        invoke-virtual {v0, v1}, Landroid/graphics/Paint;->setStyle(Landroid/graphics/Paint${'$'}Style;)V
        iput-object v0, p0, $SECTIONS_DRAWABLE_CLASS;->paint:Landroid/graphics/Paint;
        return-void
    .end method

    .method public constructor <init>()V
        .registers 1

        invoke-direct {p0}, Landroid/graphics/drawable/Drawable;-><init>()V
        return-void
    .end method

    .method public setBounds(IIII)V
        .registers 8

        new-instance v0, Ljava/lang/StringBuilder;
        const-string v1, "sb "
        invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
        invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
        const-string v1, ","
        invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
        invoke-virtual {v0, p2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
        const-string v1, " "
        invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
        invoke-virtual {v0, p3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
        const-string v1, ","
        invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
        invoke-virtual {v0, p4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
        invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
        move-result-object v0
        invoke-static {v0}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        invoke-super {p0, p1, p2, p3, p4}, Landroid/graphics/drawable/Drawable;->setBounds(IIII)V
        return-void
    .end method

    .method public setAlpha(I)V
        .registers 3

        move-object v0, p0
        iput p1, v0, $SECTIONS_DRAWABLE_CLASS;->alpha:I
        invoke-virtual {v0}, Landroid/graphics/drawable/Drawable;->invalidateSelf()V
        return-void
    .end method

    .method public getAlpha()I
        .registers 2

        iget v0, p0, $SECTIONS_DRAWABLE_CLASS;->alpha:I
        return v0
    .end method

    .method public setColorFilter(Landroid/graphics/ColorFilter;)V
        .registers 3

        move-object v0, p0
        iget-object v1, v0, $SECTIONS_DRAWABLE_CLASS;->paint:Landroid/graphics/Paint;
        invoke-virtual {v1, p1}, Landroid/graphics/Paint;->setColorFilter(Landroid/graphics/ColorFilter;)V
        invoke-virtual {v0}, Landroid/graphics/drawable/Drawable;->invalidateSelf()V
        return-void
    .end method

    .method public getOpacity()I
        .registers 1

        const/4 v0, -0x3
        return v0
    .end method

    .method public onDraw(Landroid/graphics/Canvas;)V
        .registers 2

        invoke-virtual {p0, p1}, $SECTIONS_DRAWABLE_CLASS;->draw(Landroid/graphics/Canvas;)V
        return-void
    .end method

    .method public draw(Landroid/graphics/Canvas;)V
        .registers 16
        .param p1, "canvas"

        move-object/from16 v8, p1
        invoke-static {}, $SKIP_ENGINE_CLASS;->active()$SKIP_STATE_CLASS;
        move-result-object v0
        if-eqz v0, :return
        invoke-static {}, $BEHAVIOR_STORE_CLASS;->isEnabled()Z
        move-result v1
        if-eqz v1, :return
        iget-object v1, v0, $SKIP_STATE_CLASS->segments:$SEGMENT_ARRAY
        if-eqz v1, :return
        array-length v2, v1
        if-eqz v2, :return
        iget-object v3, v0, $SKIP_STATE_CLASS->controller:Lp73/a;
        if-eqz v3, :return
        invoke-interface {v3}, Lp73/a;->getDuration()J
        move-result-wide v4
        const-wide/16 v6, 0x0
        cmp-long v2, v4, v6
        if-lez v2, :return

        const/4 v3, 0x4
        new-array v15, v3, [I
        const/4 v7, 0x0
        const/4 v9, 0x0

        :stamp
        const/4 v3, 0x4
        if-ge v9, v3, :stamped
        invoke-static {v9}, $BEHAVIOR_STORE_CLASS;->getBehavior(I)I
        move-result v3
        aput v3, v15, v9
        mul-int/lit8 v7, v7, 0x1f
        add-int/2addr v7, v3
        add-int/lit8 v9, v9, 0x1
        goto :stamp

        :stamped
        invoke-static {v1}, Ljava/lang/System;->identityHashCode(Ljava/lang/Object;)I
        move-result v3
        mul-int/lit8 v7, v7, 0x1f
        add-int/2addr v7, v3
        long-to-int v3, v4
        mul-int/lit8 v7, v7, 0x1f
        add-int/2addr v7, v3

        sget v3, $SECTIONS_DRAWABLE_CLASS;->CACHE_STAMP:I
        if-eq v7, v3, :cached
        sput v7, $SECTIONS_DRAWABLE_CLASS;->CACHE_STAMP:I
        sput-object v15, $SECTIONS_DRAWABLE_CLASS;->BEHAVIORS:[I
        invoke-static {v1, v4, v5}, $RANGES_CLASS;->build($SEGMENT_ARRAY;J)$BAND_ARRAY
        move-result-object v3
        sput-object v3, $SECTIONS_DRAWABLE_CLASS;->CACHE_BANDS:$BAND_ARRAY

        :cached
        sget-object v1, $SECTIONS_DRAWABLE_CLASS;->CACHE_BANDS:$BAND_ARRAY
        if-eqz v1, :return
        array-length v0, v1
        if-eqz v0, :return

        invoke-virtual {p0}, Landroid/graphics/drawable/Drawable;->getBounds()Landroid/graphics/Rect;
        move-result-object v3
        invoke-virtual {v3}, Landroid/graphics/Rect;->width()I
        move-result v9
        if-lez v9, :return
        invoke-virtual {v3}, Landroid/graphics/Rect;->height()I
        move-result v4
        if-lez v4, :return

        iget v10, v3, Landroid/graphics/Rect;->top:I
        iget v3, v3, Landroid/graphics/Rect;->left:I
        int-to-float v3, v3
        int-to-float v4, v4
        int-to-float v10, v10
        int-to-float v2, v9
        const v9, 0x3e800000
        mul-float v9, v9, v4
        add-float v5, v10, v9
        const v9, 0x3f000000
        mul-float v9, v9, v4
        const v10, 0x40400000
        div-float v4, v9, v10

        iget-object v13, p0, $SECTIONS_DRAWABLE_CLASS;->paint:Landroid/graphics/Paint;
        iget v15, p0, $SECTIONS_DRAWABLE_CLASS;->alpha:I
        mul-int/lit16 v15, v15, 0xb2
        div-int/lit16 v15, v15, 0xff
        const/4 v6, 0x0

        :loop
        if-ge v6, v0, :return
        aget-object v7, v1, v6
        iget v14, v7, $BAND_CLASS->category:I
        invoke-static {v14}, $BEHAVIOR_STORE_CLASS;->getBehavior(I)I
        move-result v14
        const/4 v10, 0x2
        if-eq v14, v10, :next

        iget v9, v7, $BAND_CLASS->xStart:F
        mul-float v9, v9, v2
        add-float v9, v9, v3
        iget v11, v7, $BAND_CLASS->xEnd:F
        mul-float v11, v11, v2
        add-float v11, v11, v3
        iget v10, v7, $BAND_CLASS->lane:I
        int-to-float v10, v10
        mul-float v10, v10, v4
        add-float v10, v10, v5
        add-float v12, v10, v4
        iget v14, v7, $BAND_CLASS->category:I
        invoke-static {v14}, $CATEGORY_CLASS;->color(I)I
        move-result v14
        invoke-virtual {v13, v14}, Landroid/graphics/Paint;->setColor(I)V
        invoke-virtual {v13, v15}, Landroid/graphics/Paint;->setAlpha(I)V
        invoke-virtual/range {v8 .. v13}, Landroid/graphics/Canvas;->drawRect(FFFFLandroid/graphics/Paint;)V

        :next
        add-int/lit8 v6, v6, 0x1
        goto :loop

        :return
        return-void
    .end method
""".trimIndent()

private fun seekbarSectionsSmali(): String = """
    .class public final $SEEK_SECTIONS_CLASS
    .super Ljava/lang/Object;

    .field private static CURRENT:Ljava/lang/ref/WeakReference;

    .method public static invalidate()V
        .registers 2

        sget-object v0, $SEEK_SECTIONS_CLASS;->CURRENT:Ljava/lang/ref/WeakReference;
        if-eqz v0, :return
        invoke-virtual {v0}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
        move-result-object v0
        check-cast v0, Landroid/graphics/drawable/Drawable;
        if-eqz v0, :return
        invoke-virtual {v0}, Landroid/graphics/drawable/Drawable;->invalidateSelf()V

        :return
        return-void
    .end method

    .method public static apply(Landroid/view/View;Landroid/graphics/drawable/Drawable;)V
        .registers 7

        if-eqz p1, :return
        instance-of v0, p1, Loo7/c;
        if-nez v0, :family
        instance-of v0, p1, Lyo7/c;
        if-eqz v0, :return

        :family
        invoke-virtual {p1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;
        move-result-object v4
        invoke-virtual {v4}, Ljava/lang/Class;->getName()Ljava/lang/String;
        move-result-object v4
        invoke-static {v4}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        invoke-static {}, $SKIP_ENGINE_CLASS;->active()$SKIP_STATE_CLASS;
        move-result-object v0
        if-eqz v0, :act_null

        move-object v4, p1
        check-cast v4, Landroid/graphics/drawable/LayerDrawable;
        invoke-virtual {v4}, Landroid/graphics/drawable/LayerDrawable;->getNumberOfLayers()I
        move-result v3
        new-instance v2, Ljava/lang/StringBuilder;
        const-string v4, "a-layers="
        invoke-direct {v2, v4}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
        invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
        invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
        move-result-object v2
        invoke-static {v2}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V

        move-object v0, p1
        check-cast v0, Landroid/graphics/drawable/LayerDrawable;
        invoke-virtual {v0}, Landroid/graphics/drawable/LayerDrawable;->getNumberOfLayers()I
        move-result v1
        if-lez v1, :return

        const/4 v1, 0x0
        invoke-virtual {v0, v1}, Landroid/graphics/drawable/LayerDrawable;->getDrawable(I)Landroid/graphics/drawable/Drawable;
        move-result-object v2
        invoke-virtual {v2}, Landroid/graphics/drawable/Drawable;->getBounds()Landroid/graphics/Rect;
        move-result-object v3
        instance-of v4, v2, $SECTIONS_DRAWABLE_CLASS;
        if-eqz v4, :create
        goto :reuse

        :create
        new-instance v2, $SECTIONS_DRAWABLE_CLASS;
        invoke-direct {v2}, $SECTIONS_DRAWABLE_CLASS;-><init>()V
        invoke-virtual {v2}, Ljava/lang/Object;->getClass()Ljava/lang/Class;
        move-result-object v4
        invoke-virtual {v4}, Ljava/lang/Class;->getName()Ljava/lang/String;
        move-result-object v4
        invoke-static {v4}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        invoke-virtual {v0, v1, v2}, Landroid/graphics/drawable/LayerDrawable;->setDrawable(ILandroid/graphics/drawable/Drawable;)V
        const-string v4, "post-set"
        invoke-static {v4}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V

        :reuse
        const/4 v4, 0x0
        invoke-virtual {p0}, Landroid/view/View;->getWidth()I
        move-result v0
        invoke-virtual {p0}, Landroid/view/View;->getHeight()I
        move-result v1
        invoke-virtual {v2, v4, v4, v0, v1}, Landroid/graphics/drawable/Drawable;->setBounds(IIII)V
        new-instance v0, Ljava/lang/ref/WeakReference;
        invoke-direct {v0, v2}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V
        sput-object v0, $SEEK_SECTIONS_CLASS;->CURRENT:Ljava/lang/ref/WeakReference;
        invoke-virtual {v2}, Landroid/graphics/drawable/Drawable;->invalidateSelf()V

        :return
        return-void

        :act_null
        const-string v4, "a-null"
        invoke-static {v4}, $SKIP_ENGINE_CLASS;->log(Ljava/lang/String;)V
        return-void
    .end method
""".trimIndent()

private fun skipButtonSmali(): String = """
    .class public final $SKIP_BUTTON_CLASS
    .super Ljava/lang/Object;

    .method public static dp(Landroid/content/Context;I)I
        .registers 4

        invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
        move-result-object v0
        invoke-virtual {v0}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;
        move-result-object v0
        iget v0, v0, Landroid/util/DisplayMetrics;->density:F
        int-to-float v1, p1
        mul-float/2addr v0, v1
        float-to-int v0, v0
        return v0
    .end method

    .method public static show($SKIP_STATE_CLASS;I)V
        .registers 14
        .param p0, "state"
        .param p1, "index"

        if-eqz p0, :return
        invoke-static {p0}, $SKIP_ENGINE_CLASS;->viewOf($SKIP_STATE_CLASS;)Landroid/view/View;
        move-result-object v0
        if-eqz v0, :return
        iput-object v0, p0, $SKIP_STATE_CLASS->view:Landroid/view/View;
        iget-object v1, p0, $SKIP_STATE_CLASS->segments:$SEGMENT_ARRAY
        array-length v2, v1
        if-ge p1, v2, :return
        if-ltz p1, :return
        aget-object v1, v1, p1

        iget-object v2, p0, $SKIP_STATE_CLASS->button:Landroid/widget/TextView;
        if-nez v2, :update

        invoke-virtual {v0}, Landroid/view/View;->getContext()Landroid/content/Context;
        move-result-object v3
        new-instance v2, Landroid/widget/TextView;
        invoke-direct {v2, v3}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V
        const v4, -0x33ffffff
        invoke-virtual {v2, v4}, Landroid/widget/TextView;->setBackgroundColor(I)V
        const/16 v4, 0xc
        invoke-static {v3, v4}, $SKIP_BUTTON_CLASS;->dp(Landroid/content/Context;I)I
        move-result v4
        invoke-virtual {v2, v4, v4, v4, v4}, Landroid/widget/TextView;->setPadding(IIII)V
        const/4 v4, 0x2
        const/high16 v5, 0x41600000
        invoke-virtual {v2, v4, v5}, Landroid/widget/TextView;->setTextSize(IF)V
        const/16 v4, 0x11
        invoke-virtual {v2, v4}, Landroid/widget/TextView;->setGravity(I)V
        iput-object v2, p0, $SKIP_STATE_CLASS->button:Landroid/widget/TextView;

        invoke-virtual {v0}, Landroid/view/View;->getRootView()Landroid/view/View;
        move-result-object v4
        const v5, 0x1020002
        invoke-virtual {v4, v5}, Landroid/view/View;->findViewById(I)Landroid/view/View;
        move-result-object v5
        if-nez v5, :parent
        invoke-virtual {v0}, Landroid/view/View;->getParent()Landroid/view/ViewParent;
        move-result-object v5

        :parent
        if-eqz v5, :update
        check-cast v5, Landroid/view/ViewGroup;
        new-instance v4, Landroid/widget/FrameLayout${'$'}LayoutParams;
        const/4 v6, -0x2
        invoke-direct {v4, v6, v6}, Landroid/widget/FrameLayout${'$'}LayoutParams;-><init>(II)V
        const v6, 0x800055
        iput v6, v4, Landroid/widget/FrameLayout${'$'}LayoutParams;->gravity:I
        const/16 v6, 0x10
        invoke-static {v3, v6}, $SKIP_BUTTON_CLASS;->dp(Landroid/content/Context;I)I
        move-result v6
        const/16 v7, 0x70
        invoke-static {v3, v7}, $SKIP_BUTTON_CLASS;->dp(Landroid/content/Context;I)I
        move-result v7
        const/4 v8, 0x0
        invoke-virtual {v4, v6, v8, v6, v7}, Landroid/widget/FrameLayout${'$'}LayoutParams;->setMargins(IIII)V
        invoke-virtual {v5, v2, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;Landroid/view/ViewGroup${'$'}LayoutParams;)V

        :update
        new-instance v4, $SKIP_CLICK_CLASS;
        invoke-direct {v4, p0, p1}, $SKIP_CLICK_CLASS;-><init>($SKIP_STATE_CLASS;I)V
        invoke-virtual {v2, v4}, Landroid/view/View;->setOnClickListener(Landroid/view/View${'$'}OnClickListener;)V
        iget-object v0, v1, $SEGMENT_CLASS->name:Ljava/lang/String;
        const-string v4, "Пропустить: "
        invoke-virtual {v4, v0}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
        move-result-object v4
        invoke-virtual {v2, v4}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

        iget v0, v1, $SEGMENT_CLASS->category:I
        invoke-static {v0}, $CATEGORY_CLASS;->color(I)I
        move-result v0
        const/16 v5, 0xff
        move v6, v0
        shr-int/lit8 v6, v6, 0x8
        and-int/2addr v6, v5
        const/16 v7, 0x12b
        mul-int/2addr v6, v7
        move v7, v0
        shr-int/lit8 v7, v7, 0x10
        and-int/2addr v7, v5
        const/16 v8, 0x24b
        mul-int/2addr v7, v8
        and-int/2addr v0, v5
        const/16 v8, 0x72
        mul-int/2addr v0, v8
        add-int/2addr v6, v7
        add-int/2addr v6, v0
        const/16 v7, 0x3e8
        div-int/2addr v6, v7
        const/16 v7, 0x80
        if-ge v6, v7, :black
        const/4 v0, -0x1
        goto :text_color
        :black
        const v0, 0xff000000
        :text_color
        invoke-virtual {v2, v0}, Landroid/widget/TextView;->setTextColor(I)V
        const/4 v0, 0x0
        invoke-virtual {v2, v0}, Landroid/view/View;->setVisibility(I)V
        invoke-static {p0}, $SKIP_BUTTON_CLASS;->place($SKIP_STATE_CLASS;)V

        :return
        return-void
    .end method

    .method public static hide($SKIP_STATE_CLASS;)V
        .registers 2
        .param p0, "state"

        if-eqz p0, :return
        iget-object v0, p0, $SKIP_STATE_CLASS->button:Landroid/widget/TextView;
        if-eqz v0, :return
        const/16 v1, 0x8
        invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V

        :return
        return-void
    .end method

    .method public static place($SKIP_STATE_CLASS;)V
        .registers 16
        .param p0, "state"

        if-eqz p0, :return
        iget-object v0, p0, $SKIP_STATE_CLASS->button:Landroid/widget/TextView;
        if-eqz v0, :return
        invoke-static {p0}, $SKIP_ENGINE_CLASS;->viewOf($SKIP_STATE_CLASS;)Landroid/view/View;
        move-result-object v1
        invoke-static {v1}, $SKIP_BUTTON_CLASS;->sized(Landroid/view/View;)Landroid/view/View;
        move-result-object v1
        if-eqz v1, :fallback
        goto :live

        :fallback
        iget-object v1, p0, $SKIP_STATE_CLASS->controller:Lp73/a;
        if-eqz v1, :frame
        instance-of v4, v1, Lcom/vk/libvideo/autoplay/VideoAutoPlay;
        if-eqz v4, :frame
        check-cast v1, Lcom/vk/libvideo/autoplay/VideoAutoPlay;
        iget-object v1, v1, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->K:Ljava/lang/ref/WeakReference;
        if-eqz v1, :frame
        invoke-virtual {v1}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
        move-result-object v1
        check-cast v1, Landroid/view/View;
        invoke-static {v1}, $SKIP_BUTTON_CLASS;->sized(Landroid/view/View;)Landroid/view/View;
        move-result-object v1
        if-eqz v1, :frame
        goto :live

        :frame
        invoke-virtual {v0}, Landroid/view/View;->getRootView()Landroid/view/View;
        move-result-object v4
        const v5, 0x1020002
        invoke-virtual {v4, v5}, Landroid/view/View;->findViewById(I)Landroid/view/View;
        move-result-object v1
        if-eqz v1, :return

        :live
        iput-object v1, p0, $SKIP_STATE_CLASS->view:Landroid/view/View;
        invoke-static {p0, v1, v0}, $SKIP_LAYOUT_CLASS;->watch($SKIP_STATE_CLASS;Landroid/view/View;Landroid/view/View;)V
        invoke-virtual {v0}, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup${'$'}LayoutParams;
        move-result-object v2
        if-eqz v2, :return
        check-cast v2, Landroid/widget/FrameLayout${'$'}LayoutParams;
        invoke-virtual {v0}, Landroid/view/View;->getParent()Landroid/view/ViewParent;
        move-result-object v3
        if-eqz v3, :return
        check-cast v3, Landroid/view/ViewGroup;
        const/4 v4, 0x2
        new-array v6, v4, [I
        new-array v7, v4, [I
        invoke-virtual {v1, v6}, Landroid/view/View;->getLocationInWindow([I)V
        invoke-virtual {v3, v7}, Landroid/view/View;->getLocationInWindow([I)V
        const/4 v3, 0x0
        const/4 v4, 0x0
        aget v8, v6, v4
        aget v5, v7, v4
        sub-int/2addr v8, v5
        const/4 v4, 0x1
        aget v9, v6, v4
        aget v5, v7, v4
        sub-int/2addr v9, v5
        invoke-virtual {v1}, Landroid/view/View;->getWidth()I
        move-result v10
        add-int/2addr v10, v8
        invoke-virtual {v1}, Landroid/view/View;->getHeight()I
        move-result v11
        add-int/2addr v11, v9
        invoke-virtual {v0}, Landroid/view/View;->getContext()Landroid/content/Context;
        move-result-object v4
        const/16 v5, 0x8
        invoke-static {v4, v5}, $SKIP_BUTTON_CLASS;->dp(Landroid/content/Context;I)I
        move-result v6
        const/16 v5, 0x10
        invoke-static {v4, v5}, $SKIP_BUTTON_CLASS;->dp(Landroid/content/Context;I)I
        move-result v7
        invoke-virtual {v0}, Landroid/view/View;->getWidth()I
        move-result v12
        invoke-virtual {v0}, Landroid/view/View;->getHeight()I
        move-result v13
        sub-int v4, v10, v8
        add-int v5, v6, v6
        sub-int v4, v4, v5
        const/4 v5, 0x1
        if-ge v4, v5, :w_max
        move v4, v5

        :w_max
        invoke-virtual {v0}, Landroid/widget/TextView;->getMaxWidth()I
        move-result v5
        if-eq v5, v4, :w_set
        invoke-virtual {v0, v4}, Landroid/widget/TextView;->setMaxWidth(I)V
        const/4 v3, 0x1

        :w_set
        sub-int v4, v11, v9
        add-int v5, v6, v6
        sub-int v4, v4, v5
        const/4 v5, 0x1
        if-ge v4, v5, :h_max
        move v4, v5

        :h_max
        invoke-virtual {v0}, Landroid/widget/TextView;->getMaxHeight()I
        move-result v5
        if-eq v5, v4, :h_set
        invoke-virtual {v0, v4}, Landroid/widget/TextView;->setMaxHeight(I)V
        const/4 v3, 0x1

        :h_set
        sub-int v14, v10, v12
        sub-int/2addr v14, v7
        sub-int v5, v10, v12
        sub-int/2addr v5, v6
        if-le v14, v5, :x_high
        move v14, v5

        :x_high
        add-int v5, v8, v6
        if-lt v14, v5, :x_low
        goto :x_done

        :x_low
        move v14, v5

        :x_done
        sub-int v12, v11, v13
        sub-int/2addr v12, v7
        sub-int v5, v11, v13
        sub-int/2addr v5, v6
        if-le v12, v5, :y_high
        move v12, v5

        :y_high
        add-int v5, v9, v6
        if-lt v12, v5, :y_low
        goto :y_done

        :y_low
        move v12, v5

        :y_done
        iget v5, v2, Landroid/widget/FrameLayout${'$'}LayoutParams;->leftMargin:I
        if-eq v5, v14, :margins
        iput v14, v2, Landroid/widget/FrameLayout${'$'}LayoutParams;->leftMargin:I
        const/4 v3, 0x1

        :margins
        iget v5, v2, Landroid/widget/FrameLayout${'$'}LayoutParams;->topMargin:I
        if-eq v5, v12, :zero
        iput v12, v2, Landroid/widget/FrameLayout${'$'}LayoutParams;->topMargin:I
        const/4 v3, 0x1

        :zero
        iget v5, v2, Landroid/widget/FrameLayout${'$'}LayoutParams;->rightMargin:I
        if-eqz v5, :gravity
        const/4 v5, 0x0
        iput v5, v2, Landroid/widget/FrameLayout${'$'}LayoutParams;->rightMargin:I
        iput v5, v2, Landroid/widget/FrameLayout${'$'}LayoutParams;->bottomMargin:I
        const/4 v3, 0x1

        :gravity
        iget v5, v2, Landroid/widget/FrameLayout${'$'}LayoutParams;->gravity:I
        const v4, 0x800030
        if-eq v5, v4, :changed
        iput v4, v2, Landroid/widget/FrameLayout${'$'}LayoutParams;->gravity:I
        const/4 v3, 0x1

        :changed
        if-eqz v3, :return
        invoke-virtual {v0}, Landroid/view/View;->requestLayout()V

        :return
        return-void
    .end method

    .method private static sized(Landroid/view/View;)Landroid/view/View;
        .registers 4
        .param p0, "view"

        :loop
        if-eqz p0, :null
        invoke-virtual {p0}, Landroid/view/View;->getWidth()I
        move-result v0
        if-lez v0, :up
        invoke-virtual {p0}, Landroid/view/View;->getHeight()I
        move-result v0
        if-lez v0, :up
        return-object p0

        :up
        invoke-virtual {p0}, Landroid/view/View;->getParent()Landroid/view/ViewParent;
        move-result-object v0
        if-eqz v0, :null
        instance-of v1, v0, Landroid/view/View;
        if-eqz v1, :null
        check-cast v0, Landroid/view/View;
        move-object p0, v0
        goto :loop

        :null
        const/4 v0, 0x0
        return-object v0
    .end method
""".trimIndent()

private fun skipClickSmali(): String = """
    .class public final $SKIP_CLICK_CLASS
    .super Ljava/lang/Object;
    .implements Landroid/view/View${'$'}OnClickListener;

    .field private final state:$SKIP_STATE_CLASS;
    .field private final index:I

    .method public constructor <init>($SKIP_STATE_CLASS;I)V
        .registers 3

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        iput-object p1, p0, $SKIP_CLICK_CLASS->state:$SKIP_STATE_CLASS;
        iput p2, p0, $SKIP_CLICK_CLASS->index:I
        return-void
    .end method

    .method public onClick(Landroid/view/View;)V
        .registers 3

        iget-object v0, p0, $SKIP_CLICK_CLASS->state:$SKIP_STATE_CLASS;
        iget v1, p0, $SKIP_CLICK_CLASS->index:I
        const/4 v2, 0x0
        invoke-static {v0, v1, v2}, $SKIP_ENGINE_CLASS;->skip($SKIP_STATE_CLASS;IZ)V
        return-void
    .end method
""".trimIndent()

/**
 * Re-places the button whenever its surface lays out, so rotation and surface
 * swaps settle without waiting for the next tick. The listener lives on the
 * state and is detached in `stop`.
 */
private fun skipLayoutSmali(): String = """
    .class public final $SKIP_LAYOUT_CLASS
    .super Ljava/lang/Object;
    .implements Landroid/view/View${'$'}OnLayoutChangeListener;

    .field private final state:$SKIP_STATE_CLASS;

    .method public constructor <init>($SKIP_STATE_CLASS;)V
        .registers 2

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        iput-object p1, p0, $SKIP_LAYOUT_CLASS->state:$SKIP_STATE_CLASS;
        return-void
    .end method

    .method public onLayoutChange(Landroid/view/View;IIIIIIII)V
        .registers 11
        .param p1, "view"
        .param p2, "left"
        .param p3, "top"
        .param p4, "right"
        .param p5, "bottom"
        .param p6, "oldLeft"
        .param p7, "oldTop"
        .param p8, "oldRight"
        .param p9, "oldBottom"

        iget-object v0, p0, $SKIP_LAYOUT_CLASS->state:$SKIP_STATE_CLASS;
        invoke-static {v0}, $SKIP_BUTTON_CLASS;->place($SKIP_STATE_CLASS;)V
        return-void
    .end method

    .method public static watch($SKIP_STATE_CLASS;Landroid/view/View;Landroid/view/View;)V
        .registers 6
        .param p0, "state"
        .param p1, "surface"
        .param p2, "button"

        if-eqz p0, :return
        iget-object v0, p0, $SKIP_STATE_CLASS->listener:Landroid/view/View${'$'}OnLayoutChangeListener;
        if-nez v0, :created
        new-instance v0, $SKIP_LAYOUT_CLASS
        invoke-direct {v0, p0}, $SKIP_LAYOUT_CLASS;-><init>($SKIP_STATE_CLASS;)V
        iput-object v0, p0, $SKIP_STATE_CLASS->listener:Landroid/view/View${'$'}OnLayoutChangeListener;

        :created
        iget-object v1, p0, $SKIP_STATE_CLASS->listenerView:Landroid/view/View;
        if-eq v1, p1, :surface
        if-eqz v1, :surface_add
        invoke-virtual {v1, v0}, Landroid/view/View;->removeOnLayoutChangeListener(Landroid/view/View${'$'}OnLayoutChangeListener;)V

        :surface_add
        if-eqz p1, :surface
        invoke-virtual {p1, v0}, Landroid/view/View;->addOnLayoutChangeListener(Landroid/view/View${'$'}OnLayoutChangeListener;)V
        iput-object p1, p0, $SKIP_STATE_CLASS->listenerView:Landroid/view/View;

        :surface
        iget-object v1, p0, $SKIP_STATE_CLASS->buttonView:Landroid/view/View;
        if-eq v1, p2, :return
        if-eqz v1, :button_add
        invoke-virtual {v1, v0}, Landroid/view/View;->removeOnLayoutChangeListener(Landroid/view/View${'$'}OnLayoutChangeListener;)V

        :button_add
        if-eqz p2, :return
        invoke-virtual {p2, v0}, Landroid/view/View;->addOnLayoutChangeListener(Landroid/view/View${'$'}OnLayoutChangeListener;)V
        iput-object p2, p0, $SKIP_STATE_CLASS->buttonView:Landroid/view/View;

        :return
        return-void
    .end method
""".trimIndent()
