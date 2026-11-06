package app.revanced.patches.vkvideo.settings

import app.revanced.patcher.extensions.accessFlags
import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

val profileMenuItemsFingerprint = fingerprint {
    returns("V")
    parameters("I", "I")
    custom { method, classDef ->
        classDef.type == "Lcom/vk/video/screens/profile/ProfileMenuFragment;" &&
            method.implementation?.instructions?.any { instruction ->
                (instruction as? ReferenceInstruction)?.reference
                    .let { it is FieldReference && it.name == "PLAYER_SETTINGS" }
            } == true &&
            method.implementation?.instructions?.any { instruction ->
                (instruction as? ReferenceInstruction)?.reference.let {
                    it is MethodReference &&
                        it.definingClass == "Lme/grishka/appkit/fragments/BaseRecyclerFragment;" &&
                        it.parameterTypes == listOf("Ljava/util/List;", "Z") &&
                        it.returnType == "V"
                }
            } == true
    }
}

/**
 * `yj6/r.g(Ljava/util/ArrayList;)V` (1.170) — the Sign-out row builder, called
 * (twice, one per branch) only from `ProfileMenuFragment.cm(II)V`; it is the only
 * instance method of the builder class referencing the LOGOUT menu-item field.
 * Inserting the ReVanced menu entry at its index 0 renders the entry directly
 * above Sign out (logged in) / Login (logged out) in both `cm` branches with a
 * single patch site.
 */
val signOutRowFingerprint = fingerprint {
    returns("V")
    parameters("Ljava/util/ArrayList;")
    custom { method, _ ->
        !method.accessFlags(AccessFlags.STATIC) &&
            method.implementation?.instructions?.any { instruction ->
                (instruction as? ReferenceInstruction)?.reference
                    .let { it is FieldReference && it.name == "LOGOUT" }
            } == true
    }
}
