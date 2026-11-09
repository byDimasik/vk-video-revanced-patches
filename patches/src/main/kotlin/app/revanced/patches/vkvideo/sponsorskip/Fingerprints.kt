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

/**
 * `BaseVideoPlayer.a(J)` — the single seek entry: user scrub, media keys and our own
 * skip all end here. It stores the target and delegates to the abstract `E0(J)`, so the
 * hook only narrows the paused re-evaluation window; the tick is the guarantee.
 */
val sponsorSkipSeekFingerprint = fingerprint {
    returns("V")
    parameters("J")
    custom { method, classDef ->
        classDef.type == "Lone/video/player/BaseVideoPlayer;" &&
            method.name == "a" &&
            method.implementation?.registerCount == 6 &&
            method.implementation?.instructions?.count { it.opcode == Opcode.RETURN_VOID } == 1
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

/**
 * `CompositeSeekBarView.setProgressDrawable(Drawable)` — the section-layer install
 * point. The body forwards to field `h` (`ProgressSeekBarView`), so a reordered
 * field set would show up here as a different receiver.
 */
val sponsorSkipSectionDrawableFingerprint = fingerprint {
    returns("V")
    parameters("Landroid/graphics/drawable/Drawable;")
    custom { method, classDef ->
        classDef.type == "Lone/video/controls/view/seekbar/CompositeSeekBarView;" &&
            method.name == "setProgressDrawable" &&
            method.implementation?.registerCount == 3 &&
            method.implementation?.instructions?.any {
                it.opcode == Opcode.IGET_OBJECT &&
                    (it as com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)
                        .reference.toString() == "Lone/video/controls/view/seekbar/CompositeSeekBarView;->h:Lone/video/controls/view/seekbar/ProgressSeekBarView;"
            } == true
    }
}

/**
 * The page player's section install `one/video/controls/views/seek/a.g(int, ArrayList)`.
 * The page and composite players each own a structurally identical drawable family, so
 * the class check is what keeps the two apart.
 */
val sponsorSkipSectionListFingerprint = fingerprint {
    returns("V")
    parameters("I", "Ljava/util/ArrayList;")
    custom { method, classDef ->
        classDef.type == "Lone/video/controls/views/seek/a;" &&
            method.name == "g" &&
            method.implementation?.registerCount == 13 &&
            classDef.fields.any {
                it.name == "a" && it.type == "Landroidx/appcompat/widget/AppCompatSeekBar;"
            }
    }
}
