package app.revanced.patches.vkvideo.sponsorskip

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.vkvideo.settings.parameterWidth

val sponsorSkipPatch = bytecodePatch(
    name = "SponsorSkip",
    description = "Detects sponsor, self-promo and intro segments in a video's official episodes and " +
        "skips them automatically or offers a skip button, per category.",
) {
    compatibleWith("com.vk.vkvideo")

    apply {
        LebedevRules.verify()
        injectSponsorSkipClasses(this)

        sponsorSkipVideoHandoffFingerprint.method.insertCall(
            "$SKIP_ENGINE_CLASS->onVideoChanged(Lp73/a;Lcom/vk/dto/common/VideoFile;)V",
        )
        sponsorSkipViewAttachedFingerprint.method.insertCall(
            "$SKIP_ENGINE_CLASS->onViewAttached(Lp73/a;Landroid/view/View;)V",
        )
        sponsorSkipViewSetupFingerprint.method.insertCall(
            "$SKIP_ENGINE_CLASS->onViewAttached(Lp73/a;Landroid/view/View;)V",
        )
        sponsorSkipHolderFingerprint.method.insertCall(
            "$SKIP_ENGINE_CLASS->onViewAttached(Lp73/a;Landroid/view/View;)V",
            width = 2,
        )
        sponsorSkipReleaseFingerprint.method.insertCall(
            "$SKIP_ENGINE_CLASS->onRelease(Lp73/a;)V",
        )
    }
}

/**
 * Prepends a static call that forwards every incoming argument (receiver
 * included) of this method. The inserts add no registers, so the parameter
 * window of the original body stays where it is.
 */
private fun MutableMethod.insertCall(target: String, width: Int? = null) {
    val count = width ?: parameterWidth(this)
    val first = implementation!!.registerCount - parameterWidth(this)
    check(first + count <= 16) { "${definingClass}->$name: parameter window does not fit a 4-bit invoke" }
    val registers = (0 until count).joinToString(", ") { "v${first + it}" }
    addInstruction(0, "invoke-static {$registers}, $target")
}
