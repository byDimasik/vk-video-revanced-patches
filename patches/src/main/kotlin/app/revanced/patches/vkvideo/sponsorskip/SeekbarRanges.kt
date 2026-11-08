package app.revanced.patches.vkvideo.sponsorskip

/** A seekbar band in track coordinates; [lane] is the vertical slot when segments overlap. */
internal data class Band(val xStart: Int, val xEnd: Int, val lane: Int, val category: Int)

/**
 * Reference implementation of the injected `Ranges` class: turns the video's segments
 * into drawable bands. The shipped one reads the master switch and the behaviors from
 * the store itself; this mirror takes them so the JVM tests can drive the gating.
 */
internal object SeekbarRanges {
    const val MAX_LANES = 3

    /** Bands for [segments] inside the track, one lane each at most; empty when gated off or degenerate. */
    fun build(
        segments: List<Segment>,
        behaviors: IntArray,
        enabled: Boolean,
        durationMs: Long,
        trackLeft: Int,
        trackWidth: Int,
    ): List<Band> {
        if (!enabled || durationMs <= 0 || trackWidth <= 0) return emptyList()

        val right = trackLeft + trackWidth
        val laneEnds = IntArray(MAX_LANES) { -1 }
        val bands = ArrayList<Band>()
        for (segment in segments) {
            val category = segment.category
            if (category < 0) continue
            if (behaviors.getOrElse(category) { Behavior.SHOW_SKIP_BUTTON } == Behavior.DISABLED) continue

            var xStart = trackLeft + (segment.startMs * trackWidth / durationMs).toInt()
            var xEnd = trackLeft + (segment.endMs * trackWidth / durationMs).toInt()
            xStart = xStart.coerceIn(trackLeft, right)
            xEnd = xEnd.coerceAtMost(right)
            if (xEnd <= xStart) xEnd = xStart + 1

            var lane = laneEnds.indexOfFirst { it < xStart }
            if (lane < 0) lane = MAX_LANES - 1
            xStart = maxOf(xStart, laneEnds[lane] + 1)
            xEnd = minOf(xEnd, right)
            if (xEnd <= xStart) {
                xEnd = minOf(xStart + 1, right)
                xStart = xEnd - 1
            }

            laneEnds[lane] = xEnd
            bands += Band(xStart, xEnd, lane, category)
        }
        return bands
    }
}
