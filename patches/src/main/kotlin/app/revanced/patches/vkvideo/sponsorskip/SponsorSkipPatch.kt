package app.revanced.patches.vkvideo.sponsorskip

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import app.revanced.patches.vkvideo.settings.parameterWidth

val sponsorSkipPatch = bytecodePatch(
    name = "SponsorSkip",
    description = "Detects sponsor, self-promo and intro segments in a video's official episodes and " +
        "skips them automatically or offers a skip button, per category.",
) {
    compatibleWith("com.vk.vkvideo")

    apply {
        LebedevRules.verify()
        val api = resolveAutoPlayApi(classDefs.toList()).also { autoPlayApi = it }
        injectSponsorSkipClasses(this, api)

        sponsorSkipVideoHandoffFingerprint.method.insertCall(
            "$SKIP_ENGINE_CLASS->onVideoChanged(${api.controller}$VIDEO_FILE_TYPE)V",
        )
        sponsorSkipViewAttachedFingerprint.method.insertCall(
            "$SKIP_ENGINE_CLASS->onViewAttached(${api.controller}Landroid/view/View;)V",
        )
        sponsorSkipViewSetupFingerprint.method.insertCall(
            "$SKIP_ENGINE_CLASS->onViewAttached(${api.controller}Landroid/view/View;)V",
        )
        sponsorSkipHolderFingerprint.method.insertCall(
            "$SKIP_ENGINE_CLASS->onViewAttached(${api.controller}Landroid/view/View;)V",
            width = 2,
        )
        sponsorSkipReleaseFingerprint.method.insertCall(
            "$SKIP_ENGINE_CLASS->onRelease(${api.controller})V",
        )
        sponsorSkipSeekFingerprint.method.insertBeforeReturns(
            "invoke-static {v3, v4, v5}, $SKIP_ENGINE_CLASS->onSeek(Ljava/lang/Object;J)V",
        )
        sponsorSkipSectionDrawableFingerprint.method.insertBeforeReturns(
            "invoke-static {p0, p1}, $SEEK_SECTIONS_CLASS->apply(Landroid/view/View;Landroid/graphics/drawable/Drawable;)V",
        )
        sponsorSkipSectionListFingerprint.method.insertBeforeReturns(
            "iget-object v0, p0, Lone/video/controls/views/seek/a;->a:Landroidx/appcompat/widget/AppCompatSeekBar;",
            "invoke-virtual {v0}, Landroid/widget/ProgressBar;->getProgressDrawable()Landroid/graphics/drawable/Drawable;",
            "move-result-object v1",
            "invoke-static {v0, v1}, $SEEK_SECTIONS_CLASS->apply(Landroid/view/View;Landroid/graphics/drawable/Drawable;)V",
        )
    }
}

/**
 * Inserts [instructions] before every `RETURN_VOID` of this method. The bodies are
 * post-body at that point, so they may clobber locals; the frame stays as is.
 */
private fun MutableMethod.insertBeforeReturns(vararg instructions: String) {
    val returns = implementation!!.instructions.filter { it.opcode == Opcode.RETURN_VOID }
    check(returns.isNotEmpty()) { "${definingClass}->$name has no return" }
    val block = instructions.joinToString("\n")
    returns.forEach { addInstructions(implementation!!.instructions.indexOf(it), block) }
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
