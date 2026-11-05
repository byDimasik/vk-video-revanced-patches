package app.revanced.patches.vkvideo.ad.catalog

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.patch.bytecodePatch

val hideCatalogBannersPatch = bytecodePatch(
    name = "Hide catalog ad banners",
    description = "Short-circuits catalog ad banner handling to hide promoted banners in catalog sections."
) {
    compatibleWith("com.vk.vkvideo")

    apply {
        // `CatalogAdBannerDto.slotId` is deserialized reflectively, so the getter is the choke point:
        // returning `null` makes the banner look absent to every reader.
        val slotIdMethod = catalogAdBannerSlotIdFingerprint.method
        slotIdMethod.replaceInstruction(0, "const/4 v0, 0x0")
        slotIdMethod.addInstruction(1, "return-object v0")

        // The catalog section model is built from this getter; its callers null-check the result.
        val sectionAdBannerMethod = catalogSectionAdBannerGetterFingerprint.method
        sectionAdBannerMethod.replaceInstruction(0, "const/4 v0, 0x0")
        sectionAdBannerMethod.addInstruction(1, "return-object v0")
    }
}
