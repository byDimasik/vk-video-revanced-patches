package app.revanced.patches.vkvideo.sponsorskip

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef

internal const val VIDEO_FILE_TYPE = "Lcom/vk/dto/common/VideoFile;"
internal const val VIDEO_TEXTURE_VIEW_TYPE =
    "Lcom/vk/libvideo/design/view/videotexture/VideoTextureView;"

/** The two implementations the app always ships; the interface itself is resolved from them. */
private const val REAL_AUTO_PLAY = "Lcom/vk/libvideo/autoplay/VideoAutoPlay;"
private const val EMPTY_AUTO_PLAY = "Lcom/vk/libvideo/autoplay/e;"

/**
 * The `VideoAutoPlay` interface and its seek method. Neither the interface name
 * (`p73/a` in 1.170, `b83/a` in 1.171) nor its method names survive obfuscation,
 * so both are resolved from the app's own two implementations.
 */
internal data class AutoPlayApi(val controller: String, val seekMethod: String)

/** Set by the patch before a fingerprint is resolved. */
internal var autoPlayApi: AutoPlayApi? = null

private fun api() = autoPlayApi ?: error("autoPlayApi resolved before fingerprint use")

/**
 * The seek among the interface's `(J)V` methods. There are two — a bare seek and
 * a seek that also resets resumed state; the bare one is the twin the old `s`
 * name picked, and it is the only one whose implementation jumps with a `goto`
 * instead of returning early.
 */
private fun resolveSeek(controller: ClassDef, real: ClassDef): String? {
    val seeks = controller.methods.toList().filter {
        it.parameterTypes.map(CharSequence::toString) == listOf("J") && it.returnType.toString() == "V"
    }
    if (seeks.size == 1) return seeks.single().name
    val jumping = setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)
    val jumpingSeeks = seeks.filter { seek ->
        real.methods.firstOrNull {
            it.name.toString() == seek.name.toString() &&
                it.parameterTypes.map(CharSequence::toString) == listOf("J")
        }?.implementation?.instructions?.any { it.opcode in jumping } == true
    }
    return jumpingSeeks.singleOrNull()?.name
}

/**
 * Resolves the controller from the methods the real player and the empty stub
 * share: the `VideoFile` handoff and `release()`. Only one interface carries both.
 */
internal fun resolveAutoPlayApi(classes: List<ClassDef>): AutoPlayApi {
    val real = classes.firstOrNull { it.type.toString() == REAL_AUTO_PLAY }
        ?: error("autoplay: $REAL_AUTO_PLAY not found")
    val empty = classes.firstOrNull { it.type.toString() == EMPTY_AUTO_PLAY }
        ?: error("autoplay: $EMPTY_AUTO_PLAY not found")

    val matches = real.interfaces
        .filter { iface -> empty.interfaces.any { it.toString() == iface.toString() } }
        .mapNotNull { iface ->
            val def = classes.firstOrNull { it.type.toString() == iface.toString() } ?: return@mapNotNull null
            val isController = def.methods.any {
                it.parameterTypes.map(CharSequence::toString) == listOf(VIDEO_FILE_TYPE) &&
                    it.returnType.toString() == "V"
            } && def.methods.any {
                it.name == "release" && it.parameterTypes.isEmpty() && it.returnType.toString() == "V"
            }
            if (!isController) return@mapNotNull null
            val seek = resolveSeek(def, real) ?: return@mapNotNull null
            AutoPlayApi(iface, seek)
        }

    check(matches.size == 1) {
        "autoplay: expected one controller interface implementing $VIDEO_FILE_TYPE and release()V, found $matches"
    }
    return matches.single()
}

/**
 * `VideoAutoPlay` handoff. The interface alone is not enough: the empty stub
 * implements it with the same signature, so the frame size (`.locals 3` +
 * receiver + argument) and the trailing body discriminate.
 */
val sponsorSkipVideoHandoffFingerprint = fingerprint {
    returns("V")
    parameters(VIDEO_FILE_TYPE)
    custom { method, classDef ->
        classDef.interfaces.contains(api().controller) &&
            method.implementation?.registerCount == 5 &&
            (method.implementation?.instructions?.count() ?: 0) > 2 &&
            method.implementation?.instructions?.firstOrNull()?.opcode == Opcode.IPUT_OBJECT
    }
}

/** The player view attach (`EmptyAutoPlay` is a bare return). */
val sponsorSkipViewAttachedFingerprint = fingerprint {
    returns("V")
    parameters(VIDEO_TEXTURE_VIEW_TYPE)
    custom { method, classDef ->
        classDef.interfaces.contains(api().controller) &&
            method.implementation?.registerCount == 4 &&
            (method.implementation?.instructions?.count() ?: 0) > 2
    }
}

/** `release()` — teardown (`EmptyAutoPlay` is a bare return). */
val sponsorSkipReleaseFingerprint = fingerprint {
    parameters()
    returns("V")
    custom { method, classDef ->
        classDef.interfaces.contains(api().controller) &&
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

/** The texture-view setup of the embedded player (`EmptyAutoPlay` never reaches this shape). */
val sponsorSkipViewSetupFingerprint = fingerprint {
    returns("Z")
    parameters(VIDEO_TEXTURE_VIEW_TYPE)
    custom { method, classDef ->
        classDef.interfaces.contains(api().controller) &&
            method.implementation?.registerCount == 3 &&
            (method.implementation?.instructions?.count() ?: 0) > 2 &&
            method.implementation?.instructions?.firstOrNull()?.opcode == Opcode.IF_EQZ
    }
}

/** The feed/catalog preview attach. */
val sponsorSkipHolderFingerprint = fingerprint {
    returns("V")
    parameters(
        VIDEO_TEXTURE_VIEW_TYPE,
        "Lcom/vk/libvideo/autoplay/a;",
        "Landroidx/recyclerview/widget/RecyclerView\$c0;",
    )
    custom { method, classDef ->
        classDef.interfaces.contains(api().controller) &&
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
                        .reference.toString() ==
                    "Lone/video/controls/view/seekbar/CompositeSeekBarView;->h:Lone/video/controls/view/seekbar/ProgressSeekBarView;"
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
