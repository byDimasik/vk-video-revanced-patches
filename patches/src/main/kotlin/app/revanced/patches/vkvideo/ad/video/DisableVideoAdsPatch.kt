package app.revanced.patches.vkvideo.ad.video

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.patch.bytecodePatch

val disableVideoAdsPatch = bytecodePatch(
    name = "Disable video ads",
    description = "Disables in-stream video advertisements by short-circuiting ad loading and playback controllers."
) {
    compatibleWith("com.vk.vkvideo")

    apply {
        val instreamAdJsonParserMethod = instreamAdJsonParserFingerprint.method
        instreamAdJsonParserMethod.replaceInstruction(0, "const/4 v0, 0x0")
        instreamAdJsonParserMethod.addInstruction(1, "return-object v0")

        val videoFileInstreamAdMethod = videoFileInstreamAdFingerprint.method
        videoFileInstreamAdMethod.replaceInstruction(0, "const/4 v0, 0x0")
        videoFileInstreamAdMethod.addInstruction(1, "return-object v0")

        val instreamAdLoaderMethod = instreamAdLoaderFingerprint.method
        instreamAdLoaderMethod.replaceInstruction(0, "return-void")

        val instreamAdControllerMethod = instreamAdControllerFingerprint.method
        instreamAdControllerMethod.replaceInstruction(0, "return-void")
    }
}
