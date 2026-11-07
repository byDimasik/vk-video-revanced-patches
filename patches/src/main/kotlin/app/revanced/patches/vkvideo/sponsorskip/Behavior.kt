package app.revanced.patches.vkvideo.sponsorskip

/**
 * Per-category behavior values of the `sponsorskip_settings` store. The
 * fallback for a missing, blank or unknown value is [SHOW_SKIP_BUTTON].
 */
internal object Behavior {
    const val SKIP_AUTOMATICALLY = 0
    const val SHOW_SKIP_BUTTON = 1
    const val DISABLED = 2

    fun parse(value: String?, default: Int): Int = when (value) {
        "SKIP_AUTOMATICALLY" -> SKIP_AUTOMATICALLY
        "SHOW_SKIP_BUTTON" -> SHOW_SKIP_BUTTON
        "DISABLED" -> DISABLED
        else -> default
    }
}
