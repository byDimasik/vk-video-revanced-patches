package app.revanced.patches.vkvideo.sponsorskip

/**
 * Segment categories and their fixed seekbar colors (SponsorBlock defaults).
 * The injected `Category` class carries the same table; the seekbar ticket
 * reads it from there.
 */
internal object Category {
    const val SPONSOR = 0
    const val SELFPROMO = 1
    const val INTRO = 2
    const val OUTRO = 3
    const val COUNT = 4

    val COLORS = intArrayOf(
        0xFF00D400.toInt(),
        0xFFFFFF00.toInt(),
        0xFF00FFFF.toInt(),
        0xFF0202ED.toInt(),
    )

    fun name(category: Int): String = when (category) {
        SPONSOR -> "sponsor"
        SELFPROMO -> "selfpromo"
        INTRO -> "intro"
        OUTRO -> "outro"
        else -> "unknown"
    }
}
