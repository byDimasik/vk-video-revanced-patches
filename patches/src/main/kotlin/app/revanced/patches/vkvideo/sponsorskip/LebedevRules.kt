package app.revanced.patches.vkvideo.sponsorskip

/**
 * The Лебедев channel ruleset. The injected `Rules` class carries the same
 * tables (interpolated from here), so the runtime and the tests read one source.
 *
 * Precedence: exact name, then the integration prefix, then the pre-roll
 * position heuristic; the first hit wins.
 */
internal object LebedevRules {
    const val OWNER_ID = -211437014L
    const val POSITIONAL_MAX_MS = 90_000L

    const val SPONSOR_PREFIX = "Нативная интеграция:"

    val EXACT: Map<String, Int> = mapOf(
        "Контент везде" to Category.SELFPROMO,
        "Новинки дизайна" to Category.SELFPROMO,
        "Инструмент путешественника" to Category.SELFPROMO,
        "Самый хороший дизайн" to Category.SELFPROMO,
        "Медиа про дизайн" to Category.SELFPROMO,
        "Новинка магазинуса" to Category.SELFPROMO,
        "Важнейшая новость" to Category.INTRO,
    )

    fun classify(ownerId: Long, name: String?, index: Int, startMs: Long, endMs: Long): Int {
        if (ownerId != OWNER_ID) return -1
        val trimmed = name?.trim().orEmpty()
        EXACT[trimmed]?.let { return it }
        if (trimmed.startsWith(SPONSOR_PREFIX)) return Category.SPONSOR
        if (index == 0 && startMs == 0L && endMs - startMs < POSITIONAL_MAX_MS) return Category.SPONSOR
        return -1
    }

    /** Fails the patch build when a table entry disagrees with its category. */
    fun verify() {
        EXACT.forEach { (name, category) ->
            check(classify(OWNER_ID, name, 5, 1_000, 2_000) == category) {
                "Rule '$name' does not classify as ${Category.name(category)}"
            }
        }
        check(classify(OWNER_ID, "$SPONSOR_PREFIX Levenhuk", 5, 1_000, 2_000) == Category.SPONSOR)
        check(classify(OWNER_ID, "Индилайт", 0, 0, POSITIONAL_MAX_MS - 1) == Category.SPONSOR)
        check(classify(OWNER_ID, "Индилайт", 0, 0, POSITIONAL_MAX_MS) == -1)
        check(classify(0, "Контент везде", 0, 0, 1_000) == -1)
    }
}
