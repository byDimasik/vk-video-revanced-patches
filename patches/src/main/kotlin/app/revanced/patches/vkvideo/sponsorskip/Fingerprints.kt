package app.revanced.patches.vkvideo.sponsorskip

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.Opcode

internal const val AUTO_PLAY_CONTROLLER = "Lp73/a;"

/**
 * `VideoAutoPlay.D(VideoFile)` — the video handoff. The interface alone is not
 * enough: `EmptyAutoPlay` implements it with the same signature, so the frame
 * size (`.locals 3` + receiver + argument) and the trailing body discriminate.
 */
val sponsorSkipVideoHandoffFingerprint = fingerprint {
    returns("V")
    parameters("Lcom/vk/dto/common/VideoFile;")
    custom { method, classDef ->
        classDef.interfaces.contains(AUTO_PLAY_CONTROLLER) &&
            method.name == "D" &&
            method.implementation?.registerCount == 5 &&
            (method.implementation?.instructions?.count() ?: 0) > 2 &&
            method.implementation?.instructions?.firstOrNull()?.opcode == Opcode.IPUT_OBJECT
    }
}

/** `VideoAutoPlay.W(VideoTextureView)` — the player view attach (`EmptyAutoPlay` is a bare return). */
val sponsorSkipViewAttachedFingerprint = fingerprint {
    returns("V")
    parameters("Lcom/vk/libvideo/design/view/videotexture/VideoTextureView;")
    custom { method, classDef ->
        classDef.interfaces.contains(AUTO_PLAY_CONTROLLER) &&
            method.name == "W" &&
            method.implementation?.registerCount == 4 &&
            (method.implementation?.instructions?.count() ?: 0) > 2
    }
}

/** `VideoAutoPlay.release()` — teardown (`EmptyAutoPlay` is a bare return). */
val sponsorSkipReleaseFingerprint = fingerprint {
    parameters()
    returns("V")
    custom { method, classDef ->
        classDef.interfaces.contains(AUTO_PLAY_CONTROLLER) &&
            method.name == "release" &&
            method.implementation?.registerCount == 3 &&
            (method.implementation?.instructions?.count() ?: 0) > 2
    }
}

/** `VideoAutoPlay.h0(VideoTextureView)` — the texture-view attach of the embedded player. */
val sponsorSkipViewSetupFingerprint = fingerprint {
    returns("Z")
    parameters("Lcom/vk/libvideo/design/view/videotexture/VideoTextureView;")
    custom { method, classDef ->
        classDef.interfaces.contains(AUTO_PLAY_CONTROLLER) &&
            method.name == "h0" &&
            method.implementation?.registerCount == 3 &&
            (method.implementation?.instructions?.count() ?: 0) > 2 &&
            method.implementation?.instructions?.firstOrNull()?.opcode == Opcode.IF_EQZ
    }
}

/** `VideoAutoPlay.x0(VideoTextureView, a, holder)` — the feed/catalog preview attach. */
val sponsorSkipHolderFingerprint = fingerprint {
    returns("V")
    parameters(
        "Lcom/vk/libvideo/design/view/videotexture/VideoTextureView;",
        "Lcom/vk/libvideo/autoplay/a;",
        "Landroidx/recyclerview/widget/RecyclerView\$c0;",
    )
    custom { method, classDef ->
        classDef.interfaces.contains(AUTO_PLAY_CONTROLLER) &&
            method.name == "x0" &&
            method.implementation?.registerCount == 7 &&
            method.implementation?.instructions?.firstOrNull()?.opcode == Opcode.IGET_OBJECT
    }
}
