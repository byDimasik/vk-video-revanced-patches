package app.revanced.patches.vkvideo.settings

import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.smali.SmaliTestUtils

internal const val ADS_SETTINGS_CLASS = "Lapp/revanced/extension/vkvideo/AdsSettings;"
private const val START_SETTINGS_CLASS = "Lapp/revanced/extension/vkvideo/StartReVancedSettings;"
private const val SETTINGS_DIALOG_CLASS = "Lapp/revanced/extension/vkvideo/ReVancedSettingsDialog;"
private const val TOGGLE_LISTENER_CLASS = "Lapp/revanced/extension/vkvideo/ToggleListener;"

private const val PREF_FILE = "revanced_settings"
private const val KEY_VIDEO_ADS = "revanced_ads_video"
private const val KEY_FEED_ADS = "revanced_ads_feed"
private const val KEY_CATALOG_ADS = "revanced_ads_catalog"

internal data class SettingsMenuTypes(
    val menuItemType: String,
    val menuItemWrapper: String,
    val menuItemClass: String,
    val iconResourceId: Int,
)

/**
 * Injects the ReVanced settings helper classes into the app dex.
 *
 * The classes are pure smali templates compiled through dexlib2. Existing
 * descriptors are skipped, so calling this twice never duplicates a class.
 */
internal fun injectSettingsClasses(context: BytecodePatchContext, types: SettingsMenuTypes) {
    val sources = linkedMapOf(
        ADS_SETTINGS_CLASS to adsSettingsSmali(types),
        START_SETTINGS_CLASS to START_SETTINGS_SMALI,
        SETTINGS_DIALOG_CLASS to SETTINGS_DIALOG_SMALI,
        TOGGLE_LISTENER_CLASS to TOGGLE_LISTENER_SMALI,
    )

    sources.forEach { (descriptor, source) ->
        if (context.classDefs.any { it.type == descriptor }) return@forEach
        context.classDefs.add(SmaliTestUtils.compileSmali(source))
    }
}

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

    .method public static buildContentView(Landroid/content/Context;)Landroid/view/View;
        .registers 10
        .param p0, "context"

        new-instance v0, Landroid/widget/LinearLayout;
        invoke-direct {v0, p0}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V
        const/4 v1, 0x1
        invoke-virtual {v0, v1}, Landroid/widget/LinearLayout;->setOrientation(I)V
        const/16 v1, 0x18
        invoke-virtual {v0, v1, v1, v1, v1}, Landroid/widget/LinearLayout;->setPadding(IIII)V

        new-instance v1, Landroid/widget/TextView;
        invoke-direct {v1, p0}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V
        const-string v2, "Ads"
        invoke-virtual {v1, v2}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
        invoke-virtual {v0, v1}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V

        new-instance v1, Landroid/widget/Switch;
        invoke-direct {v1, p0}, Landroid/widget/Switch;-><init>(Landroid/content/Context;)V
        const-string v2, "Block video ads"
        invoke-virtual {v1, v2}, Landroid/widget/Switch;->setText(Ljava/lang/CharSequence;)V
        invoke-static {}, $ADS_SETTINGS_CLASS->isVideoAdsEnabled()Z
        move-result v3
        invoke-virtual {v1, v3}, Landroid/widget/Switch;->setChecked(Z)V
        new-instance v3, $TOGGLE_LISTENER_CLASS
        const-string v2, "$KEY_VIDEO_ADS"
        invoke-direct {v3, v2}, $TOGGLE_LISTENER_CLASS-><init>(Ljava/lang/String;)V
        invoke-virtual {v1, v3}, Landroid/widget/Switch;->setOnCheckedChangeListener(Landroid/widget/CompoundButton${'$'}OnCheckedChangeListener;)V
        invoke-virtual {v0, v1}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V

        new-instance v1, Landroid/widget/Switch;
        invoke-direct {v1, p0}, Landroid/widget/Switch;-><init>(Landroid/content/Context;)V
        const-string v2, "Block feed ads"
        invoke-virtual {v1, v2}, Landroid/widget/Switch;->setText(Ljava/lang/CharSequence;)V
        invoke-static {}, $ADS_SETTINGS_CLASS->isFeedAdsEnabled()Z
        move-result v3
        invoke-virtual {v1, v3}, Landroid/widget/Switch;->setChecked(Z)V
        new-instance v3, $TOGGLE_LISTENER_CLASS
        const-string v2, "$KEY_FEED_ADS"
        invoke-direct {v3, v2}, $TOGGLE_LISTENER_CLASS-><init>(Ljava/lang/String;)V
        invoke-virtual {v1, v3}, Landroid/widget/Switch;->setOnCheckedChangeListener(Landroid/widget/CompoundButton${'$'}OnCheckedChangeListener;)V
        invoke-virtual {v0, v1}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V

        new-instance v1, Landroid/widget/Switch;
        invoke-direct {v1, p0}, Landroid/widget/Switch;-><init>(Landroid/content/Context;)V
        const-string v2, "Block catalog banners"
        invoke-virtual {v1, v2}, Landroid/widget/Switch;->setText(Ljava/lang/CharSequence;)V
        invoke-static {}, $ADS_SETTINGS_CLASS->isCatalogAdsEnabled()Z
        move-result v3
        invoke-virtual {v1, v3}, Landroid/widget/Switch;->setChecked(Z)V
        new-instance v3, $TOGGLE_LISTENER_CLASS
        const-string v2, "$KEY_CATALOG_ADS"
        invoke-direct {v3, v2}, $TOGGLE_LISTENER_CLASS-><init>(Ljava/lang/String;)V
        invoke-virtual {v1, v3}, Landroid/widget/Switch;->setOnCheckedChangeListener(Landroid/widget/CompoundButton${'$'}OnCheckedChangeListener;)V
        invoke-virtual {v0, v1}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V

        new-instance v1, Landroid/widget/TextView;
        invoke-direct {v1, p0}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V
        const-string v2, "Top shelf promotions are always blocked."
        invoke-virtual {v1, v2}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
        invoke-virtual {v0, v1}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V

        return-object v0
    .end method
""".trimIndent()

private const val START_SETTINGS_SMALI = """
    .class public final $START_SETTINGS_CLASS
    .super Ljava/lang/Object;
    .implements Lkotlin/jvm/functions/Function1;

    .method public constructor <init>()V
        .registers 1

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        return-void
    .end method

    .method public invoke(Ljava/lang/Object;)Ljava/lang/Object;
        .registers 4
        .param p1, "arg"

        check-cast p1, Landroid/content/Context;
        new-instance v0, $SETTINGS_DIALOG_CLASS
        invoke-direct {v0, p1}, $SETTINGS_DIALOG_CLASS-><init>(Landroid/content/Context;)V
        invoke-virtual {v0}, $SETTINGS_DIALOG_CLASS->show()V
        const/4 v0, 0x0
        return-object v0
    .end method
"""

private const val SETTINGS_DIALOG_SMALI = """
    .class public final $SETTINGS_DIALOG_CLASS
    .super Landroid/app/Dialog;

    .method public constructor <init>(Landroid/content/Context;)V
        .registers 3
        .param p1, "context"

        invoke-direct {p0, p1}, Landroid/app/Dialog;-><init>(Landroid/content/Context;)V
        const-string v0, "ReVanced"
        invoke-virtual {p0, v0}, Landroid/app/Dialog;->setTitle(Ljava/lang/CharSequence;)V
        invoke-static {p1}, $ADS_SETTINGS_CLASS->buildContentView(Landroid/content/Context;)Landroid/view/View;
        move-result-object v0
        invoke-virtual {p0, v0}, Landroid/app/Dialog;->setContentView(Landroid/view/View;)V
        return-void
    .end method
"""

private const val TOGGLE_LISTENER_SMALI = """
    .class public final $TOGGLE_LISTENER_CLASS
    .super Ljava/lang/Object;
    .implements Landroid/widget/CompoundButton${'$'}OnCheckedChangeListener;

    .field private final key:Ljava/lang/String;

    .method public constructor <init>(Ljava/lang/String;)V
        .registers 2
        .param p1, "key"

        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
        iput-object p1, p0, $TOGGLE_LISTENER_CLASS->key:Ljava/lang/String;
        return-void
    .end method

    .method public onCheckedChanged(Landroid/widget/CompoundButton;Z)V
        .registers 4
        .param p2, "isChecked"

        iget-object v0, p0, $TOGGLE_LISTENER_CLASS->key:Ljava/lang/String;
        invoke-static {v0, p2}, $ADS_SETTINGS_CLASS->setEnabled(Ljava/lang/String;Z)V
        return-void
    .end method
"""
