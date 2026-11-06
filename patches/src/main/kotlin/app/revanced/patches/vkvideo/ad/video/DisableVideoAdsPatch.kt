package app.revanced.patches.vkvideo.ad.video

import app.revanced.patches.vkvideo.settings.prependConditionalReturn
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
import app.revanced.patcher.patch.bytecodePatch

val disableVideoAdsPatch = bytecodePatch(
    name = "Disable video ads",
    description = "Disables in-stream video advertisements by short-circuiting ad loading and playback controllers."
) {
    compatibleWith("com.vk.vkvideo")
    dependsOn(revancedSettingsPatch)

    apply {
        val instreamAdJsonParserMethod = instreamAdJsonParserFingerprint.method
        instreamAdJsonParserMethod.prependConditionalReturn(
            "Lapp/revanced/extension/vkvideo/AdsSettings;->isVideoAdsEnabled()Z",
            "const/4 v0, 0x0\nreturn-object v0"
        )

        val videoFileInstreamAdMethod = videoFileInstreamAdFingerprint.method
        videoFileInstreamAdMethod.prependConditionalReturn(
            "Lapp/revanced/extension/vkvideo/AdsSettings;->isVideoAdsEnabled()Z",
            "const/4 v0, 0x0\nreturn-object v0"
        )

        val instreamAdLoaderMethod = instreamAdLoaderFingerprint.method
        instreamAdLoaderMethod.prependConditionalReturn(
            "Lapp/revanced/extension/vkvideo/AdsSettings;->isVideoAdsEnabled()Z",
            "return-void"
        )

        val instreamAdControllerMethod = instreamAdControllerFingerprint.method
        instreamAdControllerMethod.prependConditionalReturn(
            "Lapp/revanced/extension/vkvideo/AdsSettings;->isVideoAdsEnabled()Z",
            "return-void"
        )
    }
}
