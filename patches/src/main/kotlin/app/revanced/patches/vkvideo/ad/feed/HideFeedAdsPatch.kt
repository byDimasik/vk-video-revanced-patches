package app.revanced.patches.vkvideo.ad.feed

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.patch.bytecodePatch

val hideFeedAdsPatch = bytecodePatch(
    name = "Hide feed ads",
    description = "Short-circuits MyTargetNativeAdEntry and PromoPost deserializers to hide feed ads."
) {
    compatibleWith("com.vk.vkvideo")

    apply {
        val myTargetMethod = myTargetNativeAdEntryFingerprint.method
        myTargetMethod.replaceInstruction(0, "const/4 v0, 0x0")
        myTargetMethod.addInstruction(1, "return-object v0")

        val promoPostMethod = promoPostFingerprint.method
        promoPostMethod.replaceInstruction(0, "const/4 v0, 0x0")
        promoPostMethod.addInstruction(1, "return-object v0")
    }
}
