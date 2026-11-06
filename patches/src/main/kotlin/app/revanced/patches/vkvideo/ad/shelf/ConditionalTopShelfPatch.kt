package app.revanced.patches.vkvideo.ad.shelf

import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.vkvideo.settings.ADS_SETTINGS_CLASS
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

/**
 * Runtime equivalent of the deleted build-time SuppressTopShelfPatch: when
 * `isTopShelfAdsEnabled()` is false, the two catalog top-shelf views are
 * collapsed (`layoutParams.height = 0` + `setVisibility(GONE)`) by the injected
 * `AdsSettings.collapseTopShelfView(View)` helper. When true, control flows
 * straight into the original code, so the shelf renders untouched.
 *
 * Register discipline (issue #42's VerifyError class): the appended block only
 * uses ops legal at any register width — `invoke-static/range` for the collapse
 * call, `move-result`/`if-nez` (8-bit register fields) for the guard. The frame
 * grows by exactly one append-scratch register for the guard result; existing
 * registers and parameters are untouched, so no operand remap happens. The
 * collapse mechanics live in the injected helper because DEX field opcodes
 * (`iget-object`/`iput`, format 22c) are 4-bit only and could not address
 * scratch registers at v16+.
 */
val conditionalTopShelfPatch = bytecodePatch(
    name = "Conditional top shelf",
    description = "Makes the catalog top-shelf suppression a runtime toggle (4th Ads switch)."
) {
    compatibleWith("com.vk.vkvideo")
    dependsOn(revancedSettingsPatch)

    apply {
        // AdsSettings (incl. isTopShelfAdsEnabled/collapseTopShelfView) is injected by
        // the revancedSettingsPatch dependency.

        for (fingerprint in listOf(videoTopshelfBlockFingerprint, musicTopshelfFragmentFingerprint)) {
            val method = fingerprint.method
            val site = findShelfInflate(method)
            val impl = method.implementation ?: error("No implementation in ${method.definingClass}->${method.name}")

            // Append-scratch growth: +1 register at the top; nothing shifts.
            val oldCount = impl.registerCount
            method.setImplementation(
                MutableMethodImplementation(
                    ImmutableMethodImplementation(
                        oldCount + 1,
                        impl.instructions.toList(),
                        impl.tryBlocks,
                        impl.debugItems,
                    )
                )
            )

            val scratch = oldCount
            val anchor = method.implementation!!.instructions[site.insertAfterIndex + 1]
            method.addInstructionsWithLabels(
                site.insertAfterIndex + 1,
                """
                invoke-static {}, $ADS_SETTINGS_CLASS->isTopShelfAdsEnabled()Z
                move-result v$scratch
                if-nez v$scratch, :revanced_shelf_continue
                invoke-static/range {v${site.viewRegister} .. v${site.viewRegister}}, $ADS_SETTINGS_CLASS->collapseTopShelfView(Landroid/view/View;)V
                """.trimIndent(),
                ExternalLabel("revanced_shelf_continue", anchor),
            )
        }
    }
}
