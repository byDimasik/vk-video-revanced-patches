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

/**
 * Framework members that app classes legitimately inherit without declaring:
 * `Enum.ordinal()`/`name()`, the `Object` basics and `Comparable`. Anything else
 * reached only through framework supertypes is treated as unresolved, because the
 * sweep has no framework declarations to check against.
 */
private val FRAMEWORK_MEMBERS = setOf(
    "equals(Ljava/lang/Object;)Z",
    "hashCode()I",
    "toString()Ljava/lang/String;",
    "getClass()Ljava/lang/Class;",
    "clone()Ljava/lang/Object;",
    "ordinal()I",
    "name()Ljava/lang/String;",
    "compareTo(Ljava/lang/Object;)I",
    "getActivity()Landroidx/fragment/app/FragmentActivity;",
    "getContext()Landroid/content/Context;",
    "requireActivity()Landroidx/fragment/app/FragmentActivity;",
    "requireContext()Landroid/content/Context;",
)

private fun isExternal(type: String) = !type.startsWith("L") || EXTERNAL_PREFIXES.any { type.startsWith(it) }

private fun BytecodePatchContext.classDef(type: String): ClassDef? = classDefs.firstOrNull { it.type == type }

/**
 * Walk the app supertypes of `type`. Returns true when one declares exactly
 * `name(params)returnType`; `externalTail` collects whether the chain left the
 * app dexes, so the caller can decide about framework inheritance.
 */
private fun BytecodePatchContext.methodExists(
    type: String,
    name: String,
    params: List<String>,
    returnType: String,
    seen: MutableSet<String>,
    externalTail: MutableSet<Boolean>,
): Boolean {
    if (isExternal(type)) {
        externalTail += true
        return false
    }
    if (!seen.add(type)) return false
    val def = classDef(type) ?: return false
    if (def.methods.any {
            it.name == name && it.parameterTypes.toList() == params && it.returnType.toString() == returnType
        }) return true
    if (def.superclass?.let { methodExists(it, name, params, returnType, seen, externalTail) } == true) return true
    return def.interfaces.any { methodExists(it, name, params, returnType, seen, externalTail) }
}

private fun BytecodePatchContext.fieldExists(
    type: String,
    name: String,
    fieldType: String,
    seen: MutableSet<String>,
): Boolean {
    if (isExternal(type)) return false
    if (!seen.add(type)) return false
    val def = classDef(type) ?: return false
    if (def.fields.any { it.name == name && it.type == fieldType }) return true
    if (def.superclass?.let { fieldExists(it, name, fieldType, seen) } == true) return true
    return def.interfaces.any { fieldExists(it, name, fieldType, seen) }
}

/**
 * Asserts that every app member the injected code references still resolves to
 * the exact same signature in the shipped dexes.
 *
 * Class existence is not enough: a stale ctor argument (`Lp72/q;`) or a renamed
 * interface method (`VideoFile.P0`) both compile, inject and pass a
 * class-presence check while crashing with `NoSuchMethodError` on device.
 * Types outside the app (framework, AndroidX, Kotlin) are not in the dex and
 * never drift, but a framework supertype only proves the few members in
 * [FRAMEWORK_MEMBERS] — constructors resolve on their declaring class alone.
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
                val returns = reference.returnType.toString()
                val signature = "${reference.name}(${params.joinToString("")})$returns"
                val owner = classDef(reference.definingClass)
                if (owner == null) {
                    failures += "$where: owner class ${reference.definingClass} does not exist"
                    return
                }
                // A constructor is never inherited.
                if (reference.name == "<init>") {
                    if (owner.methods.none { it.name == "<init>" && it.parameterTypes.toList() == params }) {
                        failures += "$where: ${reference.definingClass}->$signature does not exist"
                    }
                    return
                }
                val externalTail = mutableSetOf<Boolean>()
                if (methodExists(reference.definingClass, reference.name, params, returns, mutableSetOf(), externalTail)) return
                if (externalTail.isNotEmpty() && signature in FRAMEWORK_MEMBERS) return
                failures += "$where: ${reference.definingClass}->$signature does not exist"
            }
            is FieldReference -> {
                if (isExternal(reference.definingClass) || reference.definingClass.startsWith(INJECTED_PREFIX)) return
                checked++
                val type = reference.type.toString()
                if (!fieldExists(reference.definingClass, reference.name, type, mutableSetOf())) {
                    failures += "$where: field ${reference.definingClass}->${reference.name}:$type does not exist"
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
