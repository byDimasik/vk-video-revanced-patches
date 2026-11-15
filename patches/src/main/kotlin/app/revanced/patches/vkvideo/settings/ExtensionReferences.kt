package app.revanced.patches.vkvideo.settings

import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
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

/**
 * The other half of the reference gate: existence is not assignability.
 *
 * A reference can resolve and still be the wrong *type*. #83 shipped exactly
 * that: `SponsorSkipBehaviorDialog$Click` implemented
 * `DialogInterface$OnClickListener`, the builder method it was handed to
 * declared `androidx/preference/c$a`, and the app's bridge `check-cast` the
 * argument — a `ClassCastException` on the first tap. Every reference resolved;
 * the cast was simply impossible.
 *
 * So the compiled extension dex is checked against the real class graph: an
 * `invoke` argument must be assignable to the declared parameter type, and a
 * `check-cast` operand must be assignable to the cast target. Both accept the
 * only safe answers — the source's transitive app supertypes, its interfaces,
 * or a superclass cast.
 */

/** Terminal of every chain; its (empty) ancestor list is known without a class def. */
private const val OBJECT_TYPE = "Ljava/lang/Object;"

/** A type violation: [member] passes/casts a [source] where the app expects [target]. */
internal data class CastViolation(
    val descriptor: String,
    val member: String,
    val kind: String,
    val source: String,
    val target: String,
) {
    override fun toString() = "$descriptor $member: $kind $source -> $target"
}

/**
 * Whether [source] can stand where [target] is declared: identical types, a
 * subtype through the class's own declared supertypes/interfaces, or anything
 * at all where `Object` is expected.
 *
 * `null` means the graph cannot decide — an ancestor outside the dexes, whose
 * own ancestors are then unknown. The gate reports only decided failures, so an
 * unresolvable ancestry can never become a false positive.
 */
private fun assignable(
    context: BytecodePatchContext,
    siblings: Map<String, ClassDef>,
    source: String,
    target: String,
): Boolean? {
    if (source == target) return true
    if (target == OBJECT_TYPE) return true
    if (!source.startsWith("L") || !target.startsWith("L")) return null
    val targetKnown = siblings[target] != null || context.classDef(target) != null
    val start = siblings[source] ?: context.classDef(source) ?: return null
    val seen = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    start.superclass?.let(queue::addLast)
    start.interfaces.forEach(queue::addLast)
    var undecided = false
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current == target) return true
        if (!seen.add(current)) continue
        val def = siblings[current] ?: context.classDef(current)
        if (def == null) {
            // A framework ancestor is undecidable only while the target itself has
            // no definition to be reached through; an in-dex target cannot hide
            // behind an external class we never resolved.
            if (current != OBJECT_TYPE && !targetKnown) undecided = true
            continue
        }
        def.superclass?.let(queue::addLast)
        def.interfaces.forEach(queue::addLast)
    }
    return if (undecided) null else false
}

private val MOVE_OBJECT = Regex("""move-object(-from16|-16)?$""")
private val CONST_STRING = Regex("""const-string(-jumbo)?$""")
private val IGET = Regex("""iget(-object|-wide|-boolean|-byte|-char|-short)?$""")

private fun widthOf(type: String) = if (type == "J" || type == "D") 2 else 1

/**
 * Walks every method of [def] and reports each `invoke` argument and
 * `check-cast` operand whose statically known type cannot satisfy the place it
 * is passed to.
 *
 * Register types are tracked in a single forward pass: parameters start with
 * their declared type, and `new-instance`, field reads, `move-object`,
 * `move-result-object` and constant loads carry a type forward. Our templates
 * are straight-line blocks, so one pass over the instruction list suffices;
 * `check-cast` re-types its destination with the cast target. A register written
 * by anything else becomes unknown, and unknown never fails.
 */
internal fun auditAssignability(
    context: BytecodePatchContext,
    def: ClassDef,
    siblings: Map<String, ClassDef> = emptyMap(),
): List<CastViolation> {
    val violations = mutableListOf<CastViolation>()
    val graph = siblings + (def.type to def)

    def.methods.forEach { method ->
        val implementation = method.implementation ?: return@forEach
        val registers = mutableMapOf<Int, String>()
        val registerCount = implementation.registerCount
        val params = buildList {
            if (method.accessFlags and AccessFlags.STATIC.value == 0) add(def.type)
            method.parameterTypes.forEach { add(it.toString()) }
        }
        var slot = registerCount - params.sumOf(::widthOf)
        params.forEach { type ->
            registers[slot] = type
            slot += widthOf(type)
        }

        val instructions = implementation.instructions.toList()
        var lastReturn: String? = null
        var index = 0
        while (index < instructions.size) {
            val instruction = instructions[index]
            index += 1
            val name = instruction.opcode.name.lowercase().replace('_', '-')
            val reference = (instruction as? ReferenceInstruction)?.reference
            val one = instruction as? OneRegisterInstruction

            fun check(at: Int?, target: String, kind: String) {
                if (at == null) return
                val source = registers[at] ?: return
                if (assignable(context, graph, source, target) == false) {
                    violations += CastViolation(def.type, method.name + signatureOf(method), "$kind r$at", source, target)
                }
            }

            if (reference is MethodReference) {
                val types = reference.parameterTypes.map { it.toString() }
                val passed = when (instruction) {
                    is FiveRegisterInstruction -> buildList {
                        add(instruction.registerC)
                        if (instruction.registerCount > 1) add(instruction.registerD)
                        if (instruction.registerCount > 2) add(instruction.registerE)
                        if (instruction.registerCount > 3) add(instruction.registerF)
                        if (instruction.registerCount > 4) add(instruction.registerG)
                    }
                    is RegisterRangeInstruction -> (instruction.startRegister until instruction.startRegister + instruction.registerCount).toList()
                    else -> emptyList()
                }
                // A non-static call carries the receiver in the first register.
                passed.drop(if (name.startsWith("invoke-static")) 0 else 1).forEachIndexed { position, at ->
                    types.getOrNull(position)?.let { check(at, it, "arg$position") }
                }
                lastReturn = reference.returnType.toString()
            }

            when {
                name == "move-result-object" -> {
                    val target = lastReturn
                    if (one != null && target != null) registers[one.registerA] = target
                }
                name == "new-instance" -> {
                    val type = (reference as? TypeReference)?.type
                    if (one != null && type != null) registers[one.registerA] = type
                }
                name == "check-cast" -> {

                    val target = (reference as? TypeReference)?.type
                    if (one != null && target != null) {
                        check(one.registerA, target, "cast")
                        registers[one.registerA] = target
                    }
                }
                MOVE_OBJECT.matches(name) -> {
                    val two = instruction as? TwoRegisterInstruction
                    val source = two?.let { registers[it.registerB] }
                    if (two != null) {
                        if (source != null) registers[two.registerA] = source else registers.remove(two.registerA)
                    }
                }
                CONST_STRING.matches(name) -> if (one != null) registers[one.registerA] = "Ljava/lang/String;"
                name == "const-class" -> if (one != null) registers[one.registerA] = "Ljava/lang/Class;"
                IGET.matches(name) || name == "sget-object" -> {
                    val type = (reference as? FieldReference)?.type
                    if (one != null) {
                        if (type != null) registers[one.registerA] = type else registers.remove(one.registerA)
                    }
                }
                else -> if (one != null) registers.remove(one.registerA)
            }
        }
    }
    return violations
}

private fun signatureOf(method: Method): String =
    "(${method.parameterTypes.joinToString("") { it.toString() }})${method.returnType}"

/**
 * The assignability pass over a whole injection batch, so a class can be
 * checked against its siblings regardless of the order they were compiled in.
 *
 * A stub context gets the same check narrowed to violations that involve our own
 * code: in a stub most framework types are absent, and an unresolvable ancestor
 * already reads as "unknown".
 */
internal fun assertAssignable(context: BytecodePatchContext, defs: List<ClassDef>) {
    if (defs.isEmpty()) return
    val siblings = defs.associateBy { it.type }
    val fullApp = classDefsByType(context).size > FULL_APP_CLASSES
    val violations = defs.flatMap { auditAssignability(context, it, siblings) }
        .filter { fullApp || it.source.startsWith(EXTENSION_PREFIX) || it.target.startsWith(EXTENSION_PREFIX) }
    check(violations.isEmpty()) {
        val shown = violations.distinct().joinToString("\n") { "  $it" }
        "Extension classes pass ${violations.size} value(s) that the APK's classes cannot accept:\n$shown"
    }
}
