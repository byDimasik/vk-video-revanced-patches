package app.revanced.patches.vkvideo.ad.shelf

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * The two runtime inflate sites of the catalog top-shelf layouts (verified in the
 * 1.170 stock dexes). Both are stable class anchors; the layout resource id is
 * captured from the matched `const`, never hardcoded — production takes whatever
 * the matched inflate feeds, and the real-APK e2e pins the 1.170 literals
 * (0x7f0e029c `catalog_video_topshelf`, 0x7f0e027e `catalog_video_music_topshelf`)
 * against `res/values/public.xml`.
 */

/** `Lni0/k;.<init>` inflates `catalog_video_topshelf` into the root View field that `getView()` returns. */
val videoTopshelfBlockFingerprint = fingerprint {
    returns("V")
    parameters(
        "Landroidx/constraintlayout/widget/ConstraintLayout;",
        "Landroid/view/LayoutInflater;",
        "Z",
        "Lcom/vk/catalog2/common/dto/ui/UIBlockTopshelf${'$'}TopshelfItem;",
        "Z",
    )
    custom { method, classDef ->
        classDef.type == "Lni0/k;" &&
            method.name == "<init>" &&
            hasShelfInflate(method) &&
            // The root field is the View that getView() hands to the catalog block holder.
            classDef.methods.any { candidate ->
                candidate.returnType == "Landroid/view/View;" &&
                    candidate.parameterTypes.isEmpty() &&
                    candidate.name == "getView" &&
                    candidate.implementation?.instructions?.any { instruction ->
                        instruction.opcode == Opcode.IGET_OBJECT &&
                            ((instruction as? ReferenceInstruction)?.reference as? com.android.tools.smali.dexlib2.iface.reference.FieldReference)
                                ?.let { it.definingClass == classDef.type && it.type == "Landroid/view/View;" } == true
                    } == true
            }
    }
}

/** `musicvideo/b.onCreateView` inflates `catalog_video_music_topshelf` into its own container. */
val musicTopshelfFragmentFingerprint = fingerprint {
    returns("Landroid/view/View;")
    parameters("Landroid/view/LayoutInflater;", "Landroid/view/ViewGroup;", "Landroid/os/Bundle;")
    custom { method, classDef ->
        classDef.type == "Lcom/vk/catalog2/common/ui/holders/video/musicvideo/b;" &&
            method.name == "onCreateView" &&
            hasShelfInflate(method)
    }
}

internal fun hasShelfInflate(method: com.android.tools.smali.dexlib2.iface.Method): Boolean {
    val instructions = method.implementation?.instructions ?: return false
    return instructions.any { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference
        instruction.opcode == Opcode.INVOKE_VIRTUAL &&
            reference is MethodReference &&
            reference.name == "inflate" &&
            reference.definingClass == "Landroid/view/LayoutInflater;" &&
            reference.parameterTypes == listOf("I", "Landroid/view/ViewGroup;", "Z") &&
            reference.returnType == "Landroid/view/View;"
    }
}

/**
 * Locates the shelf inflate inside a matched method and returns its layout
 * resource id and the register holding the inflated root right after the
 * `move-result-object`.
 */
internal fun findShelfInflate(method: com.android.tools.smali.dexlib2.iface.Method): ShelfInflateSite {
    val instructions = method.implementation?.instructions?.toList()
        ?: error("Matched shelf method has no implementation: ${method.definingClass}->${method.name}")
    val inflateIndex = instructions.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference
        instruction.opcode == Opcode.INVOKE_VIRTUAL &&
            reference is MethodReference &&
            reference.name == "inflate" &&
            reference.definingClass == "Landroid/view/LayoutInflater;" &&
            reference.parameterTypes == listOf("I", "Landroid/view/ViewGroup;", "Z") &&
            reference.returnType == "Landroid/view/View;"
    }
    if (inflateIndex < 0) error("Shelf inflate call not found in ${method.definingClass}->${method.name}")

    val inflate = instructions[inflateIndex] as FiveRegisterInstruction
    val layoutRegister = inflate.registerD
    val layoutId = ((inflateIndex downTo 0).firstNotNullOfOrNull { index ->
        val instruction = instructions[index]
        val register = (instruction as? OneRegisterInstruction)?.registerA
        if (instruction is com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction &&
            instruction.opcode in setOf(Opcode.CONST, Opcode.CONST_4, Opcode.CONST_16) &&
            register == layoutRegister
        ) {
            instruction.narrowLiteral
        } else {
            null
        }
    } ?: error("Shelf layout id const not found before the inflate in ${method.definingClass}->${method.name}"))

    if (layoutId !in LAYOUT_ID_RANGE) {
        error(
            "Shelf inflate layout id 0x${layoutId.toString(16)} is outside the layout-id range in " +
                "${method.definingClass}->${method.name}; the matched inflate is not a layout resource"
        )
    }

    val moveResult = instructions.getOrNull(inflateIndex + 1)
    if (moveResult?.opcode != Opcode.MOVE_RESULT_OBJECT) {
        error("Shelf inflate result not consumed by move-result-object in ${method.definingClass}->${method.name}")
    }
    return ShelfInflateSite(
        insertAfterIndex = inflateIndex + 1,
        layoutId = layoutId,
        viewRegister = (moveResult as OneRegisterInstruction).registerA,
    )
}

/**
 * Resource-type range check for the resolved layout id (`res/layout` type ids live
 * in 0x7f0e0000..0x7f0effff in this app). The exact ids cannot be hardcoded in
 * production (public.xml is an apktool decode artifact, absent from the binary
 * APK) — the real-APK e2e pins the 1.170 values.
 */
internal val LAYOUT_ID_RANGE = 0x7f0e0000..0x7f0effff

internal data class ShelfInflateSite(
    val insertAfterIndex: Int,
    val layoutId: Int,
    val viewRegister: Int,
)
