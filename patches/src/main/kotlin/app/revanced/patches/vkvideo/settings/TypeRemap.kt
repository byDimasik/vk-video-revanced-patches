package app.revanced.patches.vkvideo.settings

import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.WeakHashMap

/** A method reference found in an injected smali template. */
private data class TemplateRef(val owner: String, val name: String, val params: List<String>, val returnType: String) {
    val descriptor: String get() = "$owner->$name(${params.joinToString("")})$returnType"
}

private val METHOD_REF = Regex("""(L[\w/$]+;)->(<init>|[\w$<>]+)\(([^)]*)\)((?:L[\w/$]+;|\[(?:L[\w/$]+;|[\w$]+)|[\w$]+))""")

/** Splits concatenated smali parameter descriptors (`Lj/n;I[Lx/y;`). */
private fun parseTypes(spec: String): List<String> {
    val types = mutableListOf<String>()
    var i = 0
    while (i < spec.length) {
        val start = i
        while (spec[i] == '[') i++
        if (spec[i] == 'L') {
            i = spec.indexOf(';', i) + 1
            check(i > 0) { "unterminated descriptor in $spec" }
        } else {
            i++
        }
        types += spec.substring(start, i)
    }
    return types
}

private fun packageOf(descriptor: String) = descriptor.substringBeforeLast('/') + "/"

/**
 * Packages that never get obfuscated, so a missing type there means the template
 * name is wrong rather than moved (`Landroid/util/Log` must not be re-pointed at
 * `Lorg/chromium/base/Log` just because both end in `Log`).
 */
private val FIXED_PACKAGES = listOf("Landroid/", "Landroidx/", "Ljava/", "Ljavax/", "Lkotlin/", "Lkotlinx/", "Ldalvik/")

/** Every package in the dex, mapped to its top-level simple names. */
private fun packageContents(context: BytecodePatchContext): Map<String, Set<String>> {
    val contents = linkedMapOf<String, MutableSet<String>>()
    context.classDefs.forEach { def ->
        val type = def.type
        if (!type.startsWith("Lapp/revanced/")) {
            contents.getOrPut(packageOf(type)) { mutableSetOf() } += type.substringAfterLast('/')
        }
    }
    return contents
}

private val callSiteCounts = WeakHashMap<BytecodePatchContext, MutableMap<String, Int>>()

/**
 * How often app code calls `owner->name` — the tie-break for methods that R8
 * renamed and whose signature alone matches several siblings. Same-width getters
 * and their rare namesakes share a shape; the widely used one is the one the
 * template means, and renaming keeps its call sites in the same order of
 * magnitude (1.171: `VideoFile.P0` 82 callers → `O0` 83, `n` stays at 3).
 */
private fun callSiteCounts(context: BytecodePatchContext): MutableMap<String, Int> =
    callSiteCounts.getOrPut(context) {
        val counts = mutableMapOf<String, Int>()
        context.classDefs.forEach { def ->
            if (def.type.startsWith("Lapp/revanced/")) return@forEach
            def.methods.forEach { method ->
                method.implementation?.instructions?.forEach { instruction ->
                    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return@forEach
                    val key = "${reference.definingClass}->${reference.name}"
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
        }
        counts
    }

/** Object or array descriptor: the only descriptors R8 can rename. */
private fun isReference(descriptor: String) = descriptor.startsWith("L") || descriptor.startsWith("[")

/**
 * Positional agreement of `method` with the template ref: an exact descriptor is
 * worth 3, a still-matching object/array position 1 (its name may have moved), a
 * primitive-vs-object mismatch 0. A matching return type adds 2.
 */
private fun shapeScore(method: Method, ref: TemplateRef): Int {
    val params = method.parameterTypes.zip(ref.params).sumOf { (actual, old) ->
        val descriptor = actual.toString()
        when {
            descriptor == old -> 3
            isReference(descriptor) && isReference(old) -> 1
            else -> 0
        }
    }
    return params + if (method.returnType.toString() == ref.returnType) 2 else 0
}

/**
 * App method a template ref was renamed from, or null when the name still exists.
 *
 * Same name always wins. Otherwise the ref is matched by arity, return type and
 * identical parameters, and an ambiguous winner is settled by call-site count —
 * that is the only signal left once R8 substitutes the name wholesale.
 */
private fun resolveMember(context: BytecodePatchContext, owner: ClassDef, ref: TemplateRef): Method? {
    val sameName = owner.methods.filter { it.name == ref.name && it.parameterTypes.size == ref.params.size }
    val pool = if (sameName.isNotEmpty()) {
        sameName
    } else {
        // The name itself was renamed: only the shape and the return type are left.
        owner.methods.filter {
            it.parameterTypes.size == ref.params.size && it.returnType.toString() == ref.returnType
        }
    }
    if (pool.isEmpty()) return null

    val ranked = sortedByShape(pool, ref)
    if (ranked.size == 1 || ranked[0].second != ranked[1].second) return ranked.first().first
    if (ranked[0].second == 0) return null

    // A genuine tie leaves only usage as a signal: the widely called sibling is the
    // one the template means (1.171: `VideoFile.P0` 82 callers → `O0` 83, `n` 3).
    val counts = callSiteCounts(context)
    val byCalls = ranked.filter { it.second == ranked[0].second }
        .map { it.first to (counts["${it.first.definingClass}->${it.first.name}"] ?: 0) }
        .sortedByDescending { it.second }
    if (byCalls.size > 1 && byCalls[0].second == byCalls[1].second) return null
    return byCalls.first().first.takeIf { byCalls.first().second > 0 }
}

private fun sortedByShape(methods: List<Method>, ref: TemplateRef) =
    methods.map { it to shapeScore(it, ref) }.sortedByDescending { it.second }

/**
 * Rewrites the obfuscated types and member names an injected template borrows
 * from the app.
 *
 * Templates are written against one version and name app members literally. When
 * R8 renames their owner's arguments (`Lp72/e` → `Lv72/e`, `Lpx1/a` → `Ltx1/a`),
 * each reference is resolved by owner, name, arity and the largest number of
 * identical parameter types; the differing positions supply the substitution.
 * Non-obfuscated types never change, so a ref keeps at least those as anchors.
 * Refs whose owner is absent from the input (our own injected classes) are left
 * untouched, as are owner classes that moved together with their package: those
 * follow the package move once it is known.
 *
 * Owners are rescued in three steps so a template that names a whole moved
 * package (1.171: every `Lp72` class became `Lv72`) still resolves:
 *  1. refs whose owner exists resolve normally and teach the package moves;
 *  2. an absent package is matched to the existing package carrying the largest
 *     share of the simple names the template references, and all its refs move;
 *  3. only then are the member signatures resolved against the new owners, so a
 *     stale ctor argument (`Lp72/q;`) follows the owner instead of surviving.
 * Names R8 replaced outright resolve through [resolveMember].
 */
internal fun remapAppTypes(context: BytecodePatchContext, source: String): String {
    val mapping = linkedMapOf<String, String>()
    val packages = linkedMapOf<String, String>()
    val nameMapping = linkedMapOf<Pair<String, String>, String>()

    fun map(old: String, new: String) {
        // Primitives and arrays never change; only object descriptors are renamed.
        if (!old.startsWith("L") || old == new) return
        val known = mapping.putIfAbsent(old, new)
        if (known != null && known != new) {
            // A subtype in one position and its supertype in another: keep whichever
            // keeps the simple name, the narrower and the more likely real class.
            if (old.substringAfterLast('/') == new.substringAfterLast('/')) mapping[old] = new
        }
        if (old.contains('/') && new.contains('/')) {
            packages.putIfAbsent(old.substringBeforeLast('/') + "/", new.substringBeforeLast('/') + "/")
        }
    }

    fun classDef(type: String) = context.classDefs.firstOrNull { it.type == type }

    fun resolveRef(ref: TemplateRef, owner: String) {
        val def = classDef(owner) ?: return
        val method = resolveMember(context, def, ref) ?: return
        method.parameterTypes.zip(ref.params).forEach { (actual, old) -> map(old, actual.toString()) }
        map(ref.returnType, method.returnType.toString())
        nameMapping[owner to ref.name] = method.name
    }

    val refs = METHOD_REF.findAll(source).map { found ->
        TemplateRef(found.groupValues[1], found.groupValues[2], parseTypes(found.groupValues[3]), found.groupValues[4])
    }.toList()

    // 1. Owners the template still names correctly.
    refs.filter { classDef(it.owner) != null }.forEach { resolveRef(it, it.owner) }

    // 2. Owners that moved with their package. The package move is learned from the
    //    simple names the template references: an obfuscated package keeps its whole
    //    class letter set across versions, so the target package covers every ref.
    val unresolved = refs.filter { classDef(it.owner) == null && !it.owner.startsWith("Lapp/revanced/") }
    val contents by lazy { packageContents(context) }
    unresolved.map { packageOf(it.owner) }.distinct().forEach { from ->
        if (FIXED_PACKAGES.any { from.startsWith(it) }) return@forEach
        val group = unresolved.filter { packageOf(it.owner) == from }
        // A package move already learned from a resolved ref outranks content matching.
        packages[from]?.let { known ->
            group.forEach { map(it.owner, known + it.owner.substringAfterLast('/')) }
            return@forEach
        }
        val wanted = group.map { it.owner.substringAfterLast('/') }.toSet()
        // Candidates must carry the referenced simple names; when none covers the whole
        // set the widest overlap is the best a genuinely removed class allows.
        val coverage = contents.entries.maxOfOrNull { (_, names) -> names.count { it in wanted } } ?: return@forEach
        if (coverage == 0) return@forEach
        val candidates = contents.entries.filter { (_, names) -> names.count { it in wanted } == coverage }
        // Among equally-covering packages only the right one resolves the template's
        // exact member shapes; the tie-break is the package closest in size, because an
        // obfuscated app package is small while a framework-ish one spans hundreds.
        val target = candidates
            .map { (pkg, names) ->
                val exact = group.count { ref ->
                    val def = classDef(pkg + ref.owner.substringAfterLast('/')) ?: return@count false
                    def.methods.any { method ->
                        method.name == ref.name && method.parameterTypes.size == ref.params.size &&
                            method.parameterTypes.zip(ref.params).all { (actual, old) -> actual.toString() == old }
                    }
                }
                Triple(pkg, exact, names.size)
            }
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
            .firstOrNull()
            ?: return@forEach
        val targetNames = contents.getValue(target.first)
        wanted.filter { it in targetNames }.forEach { simple -> map(from + simple, target.first + simple) }
    }

    // 3. Refs that came along with the owner move still need their member signature.
    refs.forEach { ref ->
        val owner = mapping[ref.owner] ?: return@forEach
        resolveRef(ref, owner)
    }

    var result = source
    mapping.forEach { (old, new) -> result = result.replace(old, new) }
    nameMapping.forEach { (owner, old), new -> result = result.replace("$owner->$old(", "$owner->$new(") }
    return result
}
