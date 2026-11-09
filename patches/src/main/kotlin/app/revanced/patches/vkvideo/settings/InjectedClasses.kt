package app.revanced.patches.vkvideo.settings

import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.smali.SmaliTestUtils

internal const val ADS_SETTINGS_CLASS = "Lapp/revanced/extension/vkvideo/AdsSettings;"
private const val START_SETTINGS_CLASS = "Lapp/revanced/extension/vkvideo/StartReVancedSettings;"
internal const val SETTINGS_FRAGMENT_CLASS = "Lapp/revanced/extension/vkvideo/ReVancedAdsSettingsFragment;"
internal const val ROOT_FRAGMENT_CLASS = "Lapp/revanced/extension/vkvideo/ReVancedRootFragment;"
private const val GROUP_CLICK_CLASS = "Lapp/revanced/extension/vkvideo/ReVancedAdsGroupClick;"
private const val TOGGLE_LISTENER_CLASS = "Lapp/revanced/extension/vkvideo/ReVancedToggleListener;"
private const val BACK_CLICK_CLASS = "Lapp/revanced/extension/vkvideo/ReVancedBackClick;"
internal const val CONTENT_ADD_CLASS = "Lapp/revanced/extension/vkvideo/ContentAdd;"

/** Root-screen strings. The root is the ReVanced group list (one "Ads" row). */
internal const val TITLE_ROOT = "ReVanced"
internal const val TITLE_ADS_GROUP = "Ads"

/**
 * The "Ads" group row icon and tint, both preexisting app resources (no binary
 * assets are shipped — the hybrid install keeps stock resources):
 * `drawable/vk_icon_advertising_outline_24` and the themed
 * `attr/vk_ui_icon_primary`, which resolves white in dark and near-black in
 * light and is what the app's own profile rows tint leading icons with.
 */
internal const val ADS_GROUP_ICON_ID = 0x7f0816fa
internal const val ICON_PRIMARY_ATTR = 0x7f040d62

private const val PREF_FILE = "revanced_settings"
private const val KEY_VIDEO_ADS = "revanced_ads_video"
private const val KEY_FEED_ADS = "revanced_ads_feed"
private const val KEY_CATALOG_ADS = "revanced_ads_catalog"
internal const val KEY_TOPSHELF_ADS = "revanced_ads_topshelf"

/** Row labels rendered through the app's own CharSequence resource wrapper. */
internal const val LABEL_VIDEO_ADS = "Block video ads"
internal const val LABEL_FEED_ADS = "Block feed ads"
internal const val LABEL_CATALOG_ADS = "Block catalog banners"
internal const val LABEL_TOPSHELF_ADS = "Block top shelf promotions"

internal data class SettingsMenuTypes(
    val menuItemType: String,
    val menuItemWrapper: String,
    val menuItemClass: String,
    val iconResourceId: Int,
)

/**
 * Chrome references extracted from `MaterialPreferenceToolbarFragment` (the app's own
 * toolbar-carrying preference screen): existing layout/id resources, the toolbar-hide
 * helper, the `VkTopBar` constructor chain with its Kotlin default-argument constants,
 * the accessibility-back resource helper, and the top-bar "own chrome" check.
 */
internal data class ToolbarChrome(
    val chromeLayoutId: Int,
    val contentId: Int,
    val legacyToolbarId: Int,
    val shadowId: Int,
    val hideHelperRef: String,
    val appBarLayoutId: Int,
    val topBarCtor: String,
    val topBarStyleInt: Int,
    val topBarFlagsInt: Int,
    val titleCtor: String,
    val titleStyleInt: Int,
    val textCtor: String,
    val textStyleInt: Int,
    val backCtor: String,
    val backStyleInt: Int,
    val companionFieldRef: String,
    val resHelperRef: String,
    val accessibilityBackId: Int,
    val chromeCheckRef: String,
)

/**
 * Injects the ReVanced settings classes into the app dex.
 *
 * The classes are pure smali templates compiled through dexlib2. Existing
 * descriptors are skipped, so calling this twice never duplicates a class.
 */
internal fun injectSettingsClasses(
    context: BytecodePatchContext,
    types: SettingsMenuTypes,
    chrome: ToolbarChrome,
    opener: NavOpener,
    switchListenerMethod: String,
) {
    val sources = linkedMapOf(
        ADS_SETTINGS_CLASS to adsSettingsSmali(types),
        START_SETTINGS_CLASS to startSettingsSmali(opener),
        ROOT_FRAGMENT_CLASS to settingsFragmentSmali(chrome, ROOT_FRAGMENT_CLASS, ADS_SETTINGS_CLASS, TITLE_ROOT, "buildRootContent"),
        SETTINGS_FRAGMENT_CLASS to settingsFragmentSmali(chrome, SETTINGS_FRAGMENT_CLASS, ADS_SETTINGS_CLASS, TITLE_ADS_GROUP, "buildAdsContent"),
        CONTENT_ADD_CLASS to contentAddSmali(),
        GROUP_CLICK_CLASS to groupClickSmali(opener, GROUP_CLICK_CLASS, SETTINGS_FRAGMENT_CLASS, "ads group row clicked"),
        TOGGLE_LISTENER_CLASS to toggleListenerSmali(switchListenerMethod),
        BACK_CLICK_CLASS to backClickSmali(),
    )

    sources.forEach { (descriptor, source) ->
        val existing = context.classDefs.firstOrNull { it.type == descriptor }
        if (existing != null) {
            // An already-patched input may carry an older generation of the helper
            // (e.g. the dialog-era AdsSettings without the shelf methods). Injection
            // must not silently keep it: the shelf patch would call methods that
            // do not exist. Reject loudly instead.
            val missing = requiredMethods[descriptor].orEmpty()
                .filter { name -> existing.methods.none { it.name == name } }
            check(missing.isEmpty()) {
                "Stale injected class $descriptor is missing $missing; re-patch from the stock APK"
            }
            return@forEach
        }
        context.classDefs.add(SmaliTestUtils.compileSmali(source))
    }
}

private val requiredMethods = mapOf(
    ADS_SETTINGS_CLASS to listOf(
        "getBoolean", "setEnabled", "isVideoAdsEnabled", "isFeedAdsEnabled",
        "isCatalogAdsEnabled", "isTopShelfAdsEnabled", "collapseTopShelfView",
        "addMenuEntry", "buildAdsContent", "buildRootContent",
    ),
    START_SETTINGS_CLASS to listOf("<init>", "invoke"),
    SETTINGS_FRAGMENT_CLASS to listOf("<init>", "onCreateView", "onViewCreated"),
    ROOT_FRAGMENT_CLASS to listOf("<init>", "onCreateView", "onViewCreated"),
    GROUP_CLICK_CLASS to listOf("<init>", "onClick"),
    TOGGLE_LISTENER_CLASS to listOf("<init>"),
    BACK_CLICK_CLASS to listOf("<init>", "invoke"),
    CONTENT_ADD_CLASS to listOf("<init>", "run"),
)

/** Defers the content `addView` out of the layout pass it may be joining. */
internal fun contentAddSmali(): String = """
    .class public final $CONTENT_ADD_CLASS
    .super Ljava/lang/Object;
    .implements Ljava/lang/Runnable;

    .field private final frame:Landroid/view/ViewGroup;

    .field private final content:Landroid/view/View;

    .method public constructor <init>(Landroid/view/ViewGroup;Landroid/view/View;)V
        .registers 3
        .param p1, "frame"
        .param p2, "content"

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        iput-object p1, p0, $CONTENT_ADD_CLASS->frame:Landroid/view/ViewGroup;
        iput-object p2, p0, $CONTENT_ADD_CLASS->content:Landroid/view/View;
        return-void
    .end method

    .method public run()V
        .registers 2

        iget-object v0, p0, $CONTENT_ADD_CLASS->frame:Landroid/view/ViewGroup;
        iget-object v1, p0, $CONTENT_ADD_CLASS->content:Landroid/view/View;
        invoke-virtual {v0, v1}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V
        return-void
    .end method
""".trimIndent()

private fun adsSettingsSmali(types: SettingsMenuTypes): String = """
    .class public final $ADS_SETTINGS_CLASS
    .super Ljava/lang/Object;

    .method public static getBoolean(Ljava/lang/String;)Z
        .registers 3
        .param p0, "key"

        const-string v0, "$PREF_FILE"
        invoke-static {v0}, Lcom/vk/core/preference/Preference;->f(Ljava/lang/String;)Landroid/content/SharedPreferences;
        move-result-object v0
        const/4 v1, 0x1
        invoke-interface {v0, p0, v1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
        move-result v0
        return v0
    .end method

    .method public static setEnabled(Ljava/lang/String;Z)V
        .registers 3
        .param p0, "key"
        .param p1, "value"

        const-string v0, "$PREF_FILE"
        invoke-static {v0}, Lcom/vk/core/preference/Preference;->f(Ljava/lang/String;)Landroid/content/SharedPreferences;
        move-result-object v0
        invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences${'$'}Editor;
        move-result-object v0
        invoke-interface {v0, p0, p1}, Landroid/content/SharedPreferences${'$'}Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences${'$'}Editor;
        move-result-object v0
        invoke-interface {v0}, Landroid/content/SharedPreferences${'$'}Editor;->apply()V
        return-void
    .end method

    .method public static isVideoAdsEnabled()Z
        .registers 1

        const-string v0, "$KEY_VIDEO_ADS"
        invoke-static {v0}, $ADS_SETTINGS_CLASS->getBoolean(Ljava/lang/String;)Z
        move-result v0
        return v0
    .end method

    .method public static isFeedAdsEnabled()Z
        .registers 1

        const-string v0, "$KEY_FEED_ADS"
        invoke-static {v0}, $ADS_SETTINGS_CLASS->getBoolean(Ljava/lang/String;)Z
        move-result v0
        return v0
    .end method

    .method public static isCatalogAdsEnabled()Z
        .registers 1

        const-string v0, "$KEY_CATALOG_ADS"
        invoke-static {v0}, $ADS_SETTINGS_CLASS->getBoolean(Ljava/lang/String;)Z
        move-result v0
        return v0
    .end method

    .method public static isTopShelfAdsEnabled()Z
        .registers 1

        const-string v0, "$KEY_TOPSHELF_ADS"
        invoke-static {v0}, $ADS_SETTINGS_CLASS->getBoolean(Ljava/lang/String;)Z
        move-result v0
        return v0
    .end method

    .method public static collapseTopShelfView(Landroid/view/View;)V
        .registers 3
        .param p0, "shelfView"

        invoke-virtual {p0}, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup${'$'}LayoutParams;
        move-result-object v0
        const/4 v1, 0x0
        iput v1, v0, Landroid/view/ViewGroup${'$'}LayoutParams;->height:I
        invoke-virtual {p0, v0}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V
        const/16 v1, 0x8
        invoke-virtual {p0, v1}, Landroid/view/View;->setVisibility(I)V
        return-void
    .end method

    .method public static addMenuEntry(Ljava/util/List;)V
        .registers 7
        .param p0, "items"

        sget-object v0, ${types.menuItemType}->ITEM_WITH_ICON:${types.menuItemType}
        invoke-virtual {v0}, ${types.menuItemType}->ordinal()I
        move-result v0

        new-instance v1, ${types.menuItemClass}
        const v2, 0x${types.iconResourceId.toString(16)}
        const-string v3, "ReVanced"
        new-instance v4, $START_SETTINGS_CLASS
        invoke-direct {v4}, $START_SETTINGS_CLASS-><init>()V
        const/4 v5, 0x1
        invoke-direct {v1, v2, v3, v4, v5}, ${types.menuItemClass}-><init>(ILjava/lang/String;Lkotlin/jvm/functions/Function1;Z)V

        new-instance v2, ${types.menuItemWrapper}
        invoke-direct {v2, v0, v1}, ${types.menuItemWrapper}-><init>(ILjava/lang/Object;)V

        invoke-interface {p0, v2}, Ljava/util/List;->add(Ljava/lang/Object;)Z
        return-void
    .end method

    .method public static buildAdsContent(Landroid/content/Context;Lcom/vk/core/fragments/FragmentImpl;)Landroid/view/View;
        .registers 10
        .param p0, "context"
        .param p1, "fragment"

        new-instance v7, Landroid/widget/LinearLayout;
        invoke-direct {v7, p0}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V
        const/4 v0, 0x1
        invoke-virtual {v7, v0}, Landroid/widget/LinearLayout;->setOrientation(I)V
${adsRow(LABEL_VIDEO_ADS, KEY_VIDEO_ADS, "isVideoAdsEnabled")}
${adsRow(LABEL_FEED_ADS, KEY_FEED_ADS, "isFeedAdsEnabled")}
${adsRow(LABEL_CATALOG_ADS, KEY_CATALOG_ADS, "isCatalogAdsEnabled")}
${adsRow(LABEL_TOPSHELF_ADS, KEY_TOPSHELF_ADS, "isTopShelfAdsEnabled")}
        return-object v7
    .end method

${buildRootContentSmali()}
""".trimIndent()

/**
 * The ReVanced root list: a single native `VkCell` row ("Ads") with a leading
 * themed icon and the cell chevron, opening the toggles screen.
 *
 * The row is transcribed from the app's own profile-menu row binder
 * (`com/vk/video/screens/profile/adapter/holder/j.t3`): same `VkCell`
 * construction, `VkCell$Left$Main$c` icon slot (`Lp72/j` drawable provider,
 * `Lp72/b` themed tint attr), title-only middle `VkCell$Middle$e` and the
 * chevron-bearing `VkCell$Right$d` default slot model.
 */
private fun buildRootContentSmali(): String = """
    .method public static buildRootContent(Landroid/content/Context;Lcom/vk/core/fragments/FragmentImpl;)Landroid/view/View;
        .registers 12
        .param p0, "context"
        .param p1, "fragment"

        new-instance v9, Landroid/widget/LinearLayout;
        invoke-direct {v9, p0}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V
        const/4 v0, 0x1
        invoke-virtual {v9, v0}, Landroid/widget/LinearLayout;->setOrientation(I)V

        new-instance v8, Lcom/vk/core/view/components/cell/VkCell;
        const/4 v0, 0x0
        const/4 v1, 0x6
        const/4 v2, 0x0
        invoke-direct {v8, p0, v0, v1, v2}, Lcom/vk/core/view/components/cell/VkCell;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;II)V

        new-instance v0, Landroid/view/ViewGroup${'$'}LayoutParams;
        const/4 v1, -0x1
        const/4 v2, -0x2
        invoke-direct {v0, v1, v2}, Landroid/view/ViewGroup${'$'}LayoutParams;-><init>(II)V
        invoke-virtual {v8, v0}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V
        const/4 v0, 0x1
        invoke-virtual {v8, v0}, Lcom/vk/core/view/components/cell/VkCell;->setRippleEnabled(Z)V

        new-instance v0, Lp72/j;
        const v1, 0x${ADS_GROUP_ICON_ID.toString(16)}
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
        invoke-virtual {v8, v0}, Lcom/vk/core/view/components/cell/VkCell;->setLeft(Lcom/vk/core/view/components/cell/VkCell${'$'}Left${'$'}b;)V

        new-instance v0, Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}e;
        new-instance v1, Lpx1/a${'$'}h;
        const-string v2, "$TITLE_ADS_GROUP"
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
        invoke-virtual {v8, v0}, Lcom/vk/core/view/components/cell/VkCell;->setMiddle(Lcom/vk/core/view/components/cell/VkCell${'$'}Middle${'$'}b;)V

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
        invoke-virtual {v8, v1}, Lcom/vk/core/view/components/cell/VkCell;->setRight(Lcom/vk/core/view/components/cell/VkCell${'$'}Right${'$'}d;)V

        new-instance v0, $GROUP_CLICK_CLASS
        invoke-direct {v0, p1}, $GROUP_CLICK_CLASS-><init>(Lcom/vk/core/fragments/FragmentImpl;)V
        invoke-virtual {v8, v0}, Landroid/view/View;->setOnClickListener(Landroid/view/View${'$'}OnClickListener;)V

        invoke-virtual {v9, v8}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V
        return-object v9
    .end method
""".trimIndent()

/**
 * One `VkSwitchItem` row, wired exactly like the app's own `VkSwitchItemPreference`
 * binding: title → checked → listener → align-switch-to-end, with the 6dip vertical
 * row margin density-scaled at runtime (no literal dimensions or colors).
 */
private fun adsRow(label: String, key: String, enabledGetter: String): String = """
        new-instance v0, Lcom/vk/core/view/components/switch/VkSwitchItem;
        const/4 v1, 0x0
        invoke-direct {v0, p0, v1}, Lcom/vk/core/view/components/switch/VkSwitchItem;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;)V

        new-instance v2, Lpx1/a${'$'}h;
        const-string v3, "$label"
        invoke-direct {v2, v3}, Lpx1/a${'$'}h;-><init>(Ljava/lang/CharSequence;)V
        invoke-virtual {v0, v2}, Lcom/vk/core/view/components/switch/VkSwitchItem;->setTitle(Lpx1/a;)V

        invoke-static {}, $ADS_SETTINGS_CLASS->$enabledGetter()Z
        move-result v3
        invoke-virtual {v0, v3}, Lcom/vk/core/view/components/switch/VkSwitchItem;->setChecked(Z)V

        new-instance v2, $TOGGLE_LISTENER_CLASS
        const-string v3, "$key"
        invoke-direct {v2, v3}, $TOGGLE_LISTENER_CLASS-><init>(Ljava/lang/String;)V
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

/**
 * A full screen hosted like the app's own toolbar-carrying preference screens
 * (transcribed from `MaterialPreferenceToolbarFragment`): the existing
 * `appkit_toolbar_fragment_hidable_with_coordinator` chrome, its legacy toolbar and
 * shadow hidden through the app's own hide helper, and a `VkTopBar` whose back
 * handler finishes the fragment.
 */
internal fun settingsFragmentSmali(
    chrome: ToolbarChrome,
    className: String,
    contentOwner: String,
    title: String,
    contentMethod: String,
): String = """
    .class public final $className
    .super Lcom/vk/core/fragments/FragmentImpl;

    .method public constructor <init>()V
        .registers 1

        invoke-direct {p0}, Lcom/vk/core/fragments/FragmentImpl;-><init>()V
        return-void
    .end method

    .method public onCreateView(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;
        .registers 9
        .param p1, "inflater"
        .param p2, "container"
        .param p3, "savedInstanceState"

        const v0, 0x${chrome.chromeLayoutId.toString(16)}
        const/4 v1, 0x0
        invoke-virtual {p1, v0, p2, v1}, Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;
        move-result-object v0

        invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->requireContext()Landroid/content/Context;
        move-result-object v1
        invoke-static {v1, p0}, $contentOwner->$contentMethod(Landroid/content/Context;Lcom/vk/core/fragments/FragmentImpl;)Landroid/view/View;
        move-result-object v1

        const v2, 0x${chrome.contentId.toString(16)}
        invoke-virtual {v0, v2}, Landroid/view/View;->findViewById(I)Landroid/view/View;
        move-result-object v2
        check-cast v2, Landroid/view/ViewGroup;
        if-eqz v2, :cond_revanced_content_added
        invoke-virtual {v2}, Landroid/view/View;->getParent()Landroid/view/ViewParent;
        move-result-object v3
        if-eqz v3, :cond_revanced_content_direct
        new-instance v3, $CONTENT_ADD_CLASS
        invoke-direct {v3, v2, v1}, $CONTENT_ADD_CLASS-><init>(Landroid/view/ViewGroup;Landroid/view/View;)V
        invoke-virtual {v2, v3}, Landroid/view/View;->post(Ljava/lang/Runnable;)Z
        move-result v4
        goto :cond_revanced_content_added

        :cond_revanced_content_direct
        invoke-virtual {v2, v1}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

        :cond_revanced_content_added

        const v2, 0x${chrome.legacyToolbarId.toString(16)}
        invoke-virtual {v0, v2}, Landroid/view/View;->findViewById(I)Landroid/view/View;
        move-result-object v2
        check-cast v2, Landroidx/appcompat/widget/Toolbar;
        if-eqz v2, :cond_revanced_toolbar_hidden
        invoke-static {v2}, ${chrome.hideHelperRef}
        :cond_revanced_toolbar_hidden

        const v2, 0x${chrome.shadowId.toString(16)}
        invoke-virtual {v0, v2}, Landroid/view/View;->findViewById(I)Landroid/view/View;
        move-result-object v2
        check-cast v2, Lcom/vk/core/view/AppBarShadowView;
        if-eqz v2, :cond_revanced_shadow_hidden
        invoke-static {v2}, ${chrome.hideHelperRef}
        :cond_revanced_shadow_hidden

        return-object v0
    .end method

    .method public onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V
        .registers 15
        .param p1, "view"
        .param p2, "savedInstanceState"

        invoke-super {p0, p1, p2}, Lcom/vk/core/fragments/FragmentImpl;->onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V

        new-instance p1, Lcom/vk/core/view/components/topbar/VkTopBar;
        invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->requireContext()Landroid/content/Context;
        move-result-object p2
        const/4 v0, ${chrome.topBarStyleInt}
        const/4 v1, ${chrome.topBarFlagsInt}
        const/4 v2, 0x0
        invoke-direct {p1, p2, v2, v0, v1}, ${chrome.topBarCtor}

        new-instance v3, Lcom/vk/core/view/components/topbar/VkTopBar${'$'}Middle${'$'}Text;
        new-instance v4, Lcom/vk/core/view/components/topbar/VkTopBar${'$'}Middle${'$'}Text${'$'}Title;
        const-string v5, "$title"
        const/4 v8, 0x0
        const/16 v9, ${chrome.titleStyleInt}
        const/4 v6, 0x0
        const/4 v7, 0x0
        invoke-direct/range {v4 .. v9}, ${chrome.titleCtor}

        const/16 v8, ${chrome.textStyleInt}
        const/4 v5, 0x0
        invoke-direct/range {v3 .. v8}, ${chrome.textCtor}

        invoke-virtual {p1, v3}, Lcom/vk/core/view/components/topbar/VkTopBar;->setMiddle(Lcom/vk/core/view/components/topbar/VkTopBar${'$'}Middle;)V

        new-instance v4, Lcom/vk/core/view/components/topbar/VkTopBar${'$'}c;
        new-instance v5, $BACK_CLICK_CLASS
        invoke-direct {v5, p0}, $BACK_CLICK_CLASS-><init>(Lcom/vk/core/fragments/FragmentImpl;)V
        sget-object v0, ${chrome.companionFieldRef}
        const v1, 0x${chrome.accessibilityBackId.toString(16)}
        invoke-static {v0, v1}, ${chrome.resHelperRef}
        move-result-object v6
        const/4 v8, 0x0
        const/16 v9, ${chrome.backStyleInt}
        const/4 v7, 0x0
        invoke-direct/range {v4 .. v9}, ${chrome.backCtor}

        invoke-virtual {p1, v4}, Lcom/vk/core/view/components/topbar/VkTopBar;->setBack(Lcom/vk/core/view/components/topbar/VkTopBar${'$'}c;)V

        invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->getView()Landroid/view/View;
        move-result-object v0
        if-eqz v0, :cond_revanced_no_appbar
        const v1, 0x${chrome.appBarLayoutId.toString(16)}
        invoke-virtual {v0, v1}, Landroid/view/View;->findViewById(I)Landroid/view/View;
        move-result-object v0
        check-cast v0, Landroid/view/ViewGroup;
        if-eqz v0, :cond_revanced_no_appbar
        invoke-virtual {v0, p1}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V
        :cond_revanced_no_appbar

        invoke-static {p0, p1}, ${chrome.chromeCheckRef}
        move-result v0
        if-eqz v0, :cond_revanced_no_before
        const/4 v2, 0x0
        invoke-virtual {p1, v2}, Lcom/vk/core/view/components/topbar/VkTopBar;->setBefore(Lcom/vk/core/view/components/topbar/VkTopBar${'$'}d;)V
        :cond_revanced_no_before

        return-void
    .end method
""".trimIndent()

private fun startSettingsSmali(opener: NavOpener): String = """
    .class public final $START_SETTINGS_CLASS
    .super Ljava/lang/Object;
    .implements Lkotlin/jvm/functions/Function1;

    .method public constructor <init>()V
        .registers 1

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        return-void
    .end method

    .method public invoke(Ljava/lang/Object;)Ljava/lang/Object;
        .registers 5
        .param p1, "arg"

        check-cast p1, Landroid/content/Context;
        new-instance v0, ${opener.instanceType}
        const-class v1, $ROOT_FRAGMENT_CLASS
        const/4 v2, 0x0
        const/4 v3, 0x0
        invoke-direct {v0, v1, v2, v3}, ${opener.ctorOwner}-><init>(Ljava/lang/Class;Ljava/lang/Class;Landroid/os/Bundle;)V
        invoke-virtual {v0, p1}, ${opener.hOwner}->h(Landroid/content/Context;)V
        const/4 v0, 0x0
        return-object v0
    .end method
""".trimIndent()

/**
 * Click of the root "Ads" row: opens the toggles screen through the app's own
 * fragment opener (the same builder `StartReVancedSettings` uses).
 */
internal fun groupClickSmali(opener: NavOpener, className: String, targetClass: String, logLabel: String): String = """
    .class public final $className
    .super Ljava/lang/Object;
    .implements Landroid/view/View${'$'}OnClickListener;

    .field private final fragment:Lcom/vk/core/fragments/FragmentImpl;

    .method public constructor <init>(Lcom/vk/core/fragments/FragmentImpl;)V
        .registers 2
        .param p1, "fragment"

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        iput-object p1, p0, $className->fragment:Lcom/vk/core/fragments/FragmentImpl;
        return-void
    .end method

    .method public onClick(Landroid/view/View;)V
        .registers 6
        .param p1, "view"

        const-string v0, "ReVanced"
        const-string v1, "$logLabel"
        invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
        new-instance v0, ${opener.instanceType}
        const-class v1, $targetClass
        const/4 v2, 0x0
        const/4 v3, 0x0
        invoke-direct {v0, v1, v2, v3}, ${opener.ctorOwner}-><init>(Ljava/lang/Class;Ljava/lang/Class;Landroid/os/Bundle;)V
        iget-object v1, p0, $className->fragment:Lcom/vk/core/fragments/FragmentImpl;
        invoke-virtual {v0, v1}, ${opener.iOwner}->i(Lcom/vk/core/fragments/FragmentImpl;)V
        return-void
    .end method
""".trimIndent()

private fun toggleListenerSmali(listenerMethod: String): String = """
    .class public final $TOGGLE_LISTENER_CLASS
    .super Ljava/lang/Object;
    .implements Lcom/vk/core/view/components/switch/VkSwitchItem${'$'}a;

    .field private final key:Ljava/lang/String;

    .method public constructor <init>(Ljava/lang/String;)V
        .registers 2
        .param p1, "key"

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        iput-object p1, p0, $TOGGLE_LISTENER_CLASS->key:Ljava/lang/String;
        return-void
    .end method

    .method public $listenerMethod(Z)V
        .registers 3
        .param p1, "checked"

        iget-object v0, p0, $TOGGLE_LISTENER_CLASS->key:Ljava/lang/String;
        invoke-static {v0, p1}, $ADS_SETTINGS_CLASS->setEnabled(Ljava/lang/String;Z)V
        return-void
    .end method
""".trimIndent()

private fun backClickSmali(): String = """
    .class public final $BACK_CLICK_CLASS
    .super Ljava/lang/Object;
    .implements Lkotlin/jvm/functions/Function0;

    .field private final fragment:Lcom/vk/core/fragments/FragmentImpl;

    .method public constructor <init>(Lcom/vk/core/fragments/FragmentImpl;)V
        .registers 2
        .param p1, "fragment"

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        iput-object p1, p0, $BACK_CLICK_CLASS->fragment:Lcom/vk/core/fragments/FragmentImpl;
        return-void
    .end method

    .method public invoke()Ljava/lang/Object;
        .registers 2

        iget-object v0, p0, $BACK_CLICK_CLASS->fragment:Lcom/vk/core/fragments/FragmentImpl;
        invoke-virtual {v0}, Lcom/vk/core/fragments/FragmentImpl;->getActivity()Landroidx/fragment/app/FragmentActivity;
        move-result-object v0
        if-eqz v0, :cond_revanced_back_done
        check-cast v0, Landroidx/activity/ComponentActivity;
        invoke-virtual {v0}, Landroidx/activity/ComponentActivity;->onBackPressed()V
        :cond_revanced_back_done
        const/4 v0, 0x0
        return-object v0
    .end method
""".trimIndent()
