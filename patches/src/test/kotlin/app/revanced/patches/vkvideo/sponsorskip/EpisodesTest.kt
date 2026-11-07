package app.revanced.patches.vkvideo.sponsorskip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class Fixture(name: String) {
    private val doc: String = EpisodesTest::class.java.getResource("/sponsorskip/$name")!!.readText()

    val durationMs: Long = Regex("\"durationMs\": (\\d+)").find(doc)!!.groupValues[1].toLong()

    val episodes: List<Episode> = Regex("\\{\"text\": (\"(?:[^\"\\\\]|\\\\.)*\"|\"[^\"]*\"), \"time\": (-?\\d+)}")
        .findAll(doc)
        .map { Episode(it.groupValues[2].toInt(), it.groupValues[1].trim('"')) }
        .toList()
}

private fun List<Segment>.starts() = map { it.startMs to it.name }

class EpisodesTest {

    @Test
    fun `a captured episode list becomes sorted segments closed at the next start`() {
        val fixture = Fixture("episode-771.json")
        assertEquals(83, fixture.episodes.size)

        val segments = Episodes.read(fixture.episodes.reversed(), fixture.durationMs)

        assertEquals(83, segments.size)
        assertEquals(fixture.episodes.map { it.time * 1000L to it.text }.sortedBy { it.first }, segments.starts())
        assertEquals(fixture.durationMs, segments.last().endMs)
        for (index in 0 until segments.lastIndex) {
            assertEquals(segments[index + 1].startMs, segments[index].endMs)
        }
    }

    @Test
    fun `blank text, negative and over-duration starts are dropped`() {
        val segments = Episodes.read(
            listOf(
                Episode(0, "  "),
                Episode(-5, "before zero"),
                Episode(10, null),
                Episode(20, " kept "),
                Episode(30, "at duration"),
                Episode(31, "past duration"),
            ),
            30_000,
        )

        assertEquals(listOf(20_000L to "kept"), segments.starts())
        assertEquals(30_000L, segments.single().endMs)
    }

    @Test
    fun `empty, null and degenerate lists yield nothing`() {
        assertTrue(Episodes.read(null, 60_000).isEmpty())
        assertTrue(Episodes.read(emptyList(), 60_000).isEmpty())
        assertTrue(Episodes.read(listOf(Episode(0, "x")), 0).isEmpty())
    }

    @Test
    fun `a single episode at the start runs to the end`() {
        assertEquals(listOf(Segment(0, 90_000, "Индилайт")), Episodes.read(listOf(Episode(0, "Индилайт")), 90_000))
    }

    @Test
    fun `two episodes at the same time keep the later entry`() {
        val segments = Episodes.read(listOf(Episode(5, "first"), Episode(5, "second"), Episode(9, "next")), 20_000)

        assertEquals(listOf(5_000L to "second", 9_000L to "next"), segments.starts())
        assertEquals(5_000L, segments[0].startMs)
        assertEquals(9_000L, segments[0].endMs)
        assertEquals(20_000L, segments[1].endMs)
    }

    @Test
    fun `the segment cap bounds a runaway list`() {
        val many = (0 until 600).map { Episode(it, "chapter $it") }
        assertEquals(Episodes.MAX_SEGMENTS, Episodes.read(many, 1_000_000).size)
    }
}
