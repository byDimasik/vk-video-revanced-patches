package app.revanced.patches.vkvideo.ad.feed

import app.revanced.patcher.fingerprint

val myTargetNativeAdEntryFingerprint = fingerprint {
    returns("Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry;")
    parameters("Lorg/json/JSONObject;", "Lap/d;")
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
