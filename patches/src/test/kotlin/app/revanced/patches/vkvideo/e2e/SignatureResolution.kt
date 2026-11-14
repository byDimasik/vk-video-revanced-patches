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
 * Framework members an app class may legitimately inherit without declaring,
 * each with the ancestor that has to be in its chain. Presence of *any* external
 * ancestor proves nothing: `ordinal()` only exists once `Enum` is reached, not
 * for a plain `Object` subclass.
 */
private const val FRAGMENT_ACTIVITY = "Landroidx/fragment/app/FragmentActivity;"

/** R8 renames `androidx.fragment.app.Fragment`; the sibling activity keeps its name. */
private val FRAGMENT_ANCESTOR: (String) -> Boolean =
    { it.startsWith("Landroidx/fragment/app/") && it != FRAGMENT_ACTIVITY }

private val FRAMEWORK_MEMBERS = mapOf<String, (String) -> Boolean>(
    "equals(Ljava/lang/Object;)Z" to { it == "Ljava/lang/Object;" },
    "hashCode()I" to { it == "Ljava/lang/Object;" },
    "toString()Ljava/lang/String;" to { it == "Ljava/lang/Object;" },
    "getClass()Ljava/lang/Class;" to { it == "Ljava/lang/Object;" },
    "clone()Ljava/lang/Object;" to { it == "Ljava/lang/Object;" },
    "ordinal()I" to { it == "Ljava/lang/Enum;" },
    "name()Ljava/lang/String;" to { it == "Ljava/lang/Enum;" },
    "compareTo(Ljava/lang/Object;)I" to { it == "Ljava/lang/Comparable;" },
    // Fallback only: androidx ships in the dex, so the normal walk finds these on
    // `Fragment`; a renamed or repackaged build still matches the fragment package.
    "getActivity()Landroidx/fragment/app/FragmentActivity;" to FRAGMENT_ANCESTOR,
    "getContext()Landroid/content/Context;" to FRAGMENT_ANCESTOR,
    "requireActivity()Landroidx/fragment/app/FragmentActivity;" to FRAGMENT_ANCESTOR,
    "requireContext()Landroid/content/Context;" to FRAGMENT_ANCESTOR,
)

private fun hasExternalPrefix(type: String) = !type.startsWith("L") || EXTERNAL_PREFIXES.any { type.startsWith(it) }

private fun BytecodePatchContext.classDef(type: String): ClassDef? = classDefs.firstOrNull { it.type == type }

/**
 * Not resolvable from the app dexes. AndroidX ships *inside* the APK, so a
 * prefixed type is only external when it is genuinely absent — that keeps
 * `Fragment.requireActivity()` resolvable while an unrelated sibling does not
 * accidentally count.
 */
private fun BytecodePatchContext.isExternal(type: String) = hasExternalPrefix(type) && classDef(type) == null

/**
 * Walk the app supertypes of `type`. Returns true when one declares exactly
 * `name(params)returnType`; `externalChain` collects the external types the walk
 * actually reached, so only the right ancestor can justify an inherited member.
 */
private fun BytecodePatchContext.methodExists(
    type: String,
    name: String,
    params: List<String>,
    returnType: String,
    seen: MutableSet<String>,
    externalChain: MutableSet<String>,
): Boolean {
    if (isExternal(type)) {
        externalChain += type
        return false
    }
    if (!seen.add(type)) return false
    val def = classDef(type) ?: return false
    if (def.methods.any {
            it.name == name && it.parameterTypes.toList() == params && it.returnType.toString() == returnType
        }) return true
    if (def.superclass?.let { methodExists(it, name, params, returnType, seen, externalChain) } == true) return true
    return def.interfaces.any { methodExists(it, name, params, returnType, seen, externalChain) }
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
 * Whether an app method reference resolves: declared in the app class or its app
 * supertypes, or a framework member whose exact declaring ancestor is reached.
 * `owner` must exist.
 */
internal fun BytecodePatchContext.appMethodResolves(
    owner: String,
    name: String,
    params: List<String>,
    returnType: String,
): Boolean {
    if (classDef(owner) == null) return false
    val signature = "$name(${params.joinToString("")})$returnType"
    if (name == "<init>") {
        return classDef(owner)!!.methods.any { it.name == name && it.parameterTypes.toList() == params }
    }
    val externalChain = mutableSetOf<String>()
    if (methodExists(owner, name, params, returnType, mutableSetOf(), externalChain)) return true
    val declaredOn = FRAMEWORK_MEMBERS[signature] ?: return false
    return externalChain.any(declaredOn)
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
                if (!appMethodResolves(reference.definingClass, reference.name, params, returns)) {
                    failures += "$where: ${reference.definingClass}->$signature does not exist"
                }
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
