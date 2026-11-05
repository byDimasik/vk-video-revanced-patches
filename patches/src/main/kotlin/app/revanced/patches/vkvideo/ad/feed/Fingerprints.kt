package app.revanced.patches.vkvideo.ad.feed

import app.revanced.patcher.fingerprint

val myTargetNativeAdEntryFingerprint = fingerprint {
    returns("Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry;")
    // 1.165: a(JSONObject, Lap/d;) — 1.168 adds a trailing Map param and the parser
    // dependency type renamed (ap/d -> bp/e). Strings are the stable anchor.
    strings("ads", "bannerID", "pattern_type", "template_id")
}

val promoPostFingerprint = fingerprint {
    returns("Lcom/vk/dto/newsfeed/entries/PromoPost;")
    parameters(
        "Lorg/json/JSONObject;",
        "Landroid/util/ArrayMap;",
        "Landroid/util/SparseArray;",
        "Ljava/util/Map;"
    )
    strings("ads", "ads_id1", "ads_id2", "ads_title")
}
