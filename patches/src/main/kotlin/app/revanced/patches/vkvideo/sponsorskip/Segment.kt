package app.revanced.patches.vkvideo.sponsorskip

internal data class Segment(
    val startMs: Long,
    var endMs: Long,
    val name: String,
    var category: Int = -1,
)
