package app.revanced.patches.vkvideo.ad.feed

import app.revanced.patches.vkvideo.settings.prependConditionalReturn
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
import app.revanced.patcher.patch.bytecodePatch

val hideFeedAdsPatch = bytecodePatch(
    name = "Hide feed ads",
    description = "Short-circuits MyTargetNativeAdEntry and PromoPost deserializers to hide feed ads."
) {
    compatibleWith("com.vk.vkvideo")
    dependsOn(revancedSettingsPatch)

    apply {
        val myTargetMethod = myTargetNativeAdEntryFingerprint.method
        myTargetMethod.prependConditionalReturn(
            "Lapp/revanced/extension/vkvideo/AdsSettings;->isFeedAdsEnabled()Z",
            "const/4 v0, 0x0\nreturn-object v0"
        )

        val promoPostMethod = promoPostFingerprint.method
        promoPostMethod.prependConditionalReturn(
            "Lapp/revanced/extension/vkvideo/AdsSettings;->isFeedAdsEnabled()Z",
            "const/4 v0, 0x0\nreturn-object v0"
        )
    }
}
