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

// The parameter descriptors differ between APK versions (1.165: `Lmo/d5;Lmo/c1;`, 1.168: `Lno/x7;Lno/r4;`),
// and the patch only needs the method entry point, so match on the unique log strings instead.
val instreamAdLoaderFingerprint = fingerprint {
    returns("V")
    strings(
        "version",
        "2.5",
        "s2s",
        "InstreamAdFactory: invalid json-data, error: "
    )
}

// 1.168 kept all three log strings in one method; 1.170 split them (only the
// "No section" anchor remains in the controller), so match on that single stable string.
val instreamAdControllerFingerprint = fingerprint {
    returns("V")
    parameters("Ljava/lang/String;")
    strings(
        "InstreamAdEngine: No section with name "
    )
}
