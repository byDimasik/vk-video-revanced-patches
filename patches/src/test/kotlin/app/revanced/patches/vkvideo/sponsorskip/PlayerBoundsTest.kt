package app.revanced.patches.vkvideo.sponsorskip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Reference copy of the injected clamp in `SkipButton.place`. */
private object PlayerBounds {
    fun clamp(rect: IntArray, button: IntArray, pad: Int, margin: Int): IntArray {
        val (left, top, right, bottom) = rect
        val (width, height) = button
        var x = minOf(right - width - margin, right - width - pad)
        var y = minOf(bottom - height - margin, bottom - height - pad)
        if (x < left + pad) x = left + pad
        if (y < top + pad) y = top + pad
        return intArrayOf(x, y)
    }
}

class PlayerBoundsTest {

    @Test
    fun `the button lands at the bottom-right with the edge margin`() {
        val (x, y) = PlayerBounds.clamp(intArrayOf(0, 0, 1000, 600), intArrayOf(200, 80), pad = 8, margin = 16)
        assertEquals(784, x)
        assertEquals(504, y)
    }

    @Test
    fun `both axes clamp to the pad inset when the corner overflows`() {
        val (x, y) = PlayerBounds.clamp(intArrayOf(100, 100, 200, 200), intArrayOf(200, 80), pad = 8, margin = 16)
        assertEquals(108, x)
        assertEquals(108, y)
    }

    @Test
    fun `the axis that overflows clamps while the other keeps its margin`() {
        val (x, y) = PlayerBounds.clamp(intArrayOf(0, 0, 1000, 100), intArrayOf(200, 80), pad = 8, margin = 16)
        assertEquals(784, x)
        assertEquals(8, y)
    }

    @Test
    fun `a surface smaller than the button pins it to the pad inset`() {
        val (x, y) = PlayerBounds.clamp(intArrayOf(0, 0, 100, 50), intArrayOf(200, 80), pad = 8, margin = 16)
        assertEquals(8, x)
        assertEquals(8, y)
    }
}
