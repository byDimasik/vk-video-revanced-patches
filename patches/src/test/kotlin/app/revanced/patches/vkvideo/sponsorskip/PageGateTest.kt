package app.revanced.patches.vkvideo.sponsorskip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Reference copy of the injected `SkipButton.onPage` walk: the surface's own chain decides,
 * `playerContainer` is the only page evidence, any list ancestor or a visible mini overlay
 * vetoes it. The walk runs to the root with NO depth cap, so a veto at any ancestor depth
 * is honored -- the smali terminates only on the null parent (#97). The smali emits exactly
 * these four vertex kinds.
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
    fun `a recycler veto beyond the old 12-ancestor depth is honored`() {
        // Regression for the dropped depth cap: the old walk inspected only the first
        // 12 nodes, so a list ancestor at depth >= 12 let a feed card pass as the page.
        val deep = List(14) { PageGate.Node(0x100 + it) }
        val chain = listOf(surface) + deep + listOf(PageGate.Node(LIST_RECYCLER_ID), decor)
        assertTrue(chain.size >= 12)
        assertTrue(chain.indexOfFirst { it.id == LIST_RECYCLER_ID } >= 12)
        assertFalse(PageGate.onPage(attached = true, chain = chain))
    }

    @Test
    fun `a mini veto beyond the old 12-ancestor depth is honored`() {
        val deep = List(14) { PageGate.Node(0x100 + it) }
        val chain = listOf(surface) + deep +
            listOf(PageGate.Node(MINIMIZABLE_ID, visibleMini = true), decor)
        assertTrue(chain.indexOfFirst { it.visibleMini } >= 12)
        assertFalse(PageGate.onPage(attached = true, chain = chain))
    }

    @Test
    fun `a container found beyond the old 12-ancestor depth still passes`() {
        // The flip side: an unbounded walk also finds page evidence deep in the chain.
        val deep = List(14) { PageGate.Node(0x100 + it) }
        val chain = listOf(surface) + deep + listOf(container, host, decor)
        assertTrue(PageGate.onPage(attached = true, chain = chain))
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
        // Bind to the CALL inside show()'s body, not the onPage declaration that
        // precedes it in the emitted source (CR minor).
        val show = source.substringAfter(".method public static show").substringBefore(".end method")
        val gate = show.indexOf("->onPage(")
        assertTrue(gate > 0, "show does not call the page gate")
        val store = show.indexOf("->view:Landroid/view/View;")
        assertTrue(store > 0, "show does not store the surface")
        assertTrue(gate < store, "the gate runs after the surface is already stored")
        assertTrue(show.contains(":off_page"), "no off-page landing pad")
        assertTrue(
            Regex(""":off_page\s+invoke-static \{p0\}, \$?Ljava?.*hide""").containsMatchIn(show) ||
                show.substringAfter(":off_page").contains("->hide("),
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
        // Same polarity as the render callers: after the gate call, a ZERO result must
        // branch to :return (off-page), and an on-page call must fall through to the
        // position/consumption guards. Bind to the CALL in skip()'s body, not the
        // onPage declaration (CR minor).
        val gate = skip.indexOf("->onPage(")
        val after = skip.substring(gate).substringBefore("aget-object")
        assertTrue(Regex("""if-eqz v\d+, :return""").containsMatchIn(after), "skip returns on the on-page result")
        assertFalse(Regex("""if-nez v\d+, :return""").containsMatchIn(after), "skip's gate polarity is still inverted")
    }

    @Test
    fun `the onPage walk has no depth cap`() {
        val source = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        val onPage = source.substringAfter(".method public static onPage").substringBefore(".end method")
        assertFalse(Regex("""const/16 v\d+, 0xc""").containsMatchIn(onPage), "the ancestor walk still caps its depth")
        assertFalse(Regex("""if-ge v\d+, v\d+, :decide""").containsMatchIn(onPage), "the ancestor walk still caps its depth")
        assertTrue(onPage.contains("getParent"), "the walk no longer terminates on the null parent")
        assertTrue(onPage.contains(":loop"), "the walk is no longer an ancestor loop")
    }

    @Test
    fun `the mini surface fallback is gone from show`() {
        val source = sponsorSkipSources()[SKIP_BUTTON_CLASS]!!
        val show = source.substringAfter(".method public static show").substringBefore(".end method")
        assertFalse(show.contains("->K:Ljava/lang/ref/WeakReference;"), "the mini weakref branch is still there")
    }
}
