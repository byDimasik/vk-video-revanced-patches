package app.revanced.patches.vkvideo.ad.video

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.Opcode

val instreamAdJsonParserFingerprint = fingerprint {
    returns("Ljava/lang/Object;")
    parameters("Lorg/json/JSONObject;")
    strings(
        "slot_id",
        "timeout",
        "can_play",
        "midroll_percents",
        "params",
        "sections",
        "autoplay_preroll"
    )
}

val videoFileInstreamAdFingerprint = fingerprint {
    returns("Lcom/vk/dto/common/InstreamAd;")
    parameters()
    opcodes(
        Opcode.IGET_OBJECT,
        Opcode.RETURN_OBJECT
    )
}

val instreamAdLoaderFingerprint = fingerprint {
    returns("V")
    parameters("Lmo/d5;", "Lmo/c1;")
    strings(
        "version",
        "2.5",
        "s2s",
        "InstreamAdFactory: invalid json-data, error: "
    )
}

val instreamAdControllerFingerprint = fingerprint {
    returns("V")
    parameters("Ljava/lang/String;")
    strings(
        "InstreamAd: Unable to start ad - not loaded yet",
        "InstreamAd: Unable to start ad - player has not set",
        "InstreamAdEngine: No section with name "
    )
}
