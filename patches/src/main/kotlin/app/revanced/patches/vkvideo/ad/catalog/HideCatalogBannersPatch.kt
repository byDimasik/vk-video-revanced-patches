package app.revanced.patches.vkvideo.ad.catalog

import app.revanced.patches.vkvideo.settings.prependConditionalReturn
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
import app.revanced.patcher.patch.bytecodePatch

val hideCatalogBannersPatch = bytecodePatch(
    name = "Hide catalog ad banners",
    description = "Short-circuits catalog ad banner handling to hide promoted banners in catalog sections."
) {
    compatibleWith("com.vk.vkvideo")
    dependsOn(revancedSettingsPatch)

    apply {
        // `CatalogAdBannerDto.slotId` is deserialized reflectively, so the getter is the choke point:
        // returning `null` makes the banner look absent to every reader.
        val slotIdMethod = catalogAdBannerSlotIdFingerprint.method
        slotIdMethod.prependConditionalReturn(
            "Lapp/revanced/extension/vkvideo/AdsSettings;->isCatalogAdsEnabled()Z",
            "const/4 v0, 0x0\nreturn-object v0"
        )

        // The catalog section model is built from this getter; its callers null-check the result.
        val sectionAdBannerMethod = catalogSectionAdBannerGetterFingerprint.method
        sectionAdBannerMethod.prependConditionalReturn(
            "Lapp/revanced/extension/vkvideo/AdsSettings;->isCatalogAdsEnabled()Z",
            "const/4 v0, 0x0\nreturn-object v0"
        )
    }
}
