package app.revanced.patches.vkvideo.settings

import app.revanced.patcher.extensions.accessFlags
import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

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
                    .let {
                        it is FieldReference && it.name == "LOGOUT" &&
                            it.definingClass == PROFILE_MENU_ITEM_TYPE
                    }
            } == true
    }
}

/** Stable, non-obfuscated enum of the profile menu row types. */
internal const val PROFILE_MENU_ITEM_TYPE =
    "Lcom/vk/video/screens/profile/adapter/ProfileMenuItemType;"

/**
 * The app-wide fragment opener builder: the method that opens the app's own General
 * settings screen — `const-class VideoUserSettingsFragment` →
 * `new <opener>(Class, Class, Bundle)` → `<open>(Context)`. Neither the opener nor its
 * method names survive obfuscation; the opener type and both open-method names are
 * extracted from the match.
 */
val navOpenerFingerprint = fingerprint {
    custom { method, _ ->
        val instructions = method.implementation?.instructions ?: return@custom false
        val hasTargetClass = instructions.any { instruction ->
            instruction.opcode == Opcode.CONST_CLASS &&
                ((instruction as? ReferenceInstruction)?.reference as? TypeReference)
                    ?.type == NAV_OPENER_TARGET_CLASS
        }
        hasTargetClass &&
            instructions.any { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference
                instruction.opcode in setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE) &&
                    reference is MethodReference &&
                    reference.parameterTypes == listOf(
                        "Ljava/lang/Class;",
                        "Ljava/lang/Class;",
                        "Landroid/os/Bundle;",
                    )
            } &&
            instructions.any { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference
                instruction.opcode in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) &&
                    reference is MethodReference &&
                    reference.parameterTypes == listOf("Landroid/content/Context;") &&
                    reference.returnType == "V"
            }
    }
}

/**
 * `VkSwitchItem$a` — the app's switch-row listener interface; its single abstract
 * `(Z)V` method name is resolved from the match (obfuscated per version).
 */
val switchListenerMethodFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == SWITCH_LISTENER_INTERFACE &&
            method.returnType == "V" &&
            method.parameterTypes == listOf("Z")
    }
}

/**
 * `MaterialPreferenceToolbarFragment.onCreateView` — the app's own toolbar-carrying
 * preference screen; the patch transcribes its chrome (layout, ids, hide helper,
 * `VkTopBar` constructor chain) from this class's onCreateView/onViewCreated.
 */
val toolbarChromeFingerprint = fingerprint {
    returns("Landroid/view/View;")
    parameters("Landroid/view/LayoutInflater;", "Landroid/view/ViewGroup;", "Landroid/os/Bundle;")
    custom { method, classDef ->
        classDef.type == "Lcom/vk/prefui/fragments/MaterialPreferenceToolbarFragment;" &&
            method.name == "onCreateView"
    }
}

internal const val NAV_OPENER_TARGET_CLASS =
    "Lcom/vk/video/screens/settings/main/ui/VideoUserSettingsFragment;"
internal const val SWITCH_LISTENER_INTERFACE = "Lcom/vk/core/view/components/switch/VkSwitchItem\$a;"
