package app.revanced.patches.vkvideo.ad.catalog

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.Opcode

private const val CATALOG_AD_BANNER_DTO =
    "Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;"

private const val CATALOG_SECTION_DTO =
    "Lcom/vk/api/generated/catalog/dto/CatalogSectionDto;"

/**
 * `CatalogAdBannerDto.b()` — `iget-object` of the `slotId` field followed by `return-object`.
 *
 * The DTO is deserialized reflectively by Gson from the `slot_id` field (`@hn/b(value = "slot_id")`),
 * so there is no hand-written parser to short-circuit. Returning `null` from this getter makes every
 * consumer treat the banner as absent — most importantly `Laf7/j;->p(...)`, which maps
 * `CatalogSectionDto` to `CatalogSection` and guards the ad banner construction with
 * `if-eqz` on both the DTO and this getter's result (`const/4 r0, 0x0` is left in `r0`).
 */
val catalogAdBannerSlotIdFingerprint = fingerprint {
    returns("Ljava/lang/Long;")
    parameters()
    opcodes(
        Opcode.IGET_OBJECT,
        Opcode.RETURN_OBJECT
    )
    custom { method, classDef ->
        classDef.type == CATALOG_AD_BANNER_DTO &&
            method.name == "b" &&
            classDef.interfaces.contains("Landroid/os/Parcelable;")
    }
}

/**
 * `CatalogSectionDto.d()` — the only accessor of the `adBanner` field
 * (`Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;`).
 *
 * Its sole caller (`Laf7/j;->p(...)`) null-checks the result before reading `slotId`, and
 * `writeToParcel`/`hashCode`/`equals` of `CatalogSectionDto` also handle a `null` banner,
 * so returning `null` here detaches the ad banner from the catalog section model.
 */
val catalogSectionAdBannerGetterFingerprint = fingerprint {
    returns(CATALOG_AD_BANNER_DTO)
    parameters()
    opcodes(
        Opcode.IGET_OBJECT,
        Opcode.RETURN_OBJECT
    )
    custom { method, classDef ->
        classDef.type == CATALOG_SECTION_DTO && method.name == "d"
    }
}
