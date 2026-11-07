package app.revanced.patches.vkvideo.sponsorskip

/** The `time`/`text` pair of `com.vk.dto.common.VideoEpisode`, as the JSON carries it. */
internal data class Episode(val time: Int, val text: String?)

/**
 * Turns the video's official episodes into segments. Reference implementation of
 * the injected `Episodes` class: the shipped one reads `VideoFile.k3()` itself,
 * this mirror takes the list so the JVM tests can feed it fixtures.
 *
 * Source of record: the player's episode consumer reads the same `k3()` list at
 * `full/smali_classes7/com/vk/libvideo/autoplay/delegate/h.smali:8732`.
 *
 * The API times are seconds; blanks, negatives and starts at or past the duration
 * are dropped, equal starts collapse to the later entry, and the last segment runs
 * to the video's end.
 */
internal object Episodes {
    const val MAX_SEGMENTS = 500

    fun read(episodes: List<Episode?>?, durationMs: Long): List<Segment> {
        if (episodes.isNullOrEmpty()) return emptyList()

        val kept = ArrayList<Segment>()
        for (episode in episodes) {
            if (episode == null) continue
            if (kept.size == MAX_SEGMENTS) break
            val name = episode.text?.trim()
            if (name.isNullOrEmpty()) continue
            if (episode.time < 0) continue
            val startMs = episode.time * 1000L
            if (startMs >= durationMs) continue
            kept += Segment(startMs, 0, name)
        }
        if (kept.isEmpty()) return emptyList()
        kept.sortBy { it.startMs }

        val closed = ArrayList<Segment>(kept.size)
        for (segment in kept) {
            val previous = closed.lastOrNull()
            if (previous != null && previous.startMs == segment.startMs) {
                closed[closed.lastIndex] = segment
                continue
            }
            previous?.endMs = segment.startMs
            closed += segment
        }
        closed.last().endMs = durationMs
        return closed
    }
}
