package com.mahjong.guobiao.engine

import com.mahjong.guobiao.model.PlayerSeat
import com.mahjong.guobiao.model.TableState
import com.mahjong.guobiao.model.TileParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DevelopmentAnalyzerTest {

    private fun table() = TableState.fresh(PlayerSeat.EAST)

    @Test
    fun `14张未和牌返回弃牌建议且discardTile来自暗手`() {
        // 1p×2..6p×2 + 7p + 8p = 14张未和牌
        val hand = TileParser.parseHand("1122334455667p8p")
        val suggestions = DevelopmentAnalyzer.analyzeDiscard(hand, table())
        assertTrue(suggestions.isNotEmpty())
        val distinctTiles = hand.concealed.distinct()
        assertEquals(distinctTiles.size, suggestions.size)
        assertTrue(suggestions.all { it.discardTile in distinctTiles })
    }

    @Test
    fun `弃8p后听7p成七对且可起和`() {
        val hand = TileParser.parseHand("1122334455667p8p")
        val suggestions = DevelopmentAnalyzer.analyzeDiscard(hand, table())
        val sevenP = TileParser.parse("7p").first()
        val eightP = TileParser.parse("8p").first()
        val s = suggestions.first { it.discardTile == eightP }
        assertTrue(sevenP in s.resultingWaits)
        assertTrue(s.reachesMinimum)
        assertTrue(s.possibleFans.any { it.name == "七小对" })
    }

    @Test
    fun `弃废牌后听牌但无番无法起和`() {
        // 123m456p789s123p东1m: 14张未和牌
        // 弃1m后 = 123m456p789s123p东(13张), 听东成雀头, 和牌无番(门清不计入)
        val hand = TileParser.parseHand("123m456p789s123p东1m")
        val suggestions = DevelopmentAnalyzer.analyzeDiscard(hand, table())
        val oneM = TileParser.parse("1m").first()
        val east = TileParser.parse("东").first()
        val s = suggestions.first { it.discardTile == oneM }
        assertTrue(east in s.resultingWaits)
        assertFalse(s.reachesMinimum)
    }

    @Test
    fun `可起和的建议排在无法起和的前面`() {
        val hand = TileParser.parseHand("123m456p789s123p东1m")
        val suggestions = DevelopmentAnalyzer.analyzeDiscard(hand, table())
        val firstFalse = suggestions.indexOfFirst { !it.reachesMinimum }
        val lastTrue = suggestions.indexOfLast { it.reachesMinimum }
        if (firstFalse >= 0 && lastTrue >= 0) {
            assertTrue(lastTrue < firstFalse) { "可起和的建议应排在前面" }
        }
    }
}
