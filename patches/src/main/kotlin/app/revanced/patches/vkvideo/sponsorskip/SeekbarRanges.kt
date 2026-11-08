package app.revanced.patches.vkvideo.sponsorskip

/** A seekbar band as a fraction of the track; [lane] is the vertical slot when segments overlap. */
internal data class Band(val xStart: Float, val xEnd: Float, val lane: Int, val category: Int)

/**
 * Reference implementation of the injected `Ranges` class: turns the video's segments
 * into drawable bands. The injected build stays ungated so a drawable can re-read the
 * gating on every draw; [visible] mirrors that draw-time filter for the JVM tests.
 */
internal object SeekbarRanges {
    const val MAX_LANES = 3
    const val GAP = 0.001f

    /** Bands for [segments], sorted into lanes; empty when degenerate. */
    fun build(segments: List<Segment>, durationMs: Long): List<Band> {
        if (durationMs <= 0) return emptyList()

        val laneEnds = FloatArray(MAX_LANES) { -1f }
        val bands = ArrayList<Band>()
        for (segment in segments) {
            val category = segment.category
            if (category < 0) continue

            var xStart = (segment.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
            var xEnd = (segment.endMs.toFloat() / durationMs).coerceIn(0f, 1f)
            if (xEnd <= xStart) xEnd = (xStart + GAP).coerceAtMost(1f)

            var lane = laneEnds.indexOfFirst { it < xStart }
            if (lane < 0) lane = MAX_LANES - 1
            xStart = maxOf(xStart, laneEnds[lane] + GAP)
            xEnd = xEnd.coerceAtMost(1f)
            if (xEnd <= xStart) {
                xEnd = (xStart + GAP).coerceAtMost(1f)
                xStart = xEnd - GAP
            }

            laneEnds[lane] = xEnd
            bands += Band(xStart, xEnd, lane, category)
        }
        return bands
    }

    /** Draw-time gate: master off hides everything, a DISABLED category hides its own bands. */
    fun visible(bands: List<Band>, behaviors: IntArray, enabled: Boolean): List<Band> {
        if (!enabled) return emptyList()
        return bands.filter { behaviors.getOrElse(it.category) { Behavior.SHOW_SKIP_BUTTON } != Behavior.DISABLED }
    }
}
