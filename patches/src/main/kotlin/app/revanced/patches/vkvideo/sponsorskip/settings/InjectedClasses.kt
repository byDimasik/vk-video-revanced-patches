package app.revanced.patches.vkvideo.sponsorskip.settings

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patches.vkvideo.settings.ToolbarChrome
import app.revanced.patches.vkvideo.settings.CONTENT_ADD_CLASS
import app.revanced.patches.vkvideo.settings.contentAddSmali
import app.revanced.patches.vkvideo.settings.groupClickSmali
import app.revanced.patches.vkvideo.settings.settingsFragmentSmali
import app.revanced.patches.vkvideo.sponsorskip.BEHAVIOR_CLASS
import app.revanced.patches.vkvideo.sponsorskip.BEHAVIOR_STORE_CLASS
import com.android.tools.smali.smali.SmaliTestUtils

internal const val SPONSOR_SKIP_SETTINGS_PACKAGE = "Lapp/revanced/extension/sponsorskip/settings/"

internal const val SETTINGS_CLASS = "${SPONSOR_SKIP_SETTINGS_PACKAGE}SponsorSkipSettings;"
internal const val FRAGMENT_CLASS = "${SPONSOR_SKIP_SETTINGS_PACKAGE}SponsorSkipFragment;"
internal const val GROUP_CLICK_CLASS = "${SPONSOR_SKIP_SETTINGS_PACKAGE}SponsorSkipGroupClick;"
internal const val ROOT_ROW_CLASS = "${SPONSOR_SKIP_SETTINGS_PACKAGE}SponsorSkipRootRow;"
internal const val TOGGLE_CLASS = "${SPONSOR_SKIP_SETTINGS_PACKAGE}SponsorSkipToggle;"
internal const val DIALOG_CLASS = "${SPONSOR_SKIP_SETTINGS_PACKAGE}SponsorSkipBehaviorDialog;"
internal const val DIALOG_CLICK_CLASS = "${SPONSOR_SKIP_SETTINGS_PACKAGE}SponsorSkipBehaviorDialog\$Click;"
internal const val ROWS_CLASS = "${SPONSOR_SKIP_SETTINGS_PACKAGE}SponsorSkipRows;"

internal const val TITLE_SPONSOR_SKIP = "SponsorSkip"
internal const val LABEL_ENABLE = "Enable SponsorSkip"

/** `drawable/vk_icon_lego_skip_next_24`, a preexisting app resource. */
internal const val ROOT_ROW_ICON_ID = 0x7f082020

/**
 * Injector for the SponsorSkip settings screen. Same contract as the other
 * injectors: an existing descriptor is skipped, a stale generation missing a
 * required method is rejected.
 */
internal fun injectSponsorSkipSettingsClasses(
    context: BytecodePatchContext,
    chrome: ToolbarChrome,
    opener: app.revanced.patches.vkvideo.settings.NavOpener,
    switchListenerMethod: String,
) {
    settingsSources(chrome, opener, switchListenerMethod).forEach { (descriptor, source) ->
        val existing = context.classDefs.firstOrNull { it.type == descriptor }
        if (existing != null) {
            val missing = requiredMethods[descriptor].orEmpty().filter { required ->
                val (name, params) = required.split(':')
                existing.methods.none { it.name == name && it.parameterTypes.size == params.toInt() }
            }
            check(missing.isEmpty()) {
                "Stale injected class $descriptor is missing $missing; re-patch from the stock APK"
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
internal fun settingsSources(
    chrome: ToolbarChrome,
    opener: app.revanced.patches.vkvideo.settings.NavOpener,
    switchListenerMethod: String,
): Map<String, String> = linkedMapOf(
    SETTINGS_CLASS to settingsSmali(),
    ROWS_CLASS to rowsSmali(),
    DIALOG_CLICK_CLASS to dialogClickSmali(),
    DIALOG_CLASS to dialogSmali(),
    TOGGLE_CLASS to toggleSmali(switchListenerMethod),
    GROUP_CLICK_CLASS to groupClickSmali(opener, GROUP_CLICK_CLASS, FRAGMENT_CLASS, "sponsorskip group row clicked"),
    ROOT_ROW_CLASS to rootRowSmali(),
    FRAGMENT_CLASS to settingsFragmentSmali(chrome, FRAGMENT_CLASS, SETTINGS_CLASS, TITLE_SPONSOR_SKIP, "buildSponsorSkipContent"),
    CONTENT_ADD_CLASS to contentAddSmali(),
)

private val requiredMethods = mapOf(
    SETTINGS_CLASS to listOf(
        "categoryLabel:1", "behaviorLabel:1", "buildSponsorSkipContent:2", "setRowValue:3",
    ),
    ROWS_CLASS to listOf("newRow:2", "middle:3", "right:1"),
    DIALOG_CLASS to listOf("<init>:4", "show:0", "onClick:1"),
    DIALOG_CLICK_CLASS to listOf("<init>:1", "onClick:2"),
    TOGGLE_CLASS to listOf("<init>:0"),
    GROUP_CLICK_CLASS to listOf("<init>:1", "onClick:1"),
    ROOT_ROW_CLASS to listOf("add:3"),
    FRAGMENT_CLASS to listOf("<init>:0", "onCreateView:3", "onViewCreated:2"),
    CONTENT_ADD_CLASS to listOf("<init>:2", "run:0"),
)

/** The screen content class: category/behavior labels, the row update path and the tab layout. */
private fun settingsSmali(): String = """
    .class public final $SETTINGS_CLASS
    .super Ljava/lang/Object;

    .method public static categoryLabel(I)Ljava/lang/String;
        .registers 2

        packed-switch p0, :table
        const-string v0, "Sponsor"
        return-object v0

        :sponsor
        const-string v0, "Sponsor"
        return-object v0

        :selfpromo
        const-string v0, "Self-promo"
        return-object v0

        :intro
        const-string v0, "Intro"
        return-object v0

        :outro
        const-string v0, "Outro"
        return-object v0

        :table
        .packed-switch 0x0
            :sponsor
            :selfpromo
            :intro
            :outro
        .end packed-switch
    .end method

    .method public static behaviorLabel(I)Ljava/lang/String;
        .registers 2

        packed-switch p0, :table
        const-string v0, "Show skip button"
        return-object v0

        :skip
        const-string v0, "Skip automatically"
        return-object v0

        :button
        const-string v0, "Show skip button"
        return-object v0

        :disabled
        const-string v0, "Disabled"
        return-object v0

        :table
        .packed-switch 0x0
            :skip
            :button
            :disabled
        .end packed-switch
    .end method

    .method public static setRowValue(Lcom/vk/core/view/components/cell/VkCell;II)V
        .registers 9
        .param p0, "row"
        .param p1, "category"
        .param p2, "behavior"

        invoke-virtual {p0}, Landroid/view/View;->getContext()Landroid/content/Context;
        move-result-object v0
        invoke-static {v0, p1, p2}, $ROWS_CLASS->middle(Landroid/content/Context;II)Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}b;
        move-result-object v1
        invoke-virtual {p0, v1}, Lcom/vk/core/view/components/cell/VkCell;->setMiddle(Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}b;)V
        invoke-static {p0}, $ROWS_CLASS->right(Lcom/vk/core/view/components/cell/VkCell;)V
        return-void
    .end method

    .method public static buildSponsorSkipContent(Landroid/content/Context;Lcom/vk/core/fragments/FragmentImpl;)Landroid/view/View;
        .registers 10
        .param p0, "context"
        .param p1, "fragment"

        new-instance v7, Landroid/widget/LinearLayout;
        invoke-direct {v7, p0}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V
        const/4 v0, 0x1
        invoke-virtual {v7, v0}, Landroid/widget/LinearLayout;->setOrientation(I)V
${masterToggle()}
${categoryRow(0)}
${categoryRow(1)}
${categoryRow(2)}
${categoryRow(3)}
        return-object v7
    .end method
""".trimIndent()

/**
 * The master toggle, wired like the app's own switch-row binding: title →
 * checked → listener → align-switch-to-end, 6dip vertical margin.
 */
private fun masterToggle(): String = """
        new-instance v0, Lcom/vk/core/view/components/switch/VkSwitchItem;
        const/4 v1, 0x0
        invoke-direct {v0, p0, v1}, Lcom/vk/core/view/components/switch/VkSwitchItem;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;)V

        new-instance v2, Lpx1/a${'$'}h;
        const-string v3, "$LABEL_ENABLE"
        invoke-direct {v2, v3}, Lpx1/a${'$'}h;-><init>(Ljava/lang/CharSequence;)V
        invoke-virtual {v0, v2}, Lcom/vk/core/view/components/switch/VkSwitchItem;->setTitle(Lpx1/a;)V

        invoke-static {}, $BEHAVIOR_STORE_CLASS->isEnabled()Z
        move-result v3
        invoke-virtual {v0, v3}, Lcom/vk/core/view/components/switch/VkSwitchItem;->setChecked(Z)V

        new-instance v2, $TOGGLE_CLASS
        invoke-direct {v2}, $TOGGLE_CLASS-><init>()V
        invoke-virtual {v0, v2}, Lcom/vk/core/view/components/switch/VkSwitchItem;->setListener(Lcom/vk/core/view/components/switch/VkSwitchItem${'$'}a;)V

        const/4 v3, 0x1
        invoke-virtual {v0, v3}, Lcom/vk/core/view/components/switch/VkSwitchItem;->setAlignSwitchToEnd(Z)V

        invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
        move-result-object v3
        invoke-virtual {v3}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;
        move-result-object v3
        iget v3, v3, Landroid/util/DisplayMetrics;->density:F
        const/high16 v4, 0x40c00000
        mul-float/2addr v3, v4
        float-to-int v3, v3
        new-instance v4, Landroid/widget/LinearLayout${'$'}LayoutParams;
        const/4 v5, -0x1
        const/4 v6, -0x2
        invoke-direct {v4, v5, v6}, Landroid/widget/LinearLayout${'$'}LayoutParams;-><init>(II)V
        const/4 v5, 0x0
        invoke-virtual {v4, v5, v3, v5, v3}, Landroid/view/ViewGroup${'$'}MarginLayoutParams;->setMargins(IIII)V
        invoke-virtual {v0, v4}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V

        invoke-virtual {v7, v0}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V
"""

/** One category row plus its behavior popup click. */
private fun categoryRow(category: Int): String = """
        const/4 v5, 0x$category
        invoke-static {p0, v5}, $ROWS_CLASS->newRow(Landroid/content/Context;I)Lcom/vk/core/view/components/cell/VkCell;
        move-result-object v6
        new-instance v4, $DIALOG_CLASS
        invoke-direct {v4, p0, p1, v6, v5}, $DIALOG_CLASS-><init>(Landroid/content/Context;Lcom/vk/core/fragments/FragmentImpl;Lcom/vk/core/view/components/cell/VkCell;I)V
        invoke-virtual {v6, v4}, Landroid/view/View;->setOnClickListener(Landroid/view/View${'$'}OnClickListener;)V
        invoke-virtual {v7, v6}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V
"""

/**
 * Row factory: the cell build both the initial screen and the update path use,
 * so a value change never loses the title or the chevron.
 */
/** Row factory shared by the initial build and the update path, so a value change keeps title and chevron. */
private fun rowsSmali(): String = """
    .class public final $ROWS_CLASS
    .super Ljava/lang/Object;

    .method public static newRow(Landroid/content/Context;I)Lcom/vk/core/view/components/cell/VkCell;
        .registers 9
        .param p0, "context"
        .param p1, "category"

        new-instance v0, Lcom/vk/core/view/components/cell/VkCell;
        const/4 v1, 0x0
        const/4 v2, 0x6
        const/4 v3, 0x0
        invoke-direct {v0, p0, v1, v2, v3}, Lcom/vk/core/view/components/cell/VkCell;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;II)V

        new-instance v1, Landroid/view/ViewGroup${'$'}LayoutParams;
        const/4 v2, -0x1
        const/4 v3, -0x2
        invoke-direct {v1, v2, v3}, Landroid/view/ViewGroup${'$'}LayoutParams;-><init>(II)V
        invoke-virtual {v0, v1}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V

        const/4 v1, 0x1
        invoke-virtual {v0, v1}, Lcom/vk/core/view/components/cell/VkCell;->setRippleEnabled(Z)V

        invoke-static {p1}, $BEHAVIOR_STORE_CLASS->getBehavior(I)I
        move-result v1
        invoke-static {p0, p1, v1}, $ROWS_CLASS->middle(Landroid/content/Context;II)Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}b;
        move-result-object v1
        invoke-virtual {v0, v1}, Lcom/vk/core/view/components/cell/VkCell;->setMiddle(Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}b;)V

        invoke-static {v0}, $ROWS_CLASS->right(Lcom/vk/core/view/components/cell/VkCell;)V
        return-object v0
    .end method

    .method public static middle(Landroid/content/Context;II)Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}b;
        .registers 11
        .param p0, "context"
        .param p1, "category"
        .param p2, "behavior"

        new-instance v0, Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}e;
        new-instance v1, Lpx1/a${'$'}h;
        invoke-static {p1}, $SETTINGS_CLASS->categoryLabel(I)Ljava/lang/String;
        move-result-object v2
        invoke-direct {v1, v2}, Lpx1/a${'$'}h;-><init>(Ljava/lang/CharSequence;)V
        const/4 v2, 0x0
        const/4 v3, 0x0
        const/4 v4, 0x0
        const/4 v5, 0x0
        const/16 v6, 0x3e
        invoke-direct/range {v0 .. v6}, Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}e;-><init>(Lpx1/a;ILandroid/text/TextUtils${'$'}TruncateAt;Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}e${'$'}b${'$'}b;Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}e${'$'}b;I)V

        new-instance v1, Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}d;
        new-instance v2, Lpx1/a${'$'}h;
        invoke-static {p2}, $SETTINGS_CLASS->behaviorLabel(I)Ljava/lang/String;
        move-result-object v3
        invoke-direct {v2, v3}, Lpx1/a${'$'}h;-><init>(Ljava/lang/CharSequence;)V
        const/4 v3, 0x0
        const/4 v4, 0x0
        const/4 v5, 0x0
        const/4 v6, 0x0
        const/16 v7, 0x2e
        invoke-direct/range {v1 .. v7}, Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}d;-><init>(Lpx1/a;Lbj1/m;ILandroid/text/TextUtils${'$'}TruncateAt;Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}d${'$'}b${'$'}b;I)V

        move-object v3, v0
        move-object v4, v1
        sget-object v2, Lcom/vk/core/view/components/cell/VkCell${'$'}Middle;->Companion:Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}a;
        const/4 v5, 0x0
        const/4 v6, 0x4
        invoke-static/range {v2 .. v6}, Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}a;->a(Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}a;Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}e;Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}d;Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}c;I)Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}b;
        move-result-object v0
        return-object v0
    .end method

    .method public static right(Lcom/vk/core/view/components/cell/VkCell;)V
        .registers 8
        .param p0, "row"

        new-instance v0, Lcom/vk/core/view/components/cell/VkCell${'$'}Right${'$'}b;
        const/4 v1, 0x0
        const/4 v2, 0x0
        const/4 v3, 0x7
        invoke-direct {v0, v1, v2, v3}, Lcom/vk/core/view/components/cell/VkCell${'$'}Right${'$'}b;-><init>(Lkotlin/jvm/functions/Function1;Lp72/r${'$'}a;I)V
        new-instance v1, Lcom/vk/core/view/components/cell/VkCell${'$'}Right${'$'}d;
        const/4 v2, 0x0
        const/4 v3, 0x0
        const/4 v4, 0x0
        move-object v5, v0
        const/16 v6, 0x17
        invoke-direct/range {v1 .. v6}, Lcom/vk/core/view/components/cell/VkCell${'$'}Right${'$'}d;-><init>(Lcom/vk/core/view/components/cell/VkCell${'$'}Right${'$'}a;Lcom/vk/core/view/components/cell/VkCell${'$'}Right${'$'}e;Lcom/vk/core/view/components/cell/VkCell${'$'}Right${'$'}ExtraAction${'$'}a;Lcom/vk/core/view/components/cell/VkCell${'$'}Right${'$'}b;I)V
        invoke-virtual {p0, v1}, Lcom/vk/core/view/components/cell/VkCell;->setRight(Lcom/vk/core/view/components/cell/VkCell${'$'}Right${'$'}d;)V
        return-void
    .end method
""".trimIndent()

/** The behavior popup: an `OnClickListener` on the row that opens the app-styled single-choice dialog. */
private fun dialogSmali(): String = """
    .class public final $DIALOG_CLASS
    .super Ljava/lang/Object;
    .implements Landroid/view/View${'$'}OnClickListener;

    .field final context:Landroid/content/Context;
    .field final fragment:Lcom/vk/core/fragments/FragmentImpl;
    .field final row:Lcom/vk/core/view/components/cell/VkCell;
    .field final category:I

    .method public constructor <init>(Landroid/content/Context;Lcom/vk/core/fragments/FragmentImpl;Lcom/vk/core/view/components/cell/VkCell;I)V
        .registers 5
        .param p1, "context"
        .param p2, "fragment"
        .param p3, "row"
        .param p4, "category"

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        iput-object p1, p0, $DIALOG_CLASS->context:Landroid/content/Context;
        iput-object p2, p0, $DIALOG_CLASS->fragment:Lcom/vk/core/fragments/FragmentImpl;
        iput-object p3, p0, $DIALOG_CLASS->row:Lcom/vk/core/view/components/cell/VkCell;
        iput p4, p0, $DIALOG_CLASS->category:I
        return-void
    .end method

    .method public onClick(Landroid/view/View;)V
        .registers 2

        invoke-virtual {p0}, $DIALOG_CLASS->show()V
        return-void
    .end method

    .method public show()V
        .registers 6

        new-instance v0, Lhy1/c${'$'}a;
        iget-object v1, p0, $DIALOG_CLASS->context:Landroid/content/Context;
        invoke-direct {v0, v1}, Lhy1/c${'$'}a;-><init>(Landroid/content/Context;)V

        iget v1, p0, $DIALOG_CLASS->category:I
        invoke-static {v1}, $SETTINGS_CLASS->categoryLabel(I)Ljava/lang/String;
        move-result-object v1
        invoke-virtual {v0, v1}, Lhy1/c${'$'}a;->x(Ljava/lang/CharSequence;)Lhy1/c${'$'}a;

        const/4 v1, 0x3
        new-array v1, v1, [Ljava/lang/CharSequence;

        sget v3, $BEHAVIOR_CLASS->SKIP_AUTOMATICALLY:I
        invoke-static {v3}, $SETTINGS_CLASS->behaviorLabel(I)Ljava/lang/String;
        move-result-object v3
        const/4 v4, 0x0
        aput-object v3, v1, v4

        const/4 v4, 0x1
        invoke-static {v4}, $SETTINGS_CLASS->behaviorLabel(I)Ljava/lang/String;
        move-result-object v3
        aput-object v3, v1, v4

        const/4 v4, 0x2
        invoke-static {v4}, $SETTINGS_CLASS->behaviorLabel(I)Ljava/lang/String;
        move-result-object v3
        aput-object v3, v1, v4

        iget v2, p0, $DIALOG_CLASS->category:I
        invoke-static {v2}, $BEHAVIOR_STORE_CLASS->getBehavior(I)I
        move-result v2
        new-instance v3, $DIALOG_CLICK_CLASS
        invoke-direct {v3, p0}, $DIALOG_CLICK_CLASS-><init>($DIALOG_CLASS)V
        invoke-virtual {v0, v1, v2, v3}, Lhy1/c${'$'}a;->v([Ljava/lang/CharSequence;ILandroid/content/DialogInterface${'$'}OnClickListener;)Lhy1/c${'$'}a;

        invoke-virtual {v0}, Lhy1/c${'$'}a;->j()Landroidx/appcompat/app/d;
        move-result-object v0
        if-eqz v0, :done
        invoke-virtual {v0}, Landroid/app/Dialog;->show()V
        :done
        return-void
    .end method
""".trimIndent()

/** Popup selection: writes the literal, refreshes the row and closes the dialog. */
private fun dialogClickSmali(): String = """
    .class public final $DIALOG_CLICK_CLASS
    .super Ljava/lang/Object;
    .implements Landroid/content/DialogInterface${'$'}OnClickListener;

    .field final this${'$'}0:$DIALOG_CLASS

    .method public constructor <init>($DIALOG_CLASS)V
        .registers 2
        .param p1, "dialog"

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        iput-object p1, p0, $DIALOG_CLICK_CLASS->this${'$'}0:$DIALOG_CLASS
        return-void
    .end method

    .method public onClick(Landroid/content/DialogInterface;I)V
        .registers 7
        .param p1, "dialog"
        .param p2, "which"

        iget-object v0, p0, $DIALOG_CLICK_CLASS->this${'$'}0:$DIALOG_CLASS
        iget-object v1, v0, $DIALOG_CLASS->row:Lcom/vk/core/view/components/cell/VkCell;
        iget v2, v0, $DIALOG_CLASS->category:I
        invoke-static {p2}, $BEHAVIOR_CLASS->literal(I)Ljava/lang/String;
        move-result-object v3
        invoke-static {v2, v3}, $BEHAVIOR_STORE_CLASS->setBehavior(ILjava/lang/String;)V
        invoke-static {v1, v2, p2}, $SETTINGS_CLASS->setRowValue(Lcom/vk/core/view/components/cell/VkCell;II)V
        invoke-interface {p1}, Landroid/content/DialogInterface;->dismiss()V
        return-void
    .end method
""".trimIndent()

/** Master switch listener writing the enabled flag. */
private fun toggleSmali(listenerMethod: String): String = """
    .class public final $TOGGLE_CLASS
    .super Ljava/lang/Object;
    .implements Lcom/vk/core/view/components/switch/VkSwitchItem${'$'}a;

    .method public constructor <init>()V
        .registers 1

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        return-void
    .end method

    .method public $listenerMethod(Z)V
        .registers 2
        .param p1, "checked"

        invoke-static {p1}, $BEHAVIOR_STORE_CLASS->setEnabled(Z)V
        return-void
    .end method
""".trimIndent()

/**
 * The root "SponsorSkip" row, built exactly like the Ads row (icon slot,
 * chevron, ripple, click) and appended to the root list.
 */
private fun rootRowSmali(): String = """
    .class public final $ROOT_ROW_CLASS
    .super Ljava/lang/Object;

    .method public static add(Landroid/widget/LinearLayout;Landroid/content/Context;Lcom/vk/core/fragments/FragmentImpl;)V
        .registers 15
        .param p0, "list"
        .param p1, "context"
        .param p2, "fragment"

        new-instance v9, Lcom/vk/core/view/components/cell/VkCell;
        const/4 v0, 0x0
        const/4 v1, 0x6
        const/4 v2, 0x0
        invoke-direct {v9, p1, v0, v1, v2}, Lcom/vk/core/view/components/cell/VkCell;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;II)V

        new-instance v0, Landroid/view/ViewGroup${'$'}LayoutParams;
        const/4 v1, -0x1
        const/4 v2, -0x2
        invoke-direct {v0, v1, v2}, Landroid/view/ViewGroup${'$'}LayoutParams;-><init>(II)V
        invoke-virtual {v9, v0}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V
        const/4 v0, 0x1
        invoke-virtual {v9, v0}, Lcom/vk/core/view/components/cell/VkCell;->setRippleEnabled(Z)V

        new-instance v0, Lp72/j;
        const v1, 0x${ROOT_ROW_ICON_ID.toString(16)}
        invoke-direct {v0, v1}, Lp72/j;-><init>(I)V
        new-instance v1, Lp72/b;
        const v2, 0x${ICON_PRIMARY_ATTR.toString(16)}
        invoke-direct {v1, v2}, Lp72/b;-><init>(I)V
        new-instance v2, Lp72/r${'$'}a;
        invoke-direct {v2, v1}, Lp72/r${'$'}a;-><init>(Lp72/q;)V

        new-instance v1, Lcom/vk/core/view/components/cell/VkCell${'$'}Left${'$'}Main${'$'}c;
        sget-object v3, Lcom/vk/core/view/components/cell/VkCell${'$'}Left${'$'}Main${'$'}Size;->Medium:Lcom/vk/core/view/components/cell/VkCell${'$'}Left${'$'}Main${'$'}Size;
        const/16 v4, 0x8
        invoke-direct {v1, v0, v3, v2, v4}, Lcom/vk/core/view/components/cell/VkCell${'$'}Left${'$'}Main${'$'}c;-><init>(Lp72/g;Lcom/vk/core/view/components/cell/VkCell${'$'}Left${'$'}Main${'$'}Size;Lp72/r${'$'}a;I)V
        new-instance v0, Lcom/vk/core/view/components/cell/VkCell${'$'}Left${'$'}b;
        const/4 v2, 0x0
        invoke-direct {v0, v1, v2}, Lcom/vk/core/view/components/cell/VkCell${'$'}Left${'$'}b;-><init>(Lcom/vk/core/view/components/cell/VkCell${'$'}Left${'$'}Main;Lcom/vk/core/view/components/cell/VkCell${'$'}Left${'$'}c;)V
        invoke-virtual {v9, v0}, Lcom/vk/core/view/components/cell/VkCell;->setLeft(Lcom/vk/core/view/components/cell/VkCell${'$'}Left${'$'}b;)V

        new-instance v0, Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}e;
        new-instance v1, Lpx1/a${'$'}h;
        const-string v2, "$TITLE_SPONSOR_SKIP"
        invoke-direct {v1, v2}, Lpx1/a${'$'}h;-><init>(Ljava/lang/CharSequence;)V
        const/4 v2, 0x0
        const/4 v3, 0x0
        const/4 v4, 0x0
        const/4 v5, 0x0
        const/16 v6, 0x3e
        invoke-direct/range {v0 .. v6}, Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}e;-><init>(Lpx1/a;ILandroid/text/TextUtils${'$'}TruncateAt;Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}e${'$'}b${'$'}b;Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}e${'$'}b;I)V
        move-object v1, v0
        sget-object v0, Lcom/vk/core/view/components/cell/VkCell${'$'}Middle;->Companion:Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}a;
        const/4 v2, 0x0
        const/4 v3, 0x0
        const/16 v4, 0xe
        invoke-static/range {v0 .. v4}, Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}a;->a(Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}a;Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}e;Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}d;Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}c;I)Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}b;
        move-result-object v0
        invoke-virtual {v9, v0}, Lcom/vk/core/view/components/cell/VkCell;->setMiddle(Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}b;)V

        invoke-static {v9}, $ROWS_CLASS->right(Lcom/vk/core/view/components/cell/VkCell;)V

        new-instance v0, $GROUP_CLICK_CLASS
        invoke-direct {v0, p2}, $GROUP_CLICK_CLASS-><init>(Lcom/vk/core/fragments/FragmentImpl;)V
        invoke-virtual {v9, v0}, Landroid/view/View;->setOnClickListener(Landroid/view/View${'$'}OnClickListener;)V

        invoke-virtual {p0, v9}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V
        return-void
    .end method
""".trimIndent()

private const val ICON_PRIMARY_ATTR = 0x7f040d62
