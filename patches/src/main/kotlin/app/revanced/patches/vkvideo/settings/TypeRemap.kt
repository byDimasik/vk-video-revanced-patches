package app.revanced.patches.vkvideo.settings

import app.revanced.patcher.patch.BytecodePatchContext

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
 * Rewrites the obfuscated types an injected template borrows from the app.
 *
 * Templates are written against one version and name app members literally. When
 * R8 renames their owner's arguments (`Lp72/e` → `Lv72/e`, `Lpx1/a` → `Ltx1/a`),
 * each reference is resolved by owner, name, arity and the largest number of
 * identical parameter types; the differing positions supply the substitution.
 * Non-obfuscated types never change, so a ref keeps at least those as anchors.
 * Refs whose owner is absent from the input (our own injected classes) are left
 * untouched, as are owner classes that moved together with their package: those
 * are remapped by simple name once the package move itself is known.
 */
internal fun remapAppTypes(context: BytecodePatchContext, source: String): String {
    val mapping = linkedMapOf<String, String>()
    val packages = linkedMapOf<String, String>()

    fun map(old: String, new: String) {
        // Primitives and arrays never change; only object descriptors are renamed.
        if (!old.startsWith("L") || old == new) return
        val known = mapping.putIfAbsent(old, new)
        if (known != null && known != new) {
            // A subtype in one position and its supertype in another: keep whichever
            // keeps the simple name, the narrower and the more likely real class.
            if (old.substringAfterLast('/') == new.substringAfterLast('/')) mapping[old] = new
        }
        if (old.contains('/')) {
            val from = packageOf(old)
            val to = packageOf(new)
            if (from != to) packages.putIfAbsent(from, to)
        }
    }

    fun classDef(type: String) = context.classDefs.firstOrNull { it.type == type }

    val refs = METHOD_REF.findAll(source).map { found ->
        TemplateRef(found.groupValues[1], found.groupValues[2], parseTypes(found.groupValues[3]), found.groupValues[4])
    }.toList()

    refs.forEach { ref ->
        val owner = classDef(ref.owner) ?: return@forEach
        val candidates = owner.methods.filter { it.name == ref.name && it.parameterTypes.size == ref.params.size }
        if (candidates.isEmpty()) return@forEach
        val scored = candidates.map { method ->
            method to method.parameterTypes.zip(ref.params).count { (actual, old) -> actual.toString() == old }
        }.sortedByDescending { it.second }
        val best = scored.first()
        if (scored.size > 1 && scored[1].second == best.second) return@forEach
        best.first.parameterTypes.zip(ref.params).forEach { (actual, old) -> map(old, actual.toString()) }
        map(ref.returnType, best.first.returnType.toString())
    }

    // Owners that moved with their package: only once the package move is known, and
    // only when the same simple name exists under the new package.
    refs.filter { classDef(it.owner) == null }.forEach { ref ->
        val target = packages[packageOf(ref.owner)]?.let { it + ref.owner.substringAfterLast('/') }
        if (target != null && classDef(target) != null) map(ref.owner, target)
    }

    var result = source
    mapping.forEach { (old, new) -> result = result.replace(old, new) }
    return result
}
