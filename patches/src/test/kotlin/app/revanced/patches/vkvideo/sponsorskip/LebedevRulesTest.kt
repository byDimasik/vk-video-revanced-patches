package app.revanced.patches.vkvideo.sponsorskip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LebedevRulesTest {

    @Test
    fun `every recurring ad and self-promo name maps to its category`() {
        val expected = mapOf(
            "Контент везде" to Category.SELFPROMO,
            "Новинки дизайна" to Category.SELFPROMO,
            "Инструмент путешественника" to Category.SELFPROMO,
            "Самый хороший дизайн" to Category.SELFPROMO,
            "Медиа про дизайн" to Category.SELFPROMO,
            "Новинка магазинуса" to Category.SELFPROMO,
            "Важнейшая новость" to Category.INTRO,
        )
        expected.forEach { (name, category) ->
            assertEquals(category, LebedevRules.classify(LebedevRules.OWNER_ID, name, 5, 900_000, 950_000), name)
        }
    }

    @Test
    fun `the integration prefix matches whatever brand follows`() {
        assertEquals(
            Category.SPONSOR,
            LebedevRules.classify(LebedevRules.OWNER_ID, "Нативная интеграция: Levenhuk", 5, 1_342_000, 1_400_000),
        )
        assertEquals(
            Category.SPONSOR,
            LebedevRules.classify(LebedevRules.OWNER_ID, "  Нативная интеграция: Атом  ", 5, 10_000, 40_000),
        )
    }

    @Test
    fun `exact names win over the positional rule`() {
        // «Важнейшая новость» opens the 771 episode at 00:00 and stays intro, not sponsor.
        assertEquals(Category.INTRO, LebedevRules.classify(LebedevRules.OWNER_ID, "Важнейшая новость", 0, 0, 90_000))
    }

    @Test
    fun `a short first chapter at zero is a sponsor read`() {
        assertEquals(Category.SPONSOR, LebedevRules.classify(LebedevRules.OWNER_ID, "Индилайт", 0, 0, 89_000))
    }

    @Test
    fun `the positional rule stops at ninety seconds`() {
        assertEquals(-1, LebedevRules.classify(LebedevRules.OWNER_ID, "Индилайт", 0, 0, 90_000))
        assertEquals(Category.SPONSOR, LebedevRules.classify(LebedevRules.OWNER_ID, "Индилайт", 0, 0, 89_999))
    }

    @Test
    fun `the positional rule needs index zero and a zero start`() {
        assertEquals(-1, LebedevRules.classify(LebedevRules.OWNER_ID, "Индилайт", 1, 0, 89_000))
        assertEquals(-1, LebedevRules.classify(LebedevRules.OWNER_ID, "Индилайт", 0, 1_000, 89_000))
    }

    @Test
    fun `the presence of a later chapter does not matter`() {
        assertEquals(Category.SPONSOR, LebedevRules.classify(LebedevRules.OWNER_ID, "Электромобиль Атом", 0, 0, 72_000))
    }

    @Test
    fun `another channel classifies nothing`() {
        assertEquals(-1, LebedevRules.classify(0, "Контент везде", 0, 0, 60_000))
        assertEquals(-1, LebedevRules.classify(123, "Нативная интеграция: X", 3, 900_000, 950_000))
    }

    @Test
    fun `an unknown content chapter stays unclassified`() {
        assertEquals(-1, LebedevRules.classify(LebedevRules.OWNER_ID, "Новые правила маркетплейсов", 4, 65_000, 160_000))
        assertEquals(-1, LebedevRules.classify(LebedevRules.OWNER_ID, null, 4, 65_000, 160_000))
    }

    @Test
    fun `the shipped rule tables verify`() {
        LebedevRules.verify()
    }
}
