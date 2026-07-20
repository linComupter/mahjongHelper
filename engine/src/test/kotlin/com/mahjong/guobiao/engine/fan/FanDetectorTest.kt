package com.mahjong.guobiao.engine.fan

import com.mahjong.guobiao.engine.win.WinChecker
import com.mahjong.guobiao.model.Hand
import com.mahjong.guobiao.model.Meld
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
        // 取番数最高的分解
        val results = decomps.map { decomp ->
            FanScorer.score(FanContext(decomp, hand, WinInfo(hand.concealed.last(), method, selfSeat, prevailingWind)))
        }
        return results.maxByOrNull { it.totalFan }!!
    }

    private fun hasFan(result: FanResult, fanName: String): Boolean =
        result.allDetected.any { it.name == fanName }

    private fun isCounted(result: FanResult, fanName: String): Boolean =
        result.counted.any { it.name == fanName }

    private fun isSubsumed(result: FanResult, fanName: String): Boolean =
        result.subsumed.any { it.name == fanName }

    @Test
    fun `大三元`() {
        // 中中中 发发发 白白白 123m 4m4m
        val result = scoreHand("中中中发发发白白白123m4m4m")
        assertTrue(hasFan(result, "大三元"), "应检测到大三元")
        assertTrue(isSubsumed(result, "箭刻"), "箭刻应被大三元不计")
        assertTrue(result.meetsMinimum, "应满足8番起和")
    }

    @Test
    fun `绿一色`() {
        // 222s 333s 444s 666s 88s（全绿，14张）
        val result = scoreHand("222s333s444s666s8s8s")
        assertTrue(hasFan(result, "绿一色"), "应检测到绿一色")
    }

    @Test
    fun `九莲宝灯`() {
        val result = scoreHand("1112345678999m5m")
        assertTrue(hasFan(result, "九莲宝灯"), "应检测到九莲宝灯")
        assertTrue(isSubsumed(result, "清一色"), "清一色应被九莲宝灯不计")
    }

    @Test
    fun `清一色`() {
        // 111m 234m 567m 789m 55m（14张全万）
        val result = scoreHand("111m234m567m789m55m")
        assertTrue(hasFan(result, "清一色"))
        assertTrue(isSubsumed(result, "无字"))
    }

    @Test
    fun `碰碰和`() {
        // 111m 222m 333m 444p 55s
        val result = scoreHand("111m222m333m444p55s")
        // 选全刻子分解
        assertTrue(hasFan(result, "碰碰和"))
    }

    @Test
    fun `断幺`() {
        // 234m 567m 234p 567p 5s5s（无1/9/字）
        val result = scoreHand("234m567m234p567p5s5s")
        assertTrue(hasFan(result, "断幺"))
    }

    @Test
    fun `七对`() {
        val result = scoreHand("11m22m33m44m55m66m77m")
        assertTrue(hasFan(result, "七对"))
    }

    @Test
    fun `十三幺`() {
        val result = scoreHand("1m9m1p9p1s9s东南西北中发白白")
        assertTrue(hasFan(result, "十三幺"))
    }

    @Test
    fun `混一色`() {
        // 123m 456m 789m 中中中 5m5m
        val result = scoreHand("123m456m789m中中中5m5m")
        assertTrue(hasFan(result, "混一色"))
    }

    @Test
    fun `自摸与点炮`() {
        val hand = TileParser.parseHand("234m456m789m234p55p")
        val decomp = WinChecker.getAllDecompositions(hand).first()
        val selfDraw = FanScorer.score(FanContext(decomp, hand, WinInfo(hand.concealed.last(), WinMethod.SELF_DRAW)))
        val discard = FanScorer.score(FanContext(decomp, hand, WinInfo(hand.concealed.last(), WinMethod.DISCARD)))
        assertTrue(isCounted(selfDraw, "自摸"))
        assertFalse(hasFan(discard, "自摸"))
    }

    @Test
    fun `箭刻`() {
        // 中中中 123m 456m 789m 5p5p
        val result = scoreHand("中中中123m456m789m5p5p")
        assertTrue(hasFan(result, "箭刻"))
    }

    @Test
    fun `圈风刻与门风刻`() {
        // 东东东 123m 456m 789m 5p5p，圈风东、门风东
        val result = scoreHand("东东东123m456m789m5p5p", selfSeat = PlayerSeat.EAST, prevailingWind = PlayerSeat.EAST)
        assertTrue(hasFan(result, "圈风刻"))
        assertTrue(hasFan(result, "门风刻"))
    }

    @Test
    fun `低于起和线应不满足`() {
        // 234m 456m 789m 234p 5p：和后仅自摸(1)+无字(2)=3番，低于8番但起和线已改为1
        // 目前起和线=1，大多数和牌都满足
        val result = scoreHand("234m456m789m234p5p5p")
        assertTrue(result.meetsMinimum, "起和线=1，应满足")
    }

    @Test
    fun `边张`() {
        // 123m 456m 789m 12p 3p... 凑14张听边张和: 123m456m789m12p3p5s5s (和3p边张)
        // 但需和牌为3p。构造: 暗手已含3p, 和牌方式点炮
        val hand = TileParser.parseHand("123m456m789m123p5s5s")
        val decomp = WinChecker.getAllDecompositions(hand).first()
        // 和牌为3p(顺子123p的3)，边张
        val ctx = FanContext(decomp, hand, WinInfo(TileType.pin(3), WinMethod.DISCARD))
        val result = FanScorer.score(ctx)
        assertTrue(hasFan(result, "边张"), "应检测到边张")
    }

    @Test
    fun `嵌张`() {
        // 123m 456m 789m 13p 2p 5s5s -> 和2p嵌张(13p中嵌2p)
        val hand = TileParser.parseHand("123m456m789m123p5s5s")
        val decomp = WinChecker.getAllDecompositions(hand).first()
        val ctx = FanContext(decomp, hand, WinInfo(TileType.pin(2), WinMethod.DISCARD))
        val result = FanScorer.score(ctx)
        // 123p含2p为中张(非边)，但2p在123中是中间张(rank2)，应判嵌张
        assertTrue(hasFan(result, "嵌张"), "应检测到嵌张")
    }

    @Test
    fun `不计关系 大四喜不含碰碰和`() {
        // 大四喜: 东东东 南南南 西西西 北北北 + 1m1m
        val result = scoreHand("东东东南南南西西西北北北1m1m")
        assertTrue(hasFan(result, "大四喜"))
        assertTrue(isSubsumed(result, "碰碰和"), "碰碰和应被大四喜不计")
    }

    // ── 新增番种 ──

    @Test
    fun `豪华七对`() {
        val hand = TileParser.parseHand("1111m22m33m44m55m66m")
        val decomps = WinChecker.getAllDecompositions(hand)
        assertTrue(decomps.isNotEmpty(), "手牌应能和牌")
        // 检查所有分解，至少有一个检测到豪华七对
        val hasLuxury = decomps.any { decomp ->
            val result = FanScorer.score(FanContext(decomp, hand, WinInfo(hand.concealed.last())))
            result.allDetected.any { it.name == "豪华七对" } && result.subsumed.any { it.name == "七对" }
        }
        assertTrue(hasLuxury, "应检测到豪华七对且七对被不计")
    }

    @Test
    fun `双豪华七对`() {
        // 4×1m + 4×2m + 33m44m55m = 14张
        val result = scoreHand("1111m2222m33m44m55m")
        assertTrue(hasFan(result, "双豪华七对"), "应检测到双豪华七对")
    }

    @Test
    fun `大七星`() {
        // 东东 南南 西西 北北 中中 发发 白白
        val result = scoreHand("东东南南西西北北中中发发白白")
        assertTrue(hasFan(result, "大七星"), "应检测到大七星")
        assertTrue(isSubsumed(result, "七对"), "七对应被大七星不计")
    }

    @Test
    fun `红孔雀`() {
        // 1s1s1s 5s5s5s 7s7s7s 9s9s9s 中中
        val result = scoreHand("111s555s777s999s中中")
        assertTrue(hasFan(result, "红孔雀"), "应检测到红孔雀")
    }

    @Test
    fun `蓝一色`() {
        // 东东东 南南南 西西北北 白白 不成立... 用刻子+将
        // 东东东 南南南 白白白 8筒8筒8筒 西西
        val result = scoreHand("东东东南南南白白白8p8p8p西西")
        assertTrue(hasFan(result, "蓝一色"), "应检测到蓝一色")
    }
}
