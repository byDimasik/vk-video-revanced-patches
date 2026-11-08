package app.revanced.patches.vkvideo.sponsorskip.settings

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.vkvideo.settings.ADS_SETTINGS_CLASS
import app.revanced.patches.vkvideo.settings.extractToolbarChrome
import app.revanced.patches.vkvideo.settings.navOpenerFingerprint
import app.revanced.patches.vkvideo.settings.resolveNavOpener
import app.revanced.patches.vkvideo.settings.revancedSettingsPatch
import app.revanced.patches.vkvideo.settings.switchListenerMethodFingerprint
import app.revanced.patches.vkvideo.settings.toolbarChromeFingerprint
import app.revanced.patches.vkvideo.sponsorskip.sponsorSkipPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** The root list builder injected by the settings patch; only exists after it applied. */
internal val rootContentFingerprint = fingerprint {
    custom { method, classDef -> classDef.type == ADS_SETTINGS_CLASS && method.name == "buildRootContent" }
}

val sponsorSkipSettingsPatch = bytecodePatch(
    name = "SponsorSkip settings",
    description = "Adds a SponsorSkip group to the ReVanced root list whose screen sets a behavior per " +
        "segment category through a popup.",
) {
    compatibleWith("com.vk.vkvideo")
    dependsOn(revancedSettingsPatch, sponsorSkipPatch)

    apply {
        val chromeMethod = toolbarChromeFingerprint.method
        val chrome = extractToolbarChrome(classDefs.first { it.type == chromeMethod.definingClass })
        val opener = resolveNavOpener(navOpenerFingerprint.method, classDefs.toList())
        val switchListenerMethod = switchListenerMethodFingerprint.method.name

        injectSponsorSkipSettingsClasses(this, chrome, opener, switchListenerMethod)

        // The anchor only exists after the root builder was injected by the settings
        // patch, so an unexpected shape fails loudly instead of silently dropping the row.
        val root = rootContentFingerprint.method
        val instructions = root.implementation!!.instructions
        val anchors = instructions.withIndex().filter { (_, instruction) ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                reference is MethodReference &&
                reference.definingClass == "Landroid/widget/LinearLayout;" &&
                reference.name == "addView"
        }
        check(anchors.size == 1) { "expected exactly one LinearLayout->addView in the root builder, found ${anchors.size}" }
        val (anchorIndex, anchor) = anchors.single()
        check((anchor as FiveRegisterInstruction).registerC == 9) {
            "root builder addView receiver is not v9"
        }
        // A second apply reaches the same anchor through the fingerprint cache.
        val next = instructions.getOrNull(anchorIndex + 1)
        val nextReference = (next as? ReferenceInstruction)?.reference
        val alreadyInserted = next?.opcode == Opcode.INVOKE_STATIC &&
            nextReference is MethodReference &&
            nextReference.definingClass == ROOT_ROW_CLASS &&
            nextReference.name == "add"
        if (!alreadyInserted) {
            root.addInstructions(
                anchorIndex + 1,
                "invoke-static {v9, v10, v11}, $ROOT_ROW_CLASS->add(Landroid/widget/LinearLayout;Landroid/content/Context;Lcom/vk/core/fragments/FragmentImpl;)V",
            )
        }
    }
}
