package app.revanced.patches.vkvideo.sponsorskip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Reference copy of the injected `SkipButton.onPage` walk: the surface's own chain decides,
 * `playerContainer` is the only page evidence, any list ancestor or a visible mini overlay
 * vetoes it. The smali emits exactly these four vertex kinds (#97).
 */
private object PageGate {
    sealed interface Vertex {
        val id: Int
        val visibleMini: Boolean
    }

    data class Node(override val id: Int, override val visibleMini: Boolean = false) : Vertex

    fun onPage(attached: Boolean, chain: List<Vertex>): Boolean {
        if (!attached || chain.isEmpty()) return false
        var inContainer = false
        for (node in chain) {
            if (node.id == LIST_RECYCLER_ID) return false
            if (node.id == PLAYER_CONTAINER_ID) inContainer = true
            if (node.visibleMini) return false
        }
        return inContainer
    }
}

class PageGateTest {

    private val decor = PageGate.Node(0x1020002)
    private val container = PageGate.Node(PLAYER_CONTAINER_ID)
    private val host = PageGate.Node(MINIMIZABLE_ID)
    private val surface = PageGate.Node(SEEK_BAR_VIEW_ID)

    @Test
    fun `the ids are the real 1_171 resource ids`() {
        assertEquals(0x7f0b1d19, PLAYER_CONTAINER_ID)
        assertEquals(0x7f0b207d, LIST_RECYCLER_ID)
        assertEquals(0x7f0b1805, MINIMIZABLE_ID)
        assertEquals(0x7f0b17fc, MINI_PLAYER_ID)
    }

    @Test
    fun `a feed card surface shows nothing`() {
        val chain = listOf(surface, PageGate.Node(LIST_RECYCLER_ID), decor)
        assertFalse(PageGate.onPage(attached = true, chain = chain))
    }

    @Test
    fun `a full page surface shows`() {
        val chain = listOf(surface, container, host, decor)
        assertTrue(PageGate.onPage(attached = true, chain = chain))
    }

    @Test
    fun `a page left to the decor hides again`() {
        assertFalse(PageGate.onPage(attached = true, chain = listOf(surface, decor)))
        assertFalse(PageGate.onPage(attached = false, chain = listOf(surface, container, host, decor)))
    }

    @Test
    fun `the mini overlay vetoes an otherwise valid page chain`() {
        val chain = listOf(surface, container, PageGate.Node(MINIMIZABLE_ID, visibleMini = true), decor)
        assertFalse(PageGate.onPage(attached = true, chain = chain))
    }

    @Test
    fun `the gate walks up and stops at the recycler before finding a container`() {
        // A card nested in a page-like host still lists first.
        val chain = listOf(surface, PageGate.Node(LIST_RECYCLER_ID), container, host)
        assertFalse(PageGate.onPage(attached = true, chain = chain))
    }

    @Test
    fun `show() gates before it stores the surface and lands on a hide label`() {
        val source = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        val gate = source.indexOf("onPage(")
        assertTrue(gate > 0, "show does not call the page gate")
        val store = source.indexOf("->view:Landroid/view/View;")
        assertTrue(gate < store, "the gate runs after the surface is already stored")
        assertTrue(source.contains(":off_page"), "no off-page landing pad")
        assertTrue(
            Regex(""":off_page\s+invoke-static \{p0\}, \$?Ljava?.*hide""").containsMatchIn(source) ||
                source.substringAfter(":off_page").contains("->hide("),
            "the off-page pad does not hide the chip",
        )
    }

    @Test
    fun `evaluate and skip consult the same gate`() {
        val engine = sponsorSkipSources()[SKIP_ENGINE_CLASS]!!
        val evaluate = engine.substringAfter(".method private static evaluate").substringBefore(".end method")
        assertTrue(evaluate.contains("->onPage("), "evaluate is not gated")
        val skip = engine.substringAfter(".method public static skip").substringBefore(".end method")
        assertTrue(skip.contains("->onPage("), "skip is not gated")
    }

    @Test
    fun `the mini surface fallback is gone from show`() {
        val source = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        val show = source.substringAfter(".method public static show").substringBefore(".end method")
        assertFalse(show.contains("->K:Ljava/lang/ref/WeakReference;"), "the mini weakref branch is still there")
    }
}
