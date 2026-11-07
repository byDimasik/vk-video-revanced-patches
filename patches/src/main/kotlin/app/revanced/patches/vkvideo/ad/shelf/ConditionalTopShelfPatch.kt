package app.revanced.patches.vkvideo.ad.shelf

import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.vkvideo.settings.ADS_SETTINGS_CLASS
import app.revanced.patches.vkvideo.settings.growFrameWithScratch
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

/**
 * Runtime equivalent of the deleted build-time SuppressTopShelfPatch: when the
 * top-shelf toggle is ON (the default — blocking enabled), the two catalog
 * top-shelf views are collapsed (`layoutParams.height = 0` + `setVisibility(GONE)`)
 * by the injected `AdsSettings.collapseTopShelfView(View)` helper; when OFF, control
 * flows straight into the original code and the shelf renders.
 *
 * Register discipline (issue #42's VerifyError class): the frame grows by one slot
 * via the device-verified window-remap machinery (`growFrameWithScratch` — DEX places
 * incoming arguments in the LAST `paramWidth` registers, so growth MUST remap the
 * parameter window, otherwise every parameter reads one register low). The guard
 * result lives in the window slot freed by the remap; the collapse invoke uses the
 * `/range` form so the view register is legal at any width. The collapse mechanics
 * live in the injected helper because DEX field opcodes (`iget-object`/`iput`,
 * format 22c) are 4-bit only and could not address high registers.
 */
val conditionalTopShelfPatch = bytecodePatch(
    name = "Conditional top shelf",
    description = "Makes the catalog top-shelf suppression a runtime toggle (4th Ads switch)."
) {
    compatibleWith("com.vk.vkvideo")
    dependsOn(revancedSettingsPatch)

    apply {
        for (fingerprint in listOf(videoTopshelfBlockFingerprint, musicTopshelfFragmentFingerprint)) {
            val method = fingerprint.method
            val impl = method.implementation ?: error("No implementation in ${method.definingClass}->${method.name}")

            // Append-scratch growth with the parameter-window remap; returns the
            // register freed by the window shift.
            val scratch = method.growFrameWithScratch()

            // Re-locate the inflate site on the REMAPPED method: window registers
            // (e.g. the inflated root stored into a param-window field) moved +1.
            val site = findShelfInflate(method)

            val anchor = method.implementation!!.instructions[site.insertAfterIndex + 1]
            method.addInstructionsWithLabels(
                site.insertAfterIndex + 1,
                """
                invoke-static {}, $ADS_SETTINGS_CLASS->isTopShelfAdsEnabled()Z
                move-result v$scratch
                if-eqz v$scratch, :revanced_shelf_continue
                invoke-static/range {v${site.viewRegister} .. v${site.viewRegister}}, $ADS_SETTINGS_CLASS->collapseTopShelfView(Landroid/view/View;)V
                """.trimIndent(),
                ExternalLabel("revanced_shelf_continue", anchor),
            )
        }
    }
}
