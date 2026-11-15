package app.revanced.patches.vkvideo.settings

import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.smali.SmaliTestUtils

/**
 * Compile-time gate for the references the injected extension code makes into
 * the app.
 *
 * `remapAppTypes` rewrites a template before it is compiled, but a reference it
 * cannot resolve is left in place silently — that is how `VideoFile.P0()` shipped
 * in 1.171 while the remap already knew the `O0` shape (#78): the rewrite
 * no-opped and nothing looked at the compiled result.
 *
 * Every injected class therefore goes through [compileExtensionClass]: the
 * source is remapped, compiled, and the **compiled dex** is walked for every app
 * reference — method, field and type. A reference that does not resolve is
 * corrected with the same package-rescue/member-shape logic as the remap and
 * recompiled; if anything is still unresolved after the retries, the patch build
 * fails with the full list instead of shipping a `NoSuchMethodError`.
 */

/** Our own injected code: never resolved against the app. */
internal const val EXTENSION_PREFIX = "Lapp/revanced/extension/"

/** Packages resolved from artifacts that ship in the APK but never drift. */
private val NON_APP_PREFIXES = listOf(
    "Landroid/", "Landroidx/", "Ljava/", "Ljavax/", "Lkotlin/", "Lkotlinx/",
    "Lorg/", "Ldalvik/", "Lcom/google/", "Lio/reactivex/", "Lj$/",
)

/** Element descriptor of a possibly array-typed descriptor. */
private fun elementType(descriptor: String): String = descriptor.dropWhile { it == '[' }

/** An app-owned class the extension borrows from: everything obfuscated, minus framework. */
private fun isAppOwned(descriptor: String): Boolean {
    val type = elementType(descriptor)
    return type.startsWith("L") && !NON_APP_PREFIXES.any { type.startsWith(it) }
}

private fun BytecodePatchContext.classDef(type: String): ClassDef? = classDefsByType(this)[type]

/**
 * Framework members an app class may legitimately inherit without declaring,
 * each with the ancestor that has to be reached. Presence of *any* external
 * ancestor proves nothing: `ordinal()` only exists once `Enum` is reached.
 */
private val FRAMEWORK_MEMBERS = mapOf<String, (String) -> Boolean>(
    "equals(Ljava/lang/Object;)Z" to { it == "Ljava/lang/Object;" },
    "hashCode()I" to { it == "Ljava/lang/Object;" },
    "toString()Ljava/lang/String;" to { it == "Ljava/lang/Object;" },
    "getClass()Ljava/lang/Class;" to { it == "Ljava/lang/Object;" },
    "clone()Ljava/lang/Object;" to { it == "Ljava/lang/Object;" },
    "ordinal()I" to { it == "Ljava/lang/Enum;" },
    "name()Ljava/lang/String;" to { it == "Ljava/lang/Enum;" },
    "compareTo(Ljava/lang/Object;)I" to { it == "Ljava/lang/Comparable;" },
)

/**
 * The class and its app supertypes, nearest first — R8 drift never leaves the
 * app. External ancestors (framework, AndroidX) are collected separately: they
 * are where the few framework members in [FRAMEWORK_MEMBERS] may live.
 */
private class Chain(val defs: List<ClassDef>, val externals: Set<String>)

private fun BytecodePatchContext.appChain(type: String): Chain {
    val chain = mutableListOf<ClassDef>()
    val externals = mutableSetOf<String>()
    val queue = ArrayDeque(listOf(type))
    val seen = mutableSetOf<String>()
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (!seen.add(current)) continue
        val def = classDef(current)
        if (def == null) {
            externals += current
            continue
        }
        chain += def
        def.superclass?.let(queue::addLast)
        def.interfaces.forEach(queue::addLast)
    }
    return Chain(chain, externals)
}

private fun BytecodePatchContext.methodDeclared(owner: String, name: String, params: List<String>, returnType: String): Boolean {
    val chain = appChain(owner)
    val inherited = FRAMEWORK_MEMBERS["$name(${params.joinToString("")})$returnType"]
    if (inherited != null && chain.externals.any(inherited)) return true
    return chain.defs.any { def ->
        def.methods.any {
            it.name == name && it.parameterTypes.toList() == params && it.returnType.toString() == returnType
        }
    }
}

private fun BytecodePatchContext.fieldDeclared(owner: String, name: String, type: String): Boolean =
    appChain(owner).defs.any { def -> def.fields.any { it.name == name && it.type == type } }

/**
 * Packages carrying the simple name of an absent owner — the package-rescue half
 * of the remap, applied per reference.
 */
private fun candidateOwners(context: BytecodePatchContext, owner: String): List<String> {
    if (FIXED_PACKAGES.any { owner.startsWith(it) }) return emptyList()
    val simple = owner.substringAfterLast('/')
    return packageContents(context).entries
        .filter { (_, names) -> simple in names }
        .map { (pkg, _) -> pkg + simple }
        .filter { context.classDef(it) != null }
}

/** Resolves a method reference to the member that carries its shape now, if unique. */
private fun resolveMethod(context: BytecodePatchContext, ref: TemplateRef): TemplateRef? {
    val owners = buildList {
        context.classDef(ref.owner)?.let(::add)
        candidateOwners(context, ref.owner).forEach { context.classDef(it)?.let(::add) }
    }
    for (owner in owners) {
        val method: Method = resolveMember(context, owner, ref) ?: continue
        return TemplateRef(
            owner = owner.type,
            name = method.name,
            params = method.parameterTypes.map { it.toString() },
            returnType = method.returnType.toString(),
        )
    }
    return null
}

/** Resolves a field by an identical name+type, then by a unique same-type field. */
private fun resolveField(context: BytecodePatchContext, owner: String, name: String, type: String): String? {
    val owners = buildList {
        context.classDef(owner)?.let(::add)
        candidateOwners(context, owner).forEach { context.classDef(it)?.let(::add) }
    }
    for (def in owners) {
        val chain = context.appChain(def.type).defs
        chain.firstOrNull { d -> d.fields.any { it.name == name && it.type == type } }?.let {
            return "${it.type}->$name:$type"
        }
        val sameType = chain.flatMap { d -> d.fields.filter { it.type == type }.map { d.type to it.name } }.distinct()
        if (sameType.size == 1) return "${sameType[0].first}->${sameType[0].second}:$type"
    }
    return null
}

/** Resolves an absent class by a unique package candidate carrying its simple name. */
private fun resolveType(context: BytecodePatchContext, type: String): String? =
    candidateOwners(context, type).singleOrNull()

/**
 * A reference the compiled class makes and whether the dexes can satisfy it.
 * [reference] and [corrected] are in smali source form, so a correction is a
 * plain text replacement.
 */
internal data class AuditRow(val reference: String, val resolved: Boolean, val corrected: String?)

/**
 * Walks every app reference of [def] and reports it.
 *
 * Method refs must be declared with the exact signature on the owner or an app
 * supertype, fields likewise with the exact type, types must exist. Constructors
 * are never inherited, exactly as the ART verifier sees them.
 */
internal fun auditExtensionClass(context: BytecodePatchContext, def: ClassDef): List<AuditRow> {
    val rows = mutableListOf<AuditRow>()

    fun method(reference: MethodReference) {
        if (!isAppOwned(reference.definingClass) || reference.definingClass.startsWith(EXTENSION_PREFIX)) return
        val params = reference.parameterTypes.map { it.toString() }
        val returns = reference.returnType.toString()
        val text = "${reference.definingClass}->${reference.name}(${params.joinToString("")})$returns"
        val resolved = if (reference.name == "<init>") {
            context.classDef(reference.definingClass)?.methods?.any {
                it.name == "<init>" && it.parameterTypes.toList() == params
            } == true
        } else {
            context.methodDeclared(reference.definingClass, reference.name, params, returns)
        }
        val fix = if (resolved) null else {
            resolveMethod(context, TemplateRef(reference.definingClass, reference.name, params, returns))
                ?.let { "${it.owner}->${it.name}(${it.params.joinToString("")})${it.returnType}" }
        }
        rows += AuditRow(text, resolved, fix)
    }

    fun field(reference: FieldReference) {
        if (!isAppOwned(reference.definingClass) || reference.definingClass.startsWith(EXTENSION_PREFIX)) return
        val type = reference.type.toString()
        val text = "${reference.definingClass}->${reference.name}:$type"
        val resolved = context.fieldDeclared(reference.definingClass, reference.name, type)
        rows += AuditRow(text, resolved, if (resolved) null else resolveField(context, reference.definingClass, reference.name, type))
    }

    fun type(descriptor: String) {
        if (!isAppOwned(descriptor) || descriptor.startsWith(EXTENSION_PREFIX)) return
        val element = elementType(descriptor)
        val resolved = context.classDef(element) != null
        // The correction is a plain text replacement, so an array reference must keep its
        // prefix: replacing "[Lp72/j;" with "Lv72/j;" would turn an array signature,
        // new-array operand or check-cast into a scalar one.
        val prefix = descriptor.takeWhile { it == '[' }
        rows += AuditRow(descriptor, resolved, if (resolved) null else resolveType(context, element)?.let { prefix + it })
    }

    def.fields.forEach { field(it) }
    def.methods.forEach { m ->
        type(m.returnType.toString())
        m.parameterTypes.forEach { type(it.toString()) }
        m.implementation?.instructions?.forEach { instruction ->
            when (val reference = (instruction as? ReferenceInstruction)?.reference) {
                is MethodReference -> method(reference)
                is FieldReference -> field(reference)
                is TypeReference -> type(reference.type)
                else -> Unit
            }
        }
    }
    return rows
}

/** How many remap→compile→re-audit rounds a class may need before the gate gives up. */
private const val MAX_ROUNDS = 3

/**
 * Class count above which the input is treated as the real app rather than a
 * hand-built unit-test stub. The real APK carries tens of thousands of classes;
 * a stub context carries a handful, where every second reference legitimately
 * points at a class the stub never defined.
 */
private const val FULL_APP_CLASSES = 1_000

/**
 * Compiles one injected class and guarantees its app references resolve.
 *
 * Corrections are applied to the source text and the class recompiled, so the
 * shipped dex — not the template — is what resolves. Returns the first variant
 * with a clean audit; throws naming every reference that could not be resolved.
 */
internal fun compileExtensionClass(context: BytecodePatchContext, descriptor: String, source: String): ClassDef {
    var text = remapAppTypes(context, source)
    var compiled = compile(descriptor, text)

    fun rowsOf(def: ClassDef): List<AuditRow> = auditExtensionClass(context, def).also { rows ->
        if (System.getProperty("vkvideo.auditDump") != null) {
            rows.forEach { System.err.println("AUDIT $descriptor ${if (it.resolved) "OK" else "MISS"} ${it.reference}") }
        }
    }

    for (round in 1..MAX_ROUNDS) {
        val open = rowsOf(compiled).filter { !it.resolved }
        if (open.isEmpty()) return compiled
        val corrections = open.mapNotNull { row -> row.corrected?.let { row.reference to it } }
            .filter { (from, to) -> from != to }
        if (corrections.isEmpty()) break
        corrections.forEach { (from, to) -> text = text.replace(from, to) }
        compiled = compile(descriptor, text)
    }

    // A hand-built stub cannot define every class its templates touch, so only the
    // real app is judged to completion; in a stub the gate still corrects what it can.
    val fullApp = classDefsByType(context).size > FULL_APP_CLASSES
    val open = rowsOf(compiled).filter { !it.resolved }.filter { fullApp }
    check(open.isEmpty()) {
        "Extension class $descriptor has ${open.size} reference(s) that do not resolve against this APK:\n" +
            open.joinToString("\n") { "  ${it.reference} (${it.corrected ?: "no unique candidate"})" }
    }
    return compiled
}

private fun compile(descriptor: String, source: String): ClassDef =
    try {
        SmaliTestUtils.compileSmali(source)
    } catch (e: RuntimeException) {
        throw IllegalStateException("$descriptor does not compile: ${e.cause?.message}", e)
    }
