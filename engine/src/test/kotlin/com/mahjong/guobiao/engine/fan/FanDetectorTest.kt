package com.mahjong.guobiao.engine.fan

import com.mahjong.guobiao.engine.win.WinChecker
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.PlayerSeat
import com.mahjong.guobiao.model.TileParser
import com.mahjong.guobiao.model.TileType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FanDetectorTest {

    private fun scoreHand(
        text: String,
        method: WinMethod = WinMethod.SELF_DRAW,
        selfSeat: PlayerSeat = PlayerSeat.EAST,
        prevailingWind: PlayerSeat = PlayerSeat.EAST
    ): FanResult {
        val hand = TileParser.parseHand(text)
        val decomps = WinChecker.getAllDecompositions(hand)
        assert(decomps.isNotEmpty()) { "手牌未和牌: $text" }
        val results = decomps.map { decomp ->
            FanScorer.score(FanContext(decomp, hand, WinInfo(hand.concealed.last(), method, selfSeat, prevailingWind)))
        }
        return results.maxByOrNull { it.totalFan }!!
    }

    private fun hasFan(result: FanResult, fanName: String): Boolean =
        result.allDetected.any { it.name == fanName }
    private fun isSubsumed(result: FanResult, fanName: String): Boolean =
        result.subsumed.any { it.name == fanName }

    @Test
    fun `大四喜`() {
        val result = scoreHand("东东东南南南西西西北北北1m1m")
        assertTrue(hasFan(result, "大四喜"))
        assertTrue(isSubsumed(result, "碰碰胡"))
    }

    @Test
    fun `大三元`() {
        // 中中中 发发发 白白白 4m4m4m 55m（全刻子）
        val result = scoreHand("中中中发发发白白白444m55m")
        assertTrue(hasFan(result, "大三元"))
        assertTrue(isSubsumed(result, "碰碰胡"))
    }

    @Test
    fun `小四喜`() {
        // 东东东 南南南 西西西(3刻子) + 北北(雀头) + 123m(额外面子)
        val result = scoreHand("东东东南南南西西西北北123m")
        assertTrue(hasFan(result, "小四喜"))
    }

    @Test
    fun `小三元`() {
        // 中中中 发发发(2刻子) + 白白(雀头) + 123m456m
        val result = scoreHand("中中中发发发123m456m白白")
        assertTrue(hasFan(result, "小三元"))
    }

    @Test
    fun `绿一色`() {
        val result = scoreHand("222s333s444s666s8s8s")
        assertTrue(hasFan(result, "绿一色"))
    }

    @Test
    fun `九连宝灯`() {
        val result = scoreHand("1112345678999m5m")
        assertTrue(hasFan(result, "九连宝灯"))
        assertTrue(isSubsumed(result, "清一色"))
    }

    @Test
    fun `十三幺`() {
        val result = scoreHand("1m9m1p9p1s9s东南西北中发白白")
        assertTrue(hasFan(result, "十三幺"))
    }

    @Test
    fun `清一色`() {
        val result = scoreHand("111m234m567m789m55m")
        assertTrue(hasFan(result, "清一色"))
    }

    @Test
    fun `碰碰胡`() {
        val result = scoreHand("111m222m333m444p55s")
        assertTrue(hasFan(result, "碰碰胡"))
    }

    @Test
    fun `混幺九`() {
        val result = scoreHand("111m999m111p中中中11s")
        assertTrue(hasFan(result, "混幺九"))
    }

    @Test
    fun `混一色`() {
        val result = scoreHand("123m456m789m中中中5m5m")
        assertTrue(hasFan(result, "混一色"))
    }

    @Test
    fun `七小对`() {
        val result = scoreHand("11m22m33m44m55m66m77m")
        assertTrue(hasFan(result, "七小对"))
    }

    @Test
    fun `豪华七对`() {
        val hand = TileParser.parseHand("1111m22m33m44m55m66m")
        val decomps = WinChecker.getAllDecompositions(hand)
        assertTrue(decomps.isNotEmpty())
        val hasLuxury = decomps.any { decomp ->
            val result = FanScorer.score(FanContext(decomp, hand, WinInfo(hand.concealed.last())))
            result.allDetected.any { it.name == "豪华七对" } && result.subsumed.any { it.name == "七小对" }
        }
        assertTrue(hasLuxury)
    }

    @Test
    fun `双豪华七对`() {
        val result = scoreHand("1111m2222m33m44m55m")
        assertTrue(hasFan(result, "双豪华七对"))
    }

    @Test
    fun `红孔雀`() {
        val result = scoreHand("111s555s777s999s中中")
        assertTrue(hasFan(result, "红孔雀"))
    }

    @Test
    fun `蓝一色`() {
        val result = scoreHand("东东东南南南白白白8p8p8p西西")
        assertTrue(hasFan(result, "蓝一色"))
    }

    @Test
    fun `字一色`() {
        val result = scoreHand("东东东南南南西西西北北北中中")
        assertTrue(hasFan(result, "字一色"))
    }

    @Test
    fun `清幺九`() {
        val result = scoreHand("111m999m111p999p11s")
        assertTrue(hasFan(result, "清幺九"))
    }

    @Test
    fun `大七星`() {
        val result = scoreHand("东东南南西西北北中中发发白白")
        assertTrue(hasFan(result, "大七星"))
        assertTrue(isSubsumed(result, "七小对"))
    }

    @Test
    fun `不计关系 大四喜含碰碰胡`() {
        // 大四喜: 4风刻子，必然也是碰碰胡
        val result = scoreHand("东东东南南南西西西北北北1m1m")
        assertTrue(isSubsumed(result, "碰碰胡"))
    }
}
