package app.revanced.patches.vkvideo.e2e

import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import org.junit.jupiter.api.Assertions.assertTrue

/** Every class this pipeline injects lives under this prefix. */
internal const val INJECTED_PREFIX = "Lapp/revanced/extension/"

/** Types resolved from the framework/AndroidX/Kotlin artifacts, not from the app dexes. */
private val EXTERNAL_PREFIXES = listOf(
    "Landroid/", "Landroidx/", "Ljava/", "Ljavax/", "Lkotlin/", "Lkotlinx/",
    "Lorg/", "Ldalvik/", "Lcom/google/", "Lio/reactivex/", "Lj$/",
)

private fun isExternal(type: String) =
    !type.startsWith("L") || EXTERNAL_PREFIXES.any { type.startsWith(it) }

private fun BytecodePatchContext.classDef(type: String): ClassDef? = classDefs.firstOrNull { it.type == type }

/**
 * `type` or any of its supertypes declares `name` with exactly `params`.
 * Framework supertypes are not dexed here, so the walk simply ends there.
 */
private fun BytecodePatchContext.methodExists(type: String, name: String, params: List<String>, seen: MutableSet<String>): Boolean {
    if (!seen.add(type)) return false
    // Inherited from a framework supertype: not dexed here and never drifting.
    if (isExternal(type)) return true
    val def = classDef(type) ?: return false
    if (def.methods.any { it.name == name && it.parameterTypes.toList() == params }) return true
    if (def.superclass?.let { methodExists(it, name, params, seen) } == true) return true
    return def.interfaces.any { methodExists(it, name, params, seen) }
}

private fun BytecodePatchContext.fieldExists(type: String, name: String, seen: MutableSet<String>): Boolean {
    if (!seen.add(type)) return false
    if (isExternal(type)) return true
    val def = classDef(type) ?: return false
    if (def.fields.any { it.name == name }) return true
    if (def.superclass?.let { fieldExists(it, name, seen) } == true) return true
    return def.interfaces.any { fieldExists(it, name, seen) }
}

/**
 * Asserts that every app member the injected code references still resolves to
 * the exact same signature in the shipped dexes.
 *
 * Class existence is not enough: a stale ctor argument (`Lp72/q;`) or a renamed
 * interface method (`VideoFile.P0`) both compile, inject and pass a
 * class-presence check while crashing with `NoSuchMethodError` on device.
 * Types outside the app (framework, AndroidX, Kotlin) are skipped — they are not
 * in the dex and never drift.
 *
 * Returns the number of checked references so callers can demand real coverage.
 */
internal fun BytecodePatchContext.assertInjectedReferencesResolve(): Int {
    var checked = 0
    val failures = mutableListOf<String>()

    fun check(where: String, reference: Reference) {
        when (reference) {
            is MethodReference -> {
                if (isExternal(reference.definingClass) || reference.definingClass.startsWith(INJECTED_PREFIX)) return
                checked++
                val params = reference.parameterTypes.map { it.toString() }
                if (classDef(reference.definingClass) == null) {
                    failures += "$where: owner class ${reference.definingClass} does not exist"
                    return
                }
                if (!methodExists(reference.definingClass, reference.name, params, mutableSetOf())) {
                    failures += "$where: ${reference.definingClass}->${reference.name}(${params.joinToString("")}) does not exist"
                }
            }
            is FieldReference -> {
                if (isExternal(reference.definingClass) || reference.definingClass.startsWith(INJECTED_PREFIX)) return
                checked++
                if (!fieldExists(reference.definingClass, reference.name, mutableSetOf())) {
                    failures += "$where: field ${reference.definingClass}->${reference.name} does not exist"
                }
            }
            is TypeReference -> {
                if (isExternal(reference.type) || reference.type.startsWith(INJECTED_PREFIX)) return
                checked++
                if (classDef(reference.type) == null) failures += "$where: class ${reference.type} does not exist"
            }
        }
    }

    classDefs.filter { it.type.startsWith(INJECTED_PREFIX) }.forEach { def ->
        def.methods.forEach { method ->
            method.implementation?.instructions?.forEach { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference ?: return@forEach
                check("${def.type}->${method.name}${method.parameterTypes}", reference)
            }
        }
        def.fields.forEach { field ->
            val type = field.type
            if (isExternal(type) || type.startsWith(INJECTED_PREFIX)) return@forEach
            checked++
            if (classDef(type) == null) failures += "${def.type}->${field.name}: class $type does not exist"
        }
    }

    assertTrue(
        failures.isEmpty(),
        "injected code references ${failures.size} member(s) absent from the shipped dexes:\n" + failures.joinToString("\n"),
    )
    return checked
}
