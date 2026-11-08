package app.revanced.patches.vkvideo.sponsorskip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BehaviorTest {

    @Test
    fun `each literal parses to its value`() {
        assertEquals(Behavior.SKIP_AUTOMATICALLY, Behavior.parse("SKIP_AUTOMATICALLY", Behavior.DISABLED))
        assertEquals(Behavior.SHOW_SKIP_BUTTON, Behavior.parse("SHOW_SKIP_BUTTON", Behavior.DISABLED))
        assertEquals(Behavior.DISABLED, Behavior.parse("DISABLED", Behavior.SKIP_AUTOMATICALLY))
    }

    @Test
    fun `a missing or unknown value falls back`() {
        assertEquals(Behavior.SHOW_SKIP_BUTTON, Behavior.parse(null, Behavior.SHOW_SKIP_BUTTON))
        assertEquals(Behavior.SHOW_SKIP_BUTTON, Behavior.parse("", Behavior.SHOW_SKIP_BUTTON))
        assertEquals(Behavior.SHOW_SKIP_BUTTON, Behavior.parse("SOMETHING", Behavior.SHOW_SKIP_BUTTON))
    }

    @Test
    fun `every category carries a color and a distinct name`() {
        assertEquals(Category.COUNT, Category.COLORS.size)
        assertEquals(Category.COUNT, (0 until Category.COUNT).map { Category.name(it) }.toSet().size)
        assertEquals(0xFF00D400.toInt(), Category.COLORS[Category.SPONSOR])
        assertEquals(0xFFFFFF00.toInt(), Category.COLORS[Category.SELFPROMO])
        assertEquals(0xFF00FFFF.toInt(), Category.COLORS[Category.INTRO])
        assertEquals(0xFF0202ED.toInt(), Category.COLORS[Category.OUTRO])
    }
}
