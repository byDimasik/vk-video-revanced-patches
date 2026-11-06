package app.revanced.patches.vkvideo.settings

import app.revanced.patcher.fingerprint
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
